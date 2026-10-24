package com.personal.rando.ui

import androidx.compose.runtime.saveable.rememberSaveable
import com.personal.rando.ride.Period
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ride.PeriodTotal
import com.personal.rando.ride.RideStats
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.Animatable
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.rando.rando
import com.personal.rando.ride.Ride
import com.personal.rando.ui.glass.GlassIconButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import com.personal.rando.ui.glass.pressScale
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE = SimpleDateFormat("M月d日 EEE HH:mm", Locale.CHINA)

// ───────────────────────── 记录列表 ─────────────────────────

@Composable
fun RideListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onRiddenMap: () -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val rides by produceState<List<Ride>?>(null) { value = store.list() }
    RideList(rides, onBack, onOpen, onRiddenMap)
}

/** 骑行记录：一整块玻璃盖在地图上，最上面是总数，下面一条条往上浮出来 */
@Composable
fun RideList(rides: List<Ride>?, onBack: () -> Unit, onOpen: (Long) -> Unit, onRiddenMap: () -> Unit = {}, now: Long = System.currentTimeMillis()) {
    GlassPanel(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
                Spacer(Modifier.width(4.dp))
                Text("骑行记录", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            when {
                rides == null -> Unit
                rides.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有记录。在地图右边点橙色的 ▶ 开始记录，或者开始导航时会自动记录。",
                        color = InkSoft,
                        fontSize = 15.sp,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { StatsCard(rides, now, onRiddenMap) }
                    itemsIndexed(rides, key = { _, r -> r.id }) { i, ride -> RideRow(ride, i) { onOpen(ride.id) } }
                }
            }
        }
    }
}

/**
 * 统计：这周（这个月）骑了多少、和上周比，下面一排柱子是最近几周（几个月）每段的公里数。
 * 底下一行是全部合计，和「骑过的路」入口
 */
