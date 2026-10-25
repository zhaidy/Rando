package com.personal.rando.ride

import com.personal.rando.places.PcnCoverage
import com.personal.rando.places.PcnLoop
import java.util.Calendar
import java.util.TimeZone

/** 回顾一个月，或者一整年 */
enum class ReviewSpan(val label: String) { MONTH("月"), YEAR("年") }

/**
 * 一个月（一年）的回顾：骑了多少、最长最快的一次、骑了几天、连着骑了几天、
 * 最常什么时候骑，和上个月（去年）比，新点亮了多少公园连道
 */
data class Review(
    val span: ReviewSpan,
    /** 这段时间从哪天开始（毫秒） */
    val start: Long,
    /** 「2026 年 10 月」「2026 年」 */
    val title: String,
    val rides: Int,
    val meters: Double,
    val movingSeconds: Long,
    val ascendMeters: Double,
    val longest: Ride?,
    val fastest: Ride?,
    val days: Int,
    /** 最长连着骑了几天 */
    val streak: Int,
    /** 「早上」「傍晚」：最常在什么时候出门 */
    val favoriteTime: String?,
    /** 上个月（去年）骑了多少米 */
    val previousMeters: Double,
    /** 这段时间新点亮的公园连道（米）；没有公园连道数据时是 null */
    val newPcnMeters: Double? = null,
) {
    val empty: Boolean get() = rides == 0
}

object Reviews {

    /** 算到快的一次至少要骑这么远，免得 500 米的一次冲刺排第一 */
    private const val FASTEST_MIN_METERS = 5_000.0

    /**
     * [now] 所在的月（年）往前数 [back] 个；[loops] 给了就算新点亮的公园连道
     * （这段时间结束时骑过的减去开始前骑过的）
     */
    fun of(
        rides: List<Ride>,
        span: ReviewSpan,
        now: Long,
        back: Int = 0,
        zone: TimeZone = TimeZone.getDefault(),
        loops: List<PcnLoop>? = null,
    ): Review {
        val start = startOf(now, span, zone, back)
        val end = startOf(now, span, zone, back - 1)
        val previousStart = startOf(now, span, zone, back + 1)
        val inside = rides.filter { it.startedAt in start until end }
        val previous = rides.filter { it.startedAt in previousStart until start }
        val dayNumbers = inside.map { dayNumber(it.startedAt, zone) }.toSortedSet()
        val newPcn = loops?.let {
            fun ridden(list: List<Ride>) = PcnCoverage.of(it, list.map { r -> r.points.map { p -> p.location } }).riddenMeters
            if (inside.isEmpty()) 0.0 else ridden(rides.filter { r -> r.startedAt < end }) - ridden(rides.filter { r -> r.startedAt < start })
        }
        return Review(
            span = span,
            start = start,
            title = title(start, span, zone),
            rides = inside.size,
            meters = inside.sumOf { it.distanceMeters },
            movingSeconds = inside.sumOf { it.movingSeconds },
            ascendMeters = inside.sumOf { it.ascendMeters },
            longest = inside.maxByOrNull { it.distanceMeters },
            fastest = inside.filter { it.distanceMeters >= FASTEST_MIN_METERS }.maxByOrNull { it.averageSpeed },
            days = dayNumbers.size,
            streak = longestRun(dayNumbers.toList()),
            favoriteTime = inside.groupingBy { timeOfDay(it.startedAt, zone) }.eachCount().maxByOrNull { it.value }?.key,
            previousMeters = previous.sumOf { it.distanceMeters },
            newPcnMeters = newPcn,
        )
    }

    /** 连着的天数最长有几天 */
    private fun longestRun(days: List<Long>): Int {
        var best = 0
        var run = 0
        var last: Long? = null
        for (d in days) {
            run = if (last != null && d == last + 1) run + 1 else 1
            best = maxOf(best, run)
            last = d
        }
        return best
    }

    /** 当地时间的第几天 */
    private fun dayNumber(millis: Long, zone: TimeZone): Long = Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L)

    private fun startOf(now: Long, span: ReviewSpan, zone: TimeZone, back: Int): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            when (span) {
                ReviewSpan.MONTH -> add(Calendar.MONTH, -back)
                ReviewSpan.YEAR -> {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    add(Calendar.YEAR, -back)
                }
            }
        }.timeInMillis

    private fun title(start: Long, span: ReviewSpan, zone: TimeZone): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = start }
        return when (span) {
            ReviewSpan.MONTH -> "${c.get(Calendar.YEAR)} 年 ${c.get(Calendar.MONTH) + 1} 月"
            ReviewSpan.YEAR -> "${c.get(Calendar.YEAR)} 年"
        }
    }
}
