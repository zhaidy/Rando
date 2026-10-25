package com.personal.rando.places

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoveredTest {

    /** 往东骑 1.1 公里：每 0.001 度（约 111 米）一个点 */
    private val route = (0..10).map { LatLon(1.3, 103.76 + it * 0.001) }

    /** 和路线贴着的一段连廊：第 2–4 个点之间，偏北 5 米 */
    private val walkway = listOf(LatLon(1.30005, 103.762), LatLon(1.30005, 103.764))

    @Test
    fun `贴着路线的连廊算一段`() {
        val stretches = Covered.onRoute(route, listOf(walkway))
        assertEquals(1, stretches.size)
        val s = stretches[0]
        // 222 米左右，前后差一个取样点
        assertTrue(s.startMeters in 200.0..225.0)
        assertTrue(s.lengthMeters in 200.0..240.0)
    }

    @Test
    fun `离路线远的、太短的不算`() {
        val far = listOf(LatLon(1.3005, 103.762), LatLon(1.3005, 103.764))
        assertEquals(emptyList<CoveredStretch>(), Covered.onRoute(route, listOf(far)))
        val tiny = listOf(LatLon(1.30005, 103.7620), LatLon(1.30005, 103.76212))
        assertEquals(emptyList<CoveredStretch>(), Covered.onRoute(route, listOf(tiny)))
    }

    @Test
    fun `中间断开一点的连起来`() {
        val a = listOf(LatLon(1.30005, 103.762), LatLon(1.30005, 103.7629))
        val b = listOf(LatLon(1.30005, 103.7630), LatLon(1.30005, 103.764))
        assertEquals(1, Covered.onRoute(route, listOf(a, b)).size)
    }

    @Test
    fun `前面最近的一段`() {
        val list = listOf(CoveredStretch(200.0, 400.0, walkway), CoveredStretch(800.0, 900.0, walkway))
        assertEquals(200.0, list.nextAhead(0.0)!!, 1e-9)
        assertEquals(0.0, list.nextAhead(300.0)!!, 1e-9)
        assertEquals(350.0, list.nextAhead(450.0)!!, 1e-9)
        assertNull(list.nextAhead(950.0))
    }

    @Test
    fun `从 Overpass 结果里取出有顶棚的路`() {
        val json = """{"elements":[
            {"type":"node","id":1,"lat":1.3,"lon":103.8,"tags":{"amenity":"shelter"}},
            {"type":"way","id":2,"tags":{"highway":"footway","covered":"yes"},"geometry":[{"lat":1.3,"lon":103.8},{"lat":1.3001,"lon":103.8}]},
            {"type":"way","id":3,"center":{"lat":1.3,"lon":103.8},"tags":{"amenity":"toilets"}}
        ]}"""
        val covered = AmenityQuery.parseCovered(json)
        assertEquals(1, covered.size)
        assertEquals(LatLon(1.3001, 103.8), covered[0][1])
        // 补给点照旧
        assertEquals(listOf(AmenityType.SHELTER, AmenityType.TOILET), AmenityQuery.parse(json).map { it.type })
        assertTrue("covered~" in AmenityQuery.build(route))
    }
}
