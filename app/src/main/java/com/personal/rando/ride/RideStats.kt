package com.personal.rando.ride

import java.util.Calendar
import java.util.TimeZone

/** 按周还是按月统计 */
enum class Period(val label: String, val unit: String) {
    WEEK("周", "本周"),
    MONTH("月", "本月"),
}

/** 一周或一个月的合计 */
data class PeriodTotal(
    /** 这段时间从哪天开始（毫秒） */
    val start: Long,
    /** 图表下面的小字：「10/20」「10月」 */
    val label: String,
    val rides: Int,
    val meters: Double,
    val movingSeconds: Long,
)

object RideStats {

    /**
     * 最近 [count] 周（或月）每段的合计，旧的在前，最后一个是这周（这个月）。
     * 一周从周一算起，骑行按开始时间归到哪一段
     */
    fun recent(rides: List<Ride>, period: Period, count: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): List<PeriodTotal> {
        val starts = (count - 1 downTo 0).map { back -> startOf(now, period, zone, back) }
        return starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: startOf(now, period, zone, -1)
            val inside = rides.filter { it.startedAt in start until end }
            PeriodTotal(
                start = start,
                label = label(start, period, zone),
                rides = inside.size,
                meters = inside.sumOf { it.distanceMeters },
                movingSeconds = inside.sumOf { it.movingSeconds },
            )
        }
    }

    /** [now] 所在的那周（月）往前数 [back] 个的开始时间；back 为 -1 是下一个 */
    private fun startOf(now: Long, period: Period, zone: TimeZone, back: Int): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (period) {
                Period.WEEK -> {
                    // 周一是第一天：往回退到周一
                    val sinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                    add(Calendar.DAY_OF_MONTH, -sinceMonday)
                    add(Calendar.WEEK_OF_YEAR, -back)
                }
                Period.MONTH -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -back)
                }
            }
        }.timeInMillis

    private fun label(start: Long, period: Period, zone: TimeZone): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = start }
        return when (period) {
            Period.WEEK -> "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
            Period.MONTH -> "${c.get(Calendar.MONTH) + 1}月"
        }
    }
}
