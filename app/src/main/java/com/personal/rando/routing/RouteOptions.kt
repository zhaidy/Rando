package com.personal.rando.routing

import com.personal.rando.nav.Geo

/** 几条路线比什么：每一项都是越小越好，差到 [enough] 才算真的不一样 */
enum class Measure(val label: String, val enough: Int) {
    TIME("最快", 120),
    ROAD("马路最少", 300),
    CROSSINGS("过马路最少", 2),
    DISTANCE("最短", 300),
    CLIMB("爬坡最少", 15);

    fun of(route: Route): Int = when (this) {
        TIME -> route.durationSeconds
        ROAD -> route.roadMeters()
        CROSSINGS -> route.crossings().size
        DISTANCE -> route.distanceMeters
        CLIMB -> route.ascendMeters
    }
}

/** 骑在马路上的米数，马路边有人行道的也算 */
fun Route.roadMeters(): Int = breakdown().filterKeys { it in ROAD_KINDS }.values.sum()

/** 一种算法：按什么偏好算，要 BRouter 的第几条备选 */
data class RouteVariant(val prefs: RoutePrefs, val alternative: Int = 0)

/**
 * 一条可选的路线。[best] 是它在几条里拿第一的项；[better]、[worse] 是比第一条
 * （按我的偏好算的那条）好在哪、差在哪，差多少
 */
data class RouteOption(
    val route: Route,
    val prefs: RoutePrefs,
    val best: List<Measure> = emptyList(),
    val better: Map<Measure, Int> = emptyMap(),
    val worse: Map<Measure, Int> = emptyMap(),
)

object RouteOptions {

    /** 最多给几条 */
    const val MAX = 4

    /** 第一条照我的偏好算，再加一条同样偏好的备选，一条少走马路的，一条最快的 */
    fun variants(prefs: RoutePrefs): List<RouteVariant> = listOf(
        RouteVariant(prefs),
        RouteVariant(prefs, alternative = 1),
        RouteVariant(
            if (prefs.profile == BikeProfile.PCN && prefs.avoidRoads) prefs.copy(avoidRoads = false)
            else prefs.copy(profile = BikeProfile.PCN, avoidRoads = true)
        ),
        RouteVariant(if (prefs.profile == BikeProfile.ROAD) prefs.copy(profile = BikeProfile.PCN) else prefs.copy(profile = BikeProfile.ROAD)),
    ).distinct()

    /**
     * 第一条（按我的偏好）总是留着；其余的去掉跟前面某条差不多一样的，
     * 也去掉哪一项都不比第一条好的，留下的每条都有个选它的理由
     */
    fun pick(routes: List<Pair<Route, RoutePrefs>>): List<RouteOption> {
        if (routes.isEmpty()) return emptyList()
        val main = routes.first().first
        val kept = mutableListOf(routes.first())
        for (candidate in routes.drop(1)) {
            if (kept.any { same(it.first, candidate.first) }) continue
            if (Measure.entries.none { main.isWorseBy(it, candidate.first) }) continue
            kept += candidate
            if (kept.size == MAX) break
        }
        if (kept.size == 1) return listOf(RouteOption(main, routes.first().second))
        return kept.map { (route, prefs) ->
            RouteOption(
                route = route,
                prefs = prefs,
                // 几条打平的项不算谁第一
                best = Measure.entries.filter { m -> kept.all { it.first === route || it.first.isWorseBy(m, route) } },
                better = Measure.entries.filter { main.isWorseBy(it, route) }.associateWith { it.of(main) - it.of(route) },
                worse = Measure.entries.filter { route.isWorseBy(it, main) }.associateWith { it.of(route) - it.of(main) },
            )
        }
    }

    /** 这条在 [m] 上比 [other] 差得够多 */
    private fun Route.isWorseBy(m: Measure, other: Route) = m.of(this) - m.of(other) >= m.enough

    /** 差不多是同一条路：长度差不到 3%，而且 [b] 上抽出来的点九成离 [a] 不到 [NEAR_METERS] */
    fun same(a: Route, b: Route): Boolean {
        if (a.points.isEmpty() || b.points.isEmpty()) return a.points.isEmpty() && b.points.isEmpty()
        if (kotlin.math.abs(a.distanceMeters - b.distanceMeters) > 0.03 * maxOf(a.distanceMeters, b.distanceMeters)) return false
        val step = (b.points.size / SAMPLES).coerceAtLeast(1)
        val samples = b.points.indices.step(step).map { b.points[it] }
        val near = samples.count { p -> a.points.any { Geo.distance(it, p) < NEAR_METERS } }
        return near >= samples.size * 0.9
    }

    private const val SAMPLES = 60
    private const val NEAR_METERS = 30.0
}
