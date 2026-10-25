package com.personal.rando.nav

import com.personal.rando.i18n.tr
import com.personal.rando.routing.CrossingType
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.routing.StructureKind
import com.personal.rando.ui.Format

/**
 * 路况语音：快要上马路时提前说一声，快到天桥、地下通道时说怎么上下，
 * 快到明确标了「无标记」的路口时提醒看车。
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
                val ahead = Format.distance(round(distance))
                val length = Format.distance(round(next.length))
                return tr("前方${ahead}要上马路，约$length", "Road section in $ahead, about $length long")
            }
        }
        timeline.nextStructure(along)?.let { a ->
            if (a.distance <= STRUCTURE_WARNING_METERS && announced.add(key(a.atMeters))) {
                val s = a.structure
                return when (s.kind) {
                    StructureKind.OVERPASS -> tr("前方过天桥，${s.access}", "Overhead bridge ahead, ${s.access}")
                    StructureKind.UNDERPASS -> tr("前方走地下通道，${s.access}", "Underpass ahead, ${s.access}")
                }
            }
        }
        timeline.nextCrossing(along)?.let { c ->
            if (c.type == CrossingType.UNMARKED && c.distance <= CROSSING_WARNING_METERS && announced.add(key(c.atMeters))) {
                return tr("前方过马路，没有红绿灯，注意来车", "Crossing ahead with no traffic lights, watch for cars")
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
        const val STRUCTURE_WARNING_METERS = 100.0
    }
}
