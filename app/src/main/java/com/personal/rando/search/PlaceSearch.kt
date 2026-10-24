package com.personal.rando.search

import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class Place(val name: String, val detail: String, val location: LatLon)

/** 地点搜索：Photon（基于 OpenStreetMap），只搜新加坡范围内 */
class PlaceSearch(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://photon.komoot.io/api/",
) {

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "8")
            .addQueryParameter("bbox", SINGAPORE_BBOX)
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索失败（HTTP ${response.code}）")
            parse(response.body.string())
        }
    }

    companion object {
        /** 西、南、东、北 */
        const val SINGAPORE_BBOX = "103.59,1.15,104.10,1.48"

        fun parse(json: String): List<Place> {
            val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
            return (0 until features.length()).mapNotNull { i ->
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
                Place(name, detail, LatLon(lat = c.getDouble(1), lon = c.getDouble(0)))
            }.distinctBy { it.name to it.detail }
        }
    }
}
