package com.personal.rando.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class BRouterClientTest {

    @Test
    fun `请求里先经度后纬度，带上车型配置`() {
        val url = BRouterClient().url(
            from = LatLon(lat = 1.3521, lon = 103.8198),
            to = LatLon(lat = 1.2834, lon = 103.8545),
            profile = BikeProfile.ROAD,
        )
        assertEquals(
            "https://brouter.de/brouter?lonlats=103.819800%2C1.352100%7C103.854500%2C1.283400" +
                "&profile=fastbike&alternativeidx=0&format=geojson&timode=2",
            url,
        )
    }
}
