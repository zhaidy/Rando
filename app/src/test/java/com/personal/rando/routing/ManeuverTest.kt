package com.personal.rando.routing

import com.personal.rando.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverTest {

    @Test
    fun `动作的说法跟着语言`() {
        assertEquals("左转", Turn.LEFT.text)
        assertEquals("进入环岛，从第 3 个出口出", Maneuver(0, Turn.ROUNDABOUT, exit = 3).text)
        Lang.with(english = true) {
            assertEquals("Turn left", Turn.LEFT.text)
            assertEquals("At the roundabout, take the 3rd exit", Maneuver(0, Turn.ROUNDABOUT, exit = 3).text)
            assertEquals("Park connector", RoadKind.PCN.label)
        }
    }

    @Test
    fun `英文序数`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(::ordinal))
    }
}
