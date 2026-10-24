package com.personal.rando.ui

import com.personal.rando.places.ParkedBike
import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class ParkedTextTest {
    private val bike = ParkedBike(LatLon(1.30, 103.80), at = 0)

    @Test
    fun `离车多远、多久前停的`() {
        assertEquals("1.1 公里 · 2 小时前", parkedText(bike, LatLon(1.31, 103.80), 2 * 3_600_000L + 5))
        assertEquals("刚刚", parkedText(bike, null, 30_000))
        assertEquals("15 分钟前", parkedText(bike, null, 15 * 60_000L))
        assertEquals("3 天前", parkedText(bike, null, 3 * 86_400_000L))
    }
}
