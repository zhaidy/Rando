package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTimelineTest {

    /** 纬度每 0.001 度约 111.3 米；4 段各 1 个坐标间隔 */
    private val step = 111.32

    private fun route(vararg segs: Pair<String, String>) = Route(
        points = (0..segs.size).map { LatLon(1.300 + it * 0.001, 103.8) },
        distanceMeters = (segs.size * step).toInt(),
        durationSeconds = 0,
        ascendMeters = 0,
        segments = segs.mapIndexed { i, (way, node) ->
            RouteSegment(step.toInt(), RouteParser.parseTags(way), i + 1, RouteParser.parseTags(node))
        },
    )

    private val pcn = "highway=cycleway lcn=yes"
    private val road = "highway=primary"

    @Test
    fun `相邻同类并成一段，按坐标算米数`() {
        val t = RouteTimeline(route(pcn to "", pcn to "", road to "", pcn to ""))
        assertEquals(listOf(RoadKind.PCN, RoadKind.ROAD, RoadKind.PCN), t.stretches.map { it.kind })
        assertEquals(2 * step, t.stretches[0].endMeters, 1.0)
        assertEquals(4 * step, t.total, 1.0)
    }

    @Test
    fun `前方路况：这段还剩多少，下一段是什么`() {
        val t = RouteTimeline(route(pcn to "", pcn to "", road to "", pcn to ""))
        val a = t.ahead(50.0)!!
        assertEquals(RoadKind.PCN, a.current.kind)
        assertEquals(2 * step - 50, a.remainingInCurrent, 1.0)
        assertEquals(RoadKind.ROAD, a.next?.kind)
        assertNull(t.ahead(4 * step + 1))
    }

    @Test
    fun `下一个路口离多远，刚过去几米的还算`() {
        val t = RouteTimeline(route(pcn to "highway=crossing crossing=unmarked", pcn to "", pcn to ""))
        assertEquals(CrossingType.UNMARKED, t.nextCrossing(10.0)?.type)
        assertEquals(step - 10, t.nextCrossing(10.0)!!.distance, 1.0)
        assertEquals(0.0, t.nextCrossing(step + 3)!!.distance, 0.0)
        assertNull(t.nextCrossing(step + 20))
    }
}
