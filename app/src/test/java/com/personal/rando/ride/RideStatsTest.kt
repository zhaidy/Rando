package com.personal.rando.ride

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RideStatsTest {

    private val zone = TimeZone.getTimeZone("Asia/Singapore")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 8): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, 0)
        }.timeInMillis

    private fun ride(start: Long, km: Double) = Ride(start, start + 3_600_000, km * 1000, 3_000, 0.0, 0.0, emptyList())

    // 2026-10-24 是周六，这周从 10-19 周一开始
    private val now = at(2026, 10, 24, 20)

    @Test
    fun `按周合计，周一算一周的开始`() {
        val rides = listOf(
            ride(at(2026, 10, 19, 6), 10.0), // 本周一早上
            ride(at(2026, 10, 24), 5.0),
            ride(at(2026, 10, 18, 22), 20.0), // 上周日晚上
            ride(at(2026, 10, 5), 7.0),
        )
        val weeks = RideStats.recent(rides, Period.WEEK, 4, now, zone)
        assertEquals(listOf("9/28", "10/5", "10/12", "10/19"), weeks.map { it.label })
        assertEquals(listOf(0.0, 7.0, 20.0, 15.0), weeks.map { it.meters / 1000 })
        assertEquals(listOf(0, 1, 1, 2), weeks.map { it.rides })
    }

    @Test
    fun `按月合计，跨年也对`() {
        val rides = listOf(ride(at(2026, 10, 1), 30.0), ride(at(2026, 1, 31), 12.0), ride(at(2025, 12, 31, 23), 8.0))
        val months = RideStats.recent(rides, Period.MONTH, 11, now, zone)
        assertEquals("12月", months.first().label)
        assertEquals(8.0, months.first().meters / 1000, 0.01)
        assertEquals(12.0, months[1].meters / 1000, 0.01)
        assertEquals(30.0, months.last().meters / 1000, 0.01)
    }

    @Test
    fun `今天的骑行：只要今天开始的，按时间先后`() {
        val rides = listOf(
            ride(at(2026, 10, 24, 18), 6.0),
            ride(at(2026, 10, 25, 0), 3.0), // 明天凌晨
            ride(at(2026, 10, 24, 0), 4.0), // 今天刚过半夜
            ride(at(2026, 10, 23, 23), 9.0), // 昨晚
        )
        assertEquals(listOf(4.0, 6.0), RideStats.today(rides, now, zone).map { it.distanceMeters / 1000 })
    }

    @Test
    fun `日历：周一开头，月初前面空着，凑满整行`() {
        // 2026 年 10 月 1 日是周四：前面空三格；31 天 + 3 = 34，凑到 35
        val cells = RideStats.monthGrid(at(2026, 10, 20), zone)
        assertEquals(35, cells.size)
        assertEquals(listOf(null, null, null), cells.take(3))
        assertEquals(at(2026, 10, 1, hour = 0), cells[3])
        assertEquals(at(2026, 10, 31, hour = 0), cells[33])
        assertEquals(null, cells[34])
        assertEquals(at(2026, 9, 1, hour = 0), RideStats.shiftMonth(at(2026, 10, 20), -1, zone))
    }

    @Test
    fun `按开始那天分组，半夜后骑完的算开始那天`() {
        val late = ride(at(2026, 10, 25, hour = 23), 10.0)
        val morning = ride(at(2026, 10, 26, hour = 7), 5.0)
        val evening = ride(at(2026, 10, 26, hour = 18), 8.0)
        val days = RideStats.byDay(listOf(evening, late, morning), zone)
        assertEquals(listOf(late), days[at(2026, 10, 25, hour = 0)])
        assertEquals(listOf(morning, evening), days[at(2026, 10, 26, hour = 0)])
    }
}
