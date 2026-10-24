package com.personal.rando.routing

data class LatLon(val lat: Double, val lon: Double)

/** 路线上的一段：BRouter 每换一种路（标签不同）就切一段 */
data class RouteSegment(val distanceMeters: Int, val wayTags: Map<String, String>)

data class Route(
    val points: List<LatLon>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val ascendMeters: Int,
    val segments: List<RouteSegment>,
)
