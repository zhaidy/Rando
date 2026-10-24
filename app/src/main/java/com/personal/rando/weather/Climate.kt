package com.personal.rando.weather

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/** 空气质量：按新加坡 NEA 的 24 小时 PSI 分档 */
enum class Haze(val label: String, val max: Int) {
    GOOD("良好", 50),
    MODERATE("一般", 100),
    UNHEALTHY("不健康", 200),
    VERY_UNHEALTHY("非常不健康", 300),
    HAZARDOUS("危险", Int.MAX_VALUE);

    companion object {
        fun of(psi: Int): Haze = entries.first { psi <= it.max }
    }
}

/** 热的程度：按体感温度（热指数） */
enum class Heat(val label: String) {
    OK("不算热"),
    HOT("很热"),
    EXTREME("酷热");

    companion object {
        fun of(feelsLike: Double): Heat = when {
            feelsLike >= 39 -> EXTREME
            feelsLike >= 32 -> HOT
            else -> OK
        }
    }
}

/** 某个地方现在的气温、湿度、紫外线、空气质量；拿不到的是 null */
data class Climate(
    val tempC: Double? = null,
    val humidity: Double? = null,
    val uv: Int? = null,
    val psi: Int? = null,
    /** PSI 是哪一区的：north / south / east / west / central */
    val psiRegion: String? = null,
) {
    /** 体感温度（美国气象局的热指数公式）；气温低于 27°C 时差不多就是气温 */
    val feelsLike: Double?
        get() {
            val t = tempC ?: return null
            val rh = humidity ?: return t
            if (t < 27) return t
            val f = t * 9 / 5 + 32
            val hi = -42.379 + 2.04901523 * f + 10.14333127 * rh - 0.22475541 * f * rh - 6.83783e-3 * f * f -
                5.481717e-2 * rh * rh + 1.22874e-3 * f * f * rh + 8.5282e-4 * f * rh * rh - 1.99e-6 * f * f * rh * rh
            return maxOf(t, (hi - 32) * 5 / 9)
        }

    val heat: Heat? get() = feelsLike?.let(Heat::of)
    val haze: Haze? get() = psi?.let(Haze::of)

    /** 热、紫外线强、空气不好，都要多补水、少在大太阳下骑 */
    val hot: Boolean get() = (heat ?: Heat.OK) != Heat.OK

    /** 路线卡片上的一行：「31°C，体感 38°C · 紫外线 8 很强 · PSI 148 不健康」 */
    val summary: String?
        get() = listOfNotNull(
            tempC?.let { t ->
                val feels = feelsLike?.takeIf { it - t >= 2 }?.let { "，体感 ${it.roundToInt()}°C" }.orEmpty()
                "${t.roundToInt()}°C$feels"
            },
            uv?.takeIf { it >= UV_HIGH }?.let { "紫外线 $it ${if (it >= UV_VERY_HIGH) "很强" else "强"}" },
            psi?.let { "PSI $it ${Haze.of(it).label}" },
        ).joinToString(" · ").ifEmpty { null }

    /** 要不要特别提醒：酷热、紫外线很强、空气不健康 */
    val warning: String?
        get() = listOfNotNull(
            "体感 ${feelsLike?.roundToInt()}°C，注意防暑多喝水".takeIf { heat == Heat.EXTREME },
            "空气${haze?.label}（PSI $psi），少骑一会儿".takeIf { (haze?.ordinal ?: 0) >= Haze.UNHEALTHY.ordinal },
            "紫外线很强，记得防晒".takeIf { (uv ?: 0) >= UV_VERY_HIGH },
        ).joinToString("；").ifEmpty { null }

    companion object {
        const val UV_HIGH = 6
        const val UV_VERY_HIGH = 8
    }
}

object ClimateParser {

