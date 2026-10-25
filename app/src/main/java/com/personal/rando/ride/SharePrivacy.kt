package com.personal.rando.ride

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import kotlin.random.Random

/**
 * 分享图上不让人看出住哪：家周围一圈的轨迹不画。
 * 圈的圆心从家随机偏开一点、半径也带点随机——不然几张图里轨迹都在同一个圆边上断开，能反推出圆心。
 * 偏移每个人固定一个（[seed] 存起来），每次都变的话多分享几次反而一样暴露。
 * 没收藏家的时候退一步：每次骑行的起点、终点各剪掉一段。
 */
object SharePrivacy {
    const val MIN_RADIUS_METERS = 400.0
    const val EXTRA_RADIUS_METERS = 100.0
    const val MAX_OFFSET_METERS = 150.0
    const val TRIM_METERS = 200.0

    class Zone(val center: LatLon, val radius: Double)

    /** 家周围的隐私圈：家一定在圈里，离圈边至少 250 米 */
    fun zone(home: LatLon, seed: Long): Zone {
        val r = Random(seed)
        val center = Geo.offset(home, r.nextDouble(0.0, 360.0), r.nextDouble(0.0, MAX_OFFSET_METERS))
        return Zone(center, MIN_RADIUS_METERS + r.nextDouble(0.0, EXTRA_RADIUS_METERS))
    }

    /**
     * 一次骑行要画的几段：圈里的点去掉，中途穿过圈的地方断开成几段；
     * 没有圈就把头尾各剪 [TRIM_METERS]。全在圈里就一段都不剩
     */
    fun hide(track: List<LatLon>, zone: Zone?): List<List<LatLon>> {
        if (zone == null) return listOf(trim(track)).filter { it.size >= 2 }
        val pieces = mutableListOf<List<LatLon>>()
        var piece = mutableListOf<LatLon>()
        for (p in track) {
            if (Geo.distance(p, zone.center) > zone.radius) {
                piece.add(p)
            } else if (piece.isNotEmpty()) {
                pieces.add(piece)
                piece = mutableListOf()
            }
        }
        if (piece.isNotEmpty()) pieces.add(piece)
        return pieces.filter { it.size >= 2 }
    }

    /** 头尾各剪掉 [TRIM_METERS]；太短的整段不要 */
    fun trim(track: List<LatLon>): List<LatLon> {
        fun cut(t: List<LatLon>): Int {
            var d = 0.0
            for (i in 1 until t.size) {
                d += Geo.distance(t[i - 1], t[i])
                if (d >= TRIM_METERS) return i
            }
            return t.size
        }
        val from = cut(track)
        val to = track.size - cut(track.reversed())
        return if (from < to) track.subList(from, to) else emptyList()
    }

    /** 这台手机固定的随机数：第一次用的时候生成，之后一直用它 */
    fun seed(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences("share_privacy", Context.MODE_PRIVATE)
        if (!prefs.contains(SEED)) prefs.edit { putLong(SEED, Random.nextLong()) }
        return prefs.getLong(SEED, 0L)
    }

    private const val SEED = "seed"
}
