package com.personal.rando.search

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchTest {

    @Test
    fun `解析 Photon 结果`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"Marina Bay","district":"Downtown Core","postcode":"018935"},
               "geometry":{"type":"Point","coordinates":[103.8536866,1.277658]}},
              {"type":"Feature","properties":{"housenumber":"10","street":"Bayfront Avenue","district":"Downtown Core"},
               "geometry":{"type":"Point","coordinates":[103.85,1.28]}}
            ]}
        """.trimIndent()
        val places = PlaceSearch.parse(json)
        assertEquals(
            listOf(
                Place("Marina Bay", "Downtown Core · 018935", LatLon(lat = 1.277658, lon = 103.8536866)),
                Place("10 Bayfront Avenue", "Downtown Core", LatLon(lat = 1.28, lon = 103.85)),
            ),
            places,
        )
    }

    @Test
    fun `没名字也没街道的跳过，重复的去掉`() {
        val json = """
            {"features":[
              {"properties":{},"geometry":{"coordinates":[103.8,1.3]}},
              {"properties":{"name":"East Coast Park"},"geometry":{"coordinates":[103.9,1.30]}},
              {"properties":{"name":"East Coast Park"},"geometry":{"coordinates":[103.91,1.301]}}
            ]}
        """.trimIndent()
        assertEquals(listOf("East Coast Park"), PlaceSearch.parse(json).map { it.name })
    }
}
