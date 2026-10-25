package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Route
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
    /** 对上过路线没有；第一次对上的不算跳 */
    private var located = false

    fun update(location: LatLon): NavProgress {
        // 先在当前位置附近往前找，找不到再全线找（比如抄了近路又回到路线上）
        val found = nearest(location, (segment - 3)..(segment + 80))
            ?.takeIf { it.distance <= OFF_ROUTE_METERS }
            ?: nearest(location, 0 until points.size - 1)?.takeIf { it.distance <= OFF_ROUTE_METERS }
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
        return progress()
    }

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
        )
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
        const val ARRIVED_METERS = 25.0
        /** 离路线的距离差这么点算一样近 */
        private const val SAME_DISTANCE_METERS = 5.0
        /** 一次定位沿路线挪了超过这么远算跳 */
        private const val JUMP_METERS = 200.0
        /** 连续这么多次对不上，才跳到远处 */
        private const val JUMP_AFTER_MISSES = 2
        /** 过了路口这么远，就看下一个 */
        private const val PASSED_METERS = 5.0
    }
}
