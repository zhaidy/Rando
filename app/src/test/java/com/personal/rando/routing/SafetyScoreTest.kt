package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyScoreTest {

    private fun tags(text: String) = RouteParser.parseTags(text)

    private fun routeOf(vararg segments: Pair<Int, String>) = Route(
        points = emptyList(),
        distanceMeters = segments.sumOf { it.first },
        durationSeconds = 0,
        ascendMeters = 0,
        segments = segments.map { RouteSegment(it.first, tags(it.second)) },
    )

    @Test
    fun `自行车道满分，大马路最低`() {
        assertEquals(100, SafetyScore.wayScore(tags("highway=cycleway")))
        assertEquals(80, SafetyScore.wayScore(tags("highway=residential")))
        assertEquals(30, SafetyScore.wayScore(tags("highway=primary")))
        assertEquals(10, SafetyScore.wayScore(tags("highway=trunk")))
    }

    @Test
    fun `允许骑车的公园连道比没标的小路高`() {
        assertEquals(95, SafetyScore.wayScore(tags("highway=footway bicycle=designated")))
        assertEquals(75, SafetyScore.wayScore(tags("highway=footway")))
    }

    @Test
    fun `禁止骑车的路要推车，分数低`() {
        assertEquals(40, SafetyScore.wayScore(tags("highway=path bicycle=no")))
    }

    @Test
    fun `马路上画了自行车道加分`() {
        assertEquals(60, SafetyScore.wayScore(tags("highway=secondary cycleway:left=lane")))
        assertEquals(95, SafetyScore.wayScore(tags("highway=residential cycleway=track")))
    }

    @Test
    fun `按长度加权`() {
        // 900 米自行车道 + 100 米主干道 → (900*100 + 100*30) / 1000 = 93
        val result = SafetyScore.of(routeOf(900 to "highway=cycleway", 100 to "highway=primary"))!!
        assertEquals(93, result.score)
        assertEquals(0.9, result.quietShare, 1e-9)
        assertEquals("很安全", result.label)
    }

    @Test
    fun `大半是马路就要小心`() {
        val result = SafetyScore.of(routeOf(800 to "highway=primary", 200 to "highway=residential"))!!
        assertEquals(40, result.score)
        assertEquals("要小心", result.label)
    }

    @Test
    fun `没有分段时不打分`() {
        assertNull(SafetyScore.of(routeOf()))
    }
}
