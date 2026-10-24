package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTest {

    private fun route(meters: Int, seconds: Int) =
        Route(points = listOf(LatLon(1.3, 103.8), LatLon(1.31, 103.81)), distanceMeters = meters, durationSeconds = seconds, ascendMeters = 0, segments = emptyList())

    @Test
    fun `骑得不够多时没有我的速度`() {
        assertNull(MySpeed.of(emptyList()))
        // 只有一次
        assertNull(MySpeed.of(listOf(20_000.0 to 3_600L)))
        // 两次加起来不到 10 公里
        assertNull(MySpeed.of(listOf(4_000.0 to 900L, 4_000.0 to 900L)))
    }

    @Test
    fun `我的速度是最近几次的总距离除以在骑的总时间，太短的不算`() {
        val speed = MySpeed.of(listOf(10_000.0 to 2_000L, 500.0 to 10L, 8_000.0 to 1_600L))!!
        assertEquals(18_000.0 / 3_600, speed, 1e-9)
        // 只看最近 10 次
        val many = List(10) { 5_000.0 to 1_000L } + List(5) { 5_000.0 to 100L }
        assertEquals(5.0, MySpeed.of(many)!!, 1e-9)
    }

    @Test
    fun `按我的速度时用距离除以平均速度，其他档位用 BRouter 算的`() {
        val r = route(18_000, 2_400)
        assertEquals(3_600, RoutePrefs(pace = Pace.MINE, mySpeed = 5.0).duration(r))
        assertEquals(2_400, RoutePrefs(pace = Pace.MINE, mySpeed = null).duration(r))
        assertEquals(2_400, RoutePrefs(pace = Pace.FAST, mySpeed = 5.0).duration(r))
    }

    @Test
    fun `选了档位把功率带给 BRouter`() {
        val stops = listOf(LatLon(1.0, 103.0), LatLon(1.1, 103.1))
        assertTrue(BRouterClient().url(stops, "trekking", 160).contains("bikerPower=160"))
        assertTrue("bikerPower" !in BRouterClient().url(stops, "trekking"))
    }
}
