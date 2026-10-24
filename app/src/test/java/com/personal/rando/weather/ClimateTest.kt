package com.personal.rando.weather

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateTest {

    private val stations = """
        "stations":[
          {"id":"S109","name":"Ang Mo Kio Avenue 5","location":{"latitude":1.3793,"longitude":103.85}},
          {"id":"S116","name":"Pasir Panjang Terminal","location":{"latitude":1.2824,"longitude":103.7545}}
        ]
    """

    @Test
    fun `气温取离我最近的站`() {
        val json = """{"code":0,"data":{$stations,"readings":[{"timestamp":"t","data":[
            {"stationId":"S109","value":27.7},{"stationId":"S116","value":31.4}]}],"readingUnit":"deg C"}}"""
        assertEquals(31.4, ClimateParser.nearestReading(json, LatLon(1.30, 103.76))!!, 0.0)
        assertEquals(27.7, ClimateParser.nearestReading(json, LatLon(1.37, 103.84))!!, 0.0)
    }

    @Test
    fun `紫外线取最新一小时，PSI 取最近的一区`() {
        val uv = """{"data":{"records":[{"index":[{"hour":"2026-10-24T13:00:00+08:00","value":9},
            {"hour":"2026-10-24T14:00:00+08:00","value":7},{"hour":"2026-10-24T12:00:00+08:00","value":4}]}]}}"""
        assertEquals(7, ClimateParser.uv(uv))
        val psi = """{"data":{"regionMetadata":[
            {"name":"west","labelLocation":{"latitude":1.35735,"longitude":103.7}},
            {"name":"central","labelLocation":{"latitude":1.35735,"longitude":103.82}},
            {"name":"east","labelLocation":{"latitude":1.35735,"longitude":103.94}}],
            "items":[{"readings":{"psi_twenty_four_hourly":{"west":148,"central":119,"east":113}}}]}}"""
        assertEquals("west" to 148, ClimateParser.psi(psi, LatLon(1.315, 103.765)))
        assertEquals("east" to 113, ClimateParser.psi(psi, LatLon(1.33, 103.95)))
        // 碧山算中区；南区没数时取全岛最高的
        assertEquals("central" to 119, ClimateParser.psi(psi, LatLon(1.35, 103.85)))
        assertEquals("west" to 148, ClimateParser.psi(psi, LatLon(1.27, 103.82)))
    }

    @Test
    fun `体感温度和分档`() {
        // 32°C、湿度 70%：体感 40 度上下
        val hot = Climate(tempC = 32.0, humidity = 70.0)
        assertTrue(hot.feelsLike!! in 38.0..42.0)
        assertEquals(Heat.EXTREME, hot.heat)
        assertEquals(26.0, Climate(tempC = 26.0, humidity = 90.0).feelsLike!!, 0.0)
        assertEquals(Haze.UNHEALTHY, Haze.of(148))
        assertEquals(Haze.MODERATE, Haze.of(100))
        assertEquals(Haze.GOOD, Haze.of(30))
    }

    @Test
    fun `卡片上的一行和要提醒的话`() {
        val c = Climate(tempC = 32.0, humidity = 70.0, uv = 9, psi = 148, psiRegion = "west")
        assertTrue(c.summary!!, c.summary!!.startsWith("32°C，体感 "))
        assertTrue(c.summary!!.endsWith("紫外线 9 很强 · PSI 148 不健康"))
        assertEquals(3, c.warning!!.split("；").size)
        val fine = Climate(tempC = 27.0, humidity = 60.0, uv = 2, psi = 40)
        assertNull(fine.warning)
        assertEquals("27°C · PSI 40 良好", fine.summary)
    }

    @Test
    fun `喝水提醒：平时半小时一次，热的时候二十分钟，提到前方的饮水机`() {
        val r = HydrationReminder()
        val start = 1_000_000L
        r.reset(start)
        val min = 60_000L
        assertNull(r.next(start + 25 * min, start, hot = false, waterAheadMeters = null))
        assertEquals("骑了 30 分钟，喝口水吧", r.next(start + 30 * min, start, hot = false, waterAheadMeters = 5_000.0))
        assertNull(r.next(start + 45 * min, start, hot = true, waterAheadMeters = null))
        assertEquals("骑了 50 分钟，喝口水吧，前方 800 米 有饮水机", r.next(start + 50 * min, start, hot = true, waterAheadMeters = 800.0))
    }
}
