package com.personal.rando.ride

import com.personal.rando.nav.Geo

/**
 * 收到的定位先过一遍再用，免得地图上的点乱跳、镜头乱转：
 * - GPS 在出点时不要网络定位：网络定位差几十米，混进来点就来回跳，又没有速度、方向，镜头会一下转回正北
 * - GPS 自己偶尔飞出去一个点（隔一秒跑了上百米），扔掉；连着几个都这样说明真到那儿了（比如出隧道），照样用
 * - 在骑 / 停下用两个门槛，速度在 5 公里/时上下晃时不会一会儿按前进方向、一会儿按指南针来回切
 * - 骑得慢时 GPS 方向很飘，接着用上一个骑得快时的方向
 */
class FixFilter {
    private var last: Fix? = null
    private var lastGpsAt: Long? = null
    private var rejected = 0
    private var moving = false
    private var bearing: Float? = null

    /** 不要这个点返回 null；要的话返回补好在骑、方向的 */
    fun accept(fix: Fix): Fix? {
        val prev = last
        if (prev != null && drop(prev, fix)) {
            rejected++
            return null
        }
        rejected = 0
        if (!fix.network) lastGpsAt = fix.time
        val speed = fix.speed
        if (speed != null) {
            moving = if (moving) speed >= STOP_SPEED else speed >= Fix.MOVING_SPEED
        } else if (!fix.network) {
            moving = false
        }
        if (fix.bearing != null && (speed ?: 0f) >= BEARING_SPEED) bearing = fix.bearing
        val out = fix.copy(moving = moving, bearing = if (moving) bearing ?: fix.bearing else fix.bearing)
        last = out
        return out
    }

    private fun drop(prev: Fix, fix: Fix): Boolean {
        val gpsAt = lastGpsAt
        if (fix.network && gpsAt != null && fix.time - gpsAt < KEEP_GPS_MS) return true
        // GPS 自己从 3 米变到 7 米这种还是准的，照样用，不然进树荫底下好几秒都不更新
        if (fix.accuracy != null && prev.accuracy != null &&
            fix.accuracy > NAV_MAX_ACCURACY_METERS && fix.accuracy > prev.accuracy * 2 && fix.time - prev.time < KEEP_GPS_MS
        ) {
            return true
        }
        // 只拿 GPS 跟 GPS 比：从网络定位换到 GPS，差多远都是 GPS 对
        if (fix.network || prev.network || rejected >= MAX_REJECTED) return false
        val seconds = maxOf(fix.time - prev.time, 1_000L) / 1000.0
        val slack = (fix.accuracy ?: 0f) + (prev.accuracy ?: 0f) + JUMP_SLACK_METERS
        return Geo.distance(prev.location, fix.location) > slack + MAX_SPEED * seconds
    }

    companion object {
        /** GPS 出过点这么久以内，网络定位不要 */
        const val KEEP_GPS_MS = 10_000L
        /** 米/秒，大约 3 公里/时：在骑时降到这以下才算停下 */
        const val STOP_SPEED = 0.8f
        /** 米/秒，大约 7 公里/时：比这慢时 GPS 给的方向不可信 */
        const val BEARING_SPEED = 2f
        /** 米/秒，大约 70 公里/时：自行车再快也到不了，超过就是飞点 */
        const val MAX_SPEED = 20.0
        const val JUMP_SLACK_METERS = 10f
        /** 连着扔掉这么多个以后，下一个不管多远都要 */
        const val MAX_REJECTED = 3
    }
}
