package com.personal.rando.search

import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** [distanceMeters]：搜索时离我多远，不知道我在哪就是 null */
data class Place(val name: String, val detail: String, val location: LatLon, val distanceMeters: Int? = null)

/**
 * 地点搜索：Photon（基于 OpenStreetMap），只搜新加坡范围内。
 * 知道我在哪时让 Photon 优先找附近的，同名的连锁店（FairPrice、7-Eleven）按远近排
 */
class PlaceSearch(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://photon.komoot.io/api/",
) {

    suspend fun search(query: String, near: LatLon? = null): List<Place> = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "8")
            .addQueryParameter("bbox", SINGAPORE_BBOX)
            .apply {
                if (near != null) {
                    addQueryParameter("lat", near.lat.toString())
                    addQueryParameter("lon", near.lon.toString())
                }
            }
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException(tr("搜索失败（HTTP ${response.code}）", "Search failed (HTTP ${response.code})"))
            parse(response.body.string(), near)
        }
    }

    companion object {
        /** 西、南、东、北 */
        const val SINGAPORE_BBOX = "103.59,1.15,104.10,1.48"

        fun parse(json: String, near: LatLon? = null): List<Place> {
            val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
            val places = (0 until features.length()).mapNotNull { i ->
                val f = features.getJSONObject(i)
                val p = f.getJSONObject("properties")
                val c = f.getJSONObject("geometry").getJSONArray("coordinates")
                val street = listOf(p.optString("housenumber"), p.optString("street"))
                    .filter { it.isNotEmpty() }.joinToString(" ")
                val name = p.optString("name").ifEmpty { street }
                if (name.isEmpty()) return@mapNotNull null
                val detail = listOf(street, p.optString("district"), p.optString("postcode"))
                    .filter { it.isNotEmpty() && it != name }
                    .joinToString(" · ")
                val location = LatLon(lat = c.getDouble(1), lon = c.getDouble(0))
                Place(name, detail, location, near?.let { Geo.distance(it, location).toInt() })
            }.distinctBy { it.name to it.detail }
            if (near == null) return places
            // 名字的先后照 Photon 的，同一个名字的几家按远近排
            val order = places.map { it.name }.distinct()
            return places.sortedWith(compareBy({ order.indexOf(it.name) }, { it.distanceMeters }))
        }
    }
}
