package com.personal.rando.nav

import com.personal.rando.routing.LatLon
import kotlin.math.cos
import kotlin.math.sqrt

/** 几十公里以内的距离计算：把经纬度当成平面，新加坡这种尺度误差可以忽略 */
object Geo {

    private const val METERS_PER_DEGREE = 111_320.0

    fun distance(a: LatLon, b: LatLon): Double {
        val (x, y) = toMeters(b, origin = a)
        return sqrt(x * x + y * y)
    }

    /** p 到线段 ab 的最近点：返回 (在 ab 上的比例 0..1, 距离米) */
    fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val (bx, by) = toMeters(b, origin = a)
        val (px, py) = toMeters(p, origin = a)
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return t to sqrt(dx * dx + dy * dy)
    }

    private fun toMeters(p: LatLon, origin: LatLon): Pair<Double, Double> {
        val x = (p.lon - origin.lon) * METERS_PER_DEGREE * cos(Math.toRadians(origin.lat))
        val y = (p.lat - origin.lat) * METERS_PER_DEGREE
        return x to y
    }
}
