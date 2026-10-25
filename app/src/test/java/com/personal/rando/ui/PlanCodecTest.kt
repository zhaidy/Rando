package com.personal.rando.ui

import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.search.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCodecTest {

    @Test
    fun `存下来再读出来还是同样的规划`() {
        val plan = PlannerState(
            start = Place("家", "", LatLon(1.31, 103.76)),
            destination = Place("Marina Bay Sands", "10 Bayfront Avenue", LatLon(1.2834, 103.8607)),
            via = listOf(Place("地图上的途经点", "", LatLon(1.29, 103.85))),
        )
        assertEquals(plan, PlanCodec.decode(PlanCodec.encode(plan), PlannerState()))
    }

    @Test
    fun `骑一圈也存`() {
        val plan = PlannerState(loop = LoopRequest(30_000, attempt = 2))
        assertEquals(plan, PlanCodec.decode(PlanCodec.encode(plan), PlannerState()))
    }

    @Test
    fun `收藏路线的车型和避开选项也存，不被设置里的盖掉`() {
        val plan = PlannerState(
            destination = Place("East Coast", "", LatLon(1.30, 103.91)),
            prefs = RoutePrefs(BikeProfile.ROAD, avoidRoads = true, avoidBridges = true),
        )
        val restored = PlanCodec.decode(PlanCodec.encode(plan), PlannerState(prefs = RoutePrefs(BikeProfile.PCN)))
        assertEquals(plan.prefs, restored.prefs)
    }

    @Test
    fun `读不了就当没有`() {
        assertEquals(PlannerState(), PlanCodec.decode("坏了", PlannerState()))
    }
}
