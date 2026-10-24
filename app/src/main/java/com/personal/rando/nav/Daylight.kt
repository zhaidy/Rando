package com.personal.rando.nav

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 日出日落（维基百科「日出方程」的近似算法，误差一两分钟）。
 * 新加坡在赤道边上，一年四季都在 19 点前后日落，差不了半小时
 */
object Sun {

    val SINGAPORE: TimeZone = TimeZone.getTimeZone("Asia/Singapore")
    private const val LAT = 1.35
    private const val LON = 103.82
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val DAY_MS = 86_400_000.0

    /** [dayMillis] 那天（新加坡时间）的日出、日落，毫秒 */
    fun riseAndSet(dayMillis: Long, lat: Double = LAT, lon: Double = LON): Pair<Long, Long> {
        val noon = Calendar.getInstance(SINGAPORE).apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val julian = noon / DAY_MS + UNIX_EPOCH_JD
        val n = ceil(julian - J2000 + 0.0008)
        val meanNoon = n - lon / 360
        val m = (357.5291 + 0.98560028 * meanNoon).mod(360.0)
        val mRad = Math.toRadians(m)
        val center = 1.9148 * sin(mRad) + 0.02 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambda = Math.toRadians((m + center + 180 + 102.9372).mod(360.0))
        val transit = J2000 + meanNoon + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambda)
        val declination = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val latRad = Math.toRadians(lat)
        val hourAngle = acos(
            (sin(Math.toRadians(-0.833)) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))
        )
        val half = Math.toDegrees(hourAngle) / 360
        fun toMillis(jd: Double) = ((jd - UNIX_EPOCH_JD) * DAY_MS).toLong()
        return toMillis(transit - half) to toMillis(transit + half)
    }

    fun sunset(dayMillis: Long): Long = riseAndSet(dayMillis).second

    /** 天黑着：日落以后、日出以前 */
    fun isDark(now: Long): Boolean {
        val (rise, set) = riseAndSet(now)
        return now < rise || now >= set
    }
}

/**
 * 车灯提醒：规划时看会不会骑到天黑，骑的时候日落前二十分钟、日落时各说一次。
 * 和 [VoicePrompter] 一样只出文字
 */
class DaylightAlerts {

    private var warnedSoon = false
    private var warnedDark = false

    fun next(now: Long): String? {
        val sunset = Sun.sunset(now)
        val dark = Sun.isDark(now)
        return when {
            dark && !warnedDark -> {
                warnedDark = true
                warnedSoon = true
                "天黑了，记得打开前后车灯"
            }
            !dark && sunset - now <= SOON_MS && !warnedSoon -> {
                warnedSoon = true
                "还有${(sunset - now) / 60_000}分钟日落，准备好车灯"
            }
            else -> null
        }
    }

    fun reset() {
        warnedSoon = false
        warnedDark = false
    }

    companion object {
        const val SOON_MS = 20 * 60_000L

        /** 路线卡片上的一句：现在出发会不会骑到天黑；不会就是 null */
        fun plan(now: Long, durationSeconds: Int): String? {
            val arrive = now + durationSeconds * 1000L
            val sunset = Sun.sunset(now)
            return when {
                Sun.isDark(now) -> "天黑了，记得打开前后车灯"
                arrive > sunset -> "预计 ${clock(arrive)} 到，${clock(sunset)} 日落，后半程要开车灯"
                sunset - arrive < SOON_MS -> "${clock(sunset)} 日落，到的时候天快黑了"
                else -> null
            }
        }

        private fun clock(millis: Long): String =
            SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = Sun.SINGAPORE }.format(Date(millis))
    }
}
