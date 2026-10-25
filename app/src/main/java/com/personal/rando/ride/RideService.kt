package com.personal.rando.ride

import com.personal.rando.i18n.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personal.rando.R
import com.personal.rando.group.GroupState
import com.personal.rando.nav.NavProgress
import com.personal.rando.rando
import com.personal.rando.ui.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 导航、记录轨迹时的前台服务：锁屏、切到别的 App 后系统照样每秒给 GPS，进程也不会被回收。
 * 通知栏常驻一条，显示下一个路口或者骑了多远；导航、记录都停了、也不在队里了就自己退出。
 */
class RideService : Service() {

    private val scope = MainScope()
    private var listener: LocationListener? = null
    private var watchJob: Job? = null
    private var lastText: String? = null
    private var crashJob: Job? = null
    private var media: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = rando.session
        // 通知栏上点的按钮：服务已经在前台了，做完就行，不再重新开前台（后台开麦克风类型的前台服务会被拒）
        when (intent?.action) {
            ACTION_IM_OK -> {
                session.imOk()
                return START_NOT_STICKY
            }
            ACTION_SOS_NOW -> {
                session.sendSos()
                return START_NOT_STICKY
            }
        }
        // 用 startForegroundService 开的，每次都得调 startForeground，不然停掉服务时系统会让整个 App 闪退。
        // 在后台（自动开始记录、耳机键说指令）时 Android 14 起不让带麦克风，去掉麦克风再试一次
        val shown = notification(session.navigation.value, session.recording.value, rando.group.state.value)
        val started = serviceTypes().any { types ->
            runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, shown, types) }.isSuccess
        }
        if (!started) {
            // 定位权限被收回之类的，系统不让开定位前台服务
            stopSelf()
            return START_NOT_STICKY
        }
        if (listener == null) startLocation(session)
        if (media == null) startMediaButton()
        if (crashJob == null) {
            // 可能摔车了：通知栏弹一条，锁着屏也能点「我没事」
            crashJob = scope.launch {
                session.crash.collect { alert ->
                    val manager = getSystemService(NotificationManager::class.java)
                    if (alert == null) manager.cancel(CRASH_NOTIFICATION_ID) else manager.notify(CRASH_NOTIFICATION_ID, crashNotification(alert))
                }
            }
        }
        if (watchJob == null) {
            watchJob = scope.launch {
                // 骑完还在队里：服务留着，锁屏后位置照样发给队友、对讲照样听
                combine(session.navigation, session.recording, rando.group.state) { nav, rec, team -> Triple(nav, rec, team) }.collect { (nav, rec, team) ->
                    if (nav == null && rec == null && team == null) {
                        stopSelf()
                    } else {
                        // 胶囊里的字跟着正文变，正文没变就不用重发
                        val text = text(nav, rec, team)
                        if (text != lastText) {
                            lastText = text
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(nav, rec, team))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** 有麦克风权限时先试带上麦克风（锁屏后对讲还能听、按耳机键能说语音指令），不行再只要定位 */
    private fun serviceTypes(): List<Int> {
        if (Build.VERSION.SDK_INT < 29) return listOf(0)
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val mic = Build.VERSION.SDK_INT >= 30 && rando.walkie.hasMicPermission()
        return listOfNotNull(if (mic) location or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else null, location)
    }

    private fun crashNotification(alert: CrashAlert): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CRASH_CHANNEL, tr("摔车检测", "Crash detection"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("一起骑时检测到可能摔车，问你没事吧", "Checks on you when a possible crash is detected during a group ride")
            }
        )
        fun action(name: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, RideService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CRASH_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, tr("我没事", "I'm OK"), action(ACTION_IM_OK, 1))
        return if (alert.sent) {
            builder.setContentTitle(tr("已经通知队友", "Your group has been alerted")).setContentText(tr("没事的话点「我没事」，队友那边会撤掉", "If you're fine, tap \"I'm OK\" to cancel the alert")).build()
        } else {
            builder.setContentTitle(tr("你可能摔车了", "You may have crashed"))
                .setContentText(tr("没事就点「我没事」，否则 30 秒后通知队友", "Tap \"I'm OK\" if you're fine, otherwise your group is alerted in 30 seconds"))
                .setWhen(alert.deadline).setUsesChronometer(true).setChronometerCountDown(true)
                .addAction(0, tr("马上通知队友", "Alert group now"), action(ACTION_SOS_NOW, 2))
                .build()
        }
    }

    /**
     * 耳机上的播放 / 暂停键：骑行中按一下说语音指令。
     * 系统把按键给最近在放声音的 App；在听音乐时按键照样给音乐 App
     */
    private fun startMediaButton() {
        val s = MediaSession(this, "rando-voice")
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val key = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (key.keyCode !in VOICE_KEYS) return false
                if (key.action == KeyEvent.ACTION_UP) rando.voice.listen()
                return true
            }
        })
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .build()
        )
        s.isActive = true
        media = s
    }

    override fun onDestroy() {
        media?.release()
        media = null
        listener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
        getSystemService(NotificationManager::class.java).cancel(CRASH_NOTIFICATION_ID)
        scope.cancel()
        super.onDestroy()
    }

    /** 只要 GPS、每秒一次；GPS 关着才将就用网络定位 */
    @SuppressLint("MissingPermission")
    private fun startLocation(session: RideSession) {
        val manager = getSystemService(LocationManager::class.java) ?: return
        // GPS 刚开要等一会儿才有第一个点：先用一分钟内的上一个 GPS 点顶上，
        // 网络定位也听着，至少地图上先有个大概位置（太不准的不拿来导航）
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
        val l = LocationListener { session.onFix(Fix.of(it)) }
        try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { System.currentTimeMillis() - it.time < FRESH_FIX_MS }
                ?.let { session.onFix(Fix.of(it)) }
            providers.forEach { manager.requestLocationUpdates(it, 1_000L, 0f, l, Looper.getMainLooper()) }
            listener = l
        } catch (_: Exception) {
            stopSelf()
        }
    }

    /** 状态栏小胶囊里的几个字：导航时是下一个路口，不然是骑了多远 */
    private fun chip(nav: NavigationState?, rec: RecordingState?, team: GroupState?): String? {
        if (nav != null) return nav.progress?.let { chip(it, nav.rerouting) }
        return rec?.let { Format.shortDistance(it.distanceMeters.toInt()) }
            ?: team?.takeIf { it.connected }?.let { tr("${it.teammates.size + 1}人", "${it.teammates.size + 1} riders") }
    }

    private fun text(nav: NavigationState?, rec: RecordingState?, team: GroupState?): String {
        if (nav == null) {
            return rec?.let { tr("已骑 ${Format.distance(it.distanceMeters.toInt())} · ${Format.clock(it.movingSeconds)}", "Ridden ${Format.distance(it.distanceMeters.toInt())} · ${Format.clock(it.movingSeconds)}") }
                ?: team?.let { if (it.connected) tr("队里 ${it.teammates.size + 1} 人", "${it.teammates.size + 1} riders in the group") else tr("正在连接…", "Connecting…") }
                ?: tr("等待 GPS 定位…", "Waiting for GPS…")
        }
        val progress = nav.progress ?: return nav.waitingText
        return when {
            progress.arrived -> tr("已到达目的地", "You have arrived")
            nav.rerouting || progress.offRoute -> tr("已偏离路线，正在重新规划", "Off route, rerouting")
            else -> {
                val next = progress.next?.text ?: tr("到达终点", "arrive")
                val toNext = Format.distance(progress.distanceToNext.toInt())
                val left = Format.distance(progress.remainingMeters.toInt())
                tr("${toNext}后$next · 剩余 $left", "In $toNext, $next · $left left")
            }
        }
    }

    private fun notification(nav: NavigationState?, rec: RecordingState?, team: GroupState?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("骑行中", "Riding"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("导航、记录轨迹、一起骑时常驻通知栏", "Stays in the notification bar while navigating, recording or riding with a group")
            }
        )
        // 和桌面图标点进来一样，回到已经开着的界面
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                when {
                    nav != null -> tr("Rando 导航中", "Rando navigating")
                    rec != null -> tr("Rando 记录中", "Rando recording")
                    team != null -> tr("Rando 一起骑中", "Rando group ride")
                    else -> tr("Rando 记录中", "Rando recording")
                }
            )
            .setContentText(text(nav, rec, team))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            // Android 16 实时更新：状态栏左上角出个小胶囊，锁屏上是大卡片；用户在系统设置里关了就还是普通通知
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chip(nav, rec, team))
        nav?.progress?.takeIf { !it.arrived }?.let { builder.setStyle(routeProgress(it)) }
        return builder.build()
    }

    /** 导航时锁屏卡片上的进度条：骑了多少、下一个路口在哪 */
    private fun routeProgress(progress: NavProgress): NotificationCompat.ProgressStyle {
        val total = (progress.alongMeters + progress.remainingMeters).toInt().coerceAtLeast(1)
        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(total)))
            .setProgress(progress.alongMeters.toInt().coerceIn(0, total))
        if (progress.next != null && !progress.offRoute) {
            style.addProgressPoint(NotificationCompat.ProgressStyle.Point((progress.alongMeters + progress.distanceToNext).toInt().coerceIn(1, total)))
        }
        return style
    }

    companion object {
        private const val CHANNEL = "ride"
        private const val CRASH_CHANNEL = "crash"
        private const val CRASH_NOTIFICATION_ID = 30_001
        private const val ACTION_IM_OK = "com.personal.rando.IM_OK"
        private const val ACTION_SOS_NOW = "com.personal.rando.SOS_NOW"
        private val VOICE_KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        )
        private const val NOTIFICATION_ID = 30_000
        /** 上一个 GPS 点在这么久以内，开始时先拿来用 */
        private const val FRESH_FIX_MS = 60_000L

        /** 「↰ 300米」；偏了、到了也说一声 */
        fun chip(progress: NavProgress, rerouting: Boolean): String = when {
            progress.arrived -> tr("已到达", "Arrived")
            rerouting || progress.offRoute -> tr("已偏离", "Off route")
            else -> listOfNotNull(progress.next?.turn?.arrow, Format.shortDistance(progress.distanceToNext.toInt())).joinToString(" ")
        }

        fun start(context: Context) {
            // 没定位权限开不了定位前台服务，开了也只会闪退
            val location = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (!location) return
            // Android 12 起在后台不让开前台服务，会直接抛异常；开不了就先不开，总比整个 App 闪退好
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RideService::class.java)) }
        }
    }
}
