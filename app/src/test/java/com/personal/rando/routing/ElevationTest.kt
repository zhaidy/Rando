package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationTest {

    /** 一条往北的直线，每 100 米多一点一个点（纬度差 0.0009 度约 100.2 米） */
    private fun line(vararg heights: Double) = Route(
        points = heights.indices.map { LatLon(1.3 + it * 0.0009, 103.8) },
        distanceMeters = (heights.size - 1) * 100,
        durationSeconds = 0,
        ascendMeters = 0,
        segments = emptyList(),
        elevations = heights.toList(),
    )

    @Test
    fun `海拔按沿路线的距离排开`() {
        val profile = line(10.0, 12.0, 15.0).elevationProfile(smoothMeters = 0.0)
        assertEquals(listOf(10.0, 12.0, 15.0), profile.map { it.meters })
        assertEquals(0.0, profile[0].alongMeters, 0.1)
        assertEquals(200.0, profile[2].alongMeters, 1.0)
    }

    @Test
    fun `没有海拔数据时什么都没有`() {
        val route = line(10.0, 12.0).copy(elevations = emptyList())
        assertEquals(emptyList<ElevationPoint>(), route.elevationProfile())
        assertNull(route.steepestClimb())
    }

    @Test
    fun `找出 300 米内平均最陡的一段`() {
        // 前面平，中间 300 米爬 24 米（8%），后面又平
        val climb = line(10.0, 10.0, 10.0, 18.0, 26.0, 34.0, 34.0, 34.0).steepestClimb(smoothMeters = 0.0)
        assertNotNull(climb)
        assertEquals(2, climb!!.fromIndex)
        assertEquals(5, climb.toIndex)
        assertEquals(0.08, climb.grade, 0.002)
        assertEquals(200.0, climb.startMeters, 1.0)
    }

    @Test
    fun `一路都很平就不算有坡`() {
        assertNull(line(10.0, 11.0, 12.0, 13.0, 14.0, 15.0).steepestClimb())
    }

    @Test
    fun `一两个点的尖刺不算坡`() {
        // 树、高架让某个点高出一截，平滑以后整段不陡
        val climb = line(10.0, 10.0, 10.0, 10.0, 30.0, 10.0, 10.0, 10.0, 10.0, 10.0).steepestClimb()
        assertNull(climb)
    }

    @Test
    fun `海拔取前后一段的平均`() {
        val profile = line(10.0, 10.0, 40.0, 10.0, 10.0).elevationProfile(smoothMeters = 150.0)
        assertEquals(20.0, profile[2].meters, 0.01)
    }
}
