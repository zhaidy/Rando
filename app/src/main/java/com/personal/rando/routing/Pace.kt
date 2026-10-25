package com.personal.rando.routing

import com.personal.rando.i18n.tr

/**
 * 预计时间按什么速度算。BRouter 用「骑手平均功率」模拟骑行时间（默认 100 瓦，很悠闲），
 * 选了档位就把功率带给它；「按我的速度」用骑过的平均速度直接算
 */
enum class Pace(private val zh: String, private val en: String, val watts: Int?) {
    MINE("按我的速度", "My pace", null),
    EASY("悠闲", "Easy", 80),
    NORMAL("一般", "Moderate", 120),
    FAST("快", "Fast", 160);

    val label: String get() = tr(zh, en)
}

/** 从骑行记录算出来的平均速度（米/秒，只算在骑的时间） */
object MySpeed {
    /** 只看最近这么多次 */
    const val RECENT = 10
    /** 太短的不算（绕小区、试一下） */
    const val MIN_RIDE_METERS = 2_000.0
    /** 至少骑过这么多才算数 */
    const val MIN_TOTAL_METERS = 10_000.0

    /** [rides] 是 (距离米, 在骑的秒数)，新的在前；不够多时返回 null */
    fun of(rides: List<Pair<Double, Long>>): Double? {
        val used = rides.filter { (m, s) -> m >= MIN_RIDE_METERS && s > 0 }.take(RECENT)
        val meters = used.sumOf { it.first }
        val seconds = used.sumOf { it.second }
        if (used.size < 2 || meters < MIN_TOTAL_METERS) return null
        return meters / seconds
    }
}
