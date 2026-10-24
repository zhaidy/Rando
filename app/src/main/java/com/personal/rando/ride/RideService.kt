package com.personal.rando.ride

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personal.rando.R
import com.personal.rando.rando
import com.personal.rando.ui.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 导航、记录轨迹时的前台服务：锁屏、切到别的 App 后系统照样每秒给 GPS，进程也不会被回收。
 * 通知栏常驻一条，显示下一个路口或者骑了多远；都结束了就自己退出。
 */
class RideService : Service() {

    private val scope = MainScope()
    private var listener: LocationListener? = null
    private var watchJob: Job? = null
    private var lastText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = rando.session
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(session.navigation.value, session.recording.value),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (_: Exception) {
            // 定位权限被收回之类的，系统不让开定位前台服务
            stopSelf()
            return START_NOT_STICKY
        }
        if (listener == null) startLocation(session)
        if (watchJob == null) {
            watchJob = scope.launch {
                combine(session.navigation, session.recording) { nav, rec -> nav to rec }.collect { (nav, rec) ->
                    if (nav == null && rec == null) {
                        stopSelf()
                    } else {
                        val text = text(nav, rec)
                        if (text != lastText) {
                            lastText = text
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(nav, rec))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
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

    private fun text(nav: NavigationState?, rec: RecordingState?): String {
        if (nav == null) {
            return rec?.let { "已骑 ${Format.distance(it.distanceMeters.toInt())} · ${Format.clock(it.movingSeconds)}" }
                ?: "等待 GPS 定位…"
        }
        val progress = nav.progress ?: return nav.waitingText
        return when {
            progress.arrived -> "已到达目的地"
            nav.rerouting || progress.offRoute -> "已偏离路线，正在重新规划"
            else -> {
                val next = progress.next?.text ?: "到达终点"
                "${Format.distance(progress.distanceToNext.toInt())}后$next · 剩余 ${Format.distance(progress.remainingMeters.toInt())}"
            }
        }
    }

    private fun notification(nav: NavigationState?, rec: RecordingState?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "骑行中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "导航、记录轨迹时常驻通知栏"
            }
        )
        // 和桌面图标点进来一样，回到已经开着的界面
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (nav != null) "Rando 导航中" else "Rando 记录中")
            .setContentText(text(nav, rec))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "ride"
        private const val NOTIFICATION_ID = 30_000
        /** 上一个 GPS 点在这么久以内，开始时先拿来用 */
        private const val FRESH_FIX_MS = 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
        }
    }
}
