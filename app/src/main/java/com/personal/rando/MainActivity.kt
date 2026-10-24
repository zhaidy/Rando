package com.personal.rando

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
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
import com.personal.rando.ui.RouteViewModel
import com.personal.rando.ui.UpdatePrompt
import com.personal.rando.update.UpdateNotifications

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 更新后已经回到应用了，“已更新，点这里打开”的通知就没用了
        UpdateNotifications.cancelUpdated(this)
        setContent {
            RandoTheme {
                val vm: RouteViewModel = viewModel()
                // 0 是地图，-1 是记录列表，其他是某条记录的 id
                var screen by rememberSaveable { mutableLongStateOf(0L) }
                // 地图一直留着（来回切不用重新加载），记录页盖在上面
                // 每点一次「检查更新」加一，UpdatePrompt 看到变化就去查
                var updateChecks by remember { mutableIntStateOf(0) }
                MapScreen(vm, onOpenRides = { screen = -1L }, onCheckUpdate = { updateChecks++ })
                when (val s = screen) {
                    0L -> Unit
                    -1L -> {
                        BackHandler { screen = 0L }
                        RideListScreen(onBack = { screen = 0L }, onOpen = { screen = it })
                    }
                    else -> {
                        BackHandler { screen = -1L }
                        RideDetailScreen(s, onBack = { screen = -1L })
                    }
                }
                val navigation by vm.session.navigation.collectAsStateWithLifecycle()
                UpdatePrompt(navigating = navigation != null, manualChecks = updateChecks)
            }
        }
    }
}
