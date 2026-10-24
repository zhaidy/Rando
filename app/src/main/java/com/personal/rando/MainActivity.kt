package com.personal.rando

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.personal.rando.ride.RideWidget
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.rando.ui.MapScreen
import com.personal.rando.ui.RandoTheme
import com.personal.rando.ui.RideDetailScreen
import com.personal.rando.ui.RideListScreen
import com.personal.rando.ui.RiddenMapScreen
import com.personal.rando.ui.RouteViewModel
import com.personal.rando.ui.UpdatePrompt
import com.personal.rando.update.UpdateNotifications

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 更新后已经回到应用了，“已更新，点这里打开”的通知就没用了
        UpdateNotifications.cancelUpdated(this)
        handle(intent)
        setContent {
            RandoTheme {
                val vm: RouteViewModel = viewModel()
                // 0 是地图，-1 是记录列表，其他是某条记录的 id
                var screen by rememberSaveable { mutableLongStateOf(0L) }
                // 每点一次「检查更新」加一，UpdatePrompt 看到变化就去查
                var updateChecks by remember { mutableIntStateOf(0) }
                // 液态玻璃：地图登记成取景来源，上面所有玻璃面板都从这里取
                val haze = rememberHazeState()
                CompositionLocalProvider(LocalHazeState provides haze) {
                    // 地图一直留着（来回切不用重新加载），记录页盖在上面，从下面浮上来
                    MapScreen(vm, onOpenRides = { screen = -1L }, onCheckUpdate = { updateChecks++ })
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(Motion.smooth()) { it / 5 })
                                .togetherWith(fadeOut(tween(160)) + slideOutVertically(Motion.smooth()) { it / 8 })
                        },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            0L -> Unit
                            -1L -> {
                                BackHandler { screen = 0L }
                                RideListScreen(onBack = { screen = 0L }, onOpen = { screen = it }, onRiddenMap = { screen = RIDDEN })
                            }
                            RIDDEN -> {
                                BackHandler { screen = -1L }
                                RiddenMapScreen(onBack = { screen = -1L })
                            }
                            else -> {
                                BackHandler { screen = -1L }
                                RideDetailScreen(s, onBack = { screen = -1L })
                            }
                        }
                    }
                }
                val navigation by vm.session.navigation.collectAsStateWithLifecycle()
                UpdatePrompt(navigating = navigation != null, manualChecks = updateChecks)
            }
        }
    }
}

/** 桌面小组件上点了「开始记录」：有定位权限就直接开始 */
private fun ComponentActivity.handle(intent: Intent?) {
    if (intent?.action != RideWidget.ACTION_START) return
    intent.action = null
    val located = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    if (located) rando.session.startRecording()
    else Toast.makeText(this, "先允许 Rando 使用位置，才能记录", Toast.LENGTH_LONG).show()
}

/** 「骑过的路」那一页；0 是地图，-1 是记录列表，正数是某条记录 */
private const val RIDDEN = -2L
