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

    fun percent(share: Double): String = "${Math.round(share * 100)}%"
}
