package com.personal.rando.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DaylightTest {

    private fun sgt(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(Sun.SINGAPORE).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun minutesOfDay(millis: Long): Int =
        Calendar.getInstance(Sun.SINGAPORE).apply { timeInMillis = millis }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    @Test
    fun `新加坡的日出日落和气象局公布的差不了几分钟`() {
        // 6 月 21 日：日出 07:00、日落 19:12 左右
        val (rise, set) = Sun.riseAndSet(sgt(2026, 6, 21, 10))
        assertEquals(7 * 60, minutesOfDay(rise), 4)
        assertEquals(19 * 60 + 12, minutesOfDay(set), 4)
        // 11 月初日落最早，18:50 左右
        assertEquals(18 * 60 + 50, minutesOfDay(Sun.sunset(sgt(2026, 11, 3, 10))), 4)
        // 2026-02-10：日落 19:20 左右，一年里最晚的时候
        assertEquals(19 * 60 + 20, minutesOfDay(Sun.sunset(sgt(2026, 2, 10, 10))), 4)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("期望 $expected 左右，实际 $actual", kotlin.math.abs(expected - actual) <= tolerance)

    @Test
    fun `白天黑夜`() {
        assertFalse(Sun.isDark(sgt(2026, 10, 24, 12)))
        assertTrue(Sun.isDark(sgt(2026, 10, 24, 21)))
        assertTrue(Sun.isDark(sgt(2026, 10, 24, 5)))
    }

    @Test
    fun `规划时骑到天黑才提醒`() {
        assertNull(DaylightAlerts.plan(sgt(2026, 10, 24, 9), 3600))
        val late = DaylightAlerts.plan(sgt(2026, 10, 24, 18, 20), 3600)
        assertNotNull(late)
        assertTrue(late!!, "后半程要开车灯" in late)
        assertEquals("天黑了，记得打开前后车灯", DaylightAlerts.plan(sgt(2026, 10, 24, 21), 600))
    }

    @Test
    fun `骑的时候日落前二十分钟和天黑时各说一次`() {
        val a = DaylightAlerts()
        assertNull(a.next(sgt(2026, 10, 24, 17)))
        val soon = a.next(sgt(2026, 10, 24, 18, 40))
        assertNotNull(soon)
        assertTrue(soon!!, "日落" in soon)
        assertNull(a.next(sgt(2026, 10, 24, 18, 45)))
        assertEquals("天黑了，记得打开前后车灯", a.next(sgt(2026, 10, 24, 19, 5)))
        assertNull(a.next(sgt(2026, 10, 24, 19, 30)))
    }
}
