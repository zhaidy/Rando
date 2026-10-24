package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteParserTest {

    // 从 brouter.de 实际返回裁剪出来的
    private val sample = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "properties": {
              "creator": "BRouter-1.7.11-beta",
              "track-length": "15863",
              "filtered ascend": "40",
              "total-time": "2794",
              "voicehints": [[1, 4, 0, 139.0, -125], [2, 14, 2, 24.0, 29], [2, 13, 0, 0.0, 0], [9, 2, 0, 10.0, -90]],
              "messages": [
                ["Longitude", "Latitude", "Elevation", "Distance", "CostPerKm", "ElevCost", "TurnCost", "NodeCost", "InitialCost", "WayTags", "NodeTags", "Time", "Energy"],
                ["103826931", "1353220", "44", "860", "9050", "321", "73", "0", "0", "reversedirection=yes highway=path surface=dirt bicycle=no", "", "119", "10756"],
                ["103826812", "1354274", "34", "24", "1150", "45", "102", "0", "0", "highway=residential surface=asphalt oneway=yes", "", "132", "11084"]
              ]
            },
            "geometry": {
              "type": "LineString",
              "coordinates": [[103.8198, 1.3521, 44.0], [103.826931, 1.35322, 40.0], [103.826812, 1.354274, 34.0]]
            }
          }]
        }
    """.trimIndent()

    @Test
    fun `读出总长、时间和爬升`() {
        val route = RouteParser.parse(sample)
        assertEquals(15863, route.distanceMeters)
        assertEquals(2794, route.durationSeconds)
        assertEquals(40, route.ascendMeters)
    }

    @Test
    fun `坐标是先经度后纬度`() {
        val route = RouteParser.parse(sample)
        assertEquals(3, route.points.size)
        assertEquals(LatLon(lat = 1.3521, lon = 103.8198), route.points.first())
    }

    @Test
    fun `每段带上长度和路的标签`() {
        val segments = RouteParser.parse(sample).segments
        assertEquals(listOf(860, 24), segments.map { it.distanceMeters })
        assertEquals("path", segments[0].wayTags["highway"])
        assertEquals("no", segments[0].wayTags["bicycle"])
        assertEquals("residential", segments[1].wayTags["highway"])
    }

    @Test
    fun `每段的终点对到坐标序号上`() {
        val segments = RouteParser.parse(sample).segments
        assertEquals(listOf(1, 2), segments.map { it.endIndex })
    }

    @Test
    fun `读出路口动作，不认识的代码和越界的序号跳过`() {
        val maneuvers = RouteParser.parse(sample).maneuvers
        assertEquals(
            listOf(
                Maneuver(pointIndex = 1, turn = Turn.SHARP_LEFT, exit = 0, angle = -125),
                Maneuver(pointIndex = 2, turn = Turn.ROUNDABOUT, exit = 2, angle = 29),
            ),
            maneuvers,
        )
        assertEquals("进入环岛，从第 2 个出口出", maneuvers[1].text)
    }

    @Test
    fun `没有 messages 时没有分段`() {
        val json = """{"features":[{"properties":{"track-length":"100"},"geometry":{"coordinates":[[103.8,1.3],[103.9,1.4]]}}]}"""
        val route = RouteParser.parse(json)
        assertEquals(100, route.distanceMeters)
        assertEquals(0, route.durationSeconds)
        assertEquals(emptyList<RouteSegment>(), route.segments)
    }

    @Test
    fun `标签里没有等号的词跳过`() {
        assertEquals(mapOf("highway" to "cycleway"), RouteParser.parseTags("highway=cycleway  junk =x"))
    }
}
