package com.personal.rando.ui

import com.personal.rando.i18n.tr
import java.util.Locale

object Format {

    fun distance(meters: Int): String =
        if (meters < 1000) tr("$meters 米", "$meters m")
        else String.format(Locale.US, tr("%.1f 公里", "%.1f km"), meters / 1000.0)

    /** 状态栏小胶囊里放不下空格：「850米」「15.9公里」 */
    fun shortDistance(meters: Int): String = distance(meters).replace(" ", "")

    fun duration(seconds: Int): String {
        val minutes = (seconds + 59) / 60
        return if (minutes < 60) tr("$minutes 分钟", "$minutes min")
        else if (minutes % 60 == 0) tr("${minutes / 60} 小时", "${minutes / 60} h")
        else tr("${minutes / 60} 小时 ${minutes % 60} 分", "${minutes / 60} h ${minutes % 60} min")
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
    fun speed(metersPerSecond: Double): String = String.format(Locale.US, tr("%.1f 公里/时", "%.1f km/h"), metersPerSecond * 3.6)
}
