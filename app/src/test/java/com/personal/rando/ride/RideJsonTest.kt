package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class RideJsonTest {

    @Test
    fun `存进去再读出来不变，没海拔的点也行`() {
        val ride = Ride(
            id = 1_700_000_000_000,
            endedAt = 1_700_000_900_000,
            distanceMeters = 5321.5,
            movingSeconds = 840,
            ascendMeters = 23.0,
            maxSpeed = 9.2,
            points = listOf(
                TrackPoint(LatLon(1.30, 103.80), 12.5, 1_700_000_000_000),
                TrackPoint(LatLon(1.31, 103.81), null, 1_700_000_005_000),
            ),
        )
        assertEquals(ride, RideJson.decode(RideJson.encode(ride)))
        val withMates = ride.copy(teammates = listOf("爱妃", "小王"))
        assertEquals(withMates, RideJson.decode(RideJson.encode(withMates)))
    }
}
