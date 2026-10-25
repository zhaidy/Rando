package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RidePhotosTest {

    /** 早上 8 点开始，每分钟往东走一点，骑 30 分钟 */
    private val start = 1_729_728_000_000
    private val ride = Ride(
        start, start + 30 * 60_000, 6000.0, 1800, 0.0, 6.0,
        (0..30).map { TrackPoint(LatLon(1.30, 103.80 + it * 0.001), null, start + it * 60_000L) },
    )

    @Test
    fun `骑行中间拍的按前后两个点插出位置`() {
        val p = RidePhotos.place(start + 10 * 60_000 + 30_000, listOf(ride))!!
        assertEquals(1.30, p.lat, 1e-9)
        assertEquals(103.8105, p.lon, 1e-9)
    }

    @Test
    fun `骑完几分钟内拍的算在终点，再晚就对不上`() {
        assertEquals(ride.points.last().location, RidePhotos.place(ride.endedAt + 3 * 60_000, listOf(ride)))
        assertEquals(ride.points.first().location, RidePhotos.place(start - 60_000, listOf(ride)))
        assertNull(RidePhotos.place(ride.endedAt + 10 * 60_000, listOf(ride)))
    }

    @Test
    fun `照片多时按时间均匀挑，少的全要`() {
        assertEquals(listOf(0, 1, 2), RidePhotos.pick(3))
        assertEquals(listOf(0, 3, 6, 9), RidePhotos.pick(10))
        assertEquals(emptyList<Int>(), RidePhotos.pick(0))
    }

    @Test
    fun `找照片的时间段从第一次开始前到最后一次结束后`() {
        val later = ride.copy(id = start + 3_600_000, endedAt = start + 4_000_000)
        assertEquals((start - RidePhotos.MARGIN_MS)..(start + 4_000_000 + RidePhotos.MARGIN_MS), RidePhotos.window(listOf(later, ride)))
        assertNull(RidePhotos.window(emptyList()))
    }
}
