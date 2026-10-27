package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteTest {

    private val here = LatLon(1.300, 103.800)

    private fun route(vararg points: LatLon) = Route(
        points = points.toList(),
        distanceMeters = 200,
        durationSeconds = 60,
        ascendMeters = 0,
        segments = emptyList(),
    )

    /** 往北走 */
    private val north = route(here, LatLon(1.3005, 103.800), LatLon(1.301, 103.800))
    /** 先往南掉头 */
    private val south = route(here, LatLon(1.2995, 103.800), LatLon(1.299, 103.800))

    @Test
    fun `路线开头和骑行方向相反算掉头`() {
        assertTrue(Reroute.turnsBack(south, heading = 0.0))
        assertFalse(Reroute.turnsBack(north, heading = 0.0))
        // 拐个直角不算掉头
        assertFalse(Reroute.turnsBack(route(here, LatLon(1.300, 103.801)), heading = 0.0))
    }

    @Test
    fun `直接算的不用掉头就用它，要掉头就用往前那条`() {
        assertSame(north, Reroute.pick(direct = north, ahead = south, heading = 0.0))
        assertSame(north, Reroute.pick(direct = south, ahead = north, heading = 0.0))
        // 往前那条没算出来，掉头也比没有强
        assertSame(south, Reroute.pick(direct = south, ahead = null, heading = 0.0))
        assertSame(north, Reroute.pick(direct = null, ahead = north, heading = 0.0))
        // 停着不知道方向，就用直接算的
        assertSame(south, Reroute.pick(direct = south, ahead = north, heading = null))
        assertNull(Reroute.pick(direct = null, ahead = null, heading = 0.0))
    }

    @Test
    fun `往前的点放在骑行方向前面`() {
        val stops = Reroute.aheadStops(here, heading = 90.0, rest = listOf(LatLon(1.31, 103.81)))
        assertEquals(3, stops.size)
        assertEquals(Reroute.AHEAD_METERS, Geo.distance(here, stops[1]), 0.5)
        assertEquals(90.0, Geo.bearing(here, stops[1]), 0.5)
    }
}
