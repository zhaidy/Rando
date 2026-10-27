package com.personal.rando.ui

import android.content.Context
import androidx.core.content.edit

/** 界面上的小习惯：分享图深色还是浅色、写不写队友名字、骑行视图上下两块收没收，下次打开还是一样 */
class UiPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var shareDark: Boolean
        get() = prefs.getBoolean(KEY_SHARE_DARK, true)
        set(v) = prefs.edit { putBoolean(KEY_SHARE_DARK, v) }

    var shareHideTeammates: Boolean
        get() = prefs.getBoolean(KEY_SHARE_HIDE_TEAMMATES, false)
        set(v) = prefs.edit { putBoolean(KEY_SHARE_HIDE_TEAMMATES, v) }

    var rideTopOpen: Boolean
        get() = prefs.getBoolean(KEY_RIDE_TOP, true)
        set(v) = prefs.edit { putBoolean(KEY_RIDE_TOP, v) }

    /** 下面那块是哪一档，见 RideView 里的 BOTTOM_* */
    var rideBottom: Int
        get() = prefs.getInt(KEY_RIDE_BOTTOM, -1)
        set(v) = prefs.edit { putInt(KEY_RIDE_BOTTOM, v) }

    private companion object {
        const val KEY_SHARE_DARK = "share_dark"
        const val KEY_SHARE_HIDE_TEAMMATES = "share_hide_teammates"
        const val KEY_RIDE_TOP = "ride_top_open"
        const val KEY_RIDE_BOTTOM = "ride_bottom"
    }
}
