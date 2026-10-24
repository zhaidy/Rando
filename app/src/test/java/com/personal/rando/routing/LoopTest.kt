package com.personal.rando.routing

import com.personal.rando.nav.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopTest {

    private val clementi = LatLon(1.3150, 103.7650)

    @Test
    fun `三个途经点和起点都在同一个圆上`() {
        val radius = 3000.0
        val center = Geo.offset(clementi, 45.0, radius)
        val points = LoopPlanner.waypoints(clementi, radius, 45.0)
        assertEquals(3, points.size)
        (points + clementi).forEach { assertEquals(radius, Geo.distance(center, it), 5.0) }
    }

    @Test
    fun `圈的大小按要骑的距离估`() {
        // 正方形周长 4√2 r，再乘上路比直线绕的 1.3
        assertEquals(20_000.0, LoopPlanner.radiusFor(20_000.0) * 4 * Math.sqrt(2.0) * LoopPlanner.DETOUR, 1.0)
    }

    @Test
    fun `认得新加坡本岛`() {
        assertTrue(Singapore.contains(LatLon(1.3500, 103.8500))) // 碧山
        assertTrue(Singapore.contains(LatLon(1.2834, 103.8607))) // 滨海湾金沙
        assertTrue(Singapore.contains(LatLon(1.3730, 103.9490))) // 白沙
        assertFalse(Singapore.contains(LatLon(1.4600, 103.7600))) // 新山
        assertFalse(Singapore.contains(LatLon(1.2490, 103.8300))) // 圣淘沙
        assertFalse(Singapore.contains(LatLon(1.3000, 104.0500))) // 东边海上
    }

    @Test
    fun `画出来的途经点都在岛上，每次换个方向`() {
        val first = LoopPlanner.plan(clementi, 20_000.0, 0)
        val second = LoopPlanner.plan(clementi, 20_000.0, 1)
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.waypoints.all(Singapore::contains))
        assertNotEquals(first.bearing, second!!.bearing, 1.0)
    }

    @Test
    fun `岛边上画不下大圈时缩小`() {
        val changiVillage = LatLon(1.3880, 103.9870)
        val shape = LoopPlanner.plan(changiVillage, 50_000.0, 0)
        assertNotNull(shape)
        assertTrue(shape!!.radiusMeters < LoopPlanner.radiusFor(50_000.0))
    }

    @Test
    fun `大到整个岛都放不下就算了`() {
        assertNull(LoopPlanner.plan(clementi, 400_000.0, 0))
    }
}
