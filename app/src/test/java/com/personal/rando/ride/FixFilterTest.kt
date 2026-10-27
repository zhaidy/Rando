package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixFilterTest {
    /** 往北每秒约 5.5 米 */
    private fun gps(second: Int, north: Double = second * 0.00005, speed: Float = 5f, bearing: Float? = 0f) =
        Fix(LatLon(1.3 + north, 103.8), accuracy = 5f, speed = speed, bearing = bearing, time = second * 1000L)

    @Test
    fun `GPS 出点时网络定位不要`() {
        val f = FixFilter()
        assertNotNull(f.accept(gps(0)))
        assertNull(f.accept(Fix(LatLon(1.3003, 103.8), accuracy = 30f, time = 3_000, network = true)))
        // GPS 断了一阵，网络定位才顶上
        assertNotNull(f.accept(Fix(LatLon(1.3003, 103.8), accuracy = 30f, time = 20_000, network = true)))
    }

    @Test
    fun `GPS 飞出去一个点扔掉，连着几个都在那儿就用`() {
        val f = FixFilter()
        assertNotNull(f.accept(gps(0)))
        assertNotNull(f.accept(gps(1)))
        // 一秒跑了 300 多米
        assertNull(f.accept(gps(2, north = 0.003)))
        assertNotNull(f.accept(gps(3)))
        repeat(3) { assertNull(f.accept(gps(4 + it, north = 0.01))) }
        assertNotNull(f.accept(gps(7, north = 0.01)))
    }

    @Test
    fun `速度在门槛上下晃时不来回切`() {
        val f = FixFilter()
        assertTrue(f.accept(gps(0, speed = 2f))!!.moving)
        assertTrue(f.accept(gps(1, speed = 1.2f))!!.moving)
        assertFalse(f.accept(gps(2, speed = 0.5f))!!.moving)
        assertFalse(f.accept(gps(3, speed = 1.2f))!!.moving)
    }

    @Test
    fun `骑得慢时接着用上一个方向`() {
        val f = FixFilter()
        f.accept(gps(0, speed = 5f, bearing = 90f))
        assertEquals(90f, f.accept(gps(1, speed = 1.5f, bearing = 200f))!!.bearing)
        assertEquals(95f, f.accept(gps(2, speed = 5f, bearing = 95f))!!.bearing)
    }
}
