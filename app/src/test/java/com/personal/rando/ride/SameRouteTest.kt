package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SameRouteTest {

    /** 沿 [path] 每隔一小段一个点；[jitter] 让每次的 GPS 稍微不一样 */
    private fun ride(id: Long, path: List<LatLon>, seconds: Long, jitter: Double = 0.0): Ride {
        val points = SameRoute.sample(path, 200).mapIndexed { i, p ->
            TrackPoint(LatLon(p.lat + jitter * ((i % 3) - 1), p.lon + jitter * ((i % 5) - 2) / 2), null, id + i * 1000L)
        }
        val meters = points.zipWithNext { a, b -> com.personal.rando.nav.Geo.distance(a.location, b.location) }.sum()
        return Ride(id, id + seconds * 1000, meters, seconds, 0.0, 0.0, points)
    }

    /** 往东 3 公里再往北 2 公里 */
    private val commute = listOf(LatLon(1.30, 103.80), LatLon(1.30, 103.827), LatLon(1.318, 103.827))
    private val loop = listOf(LatLon(1.30, 103.80), LatLon(1.30, 103.82), LatLon(1.32, 103.82), LatLon(1.32, 103.80), LatLon(1.30, 103.80))

    @Test
    fun `同一条路 GPS 有点飘也认得出来`() {
        assertTrue(SameRoute.matches(ride(1, commute, 900), ride(2, commute, 950, jitter = 0.00008)))
    }

    @Test
    fun `反方向、另一条路、太短的都不算`() {
        val a = ride(1, commute, 900)
        assertFalse(SameRoute.matches(a, ride(2, commute.reversed(), 900)))
        assertFalse(SameRoute.matches(a, ride(3, listOf(LatLon(1.30, 103.80), LatLon(1.318, 103.80), LatLon(1.318, 103.827)), 900)))
        assertFalse(SameRoute.matches(a, ride(4, commute.take(2), 600)))
        // 一圈：顺时针和逆时针起点终点一样，也不算
        assertFalse(SameRoute.matches(ride(5, loop, 1200), ride(6, loop.reversed(), 1200)))
        assertTrue(SameRoute.matches(ride(5, loop, 1200), ride(7, loop, 1250)))
    }

    @Test
    fun `比上次快慢、最好成绩`() {
        val first = ride(1_000_000, commute, 1000)
        val second = ride(2_000_000, commute, 950)
        val now = ride(3_000_000, commute, 870)
        val record = RouteRecord.of(now, listOf(second, first))!!
        assertEquals(2, record.before)
        assertEquals(80L, record.fasterThanLast)
        assertTrue(record.best)
        assertEquals("这条路第 3 次骑，比上次快 1 分 20 秒，最好成绩！", record.text)

        val slow = ride(4_000_000, commute, 990)
        assertEquals("这条路第 4 次骑，比上次慢 2 分钟，最好成绩 14:30", RouteRecord.of(slow, listOf(now, second, first))!!.text)
        assertNull(RouteRecord.of(first, emptyList()))
        // 看最早那次的记录：之前没骑过
        assertEquals("这条路第一次骑，最好成绩 14:30", RouteRecord.of(first, listOf(now, second))!!.text)
    }
}
