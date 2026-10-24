package com.personal.rando.ride

import com.personal.rando.routing.LatLon
import org.json.JSONArray
import org.json.JSONObject

/** 骑行记录和 JSON 互转；轨迹点存成 [纬度, 经度, 海拔或 null, 时间] 省地方 */
object RideJson {

    fun encode(ride: Ride): String = JSONObject().apply {
        put("version", 1)
        put("id", ride.id)
        put("endedAt", ride.endedAt)
        put("distance", ride.distanceMeters)
        put("moving", ride.movingSeconds)
        put("ascend", ride.ascendMeters)
        put("maxSpeed", ride.maxSpeed)
        put("points", JSONArray().apply {
            ride.points.forEach { p ->
                put(JSONArray().apply {
                    put(p.location.lat)
                    put(p.location.lon)
                    put(p.altitude ?: JSONObject.NULL)
                    put(p.time)
                })
            }
        })
    }.toString()

    fun decode(json: String): Ride {
        val o = JSONObject(json)
        val points = o.getJSONArray("points")
        return Ride(
            id = o.getLong("id"),
            endedAt = o.getLong("endedAt"),
            distanceMeters = o.getDouble("distance"),
            movingSeconds = o.getLong("moving"),
            ascendMeters = o.getDouble("ascend"),
            maxSpeed = o.getDouble("maxSpeed"),
            points = (0 until points.length()).map {
                val p = points.getJSONArray(it)
                TrackPoint(
                    location = LatLon(p.getDouble(0), p.getDouble(1)),
                    altitude = if (p.isNull(2)) null else p.getDouble(2),
                    time = p.getLong(3),
                )
            },
        )
    }
}
