package com.personal.rando.routing

/**
 * 路线安全分（0–100）：每段路按 OSM 标签打分，再按长度加权平均。
 * 自行车道、公园连道最高，住宅区小路其次，越是大马路分越低。
 */
object SafetyScore {

    /** 算作「安静的路」的分数线，用来统计安静路占比 */
    const val QUIET = 80

    data class Result(val score: Int, val quietShare: Double) {
        val label: String
            get() = when {
                score >= QUIET -> "很安全"
                score >= 60 -> "还行"
                else -> "要小心"
            }
    }

    fun of(route: Route): Result? {
        val segments = route.segments.filter { it.distanceMeters > 0 }
        val total = segments.sumOf { it.distanceMeters }
        if (total == 0) return null
        val weighted = segments.sumOf { wayScore(it.wayTags).toLong() * it.distanceMeters }
        val quiet = segments.filter { wayScore(it.wayTags) >= QUIET }.sumOf { it.distanceMeters }
        return Result(
            score = Math.round(weighted.toDouble() / total).toInt(),
            quietShare = quiet.toDouble() / total,
        )
    }

    fun wayScore(tags: Map<String, String>): Int {
        val highway = tags["highway"]
        val bicycle = tags["bicycle"]
        if (highway == "cycleway") return 100
        // 禁止骑车的路段只能推着走
        if (bicycle == "no" || bicycle == "dismount") return 40
        val base = when (highway) {
            "path", "footway", "pedestrian", "track", "bridleway" ->
                if (bicycle in BIKE_OK) 95 else 75
            "living_street", "residential", "service", "unclassified" -> 80
            "tertiary", "tertiary_link" -> 60
            "secondary", "secondary_link" -> 45
            "primary", "primary_link" -> 30
            "trunk", "trunk_link", "motorway", "motorway_link" -> 10
            "steps" -> 30
            else -> 60
        }
        // 马路上画了自行车道、或者旁边有隔离的车道，加分
        val hasLane = CYCLEWAY_KEYS.any { tags[it] in LANE_VALUES }
        return if (hasLane) minOf(100, base + 15) else base
    }

    private val BIKE_OK = setOf("yes", "designated", "permissive")
    private val CYCLEWAY_KEYS = listOf("cycleway", "cycleway:left", "cycleway:right", "cycleway:both")
    private val LANE_VALUES = setOf("lane", "track", "separate", "shared_lane")
}
