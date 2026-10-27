package com.personal.rando.nav

import com.personal.rando.i18n.tr
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Route
import com.personal.rando.ui.Format
import kotlin.math.abs

data class NavProgress(
    /** 沿路线已经骑了多远 */
    val alongMeters: Double,
    val remainingMeters: Double,
    val remainingSeconds: Int,
    val next: Maneuver?,
    val distanceToNext: Double,
    /** 下一个动作之后紧跟着的那个，离得近时一起播 */
    val following: Maneuver?,
    val distanceNextToFollowing: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    /** 路线从这儿往前的方向（正北 0°，顺时针）；停下等灯时地图朝这边 */
    val heading: Double? = null,
    /** 骑偏了：最近能回到原路线的地方；新路线还没算出来时先告诉人往哪边回 */
    val rejoin: Rejoin? = null,
)

/** 回到原路线最近的地方：[meters] 米远，在 [relative] 方向（相对骑行方向，0° 正前方，顺时针；不知道骑行方向时是 null） */
data class Rejoin(val meters: Double, val relative: Double?) {
    /** 「原路线在右后方 120 米」 */
    val text: String
        get() {
            val d = Format.distance(meters.toInt())
            val side = relative?.let { side(it) } ?: return tr("离原路线 $d", "$d from the route")
            return tr("原路线在${side.first} $d", "route is $d ${side.second}")
        }

    private fun side(degrees: Double): Pair<String, String> = when (((degrees % 360) + 360) % 360) {
        in 22.5..67.5 -> "右前方" to "ahead on your right"
        in 67.5..112.5 -> "右边" to "to your right"
        in 112.5..157.5 -> "右后方" to "behind you on the right"
        in 157.5..202.5 -> "后方" to "behind you"
        in 202.5..247.5 -> "左后方" to "behind you on the left"
        in 247.5..292.5 -> "左边" to "to your left"
        in 292.5..337.5 -> "左前方" to "ahead on your left"
        else -> "前方" to "ahead"
    }
}

/** 把定位对到路线上，算出离下一个路口多远、是不是骑偏了 */
class Navigator(val route: Route) {

    private val points = route.points
    /** cumulative[i]：从起点沿路线到第 i 个坐标的距离 */
    private val cumulative = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    private val total = cumulative.lastOrNull() ?: 0.0

    private var segment = 0
    private var along = 0.0
    private var missedFixes = 0
    /** 对上过路线没有；第一次对上的不算跳 */
    private var located = false

    /** [bearing]：骑行方向（正北 0°），在骑时才有；离路线有点远、方向又不对，不用等到 [OFF_ROUTE_METERS] 就算偏 */
    fun update(location: LatLon, bearing: Double? = null): NavProgress {
        // 先在当前位置附近往前找，找不到再全线找（比如抄了近路又回到路线上）
        val found = (
            nearest(location, (segment - 3)..(segment + 80))?.takeIf { it.distance <= OFF_ROUTE_METERS }
                ?: nearest(location, 0 until points.size - 1)?.takeIf { it.distance <= OFF_ROUTE_METERS }
            )?.takeIf { s -> !(bearing != null && s.distance > WRONG_WAY_METERS && angle(bearing, segmentBearing(s.segment)) > WRONG_WAY_DEGREES) }
        // 一下子跳很远（抄近路、出隧道）要连着几次都在那边才跳：环线起点终点挨着，起步时飘一下别跳到终点算到达；
        // 还没骑到一半就直接跳到终点的不跳，当成骑偏了
        val snapped = found?.takeIf { s ->
            !located || abs(s.along - along) <= JUMP_METERS ||
                (missedFixes >= JUMP_AFTER_MISSES && !(total - s.along <= ARRIVED_METERS && along < total / 2))
        }
        if (snapped != null) {
            segment = snapped.segment
            along = snapped.along
            missedFixes = 0
            located = true
        } else {
            missedFixes++
        }
        val progress = progress()
        if (!progress.offRoute) return progress
        // 往前找最近的那段（抄近路接回后面也算）；骑过的不算，不然总是叫人掉头回去
        val back = nearest(location, (segment - 3) until points.size - 1, followProgress = false) ?: return progress
        val at = pointAt(back.along)
        val relative = bearing?.let { (Geo.bearing(location, at) - it + 360) % 360 }
        return progress.copy(rejoin = Rejoin(back.distance, relative))
    }

