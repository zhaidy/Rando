package com.personal.rando.ride

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 摔车检测：骑着骑着（GPS 速度够快），手机突然受到很大的冲击，
 * 之后十来秒手机几乎不动、GPS 速度也降到快停下 —— 多半是摔了。
 *
 * 过坑、急刹也会有冲击，但人马上接着骑，速度回来了、手机还在晃，就不算。
 * 只看加速度的大小（不管手机朝哪），单位米/秒²
 */
class CrashDetector(
    /** 冲击超过几个 g 才算 */
    private val impactG: Double = 4.0,
    /** 冲击前这么久以内在骑（速度够快）才算 */
    private val ridingWithinMs: Long = 5_000,
    private val ridingSpeed: Double = 3.0,
    /** 冲击后先等这么久（人摔倒、手机滚两下） */
    private val settleMs: Long = 2_000,
    /** 然后要静止这么久 */
    private val stillMs: Long = 10_000,
    private val stillSpeed: Double = 1.5,
) {
    private var lastRidingAt = Long.MIN_VALUE / 2
    private var lastSpeed = 0.0
    private var impactAt: Long? = null
    private var samples = 0
    private var moving = 0

    /** GPS 速度（米/秒） */
    fun onSpeed(time: Long, speed: Double) {
        lastSpeed = speed
        if (speed >= ridingSpeed) {
            lastRidingAt = time
            // 冲击后又骑起来了：过坑、急刹
            val impact = impactAt
            if (impact != null && time - impact > settleMs) impactAt = null
        }
    }

    /** 一个加速度读数（三个轴合起来的大小）；判断为摔车时返回 true，只报一次 */
    fun onAccel(time: Long, magnitude: Double): Boolean {
        val impact = impactAt
        if (impact == null) {
            if (magnitude >= impactG * G && time - lastRidingAt <= ridingWithinMs) {
                impactAt = time
                samples = 0
                moving = 0
            }
            return false
        }
        val since = time - impact
        if (since < settleMs) return false
        samples++
        if (abs(magnitude - G) > MOVING_DELTA) moving++
        // 手机一直在晃：人拿起手机或者接着骑了
        if (samples >= MIN_SAMPLES && moving > samples * MAX_MOVING_SHARE) {
            impactAt = null
            return false
        }
        if (since < settleMs + stillMs) return false
        impactAt = null
        return samples >= MIN_SAMPLES && lastSpeed < stillSpeed
    }

    fun reset() {
        impactAt = null
        lastRidingAt = Long.MIN_VALUE / 2
    }

    companion object {
        const val G = 9.81
        /** 和重力差这么多算在动 */
        private const val MOVING_DELTA = 2.5
        private const val MAX_MOVING_SHARE = 0.15
        /** 静止期间至少要有这么多读数，传感器没数据时不乱报 */
        private const val MIN_SAMPLES = 50
    }
}

/** 开着加速度计喂给 [CrashDetector]；判断为摔车时调 [onCrash] */
class CrashWatch(context: Context, private val detector: CrashDetector, private val onCrash: () -> Unit) {

    private val sensors = context.applicationContext.getSystemService(SensorManager::class.java)
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            // 开机后的时间，跟 GPS 速度那边一样（不受改手机时间影响）
            val time = SystemClock.elapsedRealtime()
            if (detector.onAccel(time, sqrt((x * x + y * y + z * z).toDouble()))) onCrash()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (listening) return
        val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        detector.reset()
        // 50 次每秒左右，够抓到冲击
        listening = sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(listener)
        listening = false
    }
}
