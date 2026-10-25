package com.personal.rando.ride

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat

/**
 * 只为了拿到「通知使用权」：有了它才能列出别的 App 的媒体会话，在骑行视图上显示歌名、切歌。
 * 不处理任何通知
 */
class MediaListener : NotificationListenerService()

/** 正在放（或暂停着）的歌；[title] 是 null 表示没有通知使用权，只知道有声音在放 */
data class NowPlaying(
    val title: String?,
    val artist: String?,
    val app: String?,
    val playing: Boolean,
)

/**
 * 骑行视图的音乐控制。有通知使用权时读媒体会话（歌名、歌手、哪个 App）并直接控制它；
 * 没有时只能看出有没有声音在放，按键当作耳机的媒体键发出去
 */
class Music(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val sessions = app.getSystemService(MediaSessionManager::class.java)

    val granted: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    private fun controller(): MediaController? {
        if (!granted) return null
        val list = runCatching { sessions?.getActiveSessions(ComponentName(app, MediaListener::class.java)) }.getOrNull().orEmpty()
        // 正在放的优先，其次暂停着的（刚按了暂停，还要能按回来）；自己那个只收耳机键的会话不算
        return list.filter { it.packageName != app.packageName }.let { all ->
            all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        }
    }

    /** 现在的状态；什么都没在放是 null */
    fun now(): NowPlaying? {
        val c = controller()
        if (c != null) {
            val meta = c.metadata
            val label = runCatching {
                app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(c.packageName, 0)).toString()
            }.getOrNull()
            return NowPlaying(
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: label ?: "",
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                app = label,
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
        return if (otherMusic()) NowPlaying(null, null, null, playing = true) else null
    }

    /**
     * 没有通知使用权时猜有没有在放音乐。光看 isMusicActive 不行：自己的导航播报、提示音也走音乐那一路，
     * 一念「空气不健康」就会当成在放歌；还得有个按「媒体」放的播放器（播报是导航语音，对讲是通话）
     */
    private fun otherMusic(): Boolean {
        val am = audio ?: return false
        if (!am.isMusicActive) return false
        val players = runCatching { am.activePlaybackConfigurations }.getOrNull().orEmpty()
        return players.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA || it.audioAttributes.usage == AudioAttributes.USAGE_UNKNOWN }
    }

    fun playPause() {
        val c = controller()
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next() = controller()?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller()?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /** 没有通知使用权时：像耳机按键一样发给正在放声音的 App */
    private fun key(code: Int) {
        val am = audio ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        /** 系统里打开「通知使用权」的页面 */
        fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
