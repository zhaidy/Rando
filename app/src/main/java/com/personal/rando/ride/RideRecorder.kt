package com.personal.rando.ride

import com.personal.rando.nav.Geo

/** 骑行中的实时数据，界面和通知栏用 */
data class RecordingState(
    val startedAt: Long,
    val distanceMeters: Double = 0.0,
    val movingSeconds: Long = 0,
    val ascendMeters: Double = 0.0,
    /** 米/秒，当前速度；停下来了就是 0 */
    val currentSpeed: Double = 0.0,
    /** 米/秒 */
    val maxSpeed: Double = 0.0,
    val track: List<TrackPoint> = emptyList(),
)

/**
 * 把一个个定位攒成轨迹，顺便算距离、骑行时间、爬升。
 * GPS 会飘：精度差的点不要，原地抖动的小位移不算，瞬移的点当成错误丢掉。
 */
class RideRecorder(private val startedAt: Long) {

    private val points = mutableListOf<TrackPoint>()
    private var distance = 0.0
    private var moving = 0L
    private var ascend = 0.0
    private var maxSpeed = 0.0
    private var currentSpeed = 0.0
    /** 爬升按滞后阈值算：海拔比参考点高出一截才计入，免得 GPS 海拔上下抖被累加 */
    private var altitudeRef: Double? = null
    /** 上一个收下的点的定位精度 */
    private var lastAccuracy: Float? = null

    /** 收下这个定位就返回 true */
    fun add(fix: Fix): Boolean {
        if (fix.accuracy != null && fix.accuracy > MAX_ACCURACY_METERS) return false
        val point = TrackPoint(fix.location, fix.altitude, fix.time)
        val last = points.lastOrNull()
        if (last == null) {
            points += point
            altitudeRef = fix.altitude
            lastAccuracy = fix.accuracy
            return true
        }
        val seconds = (fix.time - last.time) / 1000.0
        if (seconds <= 0) return false
        val d = Geo.distance(last.location, fix.location)
        val speed = d / seconds
        if (speed > MAX_SPEED) return false
        // GPS 芯片按多普勒算的速度比两点距离除时间稳得多，有就用它
        val gpsSpeed = fix.speed?.toDouble()?.coerceAtMost(MAX_SPEED)
        if (d < MIN_STEP_METERS || still(fix, gpsSpeed, d)) {
            // 原地没动；没有 GPS 速度时，停太久就当速度归零
            currentSpeed = gpsSpeed ?: if (seconds > 5) 0.0 else currentSpeed
            return false
        }
        distance += d
        currentSpeed = gpsSpeed ?: speed
        // 中间断了很久（比如隧道里没信号）就不算进骑行时间
        if ((gpsSpeed ?: speed) >= MOVING_SPEED && seconds <= MAX_GAP_SECONDS) moving += Math.round(seconds)
        maxSpeed = maxOf(maxSpeed, gpsSpeed ?: speed)
        fix.altitude?.let { alt ->
            val ref = altitudeRef
            when {
                ref == null -> altitudeRef = alt
                alt - ref >= CLIMB_STEP_METERS -> {
                    ascend += alt - ref
                    altitudeRef = alt
                }
                ref - alt >= CLIMB_STEP_METERS -> altitudeRef = alt
            }
        }
        points += point
        lastAccuracy = fix.accuracy
        return true
    }

    /**
     * 人没动、是 GPS 在飘：在楼里、车棚下精度十几二十米，点会在几十米里来回跳。
     * GPS 自己的速度说在走就信它；不然位移没跳出两个点的精度圈不算，
     * GPS 说停着也不算（不看两点算出来的速度：停得越久离上一个收下的点时间越长，算出来越慢，飘远一点就混进去了）
     */
    private fun still(fix: Fix, gpsSpeed: Double?, d: Double): Boolean {
        if (gpsSpeed != null && gpsSpeed >= MOVING_SPEED) return false
        if (gpsSpeed != null && gpsSpeed < STILL_SPEED) return true
        val noise = maxOf(fix.accuracy ?: 0f, lastAccuracy ?: 0f)
        return d < noise
    }

    fun state() = RecordingState(
        startedAt = startedAt,
        distanceMeters = distance,
        movingSeconds = moving,
        ascendMeters = ascend,
        currentSpeed = currentSpeed,
        maxSpeed = maxSpeed,
        track = points.toList(),
    )

    fun toRide(endedAt: Long) = Ride(
        id = startedAt,
        endedAt = endedAt,
        distanceMeters = distance,
        movingSeconds = moving,
        ascendMeters = ascend,
        maxSpeed = maxSpeed,
        points = points.toList(),
    )

    companion object {
        const val MAX_ACCURACY_METERS = 25f
        /** 比这小的位移当成原地抖动 */
        const val MIN_STEP_METERS = 3.0
        /** 90 公里/小时，超过就是飘点 */
        const val MAX_SPEED = 25.0
        /** 3.6 公里/小时以上才算在骑 */
        const val MOVING_SPEED = 1.0
        const val MAX_GAP_SECONDS = 30.0
        /** GPS 速度低于它就是停着 */
        const val STILL_SPEED = 0.5
        const val CLIMB_STEP_METERS = 5.0
    }
}
