package com.personal.rando.routing

import com.personal.rando.i18n.tr

/** 路口是哪种：有红绿灯最稳，没标记的要自己看车 */
enum class CrossingType(private val zh: String, private val en: String, val color: String) {
    SIGNALS("红绿灯", "Traffic lights", "#1B7F3B"),
    ZEBRA("斑马线", "Zebra crossing", "#3D7BD9"),
    UNMARKED("无标记", "Unmarked", "#E0672B"),
    UNKNOWN("没标明", "Unknown", "#8A8A8A");

    val label: String get() = tr(zh, en)

    companion object {
        /** 点的标签是路口才返回类型 */
        fun of(nodeTags: Map<String, String>): CrossingType? {
            if (nodeTags["highway"] != "crossing" && "crossing" !in nodeTags) return null
            return when (nodeTags["crossing"]) {
                "traffic_signals" -> SIGNALS
                "zebra", "marked", "uncontrolled" -> ZEBRA
                "unmarked" -> UNMARKED
                else -> UNKNOWN
            }
        }
    }
}

/** [pointIndex] 是路口在 [Route.points] 里的序号，用来算它在全程的第几米 */
data class Crossing(val location: LatLon, val type: CrossingType, val pointIndex: Int)

/** 挨这么近的路口点算同一次过马路（比如中间有隔离带的马路，两边各一个点） */
const val SAME_CROSSING_METERS = 20.0

/**
 * 这条路线要过几次马路、在哪、是哪种路口。来源有三种：
 * 路口点（crossing=*）、单独画的斑马线，以及被 [segmentKinds] 改算成过马路的一小截马路。
 * 沿着马路骑时路过的人行横道（横穿的是别人）不算
 */
fun Route.crossings(): List<Crossing> {
    if (segments.isEmpty()) return emptyList()
    val kinds = segmentKinds()
    data class Candidate(val along: Double, val pointIndex: Int, val type: CrossingType)
    val candidates = mutableListOf<Candidate>()
    var along = 0.0
    for ((i, s) in segments.withIndex()) {
        val start = along
        along += s.distanceMeters
        if (kinds[i] == RoadKind.CROSSING) {
            candidates += Candidate(start + s.distanceMeters / 2.0, s.endIndex, CrossingType.UNKNOWN)
        }
        CrossingType.of(s.nodeTags)?.let { type ->
            val alongRoad = kinds[i] in ROAD_KINDS && kinds.getOrNull(i + 1).let { it == null || it in ROAD_KINDS }
            if (!alongRoad) candidates += Candidate(along, s.endIndex, type)
        }
    }
    // 一串挨着的候选点并成一次，类型取最明确的那个
    val groups = mutableListOf<MutableList<Candidate>>()
    for (c in candidates.sortedBy { it.along }) {
        val last = groups.lastOrNull()
        if (last != null && c.along - last.last().along <= SAME_CROSSING_METERS) last += c else groups += mutableListOf(c)
    }
    return groups.mapNotNull { group ->
        val index = group.map { it.pointIndex }.firstOrNull { it in points.indices } ?: return@mapNotNull null
        Crossing(points[index], group.minOf { it.type }, index)
    }
}
