package com.personal.rando.routing

import com.personal.rando.ui.markLabel
import com.personal.rando.ui.SegmentInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadCorrectionsTest {

    /** 从南往北一条直线，每 0.0005 度（约 55 米）一个点 */
    private fun line(lon: Double, fromLat: Double, n: Int) = (0 until n).map { LatLon(fromLat + it * 0.0005, lon) }

    private val bridge = line(103.774, 1.301, 6)
    private val correction = RoadCorrection("a", bridge, 0)

    @Test
    fun `同一段路才算盖住`() {
        assertTrue(correction.covers(bridge))
        // 偏了十来米（GPS、两条线画法不同）还算
        assertTrue(correction.covers(line(103.77409, 1.301, 6)))
        // 隔壁一条路，差 100 多米
        assertFalse(correction.covers(line(103.775, 1.301, 6)))
        // 只有一头碰到
        assertFalse(correction.covers(line(103.774, 1.3035, 6)))
    }

    @Test
    fun `标过的段和旁边的马路连成一段也认得出`() {
        val longer = line(103.774, 1.301, 15)
        assertFalse(correction.covers(longer))
        assertTrue(correction.overlaps(longer))
    }

    private fun route(): Route {
        // 马路 → 自行车道（其实是马路）→ 公园连道
        val points = line(103.774, 1.2985, 16)
        return Route(
            points = points,
            distanceMeters = 830,
            durationSeconds = 200,
            ascendMeters = 0,
            segments = listOf(
                RouteSegment(280, mapOf("highway" to "residential"), endIndex = 5),
                RouteSegment(280, mapOf("highway" to "cycleway"), endIndex = 10),
                RouteSegment(280, mapOf("highway" to "footway", "route_bicycle_lcn" to "yes"), endIndex = 15),
            ),
        )
    }

    @Test
    fun `经过标过的地方按马路算`() {
        val r = route()
        val fixed = RoadCorrections.apply(r, listOf(RoadCorrection("a", r.points.subList(5, 11), 0)))
        assertEquals(listOf(RoadKind.ROAD, RoadKind.ROAD, RoadKind.PCN), fixed.segments.map { RoadKind.of(it.wayTags) })
        assertEquals("road", fixed.segments[1].wayTags[RoadCorrections.TAG])
        // 没经过就原样
        assertTrue(RoadCorrections.apply(r, listOf(RoadCorrection("b", line(103.78, 1.30, 6), 0))) === r)
    }

    @Test
    fun `不上马路时请求里带上避让线`() {
        val stops = listOf(LatLon(1.3053, 103.7718), LatLon(1.3006, 103.7740))
        assertNull(RoadCorrections.polylines(stops, listOf(correction), avoidRoads = false))
        val pl = RoadCorrections.polylines(stops, listOf(correction), avoidRoads = true)!!
        val values = pl.split(",")
        // 先经度后纬度，最后一个是代价
        assertEquals("103.774000", values[0])
        assertEquals("1.301000", values[1])
        assertEquals(bridge.size * 2 + 1, values.size)
        assertEquals(RoadCorrections.weight(correction).toString(), values.last())
        assertTrue(RoadCorrections.weight(correction) in 500..1000)
        // 远处的不带
        assertNull(RoadCorrections.polylines(listOf(LatLon(1.35, 103.95)), listOf(correction), avoidRoads = true))

        val url = BRouterClient().url(stops, "trekking", polylines = pl)
        assertTrue("polylines=103.774000%2C1.301000" in url)
    }

    @Test
    fun `存取`() {
        val list = listOf(correction, RoadCorrection.new(line(103.78, 1.30, 100), now = 5))
        assertEquals(RoadCorrection.MAX_POINTS, list[1].points.size)
        assertEquals(list, RoadCorrectionStore.decode(RoadCorrectionStore.encode(list)))
    }

    @Test
    fun `卡片上的按钮`() {
        fun info(kind: RoadKind?, points: List<LatLon> = bridge) = SegmentInfo("x", "#000000", 100, points[0], points, null, kind)
        assertEquals("其实是马路", info(RoadKind.CYCLEWAY).markLabel(false))
        assertEquals("其实是马路", info(RoadKind.PCN).markLabel(false))
        assertEquals("不是马路", info(RoadKind.ROAD).markLabel(true))
        assertNull(info(RoadKind.ROAD).markLabel(false))
        assertNull(info(RoadKind.CROSSING, listOf(bridge[0])).markLabel(false))
        assertNull(info(null).markLabel(false))
    }
}
