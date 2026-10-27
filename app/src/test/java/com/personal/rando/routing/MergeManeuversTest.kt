package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeManeuversTest {
    /** 往北一条直线，每个点隔约 5.5 米 */
    private val points = (0..200).map { LatLon(1.3 + it * 0.00005, 103.8) }

    @Test
    fun `稍向右紧接着向左急转，合成左转`() {
        // brouter.de 实际返回的：[64, 6, 0, 5.0, 31], [65, 4, 0, 331.0, -118]
        val merged = mergeClose(listOf(Maneuver(64, Turn.SLIGHT_RIGHT, angle = 31), Maneuver(65, Turn.SHARP_LEFT, angle = -118)), points)
        assertEquals(listOf(Maneuver(64, Turn.LEFT, angle = -87)), merged)
    }

    @Test
    fun `右转紧接着左转是错位路口，合成直行`() {
        val merged = mergeClose(listOf(Maneuver(10, Turn.RIGHT, angle = 85), Maneuver(12, Turn.LEFT, angle = -89)), points)
        assertEquals(listOf(Maneuver(10, Turn.STRAIGHT, angle = -4)), merged)
    }

    @Test
    fun `夹着直行的只有一个真拐弯，原样留着`() {
        val keep = Maneuver(11, Turn.KEEP_LEFT, angle = -20)
        assertEquals(listOf(keep), mergeClose(listOf(Maneuver(10, Turn.STRAIGHT), keep), points))
    }

    @Test
    fun `离得远的和环岛不合`() {
        val far = listOf(Maneuver(10, Turn.RIGHT, angle = 90), Maneuver(20, Turn.LEFT, angle = -90))
        assertEquals(far, mergeClose(far, points))
        val roundabout = listOf(Maneuver(10, Turn.RIGHT, angle = 90), Maneuver(11, Turn.ROUNDABOUT, exit = 2, angle = 30))
        assertEquals(roundabout, mergeClose(roundabout, points))
    }

    @Test
    fun `两个左转加起来掉头`() {
        val merged = mergeClose(listOf(Maneuver(10, Turn.LEFT, angle = -95), Maneuver(11, Turn.LEFT, angle = -85)), points)
        assertEquals(Turn.U_TURN, merged.single().turn)
    }
}
