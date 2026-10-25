package com.personal.rando.weather

import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** 某一区未来两小时的天气，比如 Bedok「Thundery Showers」 */
data class AreaForecast(val area: String, val location: LatLon, val forecast: String) {
    /** 会不会下雨 */
    val wet: Boolean get() = WET.any { it in forecast }
    val thunder: Boolean get() = "Thunder" in forecast
    val heavy: Boolean get() = "Heavy" in forecast

    /** 「雷阵雨」「大雨」「小雨」「阵雨」；不下雨是 null */
    val rainText: String?
        get() = when {
            !wet -> null
            thunder -> tr("雷阵雨", "thundery showers")
            heavy -> tr("大雨", "heavy rain")
            "Light" in forecast || "Passing" in forecast -> tr("小雨", "light rain")
            else -> tr("阵雨", "showers")
        }

    private companion object {
        val WET = listOf("Rain", "Shower", "Thunder", "Drizzle")
    }
}

/** 雨量站最近 5 分钟的雨量（毫米） */
data class RainGauge(val name: String, val location: LatLon, val millimeters: Double)

data class Weather(
    val forecasts: List<AreaForecast>,
    /** 预报管到几点，比如「11.00 pm to 1.00 am」；没有时是 null */
    val validText: String? = null,
    val gauges: List<RainGauge> = emptyList(),
)

/** 沿路的天气：哪些区两小时内会下雨，现在哪里在下 */
data class RouteWeather(
    /** 会下雨的区，按路线先后 */
    val wetAreas: List<AreaForecast>,
    /** 正在下雨的雨量站 */
    val rainingNow: List<RainGauge>,
) {
    val dry: Boolean get() = wetAreas.isEmpty() && rainingNow.isEmpty()

    /** 一句话，路线卡片上和语音里用 */
    val summary: String
        get() = when {
            rainingNow.isNotEmpty() -> tr("${rainingNow.first().name} 附近正在下雨", "Raining near ${rainingNow.first().name}") +
                (wetAreas.firstOrNull()?.let {
                    tr("，两小时内 ${names(wetAreas)} 一带有${worst(wetAreas)}", ", ${worst(wetAreas)} around ${names(wetAreas)} within 2 hours")
                } ?: "")
            wetAreas.isNotEmpty() -> tr("两小时内 ${names(wetAreas)} 一带可能有${worst(wetAreas)}", "Possible ${worst(wetAreas)} around ${names(wetAreas)} within 2 hours")
            else -> tr("沿途两小时内不会下雨", "No rain along the route for the next 2 hours")
        }

    private fun names(areas: List<AreaForecast>) =
        areas.take(3).joinToString(tr("、", ", ")) { it.area } + if (areas.size > 3) tr(" 等", " etc.") else ""

    private fun worst(areas: List<AreaForecast>) = when {
        areas.any { it.thunder } -> tr("雷阵雨", "thundery showers")
        else -> areas.firstOrNull { it.heavy }?.rainText ?: areas.first().rainText ?: tr("雨", "rain")
    }
}

/**
 * 只看路线附近：区的中心离路线 [areaMeters] 以内、雨量站离路线 [gaugeMeters] 以内。
 * [points] 可以是整条路线，也可以只是前方还没骑的那段
 */
fun Weather.along(points: List<LatLon>, areaMeters: Double = AREA_METERS, gaugeMeters: Double = GAUGE_METERS): RouteWeather {
    val sample = points.sampled(SAMPLE_METERS)
    fun firstNear(p: LatLon, radius: Double): Int? = sample.indexOfFirst { Geo.distance(it, p) <= radius }.takeIf { it >= 0 }
    val wet = forecasts.filter { it.wet }
        .mapNotNull { f -> firstNear(f.location, areaMeters)?.let { it to f } }
        .sortedBy { it.first }
        .map { it.second }
    val raining = gauges.filter { it.millimeters > 0 }
        .mapNotNull { g -> firstNear(g.location, gaugeMeters)?.let { it to g } }
        .sortedBy { it.first }
        .map { it.second }
    return RouteWeather(wet, raining)
}

/** 区的中心点离路线这么近，就算路过这个区 */
const val AREA_METERS = 3000.0
/** 雨量站离路线这么近，那里下雨就算路上在下 */
const val GAUGE_METERS = 2000.0
private const val SAMPLE_METERS = 300.0

/** 每隔大约 [meters] 取一个点，算距离时不用每个坐标都比 */
private fun List<LatLon>.sampled(meters: Double): List<LatLon> {
    if (size <= 2) return this
    val result = mutableListOf(first())
    var along = 0.0
    for (i in 1 until size) {
        along += Geo.distance(this[i - 1], this[i])
        if (along >= meters) {
            result += this[i]
            along = 0.0
        }
    }
    if (result.last() != last()) result += last()
    return result
}

/** 解析 data.gov.sg 的两小时预报和雨量 */
object WeatherParser {

    /** /v2/real-time/api/two-hr-forecast */
    fun forecasts(json: String): Pair<List<AreaForecast>, String?> {
        val data = JSONObject(json).getJSONObject("data")
        val meta = data.getJSONArray("area_metadata")
        val locations = (0 until meta.length()).associate {
            val m = meta.getJSONObject(it)
            val l = m.getJSONObject("label_location")
            m.getString("name") to LatLon(l.getDouble("latitude"), l.getDouble("longitude"))
        }
        val items = data.getJSONArray("items")
        if (items.length() == 0) return emptyList<AreaForecast>() to null
        val item = items.getJSONObject(items.length() - 1)
        val list = item.getJSONArray("forecasts")
        val forecasts = (0 until list.length()).mapNotNull {
            val f = list.getJSONObject(it)
            val area = f.getString("area")
            locations[area]?.let { at -> AreaForecast(area, at, f.getString("forecast")) }
        }
        val valid = item.optJSONObject("valid_period")?.optString("text")?.takeIf { it.isNotEmpty() }
        return forecasts to valid
    }

    /** /v2/real-time/api/rainfall：取最新一次读数 */
    fun rainfall(json: String): List<RainGauge> {
        val data = JSONObject(json).getJSONObject("data")
        val stations = data.getJSONArray("stations")
        val byId = (0 until stations.length()).associate {
            val s = stations.getJSONObject(it)
            val l = s.getJSONObject("location")
            s.getString("id") to (s.getString("name") to LatLon(l.getDouble("latitude"), l.getDouble("longitude")))
        }
        val readings = data.getJSONArray("readings")
        if (readings.length() == 0) return emptyList()
        val latest = readings.getJSONObject(0).getJSONArray("data")
        return (0 until latest.length()).mapNotNull {
            val r = latest.getJSONObject(it)
            val (name, at) = byId[r.getString("stationId")] ?: return@mapNotNull null
            RainGauge(name, at, r.optDouble("value", 0.0))
        }
    }
}

/** 新加坡气象局的开放数据（data.gov.sg），不需要 key */
class WeatherClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api-open.data.gov.sg/v2/real-time/api",
) {
    suspend fun fetch(): Weather = withContext(Dispatchers.IO) {
        coroutineScope {
            val forecast = async { get("$baseUrl/two-hr-forecast") }
            // 雨量拿不到不要紧，预报还能用
            val rain = async { runCatching { WeatherParser.rainfall(get("$baseUrl/rainfall")) }.getOrDefault(emptyList()) }
            val (forecasts, valid) = WeatherParser.forecasts(forecast.await())
            Weather(forecasts, valid, rain.await())
        }
    }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException(tr("天气数据 HTTP ${response.code}", "Weather data HTTP ${response.code}"))
            response.body.string()
        }
}
