package com.personal.rando.ride

import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.Format
import kotlin.math.abs

/**
 * 认出「同一条路」：起点、终点都在 [END_METERS] 以内，距离差不到 [LENGTH_TOLERANCE]，
 * 而且这次轨迹上取的点几乎都在上次轨迹旁边（[TRACK_METERS] 以内）。
 * 方向也要一样：去和回不算同一条
 */
object SameRoute {
    const val END_METERS = 250.0
    const val LENGTH_TOLERANCE = 0.12
    const val TRACK_METERS = 120.0
    private const val SAMPLES = 40
    private const val MATCH_SHARE = 0.9

    fun matches(a: Ride, b: Ride): Boolean {
        val pa = a.points.map { it.location }
        val pb = b.points.map { it.location }
        if (pa.size < 2 || pb.size < 2) return false
        if (abs(a.distanceMeters - b.distanceMeters) > maxOf(a.distanceMeters, b.distanceMeters) * LENGTH_TOLERANCE) return false
        if (Geo.distance(pa.first(), pb.first()) > END_METERS || Geo.distance(pa.last(), pb.last()) > END_METERS) return false
        val dense = sample(pb, SAMPLES * 5)
        val probes = sample(pa, SAMPLES)
        val close = probes.count { p -> dense.any { Geo.distance(it, p) <= TRACK_METERS } }
        if (close < probes.size * MATCH_SHARE) return false
        // 一圈的起点终点一样，再看方向：这次骑到 1/4、1/2、3/4 的地方，上次也是差不多骑到那儿经过的，
        // 免得反方向的一圈也算（一半处两个方向都经过对角，所以要看几处）
        val other = sample(pb, SAMPLES)
        return listOf(SAMPLES / 4, SAMPLES / 2, SAMPLES * 3 / 4).all { k ->
            val nearest = other.indices.minBy { Geo.distance(other[it], probes[k]) }
            abs(nearest - k) <= SAMPLES / 8
        }
    }

    /** 沿轨迹等距取 [n] 个点 */
    fun sample(points: List<LatLon>, n: Int): List<LatLon> {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])
        val total = cumulative.last()
        if (total <= 0) return List(n) { points.first() }
        var j = 0
        return List(n) { k ->
            val target = total * k / (n - 1)
            while (j < points.size - 2 && cumulative[j + 1] < target) j++
            val seg = cumulative[j + 1] - cumulative[j]
            val t = if (seg <= 0) 0.0 else ((target - cumulative[j]) / seg).coerceIn(0.0, 1.0)
            val p = points[j]
            val q = points[j + 1]
            LatLon(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
        }
    }
}

/** 和以前骑过的同一条路比：骑过几次、比上次快慢、是不是最好成绩 */
data class RouteRecord(
    /** 这次之前骑过几次；看旧记录时可能是 0（之后才又骑） */
    val before: Int,
    /** 比上次快了多少秒；负的是慢了；之前没骑过是 null */
    val fasterThanLast: Long?,
    /** 这次是不是最快的 */
    val best: Boolean,
    /** 不是最快时，最快那次用了多久（秒） */
    val bestSeconds: Long,
) {
    /** 「这条路第 6 次骑，比上次快 1 分 20 秒，最好成绩！」 */
    val text: String
        get() {
            val vsLast = fasterThanLast?.let { d ->
                when {
                    abs(d) < 5 -> tr("，和上次差不多", ", about the same as last time")
                    d > 0 -> tr("，比上次快 ${duration(d)}", ", ${duration(d)} faster than last time")
                    else -> tr("，比上次慢 ${duration(-d)}", ", ${duration(-d)} slower than last time")
                }
            }.orEmpty()
            val best = if (best) tr("，最好成绩！", ", personal best!") else tr("，最好成绩 ${Format.clock(bestSeconds)}", ", best ${Format.clock(bestSeconds)}")
            if (Lang.english) {
                val nth = if (before == 0) "First time" else "${ordinal(before + 1)} time"
                return "$nth on this route$vsLast$best"
            }
            val nth = if (before == 0) "第一次" else "第 ${before + 1} 次"
            return "这条路${nth}骑$vsLast$best"
        }

    companion object {
        /** [ride] 和 [others]（新的在前）里同一条路的比；没骑过返回 null */
        fun of(ride: Ride, others: List<Ride>): RouteRecord? {
            val same = others.filter { it.id != ride.id && it.movingSeconds > 0 && SameRoute.matches(ride, it) }
            if (same.isEmpty()) return null
            val earlier = same.filter { it.startedAt < ride.startedAt }
            val last = earlier.maxByOrNull { it.startedAt }
            val fastest = same.minOf { it.movingSeconds }
            return RouteRecord(
                before = earlier.size,
                fasterThanLast = last?.let { it.movingSeconds - ride.movingSeconds },
                best = ride.movingSeconds < fastest,
                bestSeconds = minOf(fastest, ride.movingSeconds),
            )
        }

        /** 2nd、3rd、11th */
        private fun ordinal(n: Int): String = n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }

        private fun duration(seconds: Long): String {
            val m = seconds / 60
            val s = seconds % 60
            return when {
                m == 0L -> tr("$s 秒", "$s s")
                s == 0L -> tr("$m 分钟", "$m min")
                else -> tr("$m 分 $s 秒", "$m min $s s")
            }
        }
    }
}
