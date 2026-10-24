package com.personal.rando.weather

import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    // 从 data.gov.sg 实际返回裁剪出来的
    private val forecastJson = """
        {"code":0,"data":{
          "area_metadata":[
            {"name":"Bedok","label_location":{"latitude":1.321,"longitude":103.924}},
            {"name":"Clementi","label_location":{"latitude":1.315,"longitude":103.76}},
            {"name":"Tampines","label_location":{"latitude":1.345,"longitude":103.944}}
          ],
          "items":[{"timestamp":"2026-10-24T23:00:00+08:00",
            "valid_period":{"start":"2026-10-24T23:00:00+08:00","end":"2026-10-25T01:00:00+08:00","text":"11.00 pm to 1.00 am"},
            "forecasts":[
              {"area":"Bedok","forecast":"Thundery Showers"},
              {"area":"Clementi","forecast":"Partly Cloudy (Night)"},
              {"area":"Tampines","forecast":"Light Rain"}
            ]}]
        }}
    """.trimIndent()

    private val rainJson = """
        {"code":0,"data":{
          "stations":[
            {"id":"S218","name":"Bukit Batok Street 34","location":{"latitude":1.36491,"longitude":103.75065}},
            {"id":"S24","name":"Upper Changi Road North","location":{"latitude":1.3678,"longitude":103.9826}}
          ],
          "readings":[{"timestamp":"2026-10-24T23:25:00+08:00","data":[
            {"stationId":"S218","value":0},{"stationId":"S24","value":0.2}
          ]}],
          "readingType":"TB1 Rainfall 5 Minute Total F","readingUnit":"mm"
        }}
    """.trimIndent()

    @Test
    fun `读出各区的两小时预报`() {
        val (forecasts, valid) = WeatherParser.forecasts(forecastJson)
        assertEquals(listOf("Bedok", "Clementi", "Tampines"), forecasts.map { it.area })
        assertEquals("11.00 pm to 1.00 am", valid)
        assertEquals(listOf("雷阵雨", null, "小雨"), forecasts.map { it.rainText })
    }

    @Test
    fun `读出雨量站最新读数`() {
        val gauges = WeatherParser.rainfall(rainJson)
        assertEquals(listOf(0.0, 0.2), gauges.map { it.millimeters })
        assertEquals("Upper Changi Road North", gauges[1].name)
    }

    @Test
    fun `只看路线经过的区`() {
        val (forecasts, _) = WeatherParser.forecasts(forecastJson)
        val weather = Weather(forecasts, gauges = WeatherParser.rainfall(rainJson))
        // 东海岸：从勿洛往西骑，不经过淡滨尼（4 公里外）也不经过樟宜雨量站
        val eastCoast = listOf(LatLon(1.315, 103.925), LatLon(1.305, 103.90), LatLon(1.300, 103.88))
        val along = weather.along(eastCoast)
        assertEquals(listOf("Bedok"), along.wetAreas.map { it.area })
        assertTrue(along.rainingNow.isEmpty())
        assertEquals("两小时内 Bedok 一带可能有雷阵雨", along.summary)
    }

    @Test
    fun `西边没雨`() {
        val (forecasts, _) = WeatherParser.forecasts(forecastJson)
        val west = listOf(LatLon(1.315, 103.76), LatLon(1.30, 103.78))
        val along = Weather(forecasts).along(west)
        assertTrue(along.dry)
        assertEquals("沿途两小时内不会下雨", along.summary)
    }

    @Test
    fun `附近雨量站在下雨就说正在下`() {
        val weather = Weather(emptyList(), gauges = WeatherParser.rainfall(rainJson))
        val changi = listOf(LatLon(1.36, 103.98), LatLon(1.37, 103.99))
        assertEquals("Upper Changi Road North 附近正在下雨", weather.along(changi).summary)
    }
}