    private fun segmentBearing(i: Int): Double = Geo.bearing(points[i], points[i + 1])

    /** 两个方向差多少度（0..180） */
    private fun angle(a: Double, b: Double): Double = abs(((a - b) % 360 + 540) % 360 - 180)

    /** 某个点对到路线上，离起点多远（米）；不跟着当前进度走，全线找最近的 */
    fun alongOf(p: LatLon): Double = nearest(p, 0 until points.size - 1, followProgress = false)?.along ?: 0.0

    private fun progress(): NavProgress {
        val upcoming = route.maneuvers.filter { cumulative[it.pointIndex] > along + PASSED_METERS }
        val next = upcoming.getOrNull(0)
        val following = upcoming.getOrNull(1)
        val remaining = (total - along).coerceAtLeast(0.0)
        return NavProgress(
            alongMeters = along,
            remainingMeters = remaining,
            remainingSeconds = if (total > 0) (route.durationSeconds * remaining / total).toInt() else 0,
            next = next,
            distanceToNext = next?.let { cumulative[it.pointIndex] - along } ?: remaining,
            following = following,
            distanceNextToFollowing = if (next != null && following != null) {
                cumulative[following.pointIndex] - cumulative[next.pointIndex]
            } else {
                Double.MAX_VALUE
            },
            offRoute = missedFixes >= OFF_ROUTE_FIXES,
            arrived = remaining <= ARRIVED_METERS,
            heading = if (located && remaining > ARRIVED_METERS) Geo.bearing(pointAt(along), pointAt(along + HEADING_METERS)) else null,
        )
    }

    /** 沿路线第 [meters] 米处的点 */
    private fun pointAt(meters: Double): LatLon {
        val m = meters.coerceIn(0.0, total)
        val i = cumulative.indexOfFirst { it >= m }.let { if (it <= 0) 0 else it - 1 }.coerceAtMost(points.size - 2)
        if (i < 0) return points.first()
        val length = cumulative[i + 1] - cumulative[i]
        val t = if (length > 0) (m - cumulative[i]) / length else 0.0
        val a = points[i]
        val b = points[i + 1]
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    private data class Snap(val segment: Int, val along: Double, val distance: Double)

    /**
     * 范围里离 [p] 最近的那段。路线来回走同一条路（进断头路掉头出来）时去程回程几乎一样近，
     * 差不多近的几段里取离现在进度最近的，不然一进去就跳到回程上，掉头的提示也没了
     */
    private fun nearest(p: LatLon, range: IntRange, followProgress: Boolean = true): Snap? {
        val from = range.first.coerceAtLeast(0)
        val to = range.last.coerceAtMost(points.size - 2)
        if (from > to) return null
        val snaps = (from..to).map { i ->
            val (t, d) = Geo.project(p, points[i], points[i + 1])
            Snap(i, cumulative[i] + t * (cumulative[i + 1] - cumulative[i]), d)
        }
        val closest = snaps.minBy { it.distance }
        if (!followProgress) return closest
        return snaps.filter { it.distance <= closest.distance + SAME_DISTANCE_METERS }.minBy { abs(it.along - along) }
    }

    companion object {
        /** 离路线超过这么远算偏了（GPS 在楼宇间会飘十几二十米） */
        const val OFF_ROUTE_METERS = 40.0
        /** 连续这么多次定位都偏，才认定骑错路，免得一次飘点就重新算路 */
        const val OFF_ROUTE_FIXES = 3
        /** 离路线超过这么远、骑的方向和路线差 [WRONG_WAY_DEGREES] 以上，也算偏（拐进了岔路，不用等到 40 米） */
        const val WRONG_WAY_METERS = 25.0
        const val WRONG_WAY_DEGREES = 60.0
        const val ARRIVED_METERS = 25.0
        /** 离路线的距离差这么点算一样近 */
        private const val SAME_DISTANCE_METERS = 5.0
        /** 一次定位沿路线挪了超过这么远算跳 */
        private const val JUMP_METERS = 200.0
        /** 连续这么多次对不上，才跳到远处 */
        private const val JUMP_AFTER_MISSES = 2
        /** 停下时地图朝的方向：看路线往前这么远 */
        private const val HEADING_METERS = 25.0
        /** 过了路口这么远，就看下一个 */
        private const val PASSED_METERS = 5.0
    }
}
