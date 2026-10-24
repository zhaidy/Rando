package com.personal.rando.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.personal.rando.ui.glass.GlassIconButton
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.Motion
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap

/**
 * 导航时镜头跟着人走：在骑就按前进方向转（GPS 方向稳），停下来按手机朝向转（指南针）。
 * 用手拖了地图就不跟了，返回的 State 变成 false，「回到我的位置」再跟上
 */
@Composable
fun rememberFollow(map: MapLibreMap?, active: Boolean, moving: Boolean, zoom: Double, tilt: Double): Follow {
    val following = remember { mutableStateOf(true) }
    val follow = remember(map) { Follow(map, following, zoom, tilt) }
    DisposableEffect(map) {
        val component = map?.locationComponent
        val listener = object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                following.value = false
            }

            override fun onCameraTrackingChanged(currentMode: Int) = Unit
        }
        component?.addOnCameraTrackingChangedListener(listener)
        onDispose { component?.removeOnCameraTrackingChangedListener(listener) }
    }
    LaunchedEffect(map, active, moving, following.value) {
        if (active && following.value) follow.apply(moving)
    }
    return follow
}

class Follow internal constructor(
    private val map: MapLibreMap?,
    private val following: MutableState<Boolean>,
    private val zoom: Double,
    private val tilt: Double,
) {
    val isFollowing: Boolean get() = following.value

    /** 「回到我的位置」：重新跟上，镜头拉回骑行的比例 */
    fun recenter() {
        following.value = true
    }

    internal fun apply(moving: Boolean) {
        val component = map?.locationComponent ?: return
        if (!component.isLocationComponentActivated) return
        component.cameraMode = if (moving) CameraMode.TRACKING_GPS else CameraMode.TRACKING_COMPASS
        component.renderMode = if (moving) RenderMode.GPS else RenderMode.COMPASS
        component.zoomWhileTracking(zoom)
        component.tiltWhileTracking(tilt)
    }
}

/** 拖开地图以后出现的「回到我的位置」 */
@Composable
fun RecenterButton(follow: Follow, modifier: Modifier = Modifier, dark: Boolean = false) {
    AnimatedVisibility(
        !follow.isFollowing,
        modifier,
        enter = scaleIn(Motion.bouncy()) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        GlassIconButton(onClick = follow::recenter, tone = if (dark) GlassTone.Dark else GlassTone.Light) {
            Icon(Icons.Default.LocationOn, contentDescription = "回到我的位置", tint = if (dark) Color.White else RandoGreen)
        }
    }
}
