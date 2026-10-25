package com.personal.rando.routing

import com.personal.rando.nav.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOptionsTest {

    private val start = LatLon(1.3000, 103.8000)

    /** 朝 [bearing] 方向骑出去再拐到终点的一条路；[road] 米马路，其余是自行车道 */
    private fun route(bearing: Double, meters: Int, seconds: Int, road: Int = 0, climb: Int = 0): Route {
        val corner = Geo.offset(start, bearing, 2000.0)
        val end = LatLon(1.3000, 103.8400)
        val points = (0..20).map { lerp(start, corner, it / 20.0) } + (1..20).map { lerp(corner, end, it / 20.0) }
        val segments = listOfNotNull(
            RouteSegment(meters - road, mapOf("highway" to "cycleway")),
            if (road > 0) RouteSegment(road, mapOf("highway" to "primary")) else null,
        )
        return Route(points, meters, seconds, climb, segments)
    }

    private fun lerp(a: LatLon, b: LatLon, t: Double) = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    private val prefs = RoutePrefs()

    @Test
    fun `按偏好算一条，再加备选、少马路、最快的`() {
        val variants = RouteOptions.variants(prefs)
        assertEquals(RouteVariant(prefs), variants[0])
        assertEquals(RouteVariant(prefs, alternative = 1), variants[1])
        assertEquals(RouteVariant(prefs.copy(avoidRoads = true)), variants[2])
        assertEquals(RouteVariant(prefs.copy(profile = BikeProfile.ROAD)), variants[3])
        // 本来就不上马路：换成可以上马路的那条
        assertEquals(RouteVariant(prefs.copy(avoidRoads = false)), RouteOptions.variants(prefs.copy(avoidRoads = true))[2])
    }

    @Test
    fun `差不多一样的路线只留一条`() {
        val main = route(0.0, 6000, 1500)
        val twin = route(0.0, 6050, 1200)
        assertTrue(RouteOptions.same(main, twin))
        assertEquals(1, RouteOptions.pick(listOf(main to prefs, twin to prefs)).size)
    }

    @Test
    fun `哪一项都不比推荐的好就不给`() {
        val main = route(0.0, 6000, 1500)
        val worse = route(90.0, 7000, 1800, road = 500)
        assertFalse(RouteOptions.same(main, worse))
        assertEquals(1, RouteOptions.pick(listOf(main to prefs, worse to prefs)).size)
    }

    @Test
    fun `每条说出比推荐的好在哪、差在哪`() {
        val main = route(0.0, 6000, 1500, road = 800)
        val fast = route(90.0, 5000, 1100, road = 2000)
        val quiet = route(180.0, 7000, 1700)
        val fastPrefs = prefs.copy(profile = BikeProfile.ROAD)
        val options = RouteOptions.pick(listOf(main to prefs, fast to fastPrefs, quiet to prefs.copy(avoidRoads = true)))
        assertEquals(3, options.size)
        assertEquals(listOf(Measure.TIME, Measure.DISTANCE), options[1].best)
        assertEquals(mapOf(Measure.TIME to 400, Measure.DISTANCE to 1000), options[1].better)
        assertEquals(mapOf(Measure.ROAD to 1200), options[1].worse)
        assertEquals(listOf(Measure.ROAD), options[2].best)
        assertEquals(fastPrefs, options[1].prefs)
    }
}
