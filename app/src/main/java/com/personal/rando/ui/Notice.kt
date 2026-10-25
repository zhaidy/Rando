package com.personal.rando.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 界面上的提示条：代替 Toast。浮在屏幕最上面，玻璃样式；
 * 失败的可以带个「重试」，Toast 一闪就没了、也点不了
 */
class Notices(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    /** [long] 多停一会儿；有 [action] 时一直停到点了或者被下一条顶掉 */
    fun show(text: String, long: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            // 新的直接顶掉旧的，不排队
            host.currentSnackbarData?.dismiss()
            val duration = when {
                action != null -> SnackbarDuration.Long
                long -> SnackbarDuration.Long
                else -> SnackbarDuration.Short
            }
            if (host.showSnackbar(text, action, duration = duration) == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/** MainActivity 提供；没提供时（截图测试之类）用 [rememberNotices] 建一个不显示的 */
val LocalNotices = staticCompositionLocalOf<Notices?> { null }

@Composable
fun rememberNotices(host: SnackbarHostState = remember { SnackbarHostState() }): Notices {
    val scope = rememberCoroutineScope()
    return remember(host, scope) { Notices(host, scope) }
}

/** 放在所有界面最上层，状态栏下面 */
@Composable
fun NoticeHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) { data ->
        // 深色、实一点：浮在浅色地图上也要一眼看清
        GlassPanel(Modifier.fillMaxWidth(), shape = GlassShapes.Card, tone = GlassTone.Tinted(Color(0xFF1C2420))) {
            Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.visuals.message,
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
                data.visuals.actionLabel?.let { label ->
                    GlassButton(onClick = data::performAction, tone = GlassTone.Tinted(RandoAccent), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
