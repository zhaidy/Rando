package com.personal.rando.places

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RouteTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 沿途补给：饮水机、厕所、便利店、维修站（公共修车点、车店）、避雨亭；终点附近的停车架 */
enum class AmenityType(val label: String, val color: String, val unit: String) {
    WATER("饮水机", "#1E88E5", "个"),
    TOILET("厕所", "#8E24AA", "个"),
    SHOP("便利店", "#F4511E", "家"),
    REPAIR("维修站", "#43A047", "个"),
    SHELTER("避雨亭", "#6D4C41", "个"),
    /** 新加坡组屋楼下到处都是，沿途不查，只查终点附近的 */
    PARKING("停车架", "#546E7A", "处");

    companion object {
        fun of(tags: Map<String, String>): AmenityType? = when {
            tags["amenity"] == "drinking_water" || (tags["amenity"] == "water_point" && tags["drinking_water"] == "yes") -> WATER
            tags["amenity"] == "toilets" -> TOILET
            tags["shop"] == "convenience" -> SHOP
            tags["amenity"] == "bicycle_repair_station" || tags["shop"] == "bicycle" -> REPAIR
            tags["amenity"] == "shelter" -> SHELTER
            tags["amenity"] == "bicycle_parking" -> PARKING
            else -> null
        }
    }
}

data class Amenity(val type: AmenityType, val location: LatLon, val name: String?)

/** 沿途查到的：补给点，和路线旁边有顶棚的路（每条是一串坐标） */
data class AlongRoute(val amenities: List<Amenity>, val covered: List<List<LatLon>>)

/** 一个补给点在路线上的位置：第几米，离路线多远 */
data class AmenityOnRoute(val amenity: Amenity, val alongMeters: Double, val offRouteMeters: Double)

/**
 * 从 OpenStreetMap（Overpass API）查路线两旁的补给点。
 * 查的是路线两边 [RADIUS_METERS] 米以内的，太远的绕过去不值
 */
class AmenityClient(
    private val http: OkHttpClient = OkHttpClient.Builder().readTimeout(40, TimeUnit.SECONDS).build(),
    private val servers: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    ),
) {
    suspend fun along(points: List<LatLon>): AlongRoute = withContext(Dispatchers.IO) {
        val query = AmenityQuery.build(points)
        var error: Exception? = null
        // 公共服务器忙的时候会超时，换一个再试
        for (server in servers) {
            try {
                val request = Request.Builder().url(server).post(FormBody.Builder().add("data", query).build()).build()
                return@withContext http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("补给点查询 HTTP ${response.code}")
                    val json = response.body.string()
                    AlongRoute(AmenityQuery.parse(json), AmenityQuery.parseCovered(json))
                }
            } catch (e: IOException) {
                error = e
            }
        }
        throw error ?: IOException("补给点查询失败")
    }
}

object AmenityQuery {

    const val RADIUS_METERS = 80
    /** 有顶棚的路：离路线这么近才算是路线本身经过的 */
    const val COVERED_METERS = 15
    /** 终点附近这么远以内的停车架 */
    const val PARKING_RADIUS_METERS = 200
    /** 停车架最多列这么多处，最近的几处 */
    const val PARKING_LIMIT = 3

    /** 路线抽稀到每隔这么远一个点，查询语句不会太长 */
    private const val STEP_METERS = 150.0