@Composable
private fun StatsCard(rides: List<Ride>, now: Long, onRiddenMap: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    val totals = remember(rides, period, now) { RideStats.recent(rides, period, if (period == Period.WEEK) 8 else 6, now) }
    val current = totals.last()
    val previous = totals.getOrNull(totals.size - 2)
    GlassPanel(Modifier.fillMaxWidth(), shape = GlassShapes.Card, tone = GlassTone.Tinted(RandoGreen)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(period.unit, Modifier.weight(1f), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Period.entries.forEach { p ->
                    val selected = p == period
                    Text(
                        p.label,
                        Modifier
                            .clip(GlassShapes.Pill)
                            .background(if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                            .clickable { period = p }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        color = if (selected) RandoGreenDeep else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                TotalStat(Format.distance(current.meters.toInt()), "${current.rides} 次")
                TotalStat(Format.clock(current.movingSeconds), "骑行时间")
                previous?.let { TotalStat(compare(current.meters - it.meters), "比上${period.label}") }
            }
            PeriodBars(totals, Modifier.padding(top = 14.dp).fillMaxWidth().height(120.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "全部 ${rides.size} 次 · ${Format.distance(rides.sumOf { it.distanceMeters }.toInt())}",
                    Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                GlassButton(onClick = onRiddenMap, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                    Text("骑过的路", color = RandoGreenDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 「+12.3 公里」「-800 米」「持平」 */
private fun compare(meters: Double): String = when {
    abs(meters) < 50 -> "持平"
    meters > 0 -> "+" + Format.distance(meters.toInt())
    else -> "-" + Format.distance((-meters).toInt())
}

/** 每周（月）一根柱子，最后一根是这周，亮一点；柱子从下往上长出来 */
@Composable
private fun PeriodBars(totals: List<PeriodTotal>, modifier: Modifier) {
    val grow = remember(totals) { Animatable(0f) }
    LaunchedEffect(totals) { grow.animateTo(1f, Motion.smooth()) }
    val max = totals.maxOf { it.meters }.coerceAtLeast(1.0)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        totals.forEachIndexed { i, t ->
            val last = i == totals.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (t.meters > 0) {
                    Text(String.format(Locale.US, "%.0f", t.meters / 1000), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight((t.meters / max * grow.value).toFloat().coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(Color.White.copy(alpha = if (last) 0.95f else 0.45f))
                )
                Text(t.label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TotalStat(value: String, label: String) {
    Column {
        // 数字大、单位小，三个并排也不折行
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.substringBefore(' '), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val unit = value.substringAfter(' ', "")
            if (unit.isNotEmpty()) Text(" $unit", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

/** 一条记录：错开一点时间从下面浮上来 */
@Composable
private fun RideRow(ride: Ride, index: Int, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        shown,
        enter = fadeIn(tween(300, delayMillis = 40 * index.coerceAtMost(8))) +
            slideInVertically(Motion.smooth()) { it / 2 },
    ) {
        val interaction = remember { MutableInteractionSource() }
        Column(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressed = 0.97f)
                .clip(GlassShapes.Card)
                .background(Color.White.copy(alpha = 0.55f))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ride.title, Modifier.weight(1f), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(DATE.format(Date(ride.startedAt)), color = InkSoft, fontSize = 13.sp)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SmallStat(Format.distance(ride.distanceMeters.toInt()), "距离")
                SmallStat(Format.clock(ride.movingSeconds), "骑行时间")
                SmallStat(Format.speed(ride.averageSpeed), "均速")
            }
        }
    }
}

@Composable
private fun SmallStat(value: String, label: String) {
    Column {
        Text(value, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = InkSoft, fontSize = 12.sp)
    }
}

// ───────────────────────── 记录详情 ─────────────────────────

@Composable
fun RideDetailScreen(id: Long, onBack: () -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val scope = rememberCoroutineScope()
    val ride by produceState<Ride?>(null, id) { value = store.get(id) }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = ride
    if (r != null) {
        RideDetail(r, onBack = onBack, onDelete = { confirmDelete = true }) { modifier -> TrackMap(r, modifier) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = GlassShapes.Sheet,
            containerColor = Color(0xF7F7FAF8),
            title = { Text("删除这条记录？") },
            text = { Text("删掉就找不回来了。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        store.delete(id)
                        onBack()
                    }
                }) { Text("删除", color = RandoRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/**
 * 轨迹地图铺满全屏，上面浮着玻璃标题条和底部的数据卡片。
 * [map] 单独传进来，截图测试里换成一张图
 */
@Composable
fun RideDetail(ride: Ride, onBack: () -> Unit, onDelete: () -> Unit, map: @Composable (Modifier) -> Unit) {
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF2F0EB))) {
            map(Modifier.fillMaxSize().hazeSource(haze))
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
                Spacer(Modifier.width(10.dp))
                GlassPanel(Modifier.weight(1f), shape = GlassShapes.Pill) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        Text(ride.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(DATE.format(Date(ride.startedAt)), color = InkSoft, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = RandoRed) }
            }
            GlassPanel(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row {
                        DetailStat(Format.distance(ride.distanceMeters.toInt()), "距离", Modifier.weight(1f))
                        DetailStat(Format.clock(ride.movingSeconds), "骑行时间", Modifier.weight(1f))
                        DetailStat("${ride.ascendMeters.toInt()} 米", "爬升", Modifier.weight(1f))
                    }
                    Row {
                        DetailStat(Format.speed(ride.averageSpeed), "均速", Modifier.weight(1f))
                        DetailStat(Format.speed(ride.maxSpeed), "最快", Modifier.weight(1f))
                        DetailStat(Format.clock((ride.endedAt - ride.startedAt) / 1000), "全程用时", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 数字大、单位小：「19.8 公里/时」三个并排也放得下 */
@Composable
private fun DetailStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.substringBefore(' '), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            val unit = value.substringAfter(' ', "")
            if (unit.isNotEmpty()) Text(" $unit", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, color = InkSoft, fontSize = 12.sp)
    }
}

/** 地图上画出这次的轨迹，镜头框住全程（上下给玻璃卡片留出位置） */
@Composable
private fun TrackMap(ride: Ride, modifier: Modifier) {
    val mapView = rememberMapView(textureMode = true)
    val density = LocalDensity.current
    var style by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(MapLayers.STYLE_URL) { s ->
                MapLayers.install(s)
                style = s
                val track = ride.points.map { it.location }
                MapLayers.showTrack(s, track)
                if (track.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(track.map { LatLng(it.lat, it.lon) }).build()
                    with(density) {
                        m.moveCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                bounds, 40.dp.roundToPx(), 120.dp.roundToPx(), 40.dp.roundToPx(), 260.dp.roundToPx(),
                            )
                        )
                    }
                }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

// ───────────────────────── 骑过的路 ─────────────────────────

/** 所有记录的轨迹叠在一张地图上，看看哪些路骑过、哪些常骑 */
@Composable
fun RiddenMapScreen(onBack: () -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val rides by produceState<List<Ride>?>(null) { value = store.list() }
    val list = rides ?: return
    RiddenMap(list, onBack) { modifier -> RiddenTracks(list, modifier) }
}

@Composable
fun RiddenMap(rides: List<Ride>, onBack: () -> Unit, map: @Composable (Modifier) -> Unit) {
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF2F0EB))) {
            map(Modifier.fillMaxSize().hazeSource(haze))
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
                Spacer(Modifier.width(10.dp))
                GlassPanel(Modifier.weight(1f), shape = GlassShapes.Pill) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        Text("骑过的路", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${rides.size} 次 · ${Format.distance(rides.sumOf { it.distanceMeters }.toInt())} · 颜色越深骑得越多",
                            color = InkSoft,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiddenTracks(rides: List<Ride>, modifier: Modifier) {
    val mapView = rememberMapView(textureMode = true)
    val density = LocalDensity.current
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(MapLayers.STYLE_URL) { s ->
                val tracks = rides.map { r -> r.points.map { it.location } }
                MapLayers.showRidden(s, tracks)
                val all = tracks.flatten()
                if (all.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(all.map { LatLng(it.lat, it.lon) }).build()
                    with(density) {
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40.dp.roundToPx(), 120.dp.roundToPx(), 40.dp.roundToPx(), 40.dp.roundToPx()))
                    }
                }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 给预览、截图用的圆角，和玻璃面板一致 */
val CardShape = RoundedCornerShape(24.dp)
