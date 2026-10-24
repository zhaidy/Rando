package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Route

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
)

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

    fun update(location: LatLon): NavProgress {
        // 先在当前位置附近往前找，找不到再全线找（比如抄了近路又回到路线上）
        val snapped = nearest(location, (segment - 3)..(segment + 80))
            ?.takeIf { it.distance <= OFF_ROUTE_METERS }
            ?: nearest(location, 0 until points.size - 1)?.takeIf { it.distance <= OFF_ROUTE_METERS }
        if (snapped != null) {
            segment = snapped.segment
            along = snapped.along
            missedFixes = 0
        } else {
            missedFixes++
        }
        return progress()
    }

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
        )
    }

    private data class Snap(val segment: Int, val along: Double, val distance: Double)

    private fun nearest(p: LatLon, range: IntRange): Snap? {
        val from = range.first.coerceAtLeast(0)
        val to = range.last.coerceAtMost(points.size - 2)
        var best: Snap? = null
        for (i in from..to) {
            val (t, d) = Geo.project(p, points[i], points[i + 1])
            if (best == null || d < best.distance) {
                best = Snap(i, cumulative[i] + t * (cumulative[i + 1] - cumulative[i]), d)
            }
        }
        return best
    }

    companion object {
        /** 离路线超过这么远算偏了（GPS 在楼宇间会飘十几二十米） */
        const val OFF_ROUTE_METERS = 40.0
        /** 连续这么多次定位都偏，才认定骑错路，免得一次飘点就重新算路 */
        const val OFF_ROUTE_FIXES = 3
        const val ARRIVED_METERS = 25.0
        /** 过了路口这么远，就看下一个 */
        private const val PASSED_METERS = 5.0
    }
}
