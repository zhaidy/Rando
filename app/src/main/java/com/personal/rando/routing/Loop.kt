package com.personal.rando.routing

import com.personal.rando.nav.Geo
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 骑一圈回到起点：在起点旁边画一个圆，起点在圆上，圆上再取三个点当途经点，
 * 让 BRouter 依次连起来。方向换一换就是另一条
 */
object LoopPlanner {

    /** 实际骑的路比直线绕，大约长这么多 */
    const val DETOUR = 1.3

    /** 圆内接正方形的周长是半径的 4√2 倍 */
    private val PERIMETER_PER_RADIUS = 4 * sqrt(2.0)

    fun radiusFor(targetMeters: Double): Double = targetMeters / (PERIMETER_PER_RADIUS * DETOUR)

    /** 朝 [bearing] 方向画圈，顺时针经过的三个途经点 */
    fun waypoints(start: LatLon, radiusMeters: Double, bearing: Double): List<LatLon> {
        val center = Geo.offset(start, bearing, radiusMeters)
        // 起点在圆心的 bearing + 180° 方向，另外三个角每隔 90° 一个
        return listOf(90.0, 180.0, 270.0).map { Geo.offset(center, bearing + 180 + it, radiusMeters) }
    }

    /**
     * 第 [attempt] 次要的圈：每次换个方向，跳过途经点掉进海里或出了新加坡的方向。
     * 先用估出来的大小；能放下的方向轮完了（或者一个都放不下，比如在岛边上），
     * 再把圈缩小一点接着轮。一个都放不下返回 null
     */
    fun plan(start: LatLon, targetMeters: Double, attempt: Int): LoopShape? {
        val radius = radiusFor(targetMeters)
        val candidates = listOf(1.0, 0.8, 0.65, 0.5).flatMap { scale ->
            // 每次跳过几个方向，相邻两次的圈差得比较远
            (0 until DIRECTIONS)
                .map { LoopShape(start, (it * STRIDE % DIRECTIONS) * 360.0 / DIRECTIONS, radius * scale) }
                .filter { it.onLand }
        }
        return candidates.getOrNull(attempt % candidates.size.coerceAtLeast(1))
    }

    private const val DIRECTIONS = 12
    /** 和 12 互质，轮一遍才会回到老方向 */
    private const val STRIDE = 5
}

/** 几个圈里挑哪个：越小越好。多骑一米马路，当成长度多差 [ROAD_WEIGHT] 米 */
object LoopScore {
    const val ROAD_WEIGHT = 8.0

    fun of(route: Route, targetMeters: Double): Double =
        abs(route.distanceMeters - targetMeters) + (route.breakdown()[RoadKind.ROAD] ?: 0) * ROAD_WEIGHT
}

/** 一个圈：从 [start] 朝 [bearing] 方向，半径 [radiusMeters] */
data class LoopShape(val start: LatLon, val bearing: Double, val radiusMeters: Double) {
    val waypoints: List<LatLon> get() = LoopPlanner.waypoints(start, radiusMeters, bearing)
    val onLand: Boolean get() = waypoints.all(Singapore::contains)

    /** 同一个方向，圈放大或缩小 [factor] 倍；超出陆地返回 null */
    fun scaled(factor: Double): LoopShape? = copy(radiusMeters = radiusMeters * factor).takeIf { it.onLand }
}

/** 新加坡本岛的大致轮廓（不含裕廊岛、圣淘沙等离岛），用来判断途经点是不是在陆地上 */
object Singapore {

    private val outline = listOf(
        1.300 to 103.628, 1.348 to 103.636, 1.385 to 103.665, 1.430 to 103.700,
        1.440 to 103.740, 1.446 to 103.770, 1.458 to 103.800, 1.462 to 103.836,
        1.430 to 103.865, 1.418 to 103.905, 1.383 to 103.950, 1.390 to 103.985,
        1.360 to 104.010, 1.320 to 103.985, 1.302 to 103.935, 1.292 to 103.890,
        1.270 to 103.865, 1.262 to 103.840, 1.264 to 103.810, 1.275 to 103.775,
        1.290 to 103.755, 1.305 to 103.720, 1.310 to 103.690, 1.300 to 103.660,
    )

    /** 射线法：往东画一条线，穿过轮廓奇数次就在里面 */
    fun contains(p: LatLon): Boolean {
        var inside = false
        var j = outline.size - 1
        for (i in outline.indices) {
            val (latI, lonI) = outline[i]
            val (latJ, lonJ) = outline[j]
            if ((latI > p.lat) != (latJ > p.lat) &&
                p.lon < (lonJ - lonI) * (p.lat - latI) / (latJ - latI) + lonI
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
