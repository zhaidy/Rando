package com.personal.rando.ride

import com.personal.rando.nav.NavProgress
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Turn
import org.junit.Assert.assertEquals
import org.junit.Test

class RideChipTest {

    private fun progress(next: Maneuver? = Maneuver(pointIndex = 3, turn = Turn.LEFT), offRoute: Boolean = false, arrived: Boolean = false) = NavProgress(
        alongMeters = 1000.0, remainingMeters = 4000.0, remainingSeconds = 900,
        next = next, distanceToNext = 300.0, following = null, distanceNextToFollowing = 0.0,
        offRoute = offRoute, arrived = arrived,
    )

    @Test
    fun `胶囊里是下一个路口的箭头和距离`() {
        assertEquals("↰ 300米", RideService.chip(progress(), rerouting = false))
    }

    @Test
    fun `没有路口了只写距离`() {
        assertEquals("300米", RideService.chip(progress(next = null), rerouting = false))
    }

    @Test
    fun `偏了、到了直接说`() {
        assertEquals("已偏离", RideService.chip(progress(offRoute = true), rerouting = false))
        assertEquals("已偏离", RideService.chip(progress(), rerouting = true))
        assertEquals("已到达", RideService.chip(progress(arrived = true), rerouting = false))
    }
}
