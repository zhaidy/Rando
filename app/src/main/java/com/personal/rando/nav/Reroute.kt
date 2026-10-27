package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import com.personal.rando.routing.Route
import kotlin.math.abs

/**
 * 骑偏了重新算路。BRouter 不知道人朝哪骑，从当前位置算，最近的路常常是「掉头回原路」；
 * 人接着往前骑，没几秒又偏了，一轮一轮重算要等一分钟。所以同时再算一条先往前骑一小段的，
 * 头一条要掉头时用这条
 */
object Reroute {
    /** 往骑行方向前面这么远放一个途经点 */
    const val AHEAD_METERS = 40.0
    /** 看路线开头这么远的方向 */
    private const val START_METERS = 30.0
    /** 路线开头和骑行方向差这么多算要掉头 */
    private const val TURN_BACK_DEGREES = 120.0

    /** 先往前骑一小段再算：[from] 之后多一个 [heading] 方向前面的点 */
    fun aheadStops(from: LatLon, heading: Double, rest: List<LatLon>): List<LatLon> =
        listOf(from, Geo.offset(from, heading, AHEAD_METERS)) + rest

    /** 路线一开头就要掉头 */
    fun turnsBack(route: Route, heading: Double): Boolean {
        val start = route.points.firstOrNull() ?: return false
        var acc = 0.0
        var end = start
        for (i in 1 until route.points.size) {
            acc += Geo.distance(route.points[i - 1], route.points[i])
            end = route.points[i]
            if (acc >= START_METERS) break
        }
        if (Geo.distance(start, end) < 1.0) return false
        val diff = abs(((Geo.bearing(start, end) - heading) % 360 + 540) % 360 - 180)
        return diff > TURN_BACK_DEGREES
    }

    /** 不用掉头的就用直接算的；要掉头又有往前那条，用往前的；都没有就是没算出来 */
    fun pick(direct: Route?, ahead: Route?, heading: Double?): Route? = when {
        direct != null && (heading == null || !turnsBack(direct, heading)) -> direct
        else -> ahead ?: direct
    }
}
