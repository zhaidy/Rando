package com.personal.rando.ui

import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.Measure
import com.personal.rando.routing.Route
import com.personal.rando.routing.RouteOption
import com.personal.rando.routing.RoutePrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteChoicesTest {

    private val route = Route(emptyList(), 6000, 1500, 0, emptyList())
    private val main = RouteOption(route, RoutePrefs(), best = listOf(Measure.ROAD))

    @Test
    fun `推荐的那条说是按偏好算的`() {
        assertEquals("推荐", optionTitle(main, 0))
        assertEquals("按你选的「公园连道」算的，也是马路最少", optionReason(main, 0, main))
    }

    @Test
    fun `其余的说怎么算的、好在哪、差在哪`() {
        val fast = RouteOption(
            route, RoutePrefs(profile = BikeProfile.ROAD), best = listOf(Measure.TIME),
            better = mapOf(Measure.TIME to 420), worse = mapOf(Measure.ROAD to 1200),
        )
        assertEquals("最快", optionTitle(fast, 1))
        assertEquals("按「公路车」算：快 7 分钟，但多 1.2 公里马路", optionReason(fast, 1, main))
        val other = RouteOption(route, RoutePrefs(), better = mapOf(Measure.CROSSINGS to 3))
        assertEquals("另一条", optionTitle(other, 2))
        assertEquals("同样的偏好，换一条路：少过 3 次马路", optionReason(other, 2, main))
    }
}
