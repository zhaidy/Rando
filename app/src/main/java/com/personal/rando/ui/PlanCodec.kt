package com.personal.rando.ui

import com.personal.rando.routing.LatLon
import com.personal.rando.search.Place
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把规划（起点、目的地、途经点、骑一圈）存成一段文字。去看实景之类切到别的 App 时，
 * Rando 在后台可能被系统关掉，回来时靠它恢复，再重新算一遍路线
 */
object PlanCodec {

    fun encode(s: PlannerState): String = JSONObject().apply {
        s.start?.let { put("start", place(it)) }
        s.destination?.let { put("destination", place(it)) }
        put("via", JSONArray(s.via.map(::place)))
        s.loop?.let { put("loop", JSONObject().put("target", it.targetMeters).put("attempt", it.attempt)) }
    }.toString()

    /** 读出来放进 [base]；读不了就原样返回 */
    fun decode(text: String, base: PlannerState): PlannerState = try {
        val json = JSONObject(text)
        val via = json.optJSONArray("via") ?: JSONArray()
        base.copy(
            start = json.optJSONObject("start")?.let(::place),
            destination = json.optJSONObject("destination")?.let(::place),
            via = (0 until via.length()).map { place(via.getJSONObject(it)) },
            loop = json.optJSONObject("loop")?.let { LoopRequest(it.getInt("target"), it.optInt("attempt")) },
        )
    } catch (_: Exception) {
        base
    }

    private fun place(p: Place) = JSONObject()
        .put("name", p.name)
        .put("detail", p.detail)
        .put("lat", p.location.lat)
        .put("lon", p.location.lon)

    private fun place(o: JSONObject) = Place(o.getString("name"), o.optString("detail"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
}
