package com.personal.rando.ui

import java.util.Locale

object Format {

    fun distance(meters: Int): String =
        if (meters < 1000) "$meters 米"
        else String.format(Locale.US, "%.1f 公里", meters / 1000.0)

    fun duration(seconds: Int): String {
        val minutes = (seconds + 59) / 60
        return if (minutes < 60) "$minutes 分钟"
        else if (minutes % 60 == 0) "${minutes / 60} 小时"
        else "${minutes / 60} 小时 ${minutes % 60} 分"
    }

    /** 第几公里：「3.2」，不带单位 */
    fun km(meters: Double): String = String.format(Locale.US, "%.1f", meters / 1000)

    fun percent(share: Double): String = "${Math.round(share * 100)}%"

    /** 图例里的占比：有但不到 1% 的写「<1%」，不写「0%」 */
    fun share(share: Double): String = if (share > 0 && share < 0.005) "<1%" else percent(share)

    /** 码表样式：12:34、1:02:03 */
    fun clock(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** 米/秒 → 「18.5 公里/时」 */
    fun speed(metersPerSecond: Double): String = String.format(Locale.US, "%.1f 公里/时", metersPerSecond * 3.6)
}
