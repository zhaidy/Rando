package com.personal.rando.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.Fix
import com.personal.rando.group.Teammate
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.nextOfEach
import com.personal.rando.ride.Settings
import com.personal.rando.ride.Calories
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.personal.rando.places.HazardType
import com.personal.rando.places.Hazard
import com.personal.rando.weather.RouteWeather
import com.personal.rando.weather.Climate
import com.personal.rando.routing.LatLon
import com.personal.rando.ride.RecordingState
import com.personal.rando.routing.CrossingType
import com.personal.rando.routing.RouteTimeline
import kotlinx.coroutines.delay
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DIM = Color(0xFF9E9E9E)
private val CLOCK = SimpleDateFormat("HH:mm", Locale.CHINA)
/** 路口离这么近才在屏幕上提示 */
private const val CROSSING_SHOWN_METERS = 300.0

/**
 * 骑行视图：黑底大字，只留骑车时要看的——下一个路口、当前速度、还剩多久多远。
 * 不导航只记录时（[navigation] 是 null）换成骑了多久、多远。
 * 地图倾斜跟着人走，只画道路轮廓
 */
@Composable
fun RideView(
    navigation: NavigationState?,
    recording: RecordingState?,
    onMap: () -> Unit,
    onStop: () -> Unit,
    /** 前方天气；不下雨就不显示 */
    weather: RouteWeather? = null,
    /** 导航路线两旁的补给点 */
    amenities: List<AmenityOnRoute> = emptyList(),
    /** 酷热、烟霾、紫外线很强时显示一行 */
    climate: Climate? = null,
    /** 一起骑时队友离我多远的一行 */
    teammates: String? = null,
    /** 一起骑时对讲的状态 */
    walkie: String? = null,
    /** 在现在的位置标一处路况 */
    onReport: (HazardType) -> Unit = {},
    /** 体重：算消耗了多少千卡 */
    weightKg: Double = Settings.DEFAULT_WEIGHT,
    onVoice: () -> Unit = {},
    /** 正在听语音指令：听到的字（还没听到是「请说…」）；没在听是 null */
    listening: String? = null,
    map: @Composable (Modifier) -> Unit,
) {
    val progress = navigation?.progress
    val timeline = remember(navigation?.route) { navigation?.route?.let(::RouteTimeline) }
    val along = progress?.alongMeters ?: 0.0
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // 地图铺满，上下两块深色玻璃浮在上面
            map(Modifier.fillMaxSize().hazeSource(haze))
            GlassPanel(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(10.dp),
                shape = GlassShapes.Card,
                tone = GlassTone.Dark,
            ) {
                Column(Modifier.padding(vertical = 14.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        if (navigation != null) {
                            RideTurn(navigation, Modifier.fillMaxWidth().padding(horizontal = 22.dp))
                        } else {
                            FreeRide(recording, Modifier.fillMaxWidth().padding(horizontal = 22.dp))
                        }
                        Clock(Modifier.align(Alignment.TopEnd).padding(horizontal = 20.dp))
                    }
                    if (timeline != null && progress != null && !progress.arrived) {
                        RoadAhead(timeline, along, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp))
                    }
                    Extras(weather, climate?.warning, amenities.takeIf { progress != null }.orEmpty(), along, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp))
                    teammates?.let { TeammatesRow(it, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp)) }
                    walkie?.let { TeammatesRow(it, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp)) }
                    listening?.let {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot("#E35B4F")
                            Text("正在听：$it", color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            GlassPanel(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(10.dp),
                tone = GlassTone.Dark,
            ) {
                Column(Modifier.padding(vertical = 14.dp)) {
                    timeline?.let { RouteStrip(it, along, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (navigation != null) {
                            BigStat(progress?.let { (it.remainingSeconds + 59) / 60 }?.toString() ?: "--", "剩余 分", Modifier.weight(1f))
                        } else {
                            BigStat(Format.clock(recording?.movingSeconds ?: 0), "骑行时间", Modifier.weight(1f))
                        }
                        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedContent(
                                targetState = String.format(Locale.US, "%.1f", (recording?.currentSpeed ?: 0.0) * 3.6),
                                transitionSpec = {
                                    (slideInVertically(Motion.smooth()) { it / 3 } + fadeIn(tween(150)))
                                        .togetherWith(slideOutVertically(Motion.smooth()) { -it / 3 } + fadeOut(tween(100)))
                                },
                                label = "speed",
                            ) { v -> Text(v, color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Medium) }
                            Text("公里/时", color = DIM, fontSize = 14.sp)
                        }
                        if (navigation != null) {
                            BigStat(
                                progress?.let { String.format(Locale.US, "%.1f", it.remainingMeters / 1000) } ?: "--",
                                "剩余 公里",
                                Modifier.weight(1f),
                            )
                        } else {
                            BigStat(
                                String.format(Locale.US, "%.1f", (recording?.distanceMeters ?: 0.0) / 1000),
                                "已骑 公里",
                                Modifier.weight(1f),
                            )
                        }
                    }
                    recording?.let { MoreStats(it, navigating = navigation != null, weightKg, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlassButton(
                            onClick = onVoice,
                            tone = if (listening != null) GlassTone.Tinted(RandoRed) else GlassTone.Dark,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(MicIcon, contentDescription = "语音指令", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        GlassButton(onClick = onMap, modifier = Modifier.weight(1f), tone = GlassTone.Dark) {
                            Text("地图", color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        ReportButton(onReport, Modifier.weight(1f))
                        GlassButton(onClick = onStop, modifier = Modifier.weight(1f), tone = GlassTone.Tinted(RandoRed)) {
                            Text("结束", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 码表的其余几项，一行小字：导航时是已骑、均速、最快、爬升（剩余在上面），
 * 自由骑行时是均速、最快、爬升、全程用时（已骑、骑行时间在上面）
 */
@Composable
private fun MoreStats(recording: RecordingState, navigating: Boolean, weightKg: Double, modifier: Modifier) {
    // 全程用时每秒走一下，不等定位
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val average = if (recording.movingSeconds > 0) recording.distanceMeters / recording.movingSeconds else 0.0
    val kmh = { mps: Double -> String.format(Locale.US, "%.1f", mps * 3.6) }
    val stats = buildList {
        if (navigating) add(String.format(Locale.US, "%.1f", recording.distanceMeters / 1000) to "已骑 公里")
        add(kmh(average) to "均速")
        add("${Calories.of(recording.distanceMeters, recording.movingSeconds, recording.ascendMeters, weightKg)}" to "千卡")
        add("${recording.ascendMeters.toInt()}" to "爬升 米")
        if (!navigating) add(Format.clock(((now - recording.startedAt) / 1000).coerceAtLeast(0)) to "全程用时")
    }
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(vertical = 8.dp),
    ) {
        stats.forEach { (value, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 20.sp),
                )
                Text(label, color = DIM, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** 右上角的钟，每分钟走一下 */
@Composable
private fun Clock(modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    Text(CLOCK.format(Date(now)), color = DIM, fontSize = 15.sp, modifier = modifier)
}

/**
 * 前方路况：现在骑的是什么路、还有多远，接下来换成什么路；
 * 300 米内有路口时再加一行，说是哪种路口
 */
@Composable
private fun RoadAhead(timeline: RouteTimeline, along: Double, modifier: Modifier) {
    val ahead = timeline.ahead(along) ?: return
    val crossing = timeline.nextCrossing(along)?.takeIf { it.distance <= CROSSING_SHOWN_METERS }
    val structure = timeline.nextStructure(along)?.takeIf { it.distance <= CROSSING_SHOWN_METERS }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(ahead.current.kind.color)
            Text(
                buildString {
                    append("${ahead.current.kind.label} · 还有 ${Format.distance(ahead.remainingInCurrent.toInt())}")
                    ahead.next?.let { append("，然后${it.kind.label} ${Format.distance(it.length.toInt())}") }
                },
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        structure?.let { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot("#FFFFFF")
                Text(
                    "前方 ${Format.distance(a.distance.toInt())} ${a.structure.kind.label} · ${a.structure.access}",
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }
        crossing?.let { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(c.type.color)
                Text(
                    "前方 ${Format.distance(c.distance.toInt())} 过马路 · ${c.type.label}",
                    color = if (c.type == CrossingType.UNMARKED) Color(0xFFFFB074) else Color.White,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun Dot(color: String) {
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(android.graphics.Color.parseColor(color)))
    )
}

/** 全程一条细条，按路的种类上色，白色竖线是现在骑到的位置 */
@Composable
private fun RouteStrip(timeline: RouteTimeline, along: Double, modifier: Modifier) {
    if (timeline.stretches.isEmpty() || timeline.total <= 0) return
    Canvas(modifier.height(10.dp)) {
        val h = size.height
        val barTop = h * 0.2f
        val barHeight = h * 0.6f
        timeline.stretches.forEach { s ->
            val x0 = (s.startMeters / timeline.total * size.width).toFloat()
            val x1 = (s.endMeters / timeline.total * size.width).toFloat()
            drawRect(
                Color(android.graphics.Color.parseColor(s.kind.color)),
                topLeft = Offset(x0, barTop),
                size = Size((x1 - x0).coerceAtLeast(1f), barHeight),
            )
        }
        // 骑过的部分压暗
        val x = (along / timeline.total).coerceIn(0.0, 1.0).toFloat() * size.width
        drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, barTop), size = Size(x, barHeight))
        drawRect(Color.White, topLeft = Offset(x - 2f, 0f), size = Size(4f, h))
    }
}

/** 顶部：大箭头加距离，下面一行小字说怎么走 */
@Composable
private fun RideTurn(navigation: NavigationState, modifier: Modifier) {
    val progress = navigation.progress
    Column(modifier) {
        when {
            progress == null -> RideMessage(navigation.waitingText)
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
private fun BigStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
        Text(label, color = DIM, fontSize = 13.sp)
    }
}

/** 前方最近的补给（五公里内，最多三种）和要下的雨 */
@Composable
private fun Extras(weather: RouteWeather?, warning: String?, amenities: List<AmenityOnRoute>, along: Double, modifier: Modifier) {
    val next = amenities.nextOfEach(along).entries
        .filter { it.value <= AMENITY_SHOWN_METERS }
        .sortedBy { it.value }
        .take(3)
    val rain = weather?.takeIf { !it.dry }?.summary
    if (next.isEmpty() && rain == null && warning == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (next.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(next.first().key.color)
                Text(
                    "前方 " + next.joinToString(" · ") { "${it.key.label} ${Format.distance(it.value.toInt())}" },
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        rain?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot("#5B9BEA")
                Text(it, color = Color(0xFF9CC6FF), fontSize = 16.sp, maxLines = 2)
            }
        }
        warning?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot("#F2A93B")
                Text(it, color = Color(0xFFFFD08A), fontSize = 16.sp, maxLines = 2)
            }
        }
    }
}

/** 「路况」：点开选施工、封路、路面不好，标在现在的位置 */
@Composable
private fun ReportButton(onReport: (HazardType) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), tone = GlassTone.Dark) {
            Text("路况", color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GlassShapes.Card, containerColor = Color(0xF2222624)) {
            HazardType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text("这里${type.label}", color = Color.White, fontSize = 17.sp) },
                    leadingIcon = { Dot(type.color) },
                    onClick = {
                        open = false
                        onReport(type)
                    },
                )
            }
        }
    }
}

/** 补给点在这么远以内才在骑行视图上说 */
private const val AMENITY_SHOWN_METERS = 5000.0

/** 不导航时顶上那块：在记录，骑了多久 */
@Composable
private fun FreeRide(recording: RecordingState?, modifier: Modifier) {
    Column(modifier) {
        RideMessage("自由骑行")
        Text(
            recording?.let {
                "已骑 ${Format.distance(it.distanceMeters.toInt())} · 爬升 ${it.ascendMeters.toInt()} 米" +
                    // 停下来不算时间，告诉人一声
                    if (it.movingSeconds > 0 && it.currentSpeed == 0.0) " · 已自动暂停" else ""
            } ?: "记录中",
            color = DIM,
            fontSize = 18.sp,
        )
    }
}

/** 黑底地图，镜头倾斜跟着人、朝骑行方向转；路线照样按路的种类上色 */
@SuppressLint("MissingPermission")
@Composable
fun RideMap(
    navigation: NavigationState?,
    fix: Fix?,
    track: List<LatLon>,
    teammates: List<Teammate>,
    modifier: Modifier,
    hazards: List<Hazard> = emptyList(),
) {
    val context = LocalContext.current
    // 盖在主地图上面，要用 TextureView
    val mapView = rememberMapView(textureMode = true)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            // 一开始就放大到骑行的比例，不然没收到定位前停在整个地球的视角
            val here = fix?.location ?: navigation?.route?.points?.firstOrNull() ?: track.lastOrNull()
            if (here != null) {
                m.cameraPosition = CameraPosition.Builder().target(LatLng(here.lat, here.lon)).zoom(RIDE_ZOOM).tilt(RIDE_TILT).build()
            }
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
                // 定位用导航自己收到的，不另外开一路：两边一致，信号差时也不会一个有一个没有
                m.locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, s)
                        .locationComponentOptions(puck)
                        .useDefaultLocationEngine(false)
                        .build()
                )
                m.locationComponent.isLocationComponentEnabled = true
                fix?.let { m.locationComponent.forceLocationUpdate(it.toLocation()) }
                map = m
            }
        }
    }
    LaunchedEffect(map, fix) {
        val m = map ?: return@LaunchedEffect
        fix?.let { m.locationComponent.forceLocationUpdate(it.toLocation()) }
    }
    LaunchedEffect(map, navigation?.route, navigation?.via) {
        val s = map?.style ?: return@LaunchedEffect
        MapLayers.show(s, navigation?.route, start = null, end = navigation?.destination, via = navigation?.via.orEmpty())
    }
    LaunchedEffect(map, teammates) {
        val s = map?.style ?: return@LaunchedEffect
        MapLayers.showTeam(s, teammates)
    }
    LaunchedEffect(map, hazards) {
        val s = map?.style ?: return@LaunchedEffect
        MapLayers.showHazards(s, hazards)
    }
    // 骑过的轨迹；按点数变化重画
    LaunchedEffect(map, track.size) {
        val s = map?.style ?: return@LaunchedEffect
        MapLayers.showTrack(s, track)
    }
    val follow = rememberFollow(map, active = map != null, moving = fix?.moving == true, zoom = RIDE_ZOOM, tilt = RIDE_TILT)
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        RecenterButton(follow, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), dark = true)
    }
}

private const val RIDE_ZOOM = 17.5
private const val RIDE_TILT = 55.0
