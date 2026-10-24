package com.personal.rando.routing

data class LatLon(val lat: Double, val lon: Double)

/**
 * 路线上的一段：BRouter 每换一种路（标签不同）就切一段。
 * [endIndex] 是这段终点在 [Route.points] 里的序号，起点是上一段的终点；对不上时是 -1
 */
data class RouteSegment(val distanceMeters: Int, val wayTags: Map<String, String>, val endIndex: Int = -1)

data class Route(
    val points: List<LatLon>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val ascendMeters: Int,
    val segments: List<RouteSegment>,
    /** 路口转向提示，按路线顺序 */
    val maneuvers: List<Maneuver> = emptyList(),
)
