package com.personal.rando.routing

import com.personal.rando.nav.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnTripTest {

    /** 往北骑，每 10 米一个点（纬度 0.00009 度约 10 米） */
    private fun north(meters: Int) = (0..meters / 10).map { LatLon(1.3 + it * 0.0000898, 103.8) }

    @Test
    fun `没骑多远不用原路返回`() {
        assertNull(ReturnTrip.stops(north(200)))
        assertNull(ReturnTrip.stops(emptyList()))
    }

    @Test
    fun `骑了一圈回到出发点附近也不用`() {
        val out = north(2000)
        assertNull(ReturnTrip.stops(out + out.asReversed()))
    }

    @Test
    fun `从现在的位置倒着沿轨迹回到出发的地方`() {
        val track = north(5000)
        val stops = ReturnTrip.stops(track)
        assertNotNull(stops)
        stops!!
        assertEquals(track.last(), stops.first())
        assertEquals(track.first(), stops.last())
        // 途经点越来越靠近出发的地方，而且都在轨迹上
        val toHome = stops.map { Geo.distance(it, track.first()) }
        assertTrue(toHome.zipWithNext().all { (a, b) -> a > b })
        assertTrue(stops.size in 4..8)
    }

    @Test
    fun `骑很远时途经点也不会太多`() {
        val stops = ReturnTrip.stops(north(60_000))!!
        assertTrue(stops.size <= ReturnTrip.MAX_VIA + 2)
    }
}
