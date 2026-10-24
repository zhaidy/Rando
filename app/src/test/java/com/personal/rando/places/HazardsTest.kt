package com.personal.rando.places

import com.personal.rando.group.GroupEvent
import com.personal.rando.group.GroupProtocol
import com.personal.rando.routing.BRouterClient
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Nogos
import com.personal.rando.routing.Route
import com.personal.rando.routing.RouteTimeline
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardsTest {

    /** 往北骑 1.1 公里 */
    private val route = Route(
        points = listOf(LatLon(1.300, 103.800), LatLon(1.310, 103.800)),
        distanceMeters = 1113, durationSeconds = 0, ascendMeters = 0, segments = emptyList(),
    )
    private val now = 1_000_000_000L

    @Test
    fun `导航时提醒前方路线上的，每处只说一次，身后的和路线外的不说`() {
        val ahead = Hazard("a", HazardType.CONSTRUCTION, LatLon(1.3050, 103.8001), now)
        val behind = Hazard("b", HazardType.CLOSED, LatLon(1.3010, 103.8000), now)
        val off = Hazard("c", HazardType.BAD_SURFACE, LatLon(1.3050, 103.8100), now)
        val alerts = HazardAlerts()
        val timeline = RouteTimeline(route)
        val list = listOf(ahead, behind, off)
        // 还远着
        assertNull(alerts.next(list, LatLon(1.302, 103.8), timeline, along = 222.0, now = now))
        assertEquals("前方 156 米 施工，注意绕行", alerts.next(list, LatLon(1.3036, 103.8), timeline, along = 400.0, now = now))
        assertNull(alerts.next(list, LatLon(1.3040, 103.8), timeline, along = 445.0, now = now))
    }

    @Test
    fun `不导航时提醒身边的，过期的不说`() {
        val bad = Hazard("a", HazardType.BAD_SURFACE, LatLon(1.3005, 103.8000), now)
        val old = Hazard("b", HazardType.CLOSED, LatLon(1.3001, 103.8000), now - Hazard.LIFETIME_MS - 1)
        val alerts = HazardAlerts()
        assertEquals("前方 55 米 路面不好，注意减速", alerts.next(listOf(old, bad), LatLon(1.3, 103.8), null, null, now))
    }

    @Test
    fun `算路绕开施工封路，但起点终点附近的不绕，太远的不带`() {
        val stops = listOf(LatLon(1.30, 103.80), LatLon(1.32, 103.82))
        val points = listOf(
            LatLon(1.31, 103.81),
            // 就在终点旁边
            LatLon(1.3201, 103.8201),
            // 远在 20 公里外
            LatLon(1.45, 103.98),
        )
        assertEquals(listOf(LatLon(1.31, 103.81)), Nogos.near(stops, points))
        val url = BRouterClient().url(stops, "trekking", nogos = Nogos.near(stops, points))
        assertTrue(url, url.contains("nogos=103.810000%2C1.310000%2C30"))
    }

    @Test
    fun `路况发给队友再收回来`() {
        val h = Hazard("h1", HazardType.CLOSED, LatLon(1.3, 103.8), 42)
        val json = JSONObject(GroupProtocol.hazard(h))
        assertEquals("hazard", json.getString("t"))
        assertEquals("h1", json.getString("hid"))
        json.put("id", "member").put("name", "阿明")
        val got = (GroupProtocol.parse(json.toString(), 0) as GroupEvent.HazardAdded).hazard
        assertEquals(h.copy(by = "阿明"), got)
        assertEquals(GroupEvent.HazardRemoved("h1"), GroupProtocol.parse("""{"t":"hazard_del","id":"m","hid":"h1"}""", 0))
    }
}
