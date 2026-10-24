package com.personal.rando.routing

/** 路线上每一段是什么路，地图上按颜色画出来，卡片上统计各占多少 */
enum class RoadKind(val label: String, val color: String) {
    PCN("公园连道", "#1B7F3B"),
    CYCLEWAY("自行车道", "#0097A7"),
    PATH("人行道、小路", "#3D7BD9"),
    ROAD_FOOTPATH("马路边人行道", "#C98A1B"),
    ROAD("马路", "#D93A2B"),
    PUSH("推车", "#8A8A8A");

    /** 新加坡在马路上骑车必须戴头盔 */
    val onRoad: Boolean get() = this == ROAD

    companion object {
        fun of(tags: Map<String, String>): RoadKind {
            val highway = tags["highway"]
            val isPath = highway in PATHS
            val isPcn = PCN_TAGS.any { tags[it] == "yes" }
            return when {
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

/** 按路的种类切开路线；没有分段信息时整条当成一段未知的路 */
fun Route.pieces(): List<RoadPiece> {
    if (segments.isEmpty() || segments.any { it.endIndex < 0 }) return emptyList()
    val pieces = mutableListOf<RoadPiece>()
    var from = 0
    for (s in segments) {
        val kind = RoadKind.of(s.wayTags)
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
    val meters = segments.groupBy { RoadKind.of(it.wayTags) }.mapValues { (_, s) -> s.sumOf { it.distanceMeters } }
    return RoadKind.entries.filter { (meters[it] ?: 0) > 0 }.associateWith { meters.getValue(it) }
}
