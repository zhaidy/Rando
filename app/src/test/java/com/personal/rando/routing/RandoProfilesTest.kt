package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandoProfilesTest {

    @Test
    fun `两个开关分别改各自那一行`() {
        val both = RandoProfiles.pcn(avoidRoads = true, avoidBridges = true)
        assertTrue(both.contains("assign avoidRoads = 1"))
        assertTrue(both.contains("assign avoidBridges = 1"))
        val none = RandoProfiles.pcn(avoidRoads = false, avoidBridges = false)
        assertTrue(none.contains("assign avoidRoads = 0"))
        assertTrue(none.contains("assign avoidBridges = 0"))
        assertTrue(none.startsWith("# Rando"))
    }

    @Test
    fun `只有公园连道要上传配置`() {
        assertEquals(
            RandoProfiles.pcn(avoidRoads = true, avoidBridges = false),
            RoutePrefs(BikeProfile.PCN, avoidRoads = true).customProfile,
        )
        assertNull(RoutePrefs(BikeProfile.CITY, avoidRoads = true, avoidBridges = true).customProfile)
    }

    @Test
    fun `中午挑树荫、天黑挑路灯`() {
        val p = RandoProfiles.pcn(avoidRoads = false, avoidBridges = false, shade = true, night = true)
        assertTrue(p.contains("assign shade = 1"))
        assertTrue(p.contains("assign night = 1"))
        assertTrue(RandoProfiles.pcn(false, false).contains("assign shade = 0"))
        assertTrue(RoutePrefs(BikeProfile.PCN, shade = true).customProfile!!.contains("assign shade = 1"))
    }

    @Test
    fun `按新加坡时间定`() {
        // 2026-10-25 12:30 / 08:00 / 21:00 新加坡时间（UTC+8）
        val noon = 1792902600000L
        val morning = noon - 270 * 60_000L
        val evening = noon + 510 * 60_000L
        assertTrue(RideTime.shade(noon))
        assertFalse(RideTime.shade(morning))
        assertFalse(RideTime.night(noon))
        assertTrue(RideTime.night(evening))
        val prefs = with(RideTime) { RoutePrefs().at(evening) }
        assertTrue(prefs.night)
        assertFalse(prefs.shade)
    }

    @Test
    fun `关了树荫、路灯就不挑`() {
        val noon = 1792902600000L
        val evening = noon + 510 * 60_000L
        val off = RoutePrefs(shadeAtNoon = false, litAtNight = false)
        assertFalse(with(RideTime) { off.at(noon) }.shade)
        assertFalse(with(RideTime) { off.at(evening) }.night)
        assertTrue(with(RideTime) { RoutePrefs().at(noon) }.shade)
    }

    @Test
    fun `树荫、路灯占多少`() {
        val route = Route(
            points = emptyList(), distanceMeters = 400, durationSeconds = 100, ascendMeters = 0,
            segments = listOf(
                RouteSegment(100, mapOf("highway" to "footway", "estimated_forest_class" to "4", "lit" to "yes")),
                RouteSegment(100, mapOf("highway" to "footway", "estimated_forest_class" to "1")),
                RouteSegment(200, mapOf("highway" to "residential")),
            ),
        )
        assertEquals(0.25, route.shadeShare(), 1e-9)
        assertEquals(0.75, route.litShare(), 1e-9)
    }
}
