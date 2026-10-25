package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import kotlin.math.roundToInt

/**
 * 分享图上的照片：按拍的时间对到轨迹上，就知道是在哪拍的——不用照片里的定位，没开定位拍的也行。
 * 骑完几分钟内拍的也算（到了地方拍一张），算在终点
 */
object RidePhotos {
    const val MARGIN_MS = 5 * 60_000L

    /** 分享图上最多放几张 */
    const val MAX = 4

    /** 找照片的时间段：第一次开始前到最后一次结束后 */
    fun window(rides: List<Ride>): LongRange? {
        if (rides.isEmpty()) return null
        return (rides.minOf { it.startedAt } - MARGIN_MS)..(rides.maxOf { it.endedAt } + MARGIN_MS)
    }

    /** [takenAt] 那会儿在哪：落在哪次骑行里就按前后两个点的时间插一下；都不在返回 null */
    fun place(takenAt: Long, rides: List<Ride>): LatLon? {
        val ride = rides.firstOrNull { takenAt in (it.startedAt - MARGIN_MS)..(it.endedAt + MARGIN_MS) } ?: return null
        val points = ride.points
        if (points.isEmpty()) return null
        if (takenAt <= points.first().time) return points.first().location
        if (takenAt >= points.last().time) return points.last().location
        // 第一个时间不早于 takenAt 的点
        var lo = 0
        var hi = points.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (points[mid].time < takenAt) lo = mid + 1 else hi = mid
        }
        val b = points[lo]
        val a = points[lo - 1]
        val span = (b.time - a.time).toDouble()
        val k = if (span > 0) (takenAt - a.time) / span else 0.0
        return LatLon(a.location.lat + (b.location.lat - a.location.lat) * k, a.location.lon + (b.location.lon - a.location.lon) * k)
    }

    /** 照片多的时候默认选哪几张：按时间均匀挑 [n] 张，不要全挤在一段路上；返回下标 */
    fun pick(count: Int, n: Int = MAX): List<Int> {
        if (count <= n) return (0 until count).toList()
        if (n == 1) return listOf(0)
        return (0 until n).map { (it * (count - 1).toDouble() / (n - 1)).roundToInt() }
    }
}