    /** 气温、湿度：离 [at] 最近的有读数的站 */
    fun nearestReading(json: String, at: LatLon): Double? {
        val data = JSONObject(json).getJSONObject("data")
        val stations = data.getJSONArray("stations")
        val where = (0 until stations.length()).associate {
            val s = stations.getJSONObject(it)
            val l = s.getJSONObject("location")
            s.getString("id") to LatLon(l.getDouble("latitude"), l.getDouble("longitude"))
        }
        val readings = data.getJSONArray("readings").optJSONObject(0)?.getJSONArray("data") ?: return null
        return (0 until readings.length())
            .map { readings.getJSONObject(it) }
            .mapNotNull { r -> where[r.getString("stationId")]?.let { Geo.distance(it, at) to r.getDouble("value") } }
            .minByOrNull { it.first }?.second
    }

    /** 紫外线：最新一个小时的 */
    fun uv(json: String): Int? {
        val records = JSONObject(json).getJSONObject("data").getJSONArray("records")
        val index = records.optJSONObject(0)?.getJSONArray("index") ?: return null
        return (0 until index.length()).map { index.getJSONObject(it) }.maxByOrNull { it.getString("hour") }?.getInt("value")
    }

    /** 24 小时 PSI：[at] 所在那一区的；那一区没数就取全岛最高的 */
    fun psi(json: String, at: LatLon): Pair<String, Int>? {
        val data = JSONObject(json).getJSONObject("data")
        val readings = data.getJSONArray("items").optJSONObject(0)
            ?.getJSONObject("readings")?.getJSONObject("psi_twenty_four_hourly") ?: return null
        val region = regionOf(at)
        if (readings.has(region)) return region to readings.getInt(region)
        return readings.keys().asSequence().map { it to readings.getInt(it) }.maxByOrNull { it.second }
    }

    /**
     * 新加坡分五区（NEA 的划法，粗略按经纬度）：中区是市中心到碧山一带，
     * 北边过了 1.40 是北区，南边海边是南区，西边过了 103.78 是西区，东边过了 103.88 是东区
     */
    fun regionOf(at: LatLon): String = when {
        at.lat > 1.40 -> "north"
        at.lon < 103.78 -> "west"
        at.lon > 103.88 -> "east"
        at.lat < 1.29 -> "south"
        else -> "central"
    }
}

/** 气温、湿度、紫外线、PSI：data.gov.sg 的开放数据，哪个拿不到就空着 */
class ClimateClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api-open.data.gov.sg/v2/real-time/api",
) {
    suspend fun at(where: LatLon): Climate = withContext(Dispatchers.IO) {
        coroutineScope {
            val temp = async { runCatching { ClimateParser.nearestReading(get("air-temperature"), where) }.getOrNull() }
            val humidity = async { runCatching { ClimateParser.nearestReading(get("relative-humidity"), where) }.getOrNull() }
            val uv = async { runCatching { ClimateParser.uv(get("uv")) }.getOrNull() }
            val psi = async { runCatching { ClimateParser.psi(get("psi"), where) }.getOrNull() }
            val p = psi.await()
            Climate(temp.await(), humidity.await(), uv.await(), p?.second, p?.first)
        }
    }

    private fun get(name: String): String =
        http.newCall(Request.Builder().url("$baseUrl/$name").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("天气数据 HTTP ${response.code}")
            response.body.string()
        }
}

/**
 * 骑行中提醒喝水：骑了一段时间说一声，热的时候隔得短一点。
 * 前方有饮水机时顺便告诉还有多远
 */
class HydrationReminder(
    private val normalMs: Long = 30 * 60_000L,
    private val hotMs: Long = 20 * 60_000L,
) {
    private var lastAt = 0L

    fun reset(startedAt: Long) {
        lastAt = startedAt
    }

    /** [now] 现在；[startedAt] 开始骑的时间；返回要说的话 */
    fun next(now: Long, startedAt: Long, hot: Boolean, waterAheadMeters: Double?): String? {
        if (lastAt < startedAt) lastAt = startedAt
        if (now - lastAt < if (hot) hotMs else normalMs) return null
        lastAt = now
        val minutes = ((now - startedAt) / 60_000).toInt()
        val water = waterAheadMeters?.takeIf { it <= WATER_MENTION_METERS }
            ?.let { "，前方 ${Format.distance(it.toInt())} 有饮水机" }.orEmpty()
        return "骑了 $minutes 分钟，喝口水吧$water"
    }

    private companion object {
        const val WATER_MENTION_METERS = 3_000.0
    }
}
