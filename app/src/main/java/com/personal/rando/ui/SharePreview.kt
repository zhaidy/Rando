package com.personal.rando.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.personal.rando.i18n.tr
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 分享图先预览：画好了弹出来看一眼，下面「存到相册」「分享」。
 * 返回的函数传进去怎么画图（参数是要不要隐藏家附近），调用就开始画、弹出预览；
 * 预览里切开关会重新画一张
 */
@Composable
fun rememberSharePreview(): (suspend (hide: Boolean) -> ShareCard.Card?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<ShareCard.Card?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var saved by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf<(suspend (Boolean) -> ShareCard.Card?)?>(null) }
    // 每次打开都默认隐藏，免得上次关掉忘了开
    var hide by remember { mutableStateOf(true) }
    var privacyLabel by remember { mutableStateOf(tr("隐藏家附近", "Hide near home")) }

    fun draw() {
        val m = make ?: return
        job?.cancel()
        card = null
        saved = false
        job = scope.launch {
            val c = m(hide)
            if (c == null) open = false else { card = c; privacyLabel = c.privacy }
        }
    }

    fun save(c: ShareCard.Card) = scope.launch {
        val ok = ShareCard.save(context, c)
        saved = ok
        Toast.makeText(context, if (ok) tr("存到相册了（Pictures/Rando）", "Saved to Photos (Pictures/Rando)") else tr("没存上", "Couldn't save"), Toast.LENGTH_SHORT).show()
    }
    // Android 9 以下存相册要「存储」权限
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val c = card
        if (granted && c != null) save(c) else if (!granted) Toast.makeText(context, tr("没有存储权限，存不了", "No storage permission, can't save"), Toast.LENGTH_SHORT).show()
    }

    if (open) {
        Dialog(onDismissRequest = { open = false; job?.cancel() }) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ShareCard.WIDTH.toFloat() / ShareCard.HEIGHT)
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val c = card
                        if (c == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = RandoGreen, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(tr("正在画图…", "Drawing…"), color = InkSoft, fontSize = 14.sp)
                            }
                        } else {
                            val image = remember(c) { c.bitmap.asImageBitmap() }
                            Image(image, contentDescription = tr("分享图预览", "Share image preview"), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(privacyLabel, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
                        Switch(
                            checked = hide,
                            onCheckedChange = { hide = it; draw() },
                            colors = SwitchDefaults.colors(checkedTrackColor = RandoGreen),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val c = card
                        GlassButton(
                            onClick = {
                                if (c == null || saved) return@GlassButton
                                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save(c)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (saved) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = RandoGreen.onGlass(), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (saved) tr("已存到相册", "Saved to Photos") else tr("存到相册", "Save to Photos"), color = if (c == null) InkSoft else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        GlassButton(
                            onClick = { if (c != null) scope.launch { ShareCard.send(context, c) } },
                            modifier = Modifier.weight(1f),
                            tone = GlassTone.Tinted(if (c == null) Color(0xFF9FB8A8) else RandoGreen),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("分享", "Share"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    return { m ->
        make = m
        hide = true
        open = true
        draw()
    }
}
