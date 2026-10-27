package com.personal.rando.ui

import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.personal.rando.i18n.tr
import com.personal.rando.ui.glass.GlassIconButton
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.Motion
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.delay

/**
 * 导航时镜头跟着人走：在骑就按前进方向转（GPS 方向稳），停下来镜头不转，停在刚才骑的方向。
 * 停下不按指南针转：手机夹在车把上，动一下车头镜头就跟着甩，车把、支架上的磁铁还会把指南针带偏；
 * 绿点外面的小三角照样指手机朝向。
 * 用手拖了地图就不跟了，返回的 State 变成 false，「回到我的位置」再跟上。
 * 给了 [autoRecenterMs] 时，在骑并且这么久没碰地图也自动跟上：边骑边拖着看前面的路不会马上被拉回去，
 * 停下来看地图也不会被拉回去
 */
@Composable
fun rememberFollow(map: MapLibreMap?, active: Boolean, moving: Boolean, zoom: Double, tilt: Double, autoRecenterMs: Long? = null): Follow {
    val following = remember { mutableStateOf(true) }
    val touchedAt = remember { mutableLongStateOf(0L) }
    val follow = remember(map) { Follow(map, following, zoom, tilt) }
    DisposableEffect(map) {
        val component = map?.locationComponent
        val listener = object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                following.value = false
                touchedAt.longValue = SystemClock.elapsedRealtime()
            }

            override fun onCameraTrackingChanged(currentMode: Int) = Unit
        }
        // 拖开以后接着拖、缩放，都从最后一次碰的时候重新算
        val touch = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) touchedAt.longValue = SystemClock.elapsedRealtime()
        }
        component?.addOnCameraTrackingChangedListener(listener)
        map?.addOnCameraMoveStartedListener(touch)
        onDispose {
            component?.removeOnCameraTrackingChangedListener(listener)
            map?.removeOnCameraMoveStartedListener(touch)
        }
    }
    LaunchedEffect(map, active, moving, following.value) {
        if (active && following.value) follow.apply(moving)
    }
    LaunchedEffect(active, moving, following.value, touchedAt.longValue) {
        val wait = autoRecenterMs ?: return@LaunchedEffect
        if (!active || !moving || following.value) return@LaunchedEffect
        delay(wait - (SystemClock.elapsedRealtime() - touchedAt.longValue))
        following.value = true
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

    /**
     * 跟着走时镜头的留白（左上右下，像素），人放在露出来的那块里。
     * 只能交给定位组件设：自己 moveCamera 改留白，地图会当成手动挪了镜头，不再跟着人走
     */
    var padding: DoubleArray? = null
        set(value) {
            field = value
            val component = map?.locationComponent ?: return
            if (value != null && following.value && component.isLocationComponentActivated && component.cameraMode != CameraMode.NONE) {
                component.paddingWhileTracking(value, 0)
            }
        }

    /** 「回到我的位置」：重新跟上，镜头拉回骑行的比例 */
    fun recenter() {
        following.value = true
    }

    internal fun apply(moving: Boolean) {
        val component = map?.locationComponent ?: return
        if (!component.isLocationComponentActivated) return
        component.cameraMode = if (moving) CameraMode.TRACKING_GPS else CameraMode.TRACKING
        component.renderMode = if (moving) RenderMode.GPS else RenderMode.COMPASS
        component.zoomWhileTracking(zoom)
        component.tiltWhileTracking(tilt)
        padding?.let { component.paddingWhileTracking(it, 0) }
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
            Icon(Icons.Default.LocationOn, contentDescription = tr("回到我的位置", "Back to my location"), tint = if (dark) Color.White else AccentInk)
        }
    }
}
