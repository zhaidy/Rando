package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class BRouterClientTest {

    @Test
    fun `请求里先经度后纬度，带上车型配置`() {
        val url = BRouterClient().url(
            stops = listOf(LatLon(lat = 1.3521, lon = 103.8198), LatLon(lat = 1.2834, lon = 103.8545)),
            profileName = BikeProfile.ROAD.brouterName,
        )
        assertEquals(
            "https://brouter.de/brouter?lonlats=103.819800%2C1.352100%7C103.854500%2C1.283400" +
                "&profile=fastbike&alternativeidx=0&format=geojson&timode=2",
            url,
        )
    }

    @Test
    fun `途经点按顺序夹在起点和终点中间`() {
        val url = BRouterClient().url(
            stops = listOf(LatLon(1.0, 103.0), LatLon(1.1, 103.1), LatLon(1.2, 103.2)),
            profileName = "trekking",
        )
        assertEquals(
            "lonlats=103.000000%2C1.000000%7C103.100000%2C1.100000%7C103.200000%2C1.200000",
            url.substringAfter('?').substringBefore('&'),
        )
    }
}
