package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadKindTest {

    private fun kind(tags: String) = RoadKind.of(RouteParser.parseTags(tags))

    @Test
    fun `按标签分出公园连道、自行车道、人行道、马路`() {
        assertEquals(RoadKind.PCN, kind("highway=cycleway bicycle=designated route_bicycle_lcn=yes"))
        assertEquals(RoadKind.PCN, kind("highway=footway lcn=yes"))
        assertEquals(RoadKind.CYCLEWAY, kind("highway=cycleway surface=asphalt"))
        assertEquals(RoadKind.PATH, kind("highway=footway surface=concrete"))
        assertEquals(RoadKind.ROAD, kind("highway=residential surface=asphalt"))
        assertEquals(RoadKind.ROAD_FOOTPATH, kind("highway=tertiary sidewalk=both"))
        // 人行道单独画了线的，马路本身还是马路
        assertEquals(RoadKind.ROAD, kind("highway=residential sidewalk=separate"))
    }

    @Test
    fun `单独画的斑马线算过马路`() {
        assertEquals(RoadKind.CROSSING, kind("highway=footway footway=crossing"))
        assertEquals(RoadKind.CROSSING, kind("highway=cycleway cycleway=crossing route_bicycle_lcn=yes"))
    }

    @Test
    fun `禁止骑车的小路、台阶、商场走廊都要推车`() {
        assertEquals(RoadKind.PUSH, kind("highway=path bicycle=no"))
        assertEquals(RoadKind.PUSH, kind("highway=steps"))
        assertEquals(RoadKind.PUSH, kind("highway=corridor"))
    }

    @Test
    fun `马路上的自行车路线不算公园连道`() {
        assertEquals(RoadKind.ROAD, kind("highway=residential route_bicycle_lcn=yes"))
    }

    private val a = LatLon(1.300, 103.800)
    private val b = LatLon(1.301, 103.800)
    private val c = LatLon(1.302, 103.800)
    private val d = LatLon(1.303, 103.800)

    private fun route(vararg segments: RouteSegment) = Route(
        points = listOf(a, b, c, d),
        distanceMeters = segments.sumOf { it.distanceMeters },
        durationSeconds = 0,
        ascendMeters = 0,
        segments = segments.toList(),
    )

    @Test
    fun `按种类切开，相邻同类的并成一条`() {
        val pcn = RouteParser.parseTags("highway=cycleway lcn=yes")
        val road = RouteParser.parseTags("highway=primary")
        val pieces = route(
            RouteSegment(100, pcn, endIndex = 1),
            RouteSegment(100, pcn, endIndex = 2),
            RouteSegment(100, road, endIndex = 3),
        ).pieces()
        assertEquals(
            listOf(RoadPiece(RoadKind.PCN, listOf(a, b, c)), RoadPiece(RoadKind.ROAD, listOf(c, d))),
            pieces,
        )
    }

    @Test
    fun `分段对不上坐标时不切`() {
        val tags = RouteParser.parseTags("highway=primary")
        assertTrue(route(RouteSegment(100, tags, endIndex = -1)).pieces().isEmpty())
    }

    @Test
    fun `统计各种路的长度`() {
        val r = route(
            RouteSegment(700, RouteParser.parseTags("highway=cycleway lcn=yes"), 1),
            RouteSegment(200, RouteParser.parseTags("highway=primary"), 2),
            RouteSegment(100, RouteParser.parseTags("highway=cycleway lcn=yes"), 3),
        )
        assertEquals(mapOf(RoadKind.PCN to 800, RoadKind.ROAD to 200), r.breakdown())
    }

    @Test
    fun `起点终点附近的推车不算，途中的算`() {
        val push = RouteParser.parseTags("highway=corridor")
        val path = RouteParser.parseTags("highway=footway")
        val r = Route(
            points = (0..5).map { LatLon(1.3 + it * 0.001, 103.8) },
            distanceMeters = 0, durationSeconds = 0, ascendMeters = 0,
            segments = listOf(
                RouteSegment(50, push, 1),
                RouteSegment(500, path, 2),
                RouteSegment(30, push, 3),
                RouteSegment(500, path, 4),
                RouteSegment(40, push, 5),
            ),
        )
        assertEquals(30, r.pushBetweenEnds())
    }
}
