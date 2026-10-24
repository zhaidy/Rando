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
            segments = props.optJSONArray("messages")?.let { segments(it, points) }.orEmpty(),
            maneuvers = props.optJSONArray("voicehints")?.let { maneuvers(it, points.size) }.orEmpty(),
        )
    }

    /** BRouter 的数字都是字符串，比如 "track-length": "15863" */
    private fun JSONObject.intOf(key: String): Int = optString(key).toIntOrNull() ?: 0

    /**
     * messages 第一行是表头，后面每行一段，按表头找「Distance」和「WayTags」两列。
     * 每行的经纬度（百万分之一度的整数）是这段的终点，顺着往后在坐标里找到它，就知道这段画到哪
     */
    private fun segments(messages: JSONArray, points: List<LatLon>): List<RouteSegment> {
        if (messages.length() < 2) return emptyList()
        val header = messages.getJSONArray(0)
        val columns = (0 until header.length()).map { header.getString(it) }
        val distanceCol = columns.indexOf("Distance")
        val tagsCol = columns.indexOf("WayTags")
        val lonCol = columns.indexOf("Longitude")
        val latCol = columns.indexOf("Latitude")
        if (distanceCol < 0 || tagsCol < 0) return emptyList()
        val keys = points.map { Math.round(it.lon * 1e6) to Math.round(it.lat * 1e6) }
        var cursor = 0
        return (1 until messages.length()).map {
            val row = messages.getJSONArray(it)
            val key = row.optString(lonCol).toLongOrNull() to row.optString(latCol).toLongOrNull()
            val end = (cursor until keys.size).firstOrNull { i -> keys[i] == key } ?: -1
            if (end >= 0) cursor = end
            RouteSegment(
                distanceMeters = row.optString(distanceCol).toIntOrNull() ?: 0,
                wayTags = parseTags(row.optString(tagsCol)),
                endIndex = end,
            )
        }
    }

    /** voicehints 每行：[坐标序号, 动作代码, 环岛出口, 到下一个提示的距离, 转角] */
    private fun maneuvers(hints: JSONArray, pointCount: Int): List<Maneuver> =
        (0 until hints.length()).mapNotNull {
            val row = hints.getJSONArray(it)
            val index = row.optInt(0, -1)
            val turn = Turn.fromCode(row.optInt(1)) ?: return@mapNotNull null
            if (index !in 0 until pointCount) return@mapNotNull null
            Maneuver(pointIndex = index, turn = turn, exit = row.optInt(2), angle = row.optInt(4))
        }

    /** "highway=residential surface=asphalt" → {highway=residential, surface=asphalt} */
    fun parseTags(text: String): Map<String, String> =
        text.split(' ').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
        }.toMap()
}
