package com.personal.rando.ride

import com.personal.rando.places.PcnLoop
import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReviewTest {

    private val sg = TimeZone.getTimeZone("Asia/Singapore")

    /** 新加坡时间某天某点 */
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(sg).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    private fun ride(start: Long, km: Double, minutes: Long, points: List<LatLon> = emptyList()) =
        Ride(start, start + minutes * 60_000, km * 1000, minutes * 60, 10.0, 0.0, points.map { TrackPoint(it, null, 0) })

    private val now = at(2026, 10, 25, 9)

    private val rides = listOf(
        ride(at(2026, 10, 3, 7), 20.0, 60),
        ride(at(2026, 10, 4, 7), 10.0, 40),
        ride(at(2026, 10, 5, 18), 30.0, 100),
        ride(at(2026, 10, 20, 7), 3.0, 5),
        ride(at(2026, 9, 10, 7), 40.0, 120),
        ride(at(2025, 12, 31, 7), 15.0, 50),
    )

    @Test
    fun `这个月`() {
        val r = Reviews.of(rides, ReviewSpan.MONTH, now, zone = sg)
        assertEquals("2026 年 10 月", r.title)
        assertEquals(4, r.rides)
        assertEquals(63_000.0, r.meters, 0.1)
        assertEquals(4, r.days)
        assertEquals(3, r.streak)
        assertEquals("早上", r.favoriteTime)
        assertEquals(30_000.0, r.longest!!.distanceMeters, 0.1)
        // 3 公里那次虽然快，太短不算
        assertEquals(20_000.0, r.fastest!!.distanceMeters, 0.1)
        assertEquals(40_000.0, r.previousMeters, 0.1)
        assertNull(r.newPcnMeters)
    }

    @Test
    fun `往前翻、按年`() {
        val sept = Reviews.of(rides, ReviewSpan.MONTH, now, back = 1, zone = sg)
        assertEquals("2026 年 9 月", sept.title)
        assertEquals(1, sept.rides)
        assertEquals(0.0, sept.previousMeters, 0.0)
        val year = Reviews.of(rides, ReviewSpan.YEAR, now, zone = sg)
        assertEquals("2026 年", year.title)
        assertEquals(5, year.rides)
        assertEquals(15_000.0, year.previousMeters, 0.1)
        assertTrue(Reviews.of(rides, ReviewSpan.MONTH, now, back = 3, zone = sg).empty)
    }

    @Test
    fun `新点亮的公园连道`() {
        val pcn = (0..10).map { LatLon(1.3, 103.9 + it * 0.001) }
        val loop = PcnLoop("Eastern Coastal Loop", listOf(pcn))
        val withPcn = listOf(
            ride(at(2026, 9, 10, 7), 5.0, 20, pcn.take(6)),
            ride(at(2026, 10, 3, 7), 5.0, 20, pcn),
        )
        val r = Reviews.of(withPcn, ReviewSpan.MONTH, now, zone = sg, loops = listOf(loop))
        // 9 月骑过前一半，10 月骑完全程：新点亮后一半
        assertEquals(550.0, r.newPcnMeters!!, 60.0)
    }
}
