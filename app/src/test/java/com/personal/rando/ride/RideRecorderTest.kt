package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRecorderTest {

    /** 纬度 0.0001 度 ≈ 11.1 米 */
    private fun fix(step: Int, seconds: Long, altitude: Double? = null, accuracy: Float? = 5f) =
        Fix(LatLon(1.3 + step * 0.0001, 103.8), accuracy = accuracy, altitude = altitude, time = seconds * 1000)

    @Test
    fun `累加距离和骑行时间`() {
        val r = RideRecorder(startedAt = 0)
        (0..10).forEach { r.add(fix(it, it * 2L)) } // 每 2 秒 11 米 ≈ 20 公里/小时
        val s = r.state()
        assertEquals(111.3, s.distanceMeters, 0.5)
        assertEquals(20, s.movingSeconds)
        assertEquals(11, s.track.size)
    }

    @Test
    fun `精度差的点不要`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0))
        assertFalse(r.add(fix(1, 2, accuracy = 60f)))
        assertTrue(r.add(fix(1, 3)))
    }

    @Test
    fun `原地抖动不算距离，等红灯不算骑行时间`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0))
        r.add(fix(1, 2))
        // 停 60 秒，GPS 在一两米内抖
        assertFalse(r.add(Fix(LatLon(1.3001 + 0.00001, 103.8), accuracy = 5f, time = 30_000)))
        val after = r.state()
        assertEquals(11.1, after.distanceMeters, 0.5)
        assertEquals(2, after.movingSeconds)
        assertEquals(0.0, after.currentSpeed, 0.0)
    }

    @Test
    fun `瞬移的飘点丢掉`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0))
        assertFalse(r.add(fix(100, 1))) // 1 秒 1.1 公里
        assertEquals(0.0, r.state().distanceMeters, 0.0)
    }

    @Test
    fun `断了很久的那段距离算上，时间不算`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0))
        r.add(fix(10, 60)) // 隧道里 60 秒没信号
        val s = r.state()
        assertEquals(111.3, s.distanceMeters, 0.5)
        assertEquals(0, s.movingSeconds)
    }

    @Test
    fun `爬升只累加超过阈值的上坡，抖动不算`() {
        val r = RideRecorder(0)
        val altitudes = listOf(10.0, 13.0, 9.0, 12.0, 16.0, 20.0, 14.0, 21.0)
        altitudes.forEachIndexed { i, alt -> r.add(fix(i, i * 2L, altitude = alt)) }
        // 参考点 10 → 9（跌够 5 才换，没换）→ 16 记 6 → 21 记 5
        assertEquals(11.0, r.state().ascendMeters, 0.01)
    }

    @Test
    fun `生成记录：平均速度按骑行时间算`() {
        val r = RideRecorder(startedAt = 1_000)
        (0..10).forEach { r.add(fix(it, it * 2L)) }
        val ride = r.toRide(endedAt = 99_000)
        assertEquals(1_000, ride.id)
        assertEquals(111.3 / 20, ride.averageSpeed, 0.05)
        assertEquals(11, ride.points.size)
    }

    @Test
    fun `有 GPS 速度就用它当当前速度和最快速度`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0))
        r.add(fix(1, 2).copy(speed = 6.5f)) // 两点算出来是 5.6 米/秒
        val s = r.state()
        assertEquals(6.5, s.currentSpeed, 0.01)
        assertEquals(6.5, s.maxSpeed, 0.01)
        // 停下来时 GPS 速度接近 0，马上就显示出来
        r.add(Fix(LatLon(1.3001, 103.8), accuracy = 5f, speed = 0.2f, time = 3_000))
        assertEquals(0.2, r.state().currentSpeed, 0.01)
    }

    @Test
    fun `在家里精度差，点在几十米里乱跳不算`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0, accuracy = 20f))
        // 每次跳 11 米，没跳出 20 米的精度圈
        listOf(1, -1, 0, 1, -1).forEachIndexed { i, step -> assertFalse(r.add(fix(step, i + 1L, accuracy = 20f))) }
        // GPS 说停着，却 6 秒跳了 33 米
        assertFalse(r.add(fix(3, 6, accuracy = 8f).copy(speed = 0.1f)))
        assertEquals(0.0, r.state().distanceMeters, 0.0)
        assertEquals(1, r.state().track.size)
    }

    @Test
    fun `精度差但 GPS 说在骑就照收`() {
        val r = RideRecorder(0)
        r.add(fix(0, 0, accuracy = 20f))
        assertTrue(r.add(fix(1, 2, accuracy = 20f).copy(speed = 5.5f)))
        // 没有 GPS 速度，骑出精度圈了也收
        assertTrue(r.add(fix(3, 6, accuracy = 20f)))
    }
}
