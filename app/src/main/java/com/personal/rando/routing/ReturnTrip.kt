package com.personal.rando.routing

import com.personal.rando.nav.Geo

/** 原路返回：从骑过的轨迹里挑出倒着走要经过的点 */
object ReturnTrip {

    /** 离出发的地方不到这么远就不用原路返回了 */
    const val MIN_DISTANCE_METERS = 300.0

    /** 途经点之间至少隔这么远 */
    const val SPACING_METERS = 800.0

    /** 途经点最多这么多，太多算路慢 */
    const val MAX_VIA = 20

    /**
     * 依次经过的点：现在的位置（轨迹最后一个点）、倒着取的途经点、出发的地方。
     * 途经点离两头太近的不要，不然一出门先绕回去碰一下。
     * 轨迹太短、或者已经回到出发的地方附近，返回 null
     */
    fun stops(track: List<LatLon>): List<LatLon>? {
        if (track.size < 2) return null
        val here = track.last()
        val home = track.first()
        if (Geo.distance(here, home) < MIN_DISTANCE_METERS) return null
        val back = track.asReversed()
        val total = back.zipWithNext { a, b -> Geo.distance(a, b) }.sum()
        val spacing = maxOf(SPACING_METERS, total / (MAX_VIA + 1))
        val via = mutableListOf<LatLon>()
        var along = 0.0
        var next = spacing
        for (i in 1 until back.size) {
            along += Geo.distance(back[i - 1], back[i])
            if (along < next) continue
            next = along + spacing
            val p = back[i]
            if (Geo.distance(p, here) > SPACING_METERS / 2 && Geo.distance(p, home) > SPACING_METERS / 2) via += p
        }
        return listOf(here) + via + home
    }
}
