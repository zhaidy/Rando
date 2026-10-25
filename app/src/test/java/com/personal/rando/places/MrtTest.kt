package com.personal.rando.places

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MrtTest {

    private val network = MrtNetwork(MrtData.parse(File("src/main/assets/mrt.json").readText()))
    private fun station(name: String) = network.stations.first { it.name == name }

    @Test
    fun `数据读得出来，换乘站有几个编号`() {
        assertTrue(network.stations.size > 150)
        assertEquals(listOf("NS17", "CC15"), station("Bishan").codes)
        assertEquals("金文泰", station("Clementi").zh)
        assertTrue(station("Clementi").exits.isNotEmpty())
    }

    @Test
    fun `同一条线直达`() {
        val trip = network.trip(station("Clementi"), station("Jurong East"))!!
        assertEquals(1, trip.legs.size)
        assertEquals("EW", trip.legs[0].line)
        assertEquals(1, trip.stops)
    }

    @Test
    fun `金文泰到碧山：换一次乘`() {
        val trip = network.trip(station("Clementi"), station("Bishan"))!!
        assertEquals(1, trip.transfers)
        assertTrue(trip.text, trip.text.startsWith("坐"))
        assertTrue(trip.minutes in 20..60)
    }

    @Test
    fun `轻轨也能到`() {
        val trip = network.trip(station("Punggol"), station("Clementi"))
        assertTrue(trip != null && trip.stops > 10)
    }

    @Test
    fun `离家近就直接骑回去，远就给坐车的方案`() {
        val home = station("Bishan").location.let { LatLon(it.lat + 0.004, it.lon) }
        assertNull(MrtHome.plan(network, LatLon(home.lat + 0.01, home.lon), home))
        val plan = MrtHome.plan(network, station("Clementi").location.let { LatLon(it.lat + 0.002, it.lon) }, home)!!
        assertEquals("Clementi", plan.board.name)
        assertEquals("Bishan", plan.alight.name)
        assertTrue(plan.text, plan.text.startsWith("骑 ") && plan.text.contains("到金文泰站"))
    }
}