    /**
     * Overpass 的 around 后面跟一串坐标时，按折线算距离；
     * 便利店、厕所多在楼里，用 nwr 把画成面的也查出来，out center 给中心点
     */
    fun build(points: List<LatLon>): String {
        val line = thin(points).joinToString(",") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }
        val around = "(around:$RADIUS_METERS,$line)"
        val end = points.last().let { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }
        return """
            [out:json][timeout:30];
            (
              nwr$around[amenity~"^(drinking_water|toilets|bicycle_repair_station|shelter)$"];
              nwr$around[shop~"^(convenience|bicycle)$"];
              nwr(around:$PARKING_RADIUS_METERS,$end)[amenity=bicycle_parking];
            );
            out center tags;
            way(around:$COVERED_METERS,$line)[highway][highway!=construction][covered~"^(yes|arcade|colonnade)$"];
            out tags geom;
        """.trimIndent()
    }

    fun parse(json: String): List<Amenity> {
        val elements = JSONObject(json).getJSONArray("elements")
        return (0 until elements.length()).mapNotNull {
            val e = elements.getJSONObject(it)
            val tagsJson = e.optJSONObject("tags") ?: return@mapNotNull null
            val tags = tagsJson.keys().asSequence().associateWith { k -> tagsJson.getString(k) }
            val type = AmenityType.of(tags) ?: return@mapNotNull null
            val at = when {
                e.has("lat") -> LatLon(e.getDouble("lat"), e.getDouble("lon"))
                e.has("center") -> e.getJSONObject("center").let { c -> LatLon(c.getDouble("lat"), c.getDouble("lon")) }
                else -> return@mapNotNull null
            }
            Amenity(type, at, tags["name"] ?: tags["brand"])
        }
    }

    /** 有顶棚的路（连廊、骑楼）：带 geometry 的 way */
    fun parseCovered(json: String): List<List<LatLon>> {
        val elements = JSONObject(json).getJSONArray("elements")
        return (0 until elements.length()).mapNotNull {
            val e = elements.getJSONObject(it)
            if (e.optJSONObject("tags")?.optString("covered").isNullOrEmpty()) return@mapNotNull null
            val g = e.optJSONArray("geometry") ?: return@mapNotNull null
            (0 until g.length()).map { i -> g.getJSONObject(i).let { p -> LatLon(p.getDouble("lat"), p.getDouble("lon")) } }
                .takeIf { line -> line.size >= 2 }
        }
    }

    private fun thin(points: List<LatLon>): List<LatLon> {
        if (points.size <= 2) return points
        val result = mutableListOf(points.first())
        for (p in points.drop(1)) if (Geo.distance(result.last(), p) >= STEP_METERS) result += p
        if (result.last() != points.last()) result += points.last()
        return result
    }
}

/** 补给点同类的挨得这么近算一处（一个饮水点常常画了好几个水龙头） */
private const val SAME_SPOT_METERS = 40.0

/**
 * 把补给点排到路线上：按全程第几米排好，离路线太远的不要，同类挨着的并成一处
 */
fun List<Amenity>.onRoute(timeline: RouteTimeline): List<AmenityOnRoute> {
    // 停车架只要终点附近最近的几处，排在终点
    val end = timeline.end
    val parking = filter { it.type == AmenityType.PARKING }
        .map { it to (end?.let { e -> Geo.distance(e, it.location) } ?: Double.MAX_VALUE) }
        .filter { (_, d) -> d <= AmenityQuery.PARKING_RADIUS_METERS * 1.25 }
        .sortedBy { it.second }
        .distinctBy { (a, _) -> a.location.let { (it.lat * 2500).toInt() to (it.lon * 2500).toInt() } }
        .take(AmenityQuery.PARKING_LIMIT)
        .map { (a, d) -> AmenityOnRoute(a, timeline.total, d) }
    val placed = filter { it.type != AmenityType.PARKING }.mapNotNull { a ->
        timeline.locate(a.location)
            ?.takeIf { (_, off) -> off <= AmenityQuery.RADIUS_METERS * 1.5 }
            ?.let { (along, off) -> AmenityOnRoute(a, along, off) }
    }.sortedBy { it.alongMeters }
    val result = mutableListOf<AmenityOnRoute>()
    for (p in placed) {
        val dup = result.lastOrNull { it.amenity.type == p.amenity.type }
            ?.takeIf { Geo.distance(it.amenity.location, p.amenity.location) < SAME_SPOT_METERS }
        if (dup == null) result += p
    }
    return result + parking
}

/** 前方最近的每种补给，离现在多远；[along] 是现在骑到第几米 */
fun List<AmenityOnRoute>.nextOfEach(along: Double): Map<AmenityType, Double> =
    filter { it.alongMeters > along }
        .groupBy { it.amenity.type }
        .mapValues { (_, list) -> list.minOf { it.alongMeters } - along }
