package com.personal.rando.routing

enum class StructureKind(val label: String, val unit: String) {
    OVERPASS("天桥", "座"),
    UNDERPASS("地下通道", "处"),
}

/**
 * 路线经过的人行天桥或地下通道。上下怎么走看两头 [NEAR_METERS] 内有没有电梯点、台阶。
 * [path] 是这段的坐标，地图上单独画出来；[startIndex] 是桥头在 [Route.points] 里的序号
 */
data class Structure(
    val kind: StructureKind,
    val path: List<LatLon>,
    val startIndex: Int,
    val lengthMeters: Int,
    val hasElevator: Boolean,
    val hasSteps: Boolean,
) {
    /** 「有电梯」「只有台阶，要扛车」…… */
    val access: String
        get() = when {
            hasElevator -> "有电梯"
            hasSteps -> "只有台阶，要扛车"
            else -> "上下方式没标明"
        }
}

/** 两头这么近的电梯、台阶算这座天桥的 */
const val NEAR_METERS = 40

/** 比这短的桥多半是跨排水沟的小桥，不算 */
const val MIN_STRUCTURE_METERS = 10

private val WALKWAYS = setOf("footway", "path", "pedestrian", "steps", "corridor")
private val PCN_TAGS = listOf("route_bicycle_lcn", "route_bicycle_rcn", "route_bicycle_ncn", "lcn")

/** 人行天桥：步行道上的桥；公园连道和允许骑车的桥（多是跨河的平桥）不算。和路线配置里的 isOverpass 一致 */
fun isOverpass(tags: Map<String, String>): Boolean =
    tags["bridge"] == "yes" && tags["highway"] in WALKWAYS &&
        PCN_TAGS.none { tags[it] == "yes" } && tags["bicycle"] !in setOf("yes", "designated")

/** 地下通道；穿过商场楼里的通道（tunnel=building_passage）不算 */
fun isUnderpass(tags: Map<String, String>): Boolean =
    tags["tunnel"] == "yes" && (tags["highway"] in WALKWAYS || tags["highway"] == "cycleway")

fun Route.structures(): List<Structure> {
    if (segments.isEmpty() || segments.any { it.endIndex !in points.indices }) return emptyList()
    fun kindOf(s: RouteSegment) = when {
        isOverpass(s.wayTags) -> StructureKind.OVERPASS
        isUnderpass(s.wayTags) -> StructureKind.UNDERPASS
        else -> null
    }
    val result = mutableListOf<Structure>()
    var i = 0
    while (i < segments.size) {
        val kind = kindOf(segments[i])
        if (kind == null) {
            i++
            continue
        }
        var j = i
        while (j + 1 < segments.size && kindOf(segments[j + 1]) == kind) j++
        val length = (i..j).sumOf { segments[it].distanceMeters }
        if (length >= MIN_STRUCTURE_METERS) {
            val startIndex = if (i == 0) 0 else segments[i - 1].endIndex
            val (elevator, steps) = accessAround(i, j)
            result += Structure(
                kind = kind,
                path = points.subList(startIndex, segments[j].endIndex + 1),
                startIndex = startIndex,
                lengthMeters = length,
                hasElevator = elevator,
                hasSteps = steps,
            )
        }
        i = j + 1
    }
    return result
}

/** 第 first..last 段两头各 [NEAR_METERS] 米内（含桥上）有没有电梯点、台阶 */
private fun Route.accessAround(first: Int, last: Int): Pair<Boolean, Boolean> {
    var from = first
    var meters = 0
    while (from > 0 && meters < NEAR_METERS) meters += segments[--from].distanceMeters
    var to = last
    meters = 0
    while (to + 1 < segments.size && meters < NEAR_METERS) meters += segments[++to].distanceMeters
    // 前一段的终点就是这一段的起点，那个点上的电梯也算
    val nearby = segments.subList(from, to + 1)
    val elevator = nearby.any { it.nodeTags["highway"] == "elevator" } ||
        (from > 0 && segments[from - 1].nodeTags["highway"] == "elevator")
    val steps = nearby.any { it.wayTags["highway"] == "steps" || it.nodeTags["highway"] == "steps" }
    return elevator to steps
}
