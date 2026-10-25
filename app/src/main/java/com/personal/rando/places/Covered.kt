package com.personal.rando.places

import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon

/** 路线上一段有顶棚的路（组屋楼下的连廊、骑楼）：从第几米到第几米 */
data class CoveredStretch(val startMeters: Double, val endMeters: Double, val points: List<LatLon>) {
    val lengthMeters: Double get() = endMeters - startMeters
}

/**
 * 下雨时哪里能躲：OpenStreetMap 里标了 covered=yes 的路，对到路线上。
 * 算路的服务器不认识这个标签，没法专门挑这种路走，只能告诉你经过的哪几段有顶棚
 */
object Covered {

    /** 路线离有顶棚的路这么近，就算走在下面 */
    const val NEAR_METERS = 12.0
    /** 短于这个的不算（过一个路口的雨棚）；前后各多算了 [NEAR_METERS]，实际有顶棚的约 15 米 */
    const val MIN_METERS = 40.0
    /** 两段之间断开这么一点（过个小路口）就连起来 */
    private const val GAP_METERS = 20.0
    /** 路线每隔这么远看一次 */
    private const val STEP_METERS = 8.0

    fun onRoute(route: List<LatLon>, ways: List<List<LatLon>>): List<CoveredStretch> {
        if (route.size < 2 || ways.isEmpty()) return emptyList()
        val boxes = ways.map { Box.of(it) }
        fun covered(p: LatLon) = ways.indices.any { i ->
            boxes[i].contains(p) && ways[i].zipWithNext().any { (a, b) -> Geo.project(p, a, b).second <= NEAR_METERS }
        }
        // 沿路线每隔几米取一个点：(第几米, 坐标, 有没有顶棚)
        val samples = mutableListOf<Triple<Double, LatLon, Boolean>>()
        var along = 0.0
        for ((a, b) in route.zipWithNext()) {
            val d = Geo.distance(a, b)
            val steps = (d / STEP_METERS).toInt().coerceAtLeast(1)
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                val p = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                samples += Triple(along + d * t, p, covered(p))
            }
            along += d
        }
        route.last().let { samples += Triple(along, it, covered(it)) }

        val runs = mutableListOf<MutableList<Pair<Double, LatLon>>>()
        for ((m, p, c) in samples) {
            if (!c) continue
            val last = runs.lastOrNull()
            if (last != null && m - last.last().first <= GAP_METERS) last += m to p else runs += mutableListOf(m to p)
        }
        return runs
            .map { run -> CoveredStretch(run.first().first, run.last().first, run.map { it.second }) }
            .filter { it.lengthMeters >= MIN_METERS }
    }

    /** 范围框，先粗筛一下再算距离 */
    private data class Box(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
        fun contains(p: LatLon) = p.lat in minLat..maxLat && p.lon in minLon..maxLon

        companion object {
            /** 外扩约 20 米 */
            private const val PAD = 0.0002

            fun of(line: List<LatLon>) = Box(
                line.minOf { it.lat } - PAD, line.maxOf { it.lat } + PAD,
                line.minOf { it.lon } - PAD, line.maxOf { it.lon } + PAD,
            )
        }
    }
}

/** 前面最近的有顶棚的路还有多远；正走在下面是 0；后面没有了是 null */
fun List<CoveredStretch>.nextAhead(along: Double): Double? =
    firstOrNull { it.endMeters > along }?.let { (it.startMeters - along).coerceAtLeast(0.0) }
