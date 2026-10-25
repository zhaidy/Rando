package com.personal.rando.routing

import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 向 BRouter 公共服务器要骑行路线 */
class BRouterClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://brouter.de/brouter",
    /** 要绕开的点（标了施工、封路的地方） */
    private val avoid: () -> List<LatLon> = { emptyList() },
    /** 标过「其实是马路」的地方 */
    private val corrections: () -> List<RoadCorrection> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun route(from: LatLon, to: LatLon, prefs: RoutePrefs): Route = route(listOf(from, to), prefs)

    /** 按顺序经过 [stops]：第一个是起点，最后一个是终点，中间是途经点；[alternative] 是第几条备选（0 是最好的那条） */
    suspend fun route(stops: List<LatLon>, asked: RoutePrefs, alternative: Int = 0): Route = withContext(Dispatchers.IO) {
        // 中午挑树荫、天黑挑路灯：按现在的时间，规划和导航中重新算路都一样
        val prefs = with(RideTime) { asked.at(clock()) }
        val watts = prefs.pace.watts
        val nogos = Nogos.near(stops, avoid())
        val marked = corrections()
        val polylines = RoadCorrections.polylines(stops, marked, prefs.avoidRoads)
        val route = run {
            val custom = prefs.customProfile ?: return@run fetch(url(stops, prefs.profile.brouterName, watts, nogos, polylines, alternative))
            // 服务器上传过的配置过一阵会被清掉，这时返回 500（没有说明）；重新上传再试一次。
            // 超时、点不可达这些重新上传也没用，直接报错，免得白等一轮
            val cached = uploaded[custom]
            if (cached != null) {
                try {
                    return@run fetch(url(stops, cached, watts, nogos, polylines, alternative))
                } catch (e: RouteHttpException) {
                    if (!e.profileGone) throw e
                    uploaded.remove(custom)
                }
            }
            fetch(url(stops, upload(custom), watts, nogos, polylines, alternative))
        }
        RoadCorrections.apply(route, marked).let { it.copy(durationSeconds = prefs.duration(it)) }
    }

    private fun fetch(url: String): Route =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body.string()
            // 出错时服务器返回纯文本说明，比如 "target island detected"
            if (!response.isSuccessful) {
                val explained = explain(body.trim())
                throw RouteHttpException(explained.ifEmpty { "HTTP ${response.code}" }, profileGone = response.code == 500 && !known(body))
            }
            RouteParser.parse(body)
        }

    /** 算路本身的出错（点不可达之类），不是配置没了 */
    private fun known(message: String): Boolean = "island" in message || "not mapped" in message || "no track found" in message

    /** [profileGone]：可能是上传的配置被服务器清掉了，值得重新上传再试 */
    private class RouteHttpException(message: String, val profileGone: Boolean) : IOException(message)

    /** 常见的几种出错换成中文 */
    private fun explain(message: String): String = when {
        "island" in message -> tr("有个点在走不通的地方（小岛、封闭区域），换个点试试", "A point is somewhere unreachable (an island or closed area), try another point")
        "not mapped" in message || "no track found" in message -> tr("附近找不到能骑的路", "No rideable path nearby")
        else -> message
    }

    /** 上传自己的配置，换回 custom_xxx 的配置名 */
    private fun upload(profile: String): String {
        val request = Request.Builder()
            .url("$baseUrl/profile")
            .post(profile.toRequestBody("text/plain".toMediaType()))
            .build()
        val json = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(tr("上传路线配置失败（HTTP ${response.code}）", "Failed to upload route profile (HTTP ${response.code})"))
            JSONObject(response.body.string())
        }
        json.optString("error").takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
        return json.getString("profileid").also { uploaded[profile] = it }
    }

    /** [watts]：骑手平均功率，只影响预计时间，不影响走哪条路；null 用配置里的默认（100 瓦） */
    fun url(stops: List<LatLon>, profileName: String, watts: Int? = null, nogos: List<LatLon> = emptyList(), polylines: String? = null, alternative: Int = 0): String =
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("lonlats", stops.joinToString("|", transform = ::coord))
            .addQueryParameter("profile", profileName)
            .addQueryParameter("alternativeidx", alternative.toString())
            .addQueryParameter("format", "geojson")
            // 带上路口转向提示（voicehints），导航播报要用
            .addQueryParameter("timode", "2")
            .apply { if (watts != null) addQueryParameter("profile:bikerPower", watts.toString()) }
            .apply {
                if (nogos.isNotEmpty()) {
                    addQueryParameter("nogos", nogos.joinToString("|") { String.format(Locale.US, "%.6f,%.6f,%d", it.lon, it.lat, Nogos.RADIUS_METERS) })
                }
            }
            .apply { if (polylines != null) addQueryParameter("polylines", polylines) }
            .build()
            .toString()

    private fun coord(p: LatLon) = String.format(Locale.US, "%.6f,%.6f", p.lon, p.lat)

    private companion object {
        /** 配置内容 → 服务器给的名字；进程内共用，规划和导航里重新算路都不用重复上传 */
        val uploaded = ConcurrentHashMap<String, String>()
    }
}

/** 算路时绕开的点：每个点周围 [RADIUS_METERS] 米不走 */
object Nogos {
    const val RADIUS_METERS = 30
    /** 离起点、途经点、终点太近的不绕：要去的地方本身就在里面会算不出路 */
    private const val KEEP_CLEAR_METERS = 80.0
    /** 只带路线附近的：起点终点围出的范围再往外扩这么多（度，约 5 公里） */
    private const val MARGIN_DEG = 0.05
    private const val MAX = 50

    fun near(stops: List<LatLon>, points: List<LatLon>): List<LatLon> {
        if (stops.isEmpty() || points.isEmpty()) return emptyList()
        val minLat = stops.minOf { it.lat } - MARGIN_DEG
        val maxLat = stops.maxOf { it.lat } + MARGIN_DEG
        val minLon = stops.minOf { it.lon } - MARGIN_DEG
        val maxLon = stops.maxOf { it.lon } + MARGIN_DEG
        return points
            .filter { it.lat in minLat..maxLat && it.lon in minLon..maxLon }
            .filter { p -> stops.none { Geo.distance(it, p) < KEEP_CLEAR_METERS } }
            .take(MAX)
    }
}
