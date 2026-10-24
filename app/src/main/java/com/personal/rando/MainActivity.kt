package com.personal.rando

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.rando.ui.MapScreen
import com.personal.rando.ui.RandoTheme
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
                MapScreen(vm)
                val navigation by vm.session.navigation.collectAsStateWithLifecycle()
                UpdatePrompt(navigating = navigation != null)
            }
        }
    }
}
