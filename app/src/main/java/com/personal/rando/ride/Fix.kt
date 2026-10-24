package com.personal.rando.ride

import android.location.Location
import com.personal.rando.routing.LatLon

/** 一次定位，只留用得着的字段，方便单元测试里造数据 */
data class Fix(
    val location: LatLon,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val time: Long = System.currentTimeMillis(),
) {
    companion object {
        fun of(l: Location) = Fix(
            location = LatLon(l.latitude, l.longitude),
            accuracy = if (l.hasAccuracy()) l.accuracy else null,
            altitude = if (l.hasAltitude()) l.altitude else null,
            speed = if (l.hasSpeed()) l.speed else null,
            time = l.time,
        )
    }
}
