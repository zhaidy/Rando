package com.personal.rando.places

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RoutePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏的路线：只存起点、途经点、终点和路线配置，再骑时按同样的点重新算，得到同一条路
 * （路边新标的施工、封路也会绕开）
 */
data class SavedRoute(
    val name: String,
    val stops: List<LatLon>,
    val profile: BikeProfile,
    val avoidRoads: Boolean,
    val avoidBridges: Boolean,
    val meters: Int,
    val savedAt: Long,
    /** 收藏时选的是 BRouter 的第几条备选；0 是按偏好最好的那条 */
    val alternative: Int = 0,
) {
    /** 起点终点差不多是一个地方：骑一圈回来的 */
    val loop: Boolean get() = stops.size >= 2 && Geo.distance(stops.first(), stops.last()) < SAME_STOP_METERS

    fun prefs(mine: RoutePrefs): RoutePrefs = mine.copy(profile = profile, avoidRoads = avoidRoads, avoidBridges = avoidBridges)

    /** 同样的点、同样的配置、同一条备选就是同一条 */
    fun same(stops: List<LatLon>, prefs: RoutePrefs, alternative: Int = 0): Boolean =
        profile == prefs.profile && avoidRoads == prefs.avoidRoads && avoidBridges == prefs.avoidBridges &&
            this.alternative == alternative &&
            this.stops.size == stops.size && this.stops.zip(stops).all { (a, b) -> Geo.distance(a, b) < SAME_STOP_METERS }

    companion object {
        const val SAME_STOP_METERS = 50.0
    }
}

object SavedRoutesJson {
    fun encode(list: List<SavedRoute>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject()
                    .put("name", r.name)
                    .put("stops", JSONArray().apply { r.stops.forEach { put(JSONArray().put(it.lat).put(it.lon)) } })
                    .put("profile", r.profile.name)
                    .put("avoidRoads", r.avoidRoads)
                    .put("avoidBridges", r.avoidBridges)
                    .put("meters", r.meters)
                    .put("savedAt", r.savedAt)
                    .put("alternative", r.alternative)
            )
        }
    }.toString()

    fun decode(json: String): List<SavedRoute> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val stops = o.getJSONArray("stops")
                SavedRoute(
                    name = o.getString("name"),
                    stops = (0 until stops.length()).map { stops.getJSONArray(it).let { p -> LatLon(p.getDouble(0), p.getDouble(1)) } },
                    profile = BikeProfile.entries.firstOrNull { it.name == o.optString("profile") } ?: BikeProfile.PCN,
                    avoidRoads = o.optBoolean("avoidRoads"),
                    avoidBridges = o.optBoolean("avoidBridges"),
                    meters = o.optInt("meters"),
                    savedAt = o.optLong("savedAt"),
                    alternative = o.optInt("alternative"),
                )
            }.getOrNull()
        }
    }
}

/** 收藏的路线，新收藏的在前；同名的覆盖 */
class SavedRouteStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val _routes = MutableStateFlow(load())
    val routes: StateFlow<List<SavedRoute>> = _routes

    fun save(route: SavedRoute) = write(listOf(route) + _routes.value.filter { it.name != route.name })

    fun remove(route: SavedRoute) = write(_routes.value - route)

    private fun write(list: List<SavedRoute>) {
        _routes.value = list
        prefs.edit { putString(KEY, SavedRoutesJson.encode(list)) }
    }

    private fun load(): List<SavedRoute> =
        prefs.getString(KEY, null)?.let { runCatching { SavedRoutesJson.decode(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "list"
    }
}
