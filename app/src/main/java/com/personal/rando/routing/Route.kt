package com.personal.rando.routing

data class LatLon(val lat: Double, val lon: Double)

/**
 * 路线上的一段：BRouter 每换一种路（标签不同）或者遇到有标签的点（比如路口）就切一段。
 * [endIndex] 是这段终点在 [Route.points] 里的序号，起点是上一段的终点；对不上时是 -1。
 * [nodeTags] 是终点那个点的标签，比如 crossing=traffic_signals
 */
data class RouteSegment(
    val distanceMeters: Int,
    val wayTags: Map<String, String>,
    val endIndex: Int = -1,
    val nodeTags: Map<String, String> = emptyMap(),
)

data class Route(
    val points: List<LatLon>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val ascendMeters: Int,
    val segments: List<RouteSegment>,
    /** 路口转向提示，按路线顺序 */
    val maneuvers: List<Maneuver> = emptyList(),
)
