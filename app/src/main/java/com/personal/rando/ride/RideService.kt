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
import kotlinx.coroutines.launch

/**
 * 导航时的前台服务：锁屏、切到别的 App 后系统照样每秒给 GPS，进程也不会被回收。
 * 通知栏常驻一条，显示下一个路口；导航结束就自己退出。
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
                this, NOTIFICATION_ID, notification(text(session.navigation.value)),
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
                session.navigation.collect { nav ->
                    if (nav == null) {
                        stopSelf()
                    } else {
                        val text = text(nav)
                        if (text != lastText) {
                            lastText = text
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
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
        val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else {
            LocationManager.NETWORK_PROVIDER
        }
        val l = LocationListener { session.onFix(Fix.of(it)) }
        try {
            manager.requestLocationUpdates(provider, 1_000L, 0f, l, Looper.getMainLooper())
            listener = l
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun text(nav: NavigationState?): String {
        val progress = nav?.progress ?: return "等待 GPS 定位…"
        return when {
            progress.arrived -> "已到达目的地"
            nav.rerouting || progress.offRoute -> "已偏离路线，正在重新规划"
            else -> {
                val next = progress.next?.text ?: "到达终点"
                "${Format.distance(progress.distanceToNext.toInt())}后$next · 剩余 ${Format.distance(progress.remainingMeters.toInt())}"
            }
        }
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "骑行导航", NotificationManager.IMPORTANCE_LOW).apply {
                description = "导航时常驻通知栏，显示下一个路口"
            }
        )
        // 和桌面图标点进来一样，回到已经开着的界面
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rando 导航中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "ride"
        private const val NOTIFICATION_ID = 30_000

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
        }
    }
}
