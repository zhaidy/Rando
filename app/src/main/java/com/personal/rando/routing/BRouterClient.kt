package com.personal.rando.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 向 BRouter 公共服务器要骑行路线 */
class BRouterClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://brouter.de/brouter",
) {

    suspend fun route(from: LatLon, to: LatLon, profile: BikeProfile): Route = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(from, to, profile)).build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            // 出错时服务器返回纯文本说明，比如 "target island detected"
            if (!response.isSuccessful) throw IOException(body.trim().ifEmpty { "HTTP ${response.code}" })
            RouteParser.parse(body)
        }
    }

    fun url(from: LatLon, to: LatLon, profile: BikeProfile): String =
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("lonlats", "${coord(from)}|${coord(to)}")
            .addQueryParameter("profile", profile.brouterName)
            .addQueryParameter("alternativeidx", "0")
            .addQueryParameter("format", "geojson")
            // 带上路口转向提示（voicehints），导航播报要用
            .addQueryParameter("timode", "2")
            .build()
            .toString()

    private fun coord(p: LatLon) = String.format(Locale.US, "%.6f,%.6f", p.lon, p.lat)
}
