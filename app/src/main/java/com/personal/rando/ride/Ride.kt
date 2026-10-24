package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import java.util.Calendar

data class TrackPoint(val location: LatLon, val altitude: Double?, val time: Long)

/** 一次骑行记录，存在 files/rides/<id>.json */
data class Ride(
    /** 就是开始时间，毫秒 */
    val id: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    /** 只算在骑的时间，等红灯、歇脚不算 */
    val movingSeconds: Long,
    val ascendMeters: Double,
    /** 米/秒 */
    val maxSpeed: Double,
    val points: List<TrackPoint>,
) {
    val startedAt: Long get() = id

    /** 米/秒；没动过就是 0 */
    val averageSpeed: Double get() = if (movingSeconds > 0) distanceMeters / movingSeconds else 0.0

    /** 「早上骑行」「傍晚骑行」，按开始时间 */
    val title: String
        get() {
            val hour = Calendar.getInstance().apply { timeInMillis = startedAt }.get(Calendar.HOUR_OF_DAY)
            val period = when (hour) {
                in 5..10 -> "早上"
                in 11..13 -> "中午"
                in 14..16 -> "下午"
                in 17..18 -> "傍晚"
                else -> "夜里"
            }
            return "${period}骑行"
        }
}
