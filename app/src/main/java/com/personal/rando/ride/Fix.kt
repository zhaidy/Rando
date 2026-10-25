package com.personal.rando.ride

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import com.personal.rando.routing.LatLon

/** 一次定位，只留用得着的字段，方便单元测试里造数据 */
data class Fix(
    val location: LatLon,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    /** 前进方向，正北 0°；停着时没有 */
    val bearing: Float? = null,
    val time: Long = System.currentTimeMillis(),
) {
    /** 喂给地图的蓝点 */
    fun toLocation(): Location = Location("rando").also { l ->
        l.latitude = location.lat
        l.longitude = location.lon
        accuracy?.let { l.accuracy = it }
        altitude?.let { l.altitude = it }
        speed?.let { l.speed = it }
        bearing?.let { l.bearing = it }
        l.time = time
    }

    /** 在骑（不是停着等红灯）：镜头按前进方向转；停着时按手机朝向 */
    val moving: Boolean get() = (speed ?: 0f) >= MOVING_SPEED


    companion object {
        fun of(l: Location) = Fix(
            location = LatLon(l.latitude, l.longitude),
            accuracy = if (l.hasAccuracy()) l.accuracy else null,
            altitude = if (l.hasAltitude()) l.altitude else null,
            speed = if (l.hasSpeed()) l.speed else null,
            bearing = if (l.hasBearing()) l.bearing else null,
            time = l.time,
        )

        /**
         * 收定位的回调。不能写成 `LocationListener { }`：Android 10 以下另外三个方法没有默认实现，
         * 一开关 GPS 系统去调它们就闪退
         */
        fun listener(onFix: (Fix) -> Unit): LocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(of(location))
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        /** 米/秒，大约 5 公里/时 */
        const val MOVING_SPEED = 1.4f
    }
}
