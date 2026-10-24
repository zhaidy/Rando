package com.personal.rando.routing

/** 路线上每一段是什么路，地图上按颜色画出来，卡片上统计各占多少 */
enum class RoadKind(val label: String, val color: String) {
    PCN("公园连道", "#1B7F3B"),
    CYCLEWAY("自行车道", "#0097A7"),
    PATH("人行道、小路", "#3D7BD9"),
    ROAD_FOOTPATH("马路边人行道", "#C98A1B"),
    ROAD("马路", "#D93A2B"),
    CROSSING("过马路", "#8E44AD"),
    PUSH("推车", "#8A8A8A");

    companion object {
        fun of(tags: Map<String, String>): RoadKind {
            val highway = tags["highway"]
            val isPath = highway in PATHS
            val isPcn = PCN_TAGS.any { tags[it] == "yes" }
            return when {
                // 单独画出来的斑马线、过街自行车道
                tags["footway"] == "crossing" || tags["cycleway"] == "crossing" -> CROSSING
                highway == "steps" || (isPath && tags["bicycle"] in setOf("no", "dismount")) -> PUSH
                isPath && isPcn -> PCN
                highway == "cycleway" -> CYCLEWAY
                isPath -> PATH
                highway in ROADS -> if (tags["sidewalk"] in FOOTPATH_SIDES) ROAD_FOOTPATH else ROAD
                // 商场走廊、站台之类
                else -> PUSH
            }
        }

        private val PCN_TAGS = listOf("route_bicycle_lcn", "route_bicycle_rcn", "route_bicycle_ncn", "lcn")
        private val PATHS = setOf("cycleway", "footway", "path", "pedestrian", "track", "bridleway")
        private val ROADS = setOf(
            "residential", "living_street", "service", "unclassified", "road",
            "tertiary", "tertiary_link", "secondary", "secondary_link",
            "primary", "primary_link", "trunk", "trunk_link", "motorway", "motorway_link",
        )
        private val FOOTPATH_SIDES = setOf("both", "left", "right", "yes")
    }
}

/** 一段同类的路，连着画成一条线 */
data class RoadPiece(val kind: RoadKind, val points: List<LatLon>)

/** 比这短、两头都不是马路的一小段马路，其实是没画斑马线的路口，算过马路 */
const val SHORT_ROAD_METERS = 15

/** 骑在马路上的两种 */
internal val ROAD_KINDS = setOf(RoadKind.ROAD, RoadKind.ROAD_FOOTPATH)

/** 每段是什么路；夹在两段小路中间的一小截马路改算成过马路 */
fun Route.segmentKinds(): List<RoadKind> {
    val kinds = segments.map { RoadKind.of(it.wayTags) }.toMutableList()
    var i = 0
    while (i < kinds.size) {
        if (kinds[i] !in ROAD_KINDS) {
            i++
            continue
        }
        var j = i
        var length = 0
        while (j < kinds.size && kinds[j] in ROAD_KINDS) length += segments[j++].distanceMeters
        if (i > 0 && j < kinds.size && length <= SHORT_ROAD_METERS) {
            for (k in i until j) kinds[k] = RoadKind.CROSSING
        }
        i = j
    }
    return kinds
}

/** 按路的种类切开路线；没有分段信息时整条当成一段未知的路 */
fun Route.pieces(): List<RoadPiece> {
    if (segments.isEmpty() || segments.any { it.endIndex < 0 }) return emptyList()
    val kinds = segmentKinds()
    val pieces = mutableListOf<RoadPiece>()
    var from = 0
    for ((index, s) in segments.withIndex()) {
        val kind = kinds[index]
        val to = s.endIndex.coerceIn(from, points.size - 1)
        val slice = points.subList(from, to + 1)
        val last = pieces.lastOrNull()
        // 相邻同类的段并成一条，地图上少画几条线
        if (last != null && last.kind == kind) {
            pieces[pieces.size - 1] = last.copy(points = last.points + slice.drop(1))
        } else if (slice.size >= 2) {
            pieces += RoadPiece(kind, slice)
        }
        from = to
    }
    return pieces
}

/** 各种路各有多少米，按 [RoadKind] 的顺序 */
fun Route.breakdown(): Map<RoadKind, Int> {
    val kinds = segmentKinds()
    val meters = segments.indices.groupBy { kinds[it] }.mapValues { (_, idx) -> idx.sumOf { segments[it].distanceMeters } }
    return RoadKind.entries.filter { (meters[it] ?: 0) > 0 }.associateWith { meters.getValue(it) }
}

/** 起点、终点这么近的推车段（商场里、停车场、楼下）不算，那是上车前、下车后的事 */
const val END_PUSH_METERS = 200

/** 途中要推车的米数，不算起点、终点附近的 */
fun Route.pushBetweenEnds(): Int {
    val kinds = segmentKinds()
    val total = segments.sumOf { it.distanceMeters }
    var start = 0
    var meters = 0
    for ((i, s) in segments.withIndex()) {
        val end = start + s.distanceMeters
        if (kinds[i] == RoadKind.PUSH && start >= END_PUSH_METERS && end <= total - END_PUSH_METERS) meters += s.distanceMeters
        start = end
    }
    return meters
}
