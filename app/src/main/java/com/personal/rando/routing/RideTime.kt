package com.personal.rando.routing

import com.personal.rando.nav.Sun
import java.util.Calendar

/** 什么时候骑：中午挑树荫，天黑挑路灯（「公园连道」配置才管用） */
object RideTime {

    /** 新加坡中午 11 点到下午 4 点太阳最毒 */
    fun shade(now: Long): Boolean =
        Calendar.getInstance(Sun.SINGAPORE).apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) in 11..15

    /** 天已经黑了，或者再过 [SOON_MS] 就黑 */
    fun night(now: Long): Boolean = Sun.isDark(now) || Sun.isDark(now + SOON_MS)

    /** 按现在的时间打开树荫、路灯 */
    fun RoutePrefs.at(now: Long): RoutePrefs = copy(shade = shade(now), night = night(now))

    private const val SOON_MS = 45 * 60_000L
}

/** 树荫下（BRouter 估的树林等级 3–6）的路占多少；没有树林数据（不是「公园连道」配置）也是 0 */
fun Route.shadeShare(): Double = share { tags -> tags["estimated_forest_class"] in SHADED }

/** 有路灯的路占多少：标了 lit=yes 的，加上马路（新加坡的马路都有灯） */
fun Route.litShare(): Double = share { tags ->
    tags["lit"] == "yes" || RoadKind.of(tags) in setOf(RoadKind.ROAD, RoadKind.ROAD_FOOTPATH, RoadKind.CROSSING)
}

private val SHADED = setOf("3", "4", "5", "6")

private fun Route.share(match: (Map<String, String>) -> Boolean): Double {
    val total = segments.sumOf { it.distanceMeters }
    if (total == 0) return 0.0
    return segments.filter { match(it.wayTags) }.sumOf { it.distanceMeters }.toDouble() / total
}
