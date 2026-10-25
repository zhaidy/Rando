package com.personal.rando.routing

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * 「这里其实是马路」：OpenStreetMap 标成自行车道、小路，实际上是有车开的马路（比如 NUS 的 College Link Rd）。
 * 存的是那一段的形状；算出来的路线经过这里就按马路算，算路时也给它加上马路的代价
 */
data class RoadCorrection(val id: String, val points: List<LatLon>, val createdAt: Long) {

    /** [path] 大部分点都贴着这一段：就是同一段路 */
    fun covers(path: List<LatLon>): Boolean {
        if (path.size < 2 || points.size < 2) return false
        val near = path.count { p -> distanceTo(p) <= NEAR_METERS }
        return near >= path.size * COVER_SHARE
    }

    /** 和 [path] 是不是同一段：任一边大部分贴着另一边（标过的段可能和旁边真的马路连成一长段） */
    fun overlaps(path: List<LatLon>): Boolean = covers(path) || RoadCorrection(id, path, createdAt).covers(points)

    private fun distanceTo(p: LatLon): Double =
        points.zipWithNext().minOf { (a, b) -> Geo.project(p, a, b).second }

    companion object {
        const val NEAR_METERS = 25.0
        const val COVER_SHARE = 0.7
        /** 存的形状最多这么多个点，带进算路请求不会太长 */
        const val MAX_POINTS = 40

        fun new(points: List<LatLon>, now: Long = System.currentTimeMillis()) =
            RoadCorrection(UUID.randomUUID().toString(), thin(points), now)

        /** 点太多时均匀挑一些，首尾保留 */
        private fun thin(points: List<LatLon>): List<LatLon> {
            if (points.size <= MAX_POINTS) return points
            val step = (points.size - 1).toDouble() / (MAX_POINTS - 1)
            return (0 until MAX_POINTS).map { points[(it * step).toInt().coerceAtMost(points.size - 1)] }
        }
    }
}

object RoadCorrections {

    /** 改过的段在 WayTags 里加这个，界面上能认出是「你标的」 */
    const val TAG = "rando_corrected"

    /**
     * 路线里经过标过的地方的那几段，改成按马路算（highway=residential，没人行道）。
     * 路的种类、马路长度、语音提醒都跟着变
     */
    fun apply(route: Route, corrections: List<RoadCorrection>): Route {
        if (corrections.isEmpty() || route.segments.any { it.endIndex < 0 }) return route
        var from = 0
        var changed = false
        val segments = route.segments.map { s ->
            val to = s.endIndex.coerceIn(from, route.points.size - 1)
            val path = route.points.subList(from, to + 1)
            from = to
            if (RoadKind.of(s.wayTags) in ROAD_KINDS || corrections.none { it.covers(path) }) {
                s
            } else {
                changed = true
                s.copy(wayTags = s.wayTags - "sidewalk" + ("highway" to "residential") + (TAG to "road"))
            }
        }
        return if (changed) route.copy(segments = segments) else route
    }

    /**
     * 开「不上马路」时，算路请求里给标过的地方加的避让线（BRouter 的 polylines，最后一个数是代价）。
     * 代价按长度算，和小路上的马路差不多贵：有别的路就绕，实在没有还是会走。
     * 没开「不上马路」时马路也就比自行车道贵一点，不用加
     */
    fun polylines(stops: List<LatLon>, corrections: List<RoadCorrection>, avoidRoads: Boolean): String? {
        if (!avoidRoads || corrections.isEmpty() || stops.isEmpty()) return null
        val minLat = stops.minOf { it.lat } - MARGIN_DEG
        val maxLat = stops.maxOf { it.lat } + MARGIN_DEG
        val minLon = stops.minOf { it.lon } - MARGIN_DEG
        val maxLon = stops.maxOf { it.lon } + MARGIN_DEG
        val near = corrections.filter { c -> c.points.any { it.lat in minLat..maxLat && it.lon in minLon..maxLon } }
        if (near.isEmpty()) return null
        return near.joinToString("|") { c ->
            c.points.joinToString(",") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) } + "," + weight(c)
        }
    }

    /** 试过 College Link Rd（约 300 米）：代价 300 走原路，1000 就绕开了 */
    fun weight(c: RoadCorrection): Int =
        (c.points.zipWithNext().sumOf { (a, b) -> Geo.distance(a, b) } * WEIGHT_PER_METER).toInt().coerceAtLeast(MIN_WEIGHT)

    private const val MARGIN_DEG = 0.05
    private const val WEIGHT_PER_METER = 3.0
    private const val MIN_WEIGHT = 100
}

/** 标过的「其实是马路」，存在手机里 */
class RoadCorrectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("road_corrections", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<RoadCorrection>> = _all

    fun add(points: List<LatLon>) = save(_all.value + RoadCorrection.new(points))

    /** 撤销：去掉盖住 [path] 的那些 */
    fun removeCovering(path: List<LatLon>) = save(_all.value.filterNot { it.overlaps(path) })

    private fun save(list: List<RoadCorrection>) {
        _all.value = list
        prefs.edit { putString(KEY, encode(list)) }
    }

    private fun load(): List<RoadCorrection> =
        prefs.getString(KEY, null)?.let { runCatching { decode(it) }.getOrNull() }.orEmpty()

    companion object {
        private const val KEY = "list"

        fun encode(list: List<RoadCorrection>): String = JSONArray().apply {
            list.forEach { c ->
                put(
                    JSONObject().put("id", c.id).put("ts", c.createdAt)
                        .put("p", JSONArray().apply { c.points.forEach { put(JSONArray().put(it.lat).put(it.lon)) } })
                )
            }
        }.toString()

        fun decode(json: String): List<RoadCorrection> {
            val arr = JSONArray(json)
            // 一条坏了只跳过那条，不要下次保存时把其他的全冲掉
            return (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("p")
                    RoadCorrection(o.getString("id"), (0 until p.length()).map { p.getJSONArray(it).let { q -> LatLon(q.getDouble(0), q.getDouble(1)) } }, o.getLong("ts"))
                }.getOrNull()
            }
        }
    }
}
