package com.personal.rando.places

import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Pace
import com.personal.rando.routing.RoutePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRoutesTest {

    private val loop = SavedRoute(
        name = "东海岸一圈", stops = listOf(LatLon(1.30, 103.90), LatLon(1.31, 103.93), LatLon(1.3001, 103.9001)),
        profile = BikeProfile.PCN, avoidRoads = true, avoidBridges = false, meters = 21_000, savedAt = 5,
    )

    @Test
    fun `存了再读出来一样`() {
        val commute = loop.copy(name = "上班", stops = listOf(LatLon(1.35, 103.82), LatLon(1.28, 103.85)), profile = BikeProfile.CITY)
        assertEquals(listOf(loop, commute), SavedRoutesJson.decode(SavedRoutesJson.encode(listOf(loop, commute))))
    }

    @Test
    fun `起点终点差不多的是一圈`() {
        assertTrue(loop.loop)
        assertFalse(loop.copy(stops = listOf(LatLon(1.35, 103.82), LatLon(1.28, 103.85))).loop)
    }

    @Test
    fun `同样的点和配置算已收藏；再骑时路线配置照收藏的，速度还按我的`() {
        val mine = RoutePrefs(BikeProfile.CITY, pace = Pace.FAST)
        val prefs = loop.prefs(mine)
        assertEquals(BikeProfile.PCN, prefs.profile)
        assertTrue(prefs.avoidRoads)
        assertEquals(Pace.FAST, prefs.pace)
        assertTrue(loop.same(listOf(LatLon(1.3001, 103.9), LatLon(1.31, 103.93), LatLon(1.30, 103.90)), prefs))
        assertFalse(loop.same(loop.stops, mine))
        assertFalse(loop.same(loop.stops.take(2), prefs))
    }

    @Test
    fun `收藏的是备选那条：记下来，换一条备选就不算已收藏`() {
        val other = loop.copy(alternative = 1)
        assertEquals(listOf(other), SavedRoutesJson.decode(SavedRoutesJson.encode(listOf(other))))
        val prefs = loop.prefs(RoutePrefs())
        assertTrue(other.same(other.stops, prefs, alternative = 1))
        assertFalse(other.same(other.stops, prefs))
        // 旧版本存的没有这个字段：当成第一条
        assertEquals(0, SavedRoutesJson.decode("""[{"name":"x","stops":[[1.3,103.9],[1.31,103.93]]}]""").single().alternative)
    }
}
