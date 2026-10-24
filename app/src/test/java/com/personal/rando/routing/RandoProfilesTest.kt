package com.personal.rando.routing

import org.junit.Assert.assertEquals
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
}
