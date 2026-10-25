package com.personal.rando.places

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlace(val name: String, val location: LatLon) {
    /** 地图上点的位置和收藏的差不多就算同一个地方 */
    fun isAt(p: LatLon) = Geo.distance(location, p) <= SAME_PLACE_METERS

    /** 显示的名字：[HOME]、[WORK] 存的是中文，英文界面换成 Home、Work；自己起的名字照原样 */
    val label: String get() = label(name)

    companion object {
        const val HOME = "家"
        const val WORK = "公司"
        const val SAME_PLACE_METERS = 30.0

        fun label(name: String): String = when (name) {
            HOME -> tr("家", "Home")
            WORK -> tr("公司", "Work")
            else -> name
        }
    }
}

object SavedPlacesJson {

    fun encode(places: List<SavedPlace>): String = JSONArray().apply {
        places.forEach { p ->
            put(JSONObject().put("name", p.name).put("lat", p.location.lat).put("lon", p.location.lon))
        }
    }.toString()

    fun decode(json: String): List<SavedPlace> {
        val arr = JSONArray(json)
        // 一条坏了只跳过那条：整个当空的话，下次一存就把家和其他收藏全冲掉了
        return (0 until arr.length()).mapNotNull {
            runCatching {
                val o = arr.getJSONObject(it)
                SavedPlace(o.getString("name"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
            }.getOrNull()
        }
    }

    /** 同名的换成新位置；家、公司排最前，其他按收藏先后 */
    fun upsert(places: List<SavedPlace>, place: SavedPlace): List<SavedPlace> =
        sorted(places.filter { it.name != place.name } + place)

    fun sorted(places: List<SavedPlace>): List<SavedPlace> =
        places.sortedBy {
            when (it.name) {
                SavedPlace.HOME -> 0
                SavedPlace.WORK -> 1
                else -> 2
            }
        }
}

/** 收藏的地方，存在 SharedPreferences 里，量很小 */
class SavedPlaceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("places", Context.MODE_PRIVATE)
    private val _places = MutableStateFlow(load())
    val places: StateFlow<List<SavedPlace>> = _places

    fun save(place: SavedPlace) = write(SavedPlacesJson.upsert(_places.value, place))

    fun remove(place: SavedPlace) = write(_places.value - place)

    private fun write(list: List<SavedPlace>) {
        _places.value = list
        prefs.edit { putString(KEY, SavedPlacesJson.encode(list)) }
    }

    private fun load(): List<SavedPlace> =
        prefs.getString(KEY, null)?.let { runCatching { SavedPlacesJson.decode(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "list"
    }
}
