package com.personal.rando.ride

import android.content.Context
import androidx.core.content.edit
import com.personal.rando.i18n.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 骑车消耗的热量（千卡）：按在骑的平均速度查运动强度（MET，运动医学常用的骑行强度表），
 * 乘以体重和在骑的时间；爬坡另外加上把人和车抬上去的功（人体效率按 24% 算）
 */
object Calories {
    /** 车重（公斤），算爬坡时加上 */
    private const val BIKE_KG = 12.0
    private const val EFFICIENCY = 0.24

    /** 平均速度（公里/时）→ MET */
    fun met(kmh: Double): Double = when {
        kmh <= 0 -> 0.0
        kmh < 16 -> 4.0
        kmh < 19 -> 6.8
        kmh < 22 -> 8.0
        kmh < 25.5 -> 10.0
        kmh < 30.5 -> 12.0
        else -> 15.8
    }

    fun of(meters: Double, movingSeconds: Long, ascendMeters: Double, weightKg: Double): Int {
        if (movingSeconds <= 0) return 0
        val hours = movingSeconds / 3600.0
        val riding = met(meters / movingSeconds * 3.6) * weightKg * hours
        val climbing = (weightKg + BIKE_KG) * 9.81 * ascendMeters / EFFICIENCY / 4184
        return (riding + climbing).roundToInt()
    }

    fun of(ride: Ride, weightKg: Double) = of(ride.distanceMeters, ride.movingSeconds, ride.ascendMeters, weightKg)
}

/**
 * 设置：体重（算卡路里）、骑车时自动开始记录、播报用哪个声音（null 是自动挑）、主页地图上画不画骑过的路、界面语言；
 * 地图用不用卫星图、画不画公园连道网络（这两个在地图右边的「图层」里切换）
 */
data class Settings(
    val weightKg: Double = DEFAULT_WEIGHT,
    val autoStart: Boolean = false,
    val voice: String? = null,
    val showRidden: Boolean = true,
    val language: Language = Language.SYSTEM,
    val satellite: Boolean = false,
    val showPcn: Boolean = true,
) {
    companion object {
        const val DEFAULT_WEIGHT = 65.0
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        Settings(
            weightKg = prefs.getFloat(KEY_WEIGHT, Settings.DEFAULT_WEIGHT.toFloat()).toDouble(),
            autoStart = prefs.getBoolean(KEY_AUTO_START, false),
            voice = prefs.getString(KEY_VOICE, null),
            showRidden = prefs.getBoolean(KEY_SHOW_RIDDEN, true),
            language = prefs.getString(KEY_LANGUAGE, null)?.let { n -> Language.entries.firstOrNull { it.name == n } } ?: Language.SYSTEM,
            satellite = prefs.getBoolean(KEY_SATELLITE, false),
            showPcn = prefs.getBoolean(KEY_SHOW_PCN, true),
        )
    )
    val settings: StateFlow<Settings> = _settings

    fun update(s: Settings) {
        _settings.value = s
        prefs.edit {
            putFloat(KEY_WEIGHT, s.weightKg.toFloat())
            putBoolean(KEY_AUTO_START, s.autoStart)
            putString(KEY_VOICE, s.voice)
            putBoolean(KEY_SHOW_RIDDEN, s.showRidden)
            putString(KEY_LANGUAGE, s.language.name)
            putBoolean(KEY_SATELLITE, s.satellite)
            putBoolean(KEY_SHOW_PCN, s.showPcn)
        }
    }

    private companion object {
        const val KEY_WEIGHT = "weight"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_VOICE = "voice"
        const val KEY_SHOW_RIDDEN = "show_ridden"
        const val KEY_LANGUAGE = "language"
        const val KEY_SATELLITE = "satellite"
        const val KEY_SHOW_PCN = "show_pcn"
    }
}
