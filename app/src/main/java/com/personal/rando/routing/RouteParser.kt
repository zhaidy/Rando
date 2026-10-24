package com.personal.rando.routing

import org.json.JSONArray
import org.json.JSONObject

/** 解析 BRouter 返回的 GeoJSON（format=geojson） */
object RouteParser {

    fun parse(json: String): Route {
        val feature = JSONObject(json).getJSONArray("features").getJSONObject(0)
        val props = feature.getJSONObject("properties")
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map {
            val c = coords.getJSONArray(it)
            LatLon(lat = c.getDouble(1), lon = c.getDouble(0))
        }
        return Route(
            points = points,
            distanceMeters = props.intOf("track-length"),
            durationSeconds = props.intOf("total-time"),
            ascendMeters = props.intOf("filtered ascend"),
            segments = props.optJSONArray("messages")?.let(::segments).orEmpty(),
        )
    }

    /** BRouter 的数字都是字符串，比如 "track-length": "15863" */
    private fun JSONObject.intOf(key: String): Int = optString(key).toIntOrNull() ?: 0

    /** messages 第一行是表头，后面每行一段，按表头找「Distance」和「WayTags」两列 */
    private fun segments(messages: JSONArray): List<RouteSegment> {
        if (messages.length() < 2) return emptyList()
        val header = messages.getJSONArray(0)
        val columns = (0 until header.length()).map { header.getString(it) }
        val distanceCol = columns.indexOf("Distance")
        val tagsCol = columns.indexOf("WayTags")
        if (distanceCol < 0 || tagsCol < 0) return emptyList()
        return (1 until messages.length()).map {
            val row = messages.getJSONArray(it)
            RouteSegment(
                distanceMeters = row.optString(distanceCol).toIntOrNull() ?: 0,
                wayTags = parseTags(row.optString(tagsCol)),
            )
        }
    }

    /** "highway=residential surface=asphalt" → {highway=residential, surface=asphalt} */
    fun parseTags(text: String): Map<String, String> =
        text.split(' ').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
        }.toMap()
}
