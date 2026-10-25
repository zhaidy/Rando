package com.personal.rando.weather

import com.personal.rando.i18n.Lang
import com.personal.rando.routing.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindTest {

    /** 往北骑 1 公里 */
    private val north = listOf(LatLon(1.30, 103.80), LatLon(1.305, 103.80), LatLon(1.309, 103.80))
    /** 往北骑过去，再往南骑回来 */
    private val outAndBack = north + north.reversed().drop(1)

    @Test
    fun `风向的名字`() {
        assertEquals("北风", Wind(0.0, 10.0).name)
        assertEquals("东北风", Wind(40.0, 10.0).name)
        assertEquals("西北风", Wind(320.0, 10.0).name)
        assertEquals("北风", Wind(340.0, 10.0).name)
        assertEquals("南风", Wind(185.0, 10.0).name)
    }

    @Test
    fun `朝着风来的方向骑是逆风`() {
        assertEquals(20.0, Wind(0.0, 20.0).headwind(north), 0.01)
        assertEquals(-20.0, Wind(180.0, 20.0).headwind(north), 0.01)
        assertEquals(0.0, Wind(90.0, 20.0).headwind(north), 0.01)
    }

    @Test
    fun `骑一圈：先顺风出去就反过来骑`() {
        // 南风：往北骑是顺风，回来顶风
        val south = Wind(180.0, 20.0).along(outAndBack)
        assertTrue(south.outbound < 0 && south.back > 0)
        assertTrue(south.betterReversed())
        assertEquals("南风 20 km/h · 去程顺风、回程逆风", south.summary(loop = true))
        // 北风：已经是先逆风
        val northWind = Wind(0.0, 20.0).along(outAndBack)
        assertFalse(northWind.betterReversed())
        assertEquals("北风 20 km/h · 去程逆风、回程顺风", northWind.summary(loop = true))
        // 风小不折腾
        assertFalse(Wind(180.0, 5.0).along(outAndBack).betterReversed())
        assertEquals("南风 5 km/h，风不大", Wind(180.0, 5.0).along(outAndBack).summary(loop = true))
    }

    @Test
    fun `去哪儿：说全程`() {
        val w = Wind(0.0, 20.0).along(north)
        assertEquals("北风 20 km/h · 全程大多逆风", w.summary(loop = false))
        assertTrue(w.strongHeadwind)
    }

    @Test
    fun `英文的风`() = Lang.with(english = true) {
        assertEquals("South wind 20 km/h · tailwind out, headwind back", Wind(180.0, 20.0).along(outAndBack).summary(loop = true))
        assertEquals("North wind 20 km/h · mostly headwind", Wind(0.0, 20.0).along(north).summary(loop = false))
        assertEquals("South wind 5 km/h, light", Wind(180.0, 5.0).along(outAndBack).summary(loop = true))
    }
}
