package com.personal.rando.nav

import com.personal.rando.routing.CrossingType
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.ui.Format

/**
 * 路况语音：快要上马路时提前说一声，快到明确标了「无标记」的路口时提醒看车。
 * 「没标明」的路口数据里太多，每个都说会一路响个不停，只在屏幕上显示。
 * 和 [VoicePrompter] 一样只出文字，每处只说一次
 */
class RoadAlerts(private val timeline: RouteTimeline) {

    /** 已经提醒过的路段起点、路口位置（米） */
    private val announced = mutableSetOf<Long>()

    fun next(along: Double): String? {
        timeline.ahead(along)?.next?.let { next ->
            val distance = next.startMeters - along
            if (next.kind == RoadKind.ROAD && distance <= ROAD_WARNING_METERS && announced.add(key(next.startMeters))) {
                return "前方${Format.distance(round(distance))}要上马路，约${Format.distance(round(next.length))}"
            }
        }
        timeline.nextCrossing(along)?.let { c ->
            if (c.type == CrossingType.UNMARKED && c.distance <= CROSSING_WARNING_METERS && announced.add(key(c.atMeters))) {
                return "前方过马路，没有红绿灯，注意来车"
            }
        }
        return null
    }

    private fun key(meters: Double) = Math.round(meters)

    private fun round(meters: Double): Int =
        if (meters >= 100) (Math.round(meters / 50) * 50).toInt() else (Math.round(meters / 10) * 10).toInt().coerceAtLeast(10)

    companion object {
        const val ROAD_WARNING_METERS = 150.0
        const val CROSSING_WARNING_METERS = 50.0
    }
}
