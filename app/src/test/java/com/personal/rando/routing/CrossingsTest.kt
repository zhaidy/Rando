package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossingsTest {

    private fun tags(text: String) = RouteParser.parseTags(text)

    /** 每段 10 个坐标点之间，终点序号依次是 1、2、3… */
    private fun route(vararg segments: Pair<Int, Pair<String, String>>) = Route(
        points = (0..segments.size).map { LatLon(1.3 + it * 0.0001, 103.8) },
        distanceMeters = segments.sumOf { it.first },
        durationSeconds = 0,
        ascendMeters = 0,
        segments = segments.mapIndexed { i, (d, t) -> RouteSegment(d, tags(t.first), i + 1, tags(t.second)) },
    )

    private val pcn = "highway=cycleway lcn=yes"
    private val road = "highway=residential"

    @Test
    fun `路口点的种类`() {
        assertEquals(CrossingType.SIGNALS, CrossingType.of(tags("highway=crossing crossing=traffic_signals")))
        assertEquals(CrossingType.ZEBRA, CrossingType.of(tags("highway=crossing crossing=zebra")))
        assertEquals(CrossingType.UNMARKED, CrossingType.of(tags("highway=crossing crossing=unmarked")))
        assertEquals(CrossingType.UNKNOWN, CrossingType.of(tags("highway=crossing")))
        assertNull(CrossingType.of(tags("highway=traffic_signals")))
    }

    @Test
    fun `夹在小路中间的一小截马路算过马路，长的还是马路`() {
        val short = route(100 to (pcn to ""), 12 to (road to ""), 100 to (pcn to ""))
        assertEquals(listOf(RoadKind.PCN, RoadKind.CROSSING, RoadKind.PCN), short.segmentKinds())
        assertEquals(mapOf(RoadKind.PCN to 200, RoadKind.CROSSING to 12), short.breakdown())

        val long = route(100 to (pcn to ""), 40 to (road to ""), 100 to (pcn to ""))
        assertEquals(RoadKind.ROAD, long.segmentKinds()[1])
        // 起点、终点那头的马路不算过马路
        assertEquals(RoadKind.ROAD, route(8 to (road to ""), 100 to (pcn to "")).segmentKinds()[0])
    }

    @Test
    fun `中间有隔离带的马路两个路口点算一次，取最明确的种类`() {
        val r = route(
            100 to (pcn to ""),
            8 to ("highway=cycleway cycleway=crossing" to "highway=crossing"),
            8 to ("highway=cycleway cycleway=crossing" to "highway=crossing crossing=traffic_signals"),
            100 to (pcn to "highway=crossing crossing=unmarked"),
        )
        val crossings = r.crossings()
        assertEquals(listOf(CrossingType.SIGNALS, CrossingType.UNMARKED), crossings.map { it.type })
    }

    @Test
    fun `沿着马路骑时路过的人行横道不算`() {
        val r = route(100 to (road to "highway=crossing crossing=zebra"), 100 to (road to ""))
        assertEquals(emptyList<Crossing>(), r.crossings())
    }

    @Test
    fun `没画斑马线的路口按没标明算`() {
        val r = route(100 to (pcn to ""), 10 to (road to ""), 100 to (pcn to ""))
        assertEquals(listOf(CrossingType.UNKNOWN), r.crossings().map { it.type })
    }
}
