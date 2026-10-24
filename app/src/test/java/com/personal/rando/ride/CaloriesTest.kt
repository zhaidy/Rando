package com.personal.rando.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaloriesTest {

    @Test
    fun `一小时 20 公里、65 公斤，平路大约 520 千卡`() {
        assertEquals(520, Calories.of(20_000.0, 3_600, 0.0, 65.0))
    }

    @Test
    fun `骑得快、爬坡、体重大都更多；没动是 0`() {
        val base = Calories.of(20_000.0, 3_600, 0.0, 65.0)
        assertTrue(Calories.of(26_000.0, 3_600, 0.0, 65.0) > base)
        assertTrue(Calories.of(20_000.0, 3_600, 200.0, 65.0) > base)
        assertTrue(Calories.of(20_000.0, 3_600, 0.0, 80.0) > base)
        assertEquals(0, Calories.of(0.0, 0, 0.0, 65.0))
        // 爬 200 米大约多 150 千卡
        assertEquals(150.0, (Calories.of(20_000.0, 3_600, 200.0, 65.0) - base).toDouble(), 5.0)
    }
}
