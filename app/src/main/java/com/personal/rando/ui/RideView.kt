package com.personal.rando.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.RecordingState
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.Style
import java.util.Locale

private val DIM = Color(0xFF9E9E9E)

/**
 * 骑行视图：黑底大字，只留骑车时要看的——下一个路口、当前速度、还剩多久多远。
 * 地图倾斜跟着人走，只画道路轮廓
 */
@Composable
fun RideView(
    navigation: NavigationState,
    recording: RecordingState?,
    onMap: () -> Unit,
    onStop: () -> Unit,
) {
    val progress = navigation.progress
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        RideTurn(navigation, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
        RideMap(navigation, Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                String.format(Locale.US, "%.1f", (recording?.currentSpeed ?: 0.0) * 3.6),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Medium,
            )
            Text("公里/时", color = DIM, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            BigStat(progress?.let { (it.remainingSeconds + 59) / 60 }?.toString() ?: "--", "分", Modifier.weight(1f))
            BigStat(
                progress?.let { String.format(Locale.US, "%.1f", it.remainingMeters / 1000) } ?: "--",
                "公里",
                Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) { Text("地图", color = DIM) }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("结束", color = DIM) }
        }
    }
}

/** 顶部：大箭头加距离，下面一行小字说怎么走 */
@Composable
private fun RideTurn(navigation: NavigationState, modifier: Modifier) {
    val progress = navigation.progress
    Column(modifier) {
        when {
            progress == null -> RideMessage("等待 GPS 定位…")
            progress.arrived -> RideMessage("已到达目的地")
            navigation.rerouting || progress.offRoute -> RideMessage("已偏离路线，正在重新规划…")
            else -> {
                val next = progress.next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(next?.turn?.arrow ?: "⚑", color = Color.White, fontSize = 64.sp)
                    Spacer(Modifier.width(20.dp))
                    val distance = Format.distance(progress.distanceToNext.toInt())
                    val number = distance.substringBefore(' ')
                    val unit = distance.substringAfter(' ', "")
                    Text(number, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text(unit, color = Color.White, fontSize = 28.sp)
                }
                Text(
                    next?.text ?: "到达终点",
                    color = DIM,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RideMessage(text: String) {
    Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun BigStat(value: String, unit: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Medium)
        Text("剩余 $unit", color = DIM, fontSize = 15.sp)
    }
}

/** 黑底地图，镜头倾斜跟着人、朝骑行方向转；路线照样按路的种类上色 */
@SuppressLint("MissingPermission")
@Composable
private fun RideMap(navigation: NavigationState, modifier: Modifier) {
    val context = LocalContext.current
    // 盖在主地图上面，要用 TextureView
    val mapView = rememberMapView(textureMode = true)
    var style by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.setStyle(Style.Builder().fromJson(RideMapStyle.JSON)) { s ->
                MapLayers.install(s, dark = true)
                val puck = LocationComponentOptions.builder(context)
                    .foregroundTintColor(android.graphics.Color.parseColor("#2E7D4F"))
                    .bearingTintColor(android.graphics.Color.parseColor("#2E7D4F"))
                    .accuracyAlpha(0f)
                    .build()
                m.locationComponent.apply {
                    activateLocationComponent(
                        LocationComponentActivationOptions.builder(context, s).locationComponentOptions(puck).build()
                    )
                    isLocationComponentEnabled = true
                    renderMode = RenderMode.GPS
                    cameraMode = CameraMode.TRACKING_GPS
                    zoomWhileTracking(17.5)
                    tiltWhileTracking(55.0)
                }
                style = s
            }
        }
    }
    LaunchedEffect(style, navigation.route) {
        val s = style ?: return@LaunchedEffect
        MapLayers.show(s, navigation.route, start = null, end = navigation.destination)
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }
}
