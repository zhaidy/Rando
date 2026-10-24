package com.personal.rando.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 向 BRouter 公共服务器要骑行路线 */
class BRouterClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://brouter.de/brouter",
) {

    suspend fun route(from: LatLon, to: LatLon, profile: BikeProfile): Route = withContext(Dispatchers.IO) {
        val custom = profile.custom ?: return@withContext fetch(url(from, to, profile.brouterName))
        // 服务器上传过的配置过一阵会被清掉，这时返回 500；重新上传再试一次
        val cached = uploaded[custom]
        if (cached != null) {
            try {
                return@withContext fetch(url(from, to, cached))
            } catch (_: IOException) {
                uploaded.remove(custom)
            }
        }
        fetch(url(from, to, upload(custom)))
    }

    private fun fetch(url: String): Route =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body.string()
            // 出错时服务器返回纯文本说明，比如 "target island detected"
            if (!response.isSuccessful) throw IOException(body.trim().ifEmpty { "HTTP ${response.code}" })
            RouteParser.parse(body)
        }

    /** 上传自己的配置，换回 custom_xxx 的配置名 */
    private fun upload(profile: String): String {
        val request = Request.Builder()
            .url("$baseUrl/profile")
            .post(profile.toRequestBody("text/plain".toMediaType()))
            .build()
        val json = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("上传路线配置失败（HTTP ${response.code}）")
            JSONObject(response.body.string())
        }
        json.optString("error").takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
        return json.getString("profileid").also { uploaded[profile] = it }
    }

    fun url(from: LatLon, to: LatLon, profileName: String): String =
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("lonlats", "${coord(from)}|${coord(to)}")
            .addQueryParameter("profile", profileName)
            .addQueryParameter("alternativeidx", "0")
            .addQueryParameter("format", "geojson")
            // 带上路口转向提示（voicehints），导航播报要用
            .addQueryParameter("timode", "2")
            .build()
            .toString()

    private fun coord(p: LatLon) = String.format(Locale.US, "%.6f,%.6f", p.lon, p.lat)

    private companion object {
        /** 配置内容 → 服务器给的名字；进程内共用，规划和导航里重新算路都不用重复上传 */
        val uploaded = ConcurrentHashMap<String, String>()
    }
}
