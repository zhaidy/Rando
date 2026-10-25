package com.personal.rando.update

import com.personal.rando.i18n.tr
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** 接收 PackageInstaller 的安装结果；成功时进程会被替换，不用处理 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // 第一次（安装来源还不是 Rando）或 Android 11 及以下：系统要用户确认一次
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // 用户在确认界面点了取消，不用提醒
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: tr("错误码 $status", "Error code $status")
                UpdateNotifications.show(context, tr("Rando 更新失败", "Rando update failed"), message)
            }
        }
    }
}
