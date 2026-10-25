package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.RouteParser
import com.personal.rando.routing.RouteSegment
import com.personal.rando.routing.RouteTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadAlertsTest {

    private val step = 111.32

    private fun alerts(vararg segs: Pair<String, String>) = RoadAlerts(
        RouteTimeline(
            Route(
                points = (0..segs.size).map { LatLon(1.300 + it * 0.001, 103.8) },
                distanceMeters = 0,
                durationSeconds = 0,
                ascendMeters = 0,
                segments = segs.mapIndexed { i, (way, node) ->
                    RouteSegment(step.toInt(), RouteParser.parseTags(way), i + 1, RouteParser.parseTags(node))
                },
            )
        )
    )

    private val pcn = "highway=cycleway lcn=yes"

    @Test
    fun `快上马路时说一次`() {
        val a = alerts(pcn to "", pcn to "", "highway=primary" to "", pcn to "")
        assertNull(a.next(10.0))
        assertEquals("前方100 米要上马路，约100 米", a.next(2 * step - 100))
        assertNull(a.next(2 * step - 60))
    }

    @Test
    fun `无标记的路口提醒一次，过了也不再说`() {
        val a = alerts(pcn to "highway=crossing crossing=unmarked", pcn to "", pcn to "")
        assertNull(a.next(10.0))
        assertEquals("前方过马路，没有红绿灯，注意来车", a.next(step - 30))
        assertNull(a.next(step - 10))
        assertNull(a.next(step + 2))
    }

    @Test
    fun `有红绿灯和没标明的路口不念`() {
        assertNull(alerts(pcn to "highway=crossing crossing=traffic_signals", pcn to "").next(step - 30))
        assertNull(alerts(pcn to "highway=crossing", pcn to "").next(step - 30))
    }

    @Test
    fun `快到天桥时说怎么上下`() {
        val a = alerts(pcn to "highway=elevator", "highway=footway bridge=yes" to "highway=elevator", pcn to "")
        assertNull(a.next(0.0))
        assertEquals("前方过天桥，有电梯", a.next(step + 20))
        assertNull(a.next(step + 40))
    }

    @Test
    fun `上马路的地方正好是无标记路口，两句都说`() {
        val a = alerts(pcn to "highway=crossing crossing=unmarked", "highway=primary" to "", pcn to "")
        assertEquals("前方100 米要上马路，约100 米", a.next(step - 100))
        assertEquals("前方过马路，没有红绿灯，注意来车", a.next(step - 30))
    }
}
