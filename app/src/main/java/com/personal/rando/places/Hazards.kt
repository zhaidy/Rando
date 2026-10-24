package com.personal.rando.places

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RouteTimeline
import com.personal.rando.ui.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 路况：施工、封路以后算路绕开；路面不好照走，骑近了提醒 */
enum class HazardType(val label: String, val color: String, val avoid: Boolean) {
    CONSTRUCTION("施工", "#F57C00", true),
    CLOSED("封路", "#D32F2F", true),
    BAD_SURFACE("路面不好", "#8D6E63", false),
}

data class Hazard(
    val id: String,
    val type: HazardType,
    val location: LatLon,
    val createdAt: Long,
    /** 谁标的；自己标的是 null */
    val by: String? = null,
) {
    fun expired(now: Long): Boolean = now - createdAt > LIFETIME_MS

    companion object {
        /** 施工、封路一般过一阵就好了：30 天后自动不算 */
        const val LIFETIME_MS = 30L * 24 * 3600 * 1000

        fun new(type: HazardType, at: LatLon, now: Long = System.currentTimeMillis()) =
            Hazard(UUID.randomUUID().toString(), type, at, now)
    }
}

object HazardJson {
    fun encode(h: Hazard): JSONObject = JSONObject()
        .put("id", h.id).put("type", h.type.name)
        .put("lat", h.location.lat).put("lon", h.location.lon)
        .put("ts", h.createdAt)
        .apply { h.by?.let { put("by", it) } }

    fun decode(o: JSONObject): Hazard? = runCatching {
        Hazard(
            id = o.getString("id"),
            type = HazardType.valueOf(o.getString("type")),
            location = LatLon(o.getDouble("lat"), o.getDouble("lon")),
            createdAt = o.getLong("ts"),
            by = o.optString("by").ifEmpty { null },
        )
    }.getOrNull()
}

/** 标过的路况，存在手机里；过期的读出来时就丢掉 */
class HazardStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("hazards", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Hazard>> = _all

    fun add(h: Hazard) {
        if (_all.value.any { it.id == h.id }) return
        save(_all.value + h)
    }

    fun remove(id: String) = save(_all.value.filterNot { it.id == id })

    /** 算路要绕开的点：没过期的施工、封路 */
    fun avoidPoints(now: Long = System.currentTimeMillis()): List<LatLon> =
        _all.value.filter { it.type.avoid && !it.expired(now) }.map { it.location }

    private fun load(): List<Hazard> {
        val now = System.currentTimeMillis()
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { HazardJson.decode(arr.getJSONObject(it)) }.filterNot { it.expired(now) }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Hazard>) {
        _all.value = list
        prefs.edit { putString(KEY, JSONArray().apply { list.forEach { put(HazardJson.encode(it)) } }.toString()) }
    }

    private companion object {
        const val KEY = "list"
    }
}

/**
 * 骑行中路况提醒：导航时说前方路线上的（「前方 120 米施工」），
 * 自由骑行时说身边的；每处一次骑行只说一次
 */
class HazardAlerts {
    private val warned = mutableSetOf<String>()

    fun reset() = warned.clear()

    /** 这一处不用提醒（自己刚标的） */
    fun skip(id: String) {
        warned += id
    }

    fun next(hazards: List<Hazard>, me: LatLon, timeline: RouteTimeline?, along: Double?, now: Long = System.currentTimeMillis()): String? {
        for (h in hazards) {
            if (h.id in warned || h.expired(now)) continue
            val ahead: Double? = if (timeline != null && along != null) {
                timeline.locate(h.location)
                    ?.takeIf { (at, off) -> off <= ON_ROUTE_METERS && at > along }
                    ?.let { (at, _) -> at - along }
            } else {
                Geo.distance(me, h.location).takeIf { it <= NEARBY_METERS }
            }
            if (ahead == null || ahead > WARN_METERS) continue
            warned += h.id
            val verb = if (h.type.avoid) "注意绕行" else "注意减速"
            return "前方 ${Format.distance(ahead.toInt())} ${h.type.label}，$verb"
        }
        return null
    }

    private companion object {
        const val ON_ROUTE_METERS = 40.0
        const val WARN_METERS = 200.0
        const val NEARBY_METERS = 120.0
    }
}
