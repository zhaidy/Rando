package com.personal.rando.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.rando.update.UpdateChecker
import com.personal.rando.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 启动时静默检查 GitHub Releases，有新版本才弹窗；导航中先不弹，免得挡住路。
 * [manualChecks] 每加一就是用户在菜单里点了「检查更新」，这时没新版本也提示一声
 */
@Composable
fun UpdatePrompt(navigating: Boolean, manualChecks: Int) {
    val context = LocalContext.current
    val updater = remember { UpdateChecker(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // 装好后的「已更新」通知要这个权限；不给也不影响更新本身
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(manualChecks) {
        val manual = manualChecks > 0
        if (!manual) updater.cleanup()
        try {
            val info = updater.check()
            if (info != null) {
                update = info
            } else if (manual) {
                Toast.makeText(context, "已是最新版本（build ${updater.currentBuild()}）", Toast.LENGTH_SHORT).show()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (manual) Toast.makeText(context, "检查更新失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val info = update ?: return
    if (navigating) return
    AlertDialog(
        onDismissRequest = { if (!updating) update = null },
        title = { Text("发现新版本 build ${info.build}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info.changes.isNotEmpty()) {
                    Text("更新内容", style = MaterialTheme.typography.titleSmall)
                    info.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(12.dp))
                }
                Text(message ?: if (updating) "正在下载安装…装好后应用会自动重新打开。" else "装好后应用会自动重新打开。")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !updating,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    updating = true
                    message = null
                    scope.launch {
                        try {
                            val installing = updater.install(updater.download(info))
                            if (installing) update = null else message = "请先允许安装未知应用，再点一次更新"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = "下载失败：${e.message}"
                        } finally {
                            updating = false
                        }
                    }
                },
            ) { Text("更新") }
        },
        dismissButton = { TextButton(enabled = !updating, onClick = { update = null }) { Text("以后再说") } },
    )
}
