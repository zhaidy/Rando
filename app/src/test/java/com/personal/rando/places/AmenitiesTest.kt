package com.personal.rando.places

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import com.personal.rando.routing.RouteTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmenitiesTest {

    private val json = """
        {"version":0.6,"elements":[
          {"type":"node","id":1,"lat":1.3001,"lon":103.8003,"tags":{"amenity":"drinking_water"}},
          {"type":"node","id":2,"lat":1.30012,"lon":103.80032,"tags":{"amenity":"drinking_water"}},
          {"type":"way","id":3,"center":{"lat":1.3050,"lon":103.8002},"tags":{"amenity":"toilets"}},
          {"type":"node","id":4,"lat":1.3080,"lon":103.8001,"tags":{"shop":"convenience","brand":"7-Eleven"}},
          {"type":"node","id":5,"lat":1.3020,"lon":103.8000,"tags":{"amenity":"bench"}},
          {"type":"node","id":6,"lat":1.3030,"lon":103.8200,"tags":{"amenity":"toilets"}}
        ]}
    """.trimIndent()

    /** 往北骑 1.1 公里 */
    private val route = Route(
        points = listOf(LatLon(1.300, 103.800), LatLon(1.310, 103.800)),
        distanceMeters = 1113, durationSeconds = 0, ascendMeters = 0, segments = emptyList(),
    )

    @Test
    fun `认出补给的种类，画成面的取中心点，不认识的跳过`() {
        val list = AmenityQuery.parse(json)
        assertEquals(
            listOf(AmenityType.WATER, AmenityType.WATER, AmenityType.TOILET, AmenityType.SHOP, AmenityType.TOILET),
            list.map { it.type },
        )
        assertEquals(LatLon(1.3050, 103.8002), list[2].location)
        assertEquals("7-Eleven", list[3].name)
    }

    @Test
    fun `排到路线上：按先后，太远的不要，挨着的同类并成一处`() {
        val placed = AmenityQuery.parse(json).onRoute(RouteTimeline(route))
        assertEquals(listOf(AmenityType.WATER, AmenityType.TOILET, AmenityType.SHOP), placed.map { it.amenity.type })
        assertEquals(556.0, placed[1].alongMeters, 5.0)
    }

    @Test
    fun `前方最近的每种补给`() {
        val placed = AmenityQuery.parse(json).onRoute(RouteTimeline(route))
        val next = placed.nextOfEach(along = 100.0)
        assertEquals(setOf(AmenityType.TOILET, AmenityType.SHOP), next.keys)
        assertEquals(456.0, next.getValue(AmenityType.TOILET), 5.0)
    }

    @Test
    fun `查询语句沿着路线查`() {
        val q = AmenityQuery.build(route.points)
        assertTrue(q, "around:80,1.30000,103.80000,1.31000,103.80000" in q)
        assertTrue("drinking_water" in q && "convenience" in q)
    }
}
