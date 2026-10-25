package com.personal.rando.weather

import com.personal.rando.routing.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 风：从哪个方向吹来（正北 0°，顺时针），多快（km/h） */
data class Wind(val fromDegrees: Double, val kmh: Double) {

    /** 「东北风」：风从哪边来 */
    val name: String
        get() = NAMES[(((fromDegrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8] + "风"

    /** 风小到骑车感觉不出来 */
    val calm: Boolean get() = kmh < CALM_KMH

    /**
     * 沿 [points] 骑，平均顶着多大的风（km/h）：正数是逆风，负数是顺风，按每段长度加权。
     * 横着吹的不算
     */
    fun headwind(points: List<LatLon>): Double {
        val rad = Math.toRadians(fromDegrees)
        var sum = 0.0
        var length = 0.0
        for ((a, b) in points.zipWithNext()) {
            val dx = (b.lon - a.lon) * METERS_PER_DEGREE * cos(Math.toRadians(a.lat))
            val dy = (b.lat - a.lat) * METERS_PER_DEGREE
            val d = hypot(dx, dy)
            if (d == 0.0) continue
            // 朝着风吹来的方向骑就是逆风
            sum += kmh * (sin(rad) * dx + cos(rad) * dy)
            length += d
        }
        return if (length == 0.0) 0.0 else sum / length
    }

    /** 骑一条路线的风：去程（前一半）和回程（后一半）分开看 */
    fun along(points: List<LatLon>): RouteWind {
        val half = halfIndex(points)
        return RouteWind(this, headwind(points.subList(0, half + 1)), headwind(points.subList(half, points.size)), headwind(points))
    }

    companion object {
        /** 1 节 = 1.852 km/h */
        const val KMH_PER_KNOT = 1.852
        /** 这么小的风不提 */
        const val CALM_KMH = 8.0
        private const val METERS_PER_DEGREE = 111_320.0
        private val NAMES = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

        /** 按长度算的中点 */
        fun halfIndex(points: List<LatLon>): Int {
            if (points.size < 3) return 0
            val lengths = points.zipWithNext { a, b -> hypot((b.lat - a.lat), (b.lon - a.lon) * cos(Math.toRadians(a.lat))) }
            val total = lengths.sum()
            var along = 0.0
            lengths.forEachIndexed { i, l ->
                along += l
                if (along >= total / 2) return i + 1
            }
            return points.size / 2
        }
    }
}

/** 一条路线上的风：[outbound] 前一半、[back] 后一半、[overall] 全程，正数是逆风 */
data class RouteWind(val wind: Wind, val outbound: Double, val back: Double, val overall: Double) {

    /** 骑一圈时：先顺风出去、逆风回来（累了还顶风），反着骑更好 */
    fun betterReversed(): Boolean = !wind.calm && back - outbound > SWITCH_KMH

    /** 「东北风 15 km/h · 去程逆风、回程顺风」；风小就只说风向 */
    fun summary(loop: Boolean): String {
        val head = "${wind.name} ${wind.kmh.roundToInt()} km/h"
        if (wind.calm) return "$head，风不大"
        return if (loop && kind(outbound) != kind(back)) {
            "$head · 去程${kind(outbound)}、回程${kind(back)}"
        } else {
            "$head · 全程大多${kind(overall)}"
        }
    }

    /** 顶风很大，值得放在最上面说 */
    val strongHeadwind: Boolean get() = overall >= STRONG_KMH

    private fun kind(h: Double): String = when {
        h >= SIDE_KMH -> "逆风"
        h <= -SIDE_KMH -> "顺风"
        else -> "侧风"
    }

    private companion object {
        /** 平均顶风小于这么多算侧风 */
        const val SIDE_KMH = 3.0
        /** 回程比去程多顶这么多风才反过来骑 */
        const val SWITCH_KMH = 3.0
        const val STRONG_KMH = 12.0
    }
}

/** 离 [at] 最近的气象站的风向、风速：data.gov.sg 的开放数据 */
class WindClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api-open.data.gov.sg/v2/real-time/api",
) {
    suspend fun at(at: LatLon): Wind? = withContext(Dispatchers.IO) {
        coroutineScope {
            val direction = async { runCatching { ClimateParser.nearestReading(get("wind-direction"), at) }.getOrNull() }
            val speed = async { runCatching { ClimateParser.nearestReading(get("wind-speed"), at) }.getOrNull() }
            val d = direction.await() ?: return@coroutineScope null
            val knots = speed.await() ?: return@coroutineScope null
            Wind(d, knots * Wind.KMH_PER_KNOT)
        }
    }

    private fun get(name: String): String =
        http.newCall(Request.Builder().url("$baseUrl/$name").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("风 HTTP ${response.code}")
            response.body.string()
        }
}
