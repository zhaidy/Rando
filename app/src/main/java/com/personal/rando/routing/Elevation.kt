package com.personal.rando.routing

import com.personal.rando.nav.Geo

/** 海拔图上的一个点：沿路线骑了多远、多高 */
data class ElevationPoint(val alongMeters: Double, val meters: Double)

/** 一段坡：从第 [fromIndex] 到第 [toIndex] 个坐标 */
data class Climb(val fromIndex: Int, val toIndex: Int, val startMeters: Double, val lengthMeters: Double, val gainMeters: Double) {
    val grade: Double get() = if (lengthMeters > 0) gainMeters / lengthMeters else 0.0
}

/**
 * 沿路线的海拔；服务器没给海拔就是空的。
 * 海拔是卫星测的，树、楼、高架都会让个别点高出一截，所以每个点取前后 [smoothMeters] 内的平均
 */
fun Route.elevationProfile(smoothMeters: Double = SMOOTH_METERS): List<ElevationPoint> {
    if (elevations.size != points.size || points.size < 2) return emptyList()
    val along = DoubleArray(points.size)
    for (i in 1 until points.size) along[i] = along[i - 1] + Geo.distance(points[i - 1], points[i])
    var lo = 0
    var hi = 0
    var sum = 0.0
    return points.indices.map { i ->
        while (hi < points.size && along[hi] <= along[i] + smoothMeters) sum += elevations[hi++]
        while (along[lo] < along[i] - smoothMeters) sum -= elevations[lo++]
        ElevationPoint(along[i], sum / (hi - lo))
    }
}

/** 海拔平滑的半径 */
const val SMOOTH_METERS = 150.0

/**
 * 最陡的一段上坡：在至少 [windowMeters] 长的一截里平均坡度最大的。
 * 海拔数据是卫星测的，几十米内的起伏不准（树、楼、高架都会算进去），所以按一截来看；
 * 比 [MIN_CLIMB_GRADE] 还缓的不算坡，返回 null
 */
fun Route.steepestClimb(windowMeters: Double = CLIMB_WINDOW_METERS, smoothMeters: Double = SMOOTH_METERS): Climb? {
    val profile = elevationProfile(smoothMeters)
    if (profile.isEmpty()) return null
    var best: Climb? = null
    var j = 0
    for (i in profile.indices) {
        if (j <= i) j = i + 1
        while (j < profile.size && profile[j].alongMeters - profile[i].alongMeters < windowMeters) j++
        if (j >= profile.size) break
        val length = profile[j].alongMeters - profile[i].alongMeters
        val gain = profile[j].meters - profile[i].meters
        val climb = Climb(i, j, profile[i].alongMeters, length, gain)
        if (climb.grade >= MIN_CLIMB_GRADE && (best == null || climb.grade > best.grade)) best = climb
    }
    return best
}

const val CLIMB_WINDOW_METERS = 300.0
const val MIN_CLIMB_GRADE = 0.03
