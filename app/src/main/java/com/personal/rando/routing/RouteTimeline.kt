package com.personal.rando.routing

import com.personal.rando.nav.Geo

/** 一段连续同类的路，在全程的第几米到第几米 */
data class Stretch(val kind: RoadKind, val startMeters: Double, val endMeters: Double) {
    val length: Double get() = endMeters - startMeters
}

/** 前方的路况：现在这段还剩多少，下一段是什么 */
data class Ahead(
    val current: Stretch,
    val remainingInCurrent: Double,
    val next: Stretch?,
)

/** [atMeters] 是路口在全程的第几米，[distance] 是离现在多远 */
data class CrossingAhead(val type: CrossingType, val atMeters: Double, val distance: Double)

/**
 * 把路线摊成一条时间轴：每段路、每个路口在全程的第几米。
 * 距离按坐标算，和 [com.personal.rando.nav.Navigator] 的 alongMeters 对得上
 */
class RouteTimeline(route: Route) {

    private val cumulative = DoubleArray(route.points.size).also { acc ->
        for (i in 1 until route.points.size) acc[i] = acc[i - 1] + Geo.distance(route.points[i - 1], route.points[i])
    }
    val total: Double = cumulative.lastOrNull() ?: 0.0

    /** 相邻同类的段已经并好；分段对不上坐标时是空的 */
    val stretches: List<Stretch> = buildList {
        if (route.segments.isEmpty() || route.segments.any { it.endIndex !in cumulative.indices }) return@buildList
        val kinds = route.segmentKinds()
        var start = 0.0
        for ((i, s) in route.segments.withIndex()) {
            val end = cumulative[s.endIndex]
            val last = lastOrNull()
            if (last != null && last.kind == kinds[i]) {
                set(size - 1, last.copy(endMeters = end))
            } else if (end > start) {
                add(Stretch(kinds[i], start, end))
            }
            start = end
        }
    }

    private val crossings: List<Pair<Double, CrossingType>> =
        route.crossings().map { cumulative[it.pointIndex] to it.type }

    fun ahead(along: Double): Ahead? {
        val index = stretches.indexOfFirst { along < it.endMeters }.takeIf { it >= 0 } ?: return null
        val current = stretches[index]
        return Ahead(current, current.endMeters - along, stretches.getOrNull(index + 1))
    }

    /** 前面最近的一次过马路；刚过去几米的也算还没过完 */
    fun nextCrossing(along: Double): CrossingAhead? =
        crossings.firstOrNull { it.first >= along - PASSING_METERS }
            ?.let { CrossingAhead(it.second, it.first, (it.first - along).coerceAtLeast(0.0)) }

    private companion object {
        const val PASSING_METERS = 5.0
    }
}
