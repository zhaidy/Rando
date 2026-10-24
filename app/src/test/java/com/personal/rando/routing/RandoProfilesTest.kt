package com.personal.rando.routing

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandoProfilesTest {

    @Test
    fun `两种模式只差马路开关`() {
        val normal = RandoProfiles.pcn(avoidRoads = false)
        val strict = RandoProfiles.pcn(avoidRoads = true)
        assertTrue(normal.contains("assign avoidRoads = 0"))
        assertTrue(strict.contains("assign avoidRoads = 1"))
        assertNotEquals(normal, strict)
        assertTrue(strict.startsWith("# Rando"))
    }
}
