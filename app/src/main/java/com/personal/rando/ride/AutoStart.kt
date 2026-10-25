package com.personal.rando.ride

import com.personal.rando.i18n.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.personal.rando.MainActivity
import com.personal.rando.R
import com.personal.rando.rando

/**
 * 骑车自动开始记录：让系统的活动识别在认出「开始骑车」时通知我们。
 * 定位是「始终允许」时直接在后台开始记录；不然弹一条通知，点一下开始
 */
object AutoStart {

    fun hasRecognitionPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 按设置开 / 关；没权限就什么都不做 */
    @SuppressLint("MissingPermission")
    fun apply(context: Context, on: Boolean) {
        val app = context.applicationContext
        val client = ActivityRecognition.getClient(app)
        val pending = pendingIntent(app)
        if (!on || !hasRecognitionPermission(app)) {
            runCatching { client.removeActivityTransitionUpdates(pending) }
            return
        }
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_BICYCLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
        )
        runCatching { client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pending) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, AutoStartReceiver::class.java),
            // 活动识别要往里面塞结果，得是可变的
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

    /** 开始骑车了：能直接开始就开始，不然弹通知问 */
    fun onCycling(context: Context) {
        val session = context.rando.session
        if (session.recording.value != null) return
        if (hasBackgroundLocation(context)) {
            session.startRecording(auto = true)
            session.say(tr("检测到你在骑车，开始记录", "Looks like you're riding, recording started"))
        } else {
            notify(context)
        }
    }

    private fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("自动开始记录", "Auto-start recording"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("检测到在骑车时问要不要开始记录", "Asks whether to start recording when riding is detected")
            }
        )
        val start = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).setAction(RideWidget.ACTION_START)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tr("在骑车吗？", "Riding?"))
                .setContentText(tr("点一下开始记录（定位设成「始终允许」就能自动开始）", "Tap to start recording (set location to \"Allow all the time\" to start automatically)"))
                .setContentIntent(start)
                .setAutoCancel(true)
                .setTimeoutAfter(10 * 60_000L)
                .build(),
        )
    }

    private const val CHANNEL = "auto_start"
    private const val NOTIFICATION_ID = 30_002
}

/** 活动识别的结果：认出开始骑车 */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val cycling = result.transitionEvents.any {
            it.activityType == DetectedActivity.ON_BICYCLE && it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        if (cycling && context.rando.settings.settings.value.autoStart) AutoStart.onCycling(context)
    }
}

/** 开机后活动识别的登记没了，重新登记 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AutoStart.apply(context, context.rando.settings.settings.value.autoStart)
    }
}
