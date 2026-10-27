package com.personal.rando.ride

import com.personal.rando.i18n.tr
import com.personal.rando.routing.LatLon
import java.util.Calendar
import java.util.TimeZone

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
    /** 一起骑的队友名字（骑的时候在身边出现过的），按先后；以前的记录没有 */
    val teammates: List<String> = emptyList(),
) {
    val startedAt: Long get() = id

    /** 米/秒；没动过就是 0 */
    val averageSpeed: Double get() = if (movingSeconds > 0) distanceMeters / movingSeconds else 0.0

    /** 「早上骑行」「傍晚骑行」，按开始时间 */
    val title: String
        get() {
            return tr("${timeOfDay(startedAt)}骑行", "${timeOfDay(startedAt)} ride")
        }
}

/** 「早上」「中午」「下午」「傍晚」「夜里」：[millis] 在 [zone] 是什么时候 */
fun timeOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
    when (Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)) {
        in 5..10 -> tr("早上", "Morning")
        in 11..13 -> tr("中午", "Lunch")
        in 14..16 -> tr("下午", "Afternoon")
        in 17..18 -> tr("傍晚", "Evening")
        else -> tr("夜里", "Night")
    }
