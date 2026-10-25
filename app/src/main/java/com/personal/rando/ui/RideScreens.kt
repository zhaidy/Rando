package com.personal.rando.ui

import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.ride.HeatLevel
import com.personal.rando.ride.RideHeat
import com.personal.rando.ride.Review
import com.personal.rando.ride.ReviewSpan
import com.personal.rando.ride.Reviews
import androidx.compose.ui.text.style.TextAlign
import com.personal.rando.ride.HeatPiece
import com.personal.rando.places.PcnCoverage
import com.personal.rando.places.PcnProgress
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Share
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.rando.ride.Settings
import com.personal.rando.ride.Calories
import com.personal.rando.ride.RouteRecord
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

private val DATE get() = SimpleDateFormat(tr("M月d日 EEE HH:mm", "EEE d MMM HH:mm"), Lang.locale)

/** 英文的「3 rides」 */
private fun ridesEn(n: Int): String = if (n == 1) "1 ride" else "$n rides"

// ───────────────────────── 记录列表 ─────────────────────────

@Composable
fun RideListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onRiddenMap: () -> Unit, onReview: () -> Unit = {}) {
    val store = LocalContext.current.rando.session.rides
    val rides by produceState<List<Ride>?>(null) { value = store.list() }
    val context = LocalContext.current
    val preview = rememberSharePreview()
    RideList(rides, onBack, onOpen, onRiddenMap, onReview = onReview, onShareToday = { today -> preview { ShareCard.dayCard(context, today) } })
}

/** 骑行记录：一整块玻璃盖在地图上，最上面是总数，下面一条条往上浮出来 */
@Composable
fun RideList(
    rides: List<Ride>?,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onRiddenMap: () -> Unit = {},
    now: Long = System.currentTimeMillis(),
    onReview: () -> Unit = {},
    /** 分享今天骑的几次；今天没骑就不显示按钮 */
    onShareToday: (List<Ride>) -> Unit = {},
) {
    val today = remember(rides, now) { rides?.let { RideStats.today(it, now) }.orEmpty() }
    GlassPanel(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"), tint = Ink) }
                Spacer(Modifier.width(4.dp))
                Text(tr("骑行记录", "Rides"), Modifier.weight(1f), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                if (today.isNotEmpty()) {
                    GlassButton(onClick = { onShareToday(today) }, tone = GlassTone.Tinted(RandoGreen), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("分享今天", "Share today"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            when {
                rides == null -> Unit
                rides.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("还没有记录。在地图右边点橙色的 ▶ 开始记录，或者开始导航时会自动记录。", "No rides yet. Tap the orange ▶ on the right of the map to start recording, or start navigating and it records automatically."),
                        color = InkSoft,
                        fontSize = 15.sp,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { StatsCard(rides, now, onRiddenMap, onReview) }
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
private fun StatsCard(rides: List<Ride>, now: Long, onRiddenMap: () -> Unit, onReview: () -> Unit) {
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
                TotalStat(Format.distance(current.meters.toInt()), tr("${current.rides} 次", ridesEn(current.rides)))
                TotalStat(Format.clock(current.movingSeconds), tr("骑行时间", "Ride time"))
                previous?.let { TotalStat(compare(current.meters - it.meters), tr("比上${period.label}", "vs last ${period.label.lowercase()}")) }
            }
            PeriodBars(totals, Modifier.padding(top = 14.dp).fillMaxWidth().height(120.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("全部 ${rides.size} 次", "All ${ridesEn(rides.size)}") + " · ${Format.distance(rides.sumOf { it.distanceMeters }.toInt())}",
                    Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                GlassButton(onClick = onReview, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(tr("回顾", "Review"), color = RandoGreenDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                GlassButton(onClick = onRiddenMap, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(tr("骑过的路", "Ridden roads"), color = RandoGreenDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 「+12.3 公里」「-800 米」「持平」 */
private fun compare(meters: Double): String = when {
    abs(meters) < 50 -> tr("持平", "Same")
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
                SmallStat(Format.distance(ride.distanceMeters.toInt()), tr("距离", "Distance"))
                SmallStat(Format.clock(ride.movingSeconds), tr("骑行时间", "Ride time"))
                SmallStat(Format.speed(ride.averageSpeed), tr("均速", "Avg speed"))
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

/**
 * 分享按钮按下去做什么：那天只骑了这一次就直接出预览；
 * 骑了好几次先问一下，分享这一次还是那天全部
 */
@Composable
fun rememberRideShare(ride: Ride, record: String?): () -> Unit {
    val context = LocalContext.current
    val preview = rememberSharePreview()
    val store = context.rando.session.rides
    val day by produceState(listOf(ride), ride) {
        value = RideStats.today(store.list().filter { it.id != ride.id } + ride, ride.startedAt)
    }
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        val isToday = remember(ride) { RideStats.today(listOf(ride), System.currentTimeMillis()).isNotEmpty() }
        AlertDialog(
            onDismissRequest = { asking = false },
            shape = GlassShapes.Sheet,
            containerColor = Color(0xF7F7FAF8),
            title = { Text(tr("分享哪些？", "Share what?")) },
            text = {
                Text(
                    tr(
                        "${if (isToday) "今天" else "这天"}骑了 ${day.size} 次，一共 ${Format.distance(day.sumOf { it.distanceMeters }.toInt())}。",
                        "${ridesEn(day.size)} ${if (isToday) "today" else "that day"}, ${Format.distance(day.sumOf { it.distanceMeters }.toInt())} in total.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    preview { ShareCard.dayCard(context, day) }
                }) { Text(if (isToday) tr("今天全部", "All of today") else tr("这天全部", "Whole day"), color = RandoGreen) }
            },
            dismissButton = {
                TextButton(onClick = {
                    asking = false
                    preview { ShareCard.card(context, ride, record) }
                }) { Text(tr("只分享这次", "Just this ride")) }
            },
        )
    }
    return { if (day.size > 1) asking = true else preview { ShareCard.card(context, ride, record) } }
}

// ───────────────────────── 记录详情 ─────────────────────────

@Composable
fun RideDetailScreen(id: Long, onBack: () -> Unit) {
    val store = LocalContext.current.rando.session.rides
    val scope = rememberCoroutineScope()
    val ride by produceState<Ride?>(null, id) { value = store.get(id) }
    val weight = LocalContext.current.rando.settings.settings.collectAsStateWithLifecycle().value.weightKg
    val record by produceState<String?>(null, ride) { value = ride?.let { r -> RouteRecord.of(r, store.list())?.text } }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = ride
    if (r != null) {
        RideDetail(
            r, onBack = onBack, onDelete = { confirmDelete = true }, record = record,
            onShare = rememberRideShare(r, record),
            weightKg = weight,
        ) { modifier -> TrackMap(r, modifier) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = GlassShapes.Sheet,
            containerColor = Color(0xF7F7FAF8),
            title = { Text(tr("删除这条记录？", "Delete this ride?")) },
            text = { Text(tr("删掉就找不回来了。", "This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        store.delete(id)
                        onBack()
                    }
                }) { Text(tr("删除", "Delete"), color = RandoRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

/**
 * 轨迹地图铺满全屏，上面浮着玻璃标题条和底部的数据卡片。
 * [map] 单独传进来，截图测试里换成一张图
 */
@Composable
fun RideDetail(
    ride: Ride,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    /** 骑过的路：和其他几次比的那句 */
    record: String? = null,
    onShare: () -> Unit = {},
    weightKg: Double = Settings.DEFAULT_WEIGHT,
    map: @Composable (Modifier) -> Unit,
) {
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
                GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"), tint = Ink) }
                Spacer(Modifier.width(10.dp))
                GlassPanel(Modifier.weight(1f), shape = GlassShapes.Pill) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        Text(ride.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            DATE.format(Date(ride.startedAt)) + tr(" · 全程 ", " · Total ") + Format.clock((ride.endedAt - ride.startedAt) / 1000),
                            color = InkSoft, fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = tr("分享", "Share"), tint = Ink) }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = tr("删除", "Delete"), tint = RandoRed) }
            }
            GlassPanel(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    record?.let { Text(it, color = RandoGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Row {
                        DetailStat(Format.distance(ride.distanceMeters.toInt()), tr("距离", "Distance"), Modifier.weight(1f))
                        DetailStat(Format.clock(ride.movingSeconds), tr("骑行时间", "Ride time"), Modifier.weight(1f))
                        DetailStat(tr("${ride.ascendMeters.toInt()} 米", "${ride.ascendMeters.toInt()} m"), tr("爬升", "Climb"), Modifier.weight(1f))
                    }
                    Row {
                        DetailStat(Format.speed(ride.averageSpeed), tr("均速", "Avg speed"), Modifier.weight(1f))
                        DetailStat(Format.speed(ride.maxSpeed), tr("最快", "Top speed"), Modifier.weight(1f))
                        DetailStat(tr("${Calories.of(ride, weightKg)} 千卡", "${Calories.of(ride, weightKg)} kcal"), tr("消耗", "Burned"), Modifier.weight(1f))
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

/** 骑过的路有两种看法：常骑的路（热力），和全岛公园连道点亮了多少 */
enum class RiddenMode(private val zh: String, private val en: String) {
    HEAT("常骑的路", "Frequent roads"), PCN("公园连道", "PCN");

    val label: String get() = tr(zh, en)
}

/** 所有记录的轨迹叠在一张地图上，看看哪些路骑过、哪些常骑；也可以看公园连道点亮了多少 */
@Composable
fun RiddenMapScreen(onBack: () -> Unit) {
    val app = LocalContext.current.rando
    val rides by produceState<List<Ride>?>(null) { value = app.session.rides.list() }
    val list = rides ?: return
    var mode by rememberSaveable { mutableStateOf(RiddenMode.HEAT) }
    // 对格子要点时间，放到后台算
    val pcn by produceState<PcnProgress?>(null, list) {
        value = withContext(Dispatchers.Default) { PcnCoverage.of(app.pcn, list.map { r -> r.points.map { it.location } }) }
    }
    RiddenMap(list, onBack, mode, { mode = it }, pcn) { modifier -> RiddenTracks(list, mode, pcn, modifier) }
}

@Composable
fun RiddenMap(
    rides: List<Ride>,
    onBack: () -> Unit,
    mode: RiddenMode = RiddenMode.HEAT,
    onMode: (RiddenMode) -> Unit = {},
    pcn: PcnProgress? = null,
    map: @Composable (Modifier) -> Unit,
) {
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
                GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"), tint = Ink) }
                Spacer(Modifier.width(10.dp))
                GlassPanel(Modifier.weight(1f), shape = GlassShapes.Card) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("骑过的路", "Ridden roads"), Modifier.weight(1f), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            RiddenMode.entries.forEach { m ->
                                val selected = m == mode
                                Text(
                                    m.label,
                                    Modifier
                                        .clip(GlassShapes.Pill)
                                        .background(if (selected) RandoGreen else Color.Transparent)
                                        .clickable { onMode(m) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = if (selected) Color.White else Ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        when (mode) {
                            RiddenMode.HEAT -> {
                                Text(
                                    tr("${rides.size} 次", ridesEn(rides.size)) + " · ${Format.distance(rides.sumOf { it.distanceMeters }.toInt())}",
                                    color = InkSoft,
                                    fontSize = 12.sp,
                                )
                                // 图例：每档一个色块
                                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HeatLevel.entries.forEach { level -> LegendItem(level.color, level.label) }
                                }
                            }
                            RiddenMode.PCN -> {
                                Text(
                                    pcn?.let { tr("全岛公园连道骑过 ", "Ridden ") + Format.share(it.share) + tr("", " of all PCNs") + " · ${Format.distance(it.riddenMeters.toInt())} / ${Format.distance(it.totalMeters.toInt())}" }
                                        ?: tr("正在算…", "Calculating…"),
                                    color = InkSoft,
                                    fontSize = 12.sp,
                                )
                                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LegendItem(MapLayers.PCN_RIDDEN, tr("骑过", "Ridden"))
                                    LegendItem(MapLayers.PCN_NOT_YET, tr("还没骑", "Not yet"))
                                }
                            }
                        }
                    }
                }
            }
            if (mode == RiddenMode.PCN && pcn != null) {
                LoopsCard(pcn, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
            }
        }
    }
}

@Composable
private fun LegendItem(color: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(GlassShapes.Pill).background(kindColor(color)))
        Spacer(Modifier.width(4.dp))
        Text(label, color = InkSoft, fontSize = 11.sp)
    }
}

/** 6 个大环各骑了多少，骑得多的在前 */
@Composable
private fun LoopsCard(pcn: PcnProgress, modifier: Modifier) {
    GlassPanel(modifier.fillMaxWidth(), shape = GlassShapes.Card) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pcn.loops.sortedByDescending { it.share }.forEach { p ->
                Column {
                    Row {
                        Text(p.loop.label, Modifier.weight(1f), color = Ink, fontSize = 14.sp)
                        Text("${Format.share(p.share)} · " + tr("共 ${Format.distance(p.totalMeters.toInt())}", "of ${Format.distance(p.totalMeters.toInt())}"), color = InkSoft, fontSize = 13.sp)
                    }
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(5.dp).clip(GlassShapes.Pill).background(Color(0x22000000))) {
                        Box(
                            Modifier
                                .fillMaxWidth(p.share.toFloat().coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(GlassShapes.Pill)
                                .background(kindColor(MapLayers.PCN_RIDDEN))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiddenTracks(rides: List<Ride>, mode: RiddenMode, pcn: PcnProgress?, modifier: Modifier) {
    val mapView = rememberMapView(textureMode = true)
    val density = LocalDensity.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val tracks = remember(rides) { rides.map { r -> r.points.map { it.location } } }
    val heat by produceState<List<HeatPiece>?>(null, tracks) { value = withContext(Dispatchers.Default) { RideHeat.pieces(tracks) } }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(MapLayers.STYLE_URL) { s ->
                map = m
                style = s
            }
        }
    }
    LaunchedEffect(style, mode, heat, pcn) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val shown = when (mode) {
            RiddenMode.HEAT -> {
                MapLayers.showPcn(s, emptyList())
                MapLayers.showRidden(s, heat.orEmpty())
                tracks.flatten()
            }
            RiddenMode.PCN -> {
                MapLayers.showRidden(s, emptyList())
                MapLayers.showPcn(s, pcn?.pieces.orEmpty())
                pcn?.pieces?.flatMap { it.points }.orEmpty()
            }
        }
        if (shown.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(shown.map { LatLng(it.lat, it.lon) }).build()
            val bottom = if (mode == RiddenMode.PCN) 300 else 40
            with(density) {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40.dp.roundToPx(), 140.dp.roundToPx(), 40.dp.roundToPx(), bottom.dp.roundToPx()))
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 给预览、截图用的圆角，和玻璃面板一致 */
val CardShape = RoundedCornerShape(24.dp)

// ───────────────────────── 月度、年度回顾 ─────────────────────────

@Composable
fun ReviewScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val app = LocalContext.current.rando
    val rides by produceState<List<Ride>?>(null) { value = app.session.rides.list() }
    val list = rides ?: return
    var span by rememberSaveable { mutableStateOf(ReviewSpan.MONTH) }
    var back by rememberSaveable { mutableStateOf(0) }
    val now = remember { System.currentTimeMillis() }
    // 新点亮的公园连道要对一遍格子，放到后台算
    val review by produceState<Review?>(null, list, span, back) {
        value = Reviews.of(list, span, now, back)
        value = withContext(Dispatchers.Default) { Reviews.of(list, span, now, back, loops = app.pcn) }
    }
    ReviewPage(review, span, onBack, onOpen, onSpan = { span = it; back = 0 }, onPrevious = { back++ }, onNext = { if (back > 0) back-- })
}

/** 回顾：上面选月 / 年、往前往后翻，下面是这段时间的数字和亮点 */
@Composable
fun ReviewPage(
    review: Review?,
    span: ReviewSpan,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onSpan: (ReviewSpan) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    GlassPanel(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回", "Back"), tint = Ink) }
                Spacer(Modifier.width(4.dp))
                Text(tr("回顾", "Review"), Modifier.weight(1f), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                ReviewSpan.entries.forEach { s ->
                    val selected = s == span
                    Text(
                        s.label,
                        Modifier
                            .clip(GlassShapes.Pill)
                            .background(if (selected) RandoGreen else Color.Transparent)
                            .clickable { onSpan(s) }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        color = if (selected) Color.White else Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val r = review ?: return@Column
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTap(onClick = onPrevious) { Text("‹", color = Ink, fontSize = 26.sp) }
                        Text(r.title, Modifier.weight(1f), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        IconTap(onClick = onNext) { Text("›", color = Ink, fontSize = 26.sp) }
                    }
                }
                if (r.empty) {
                    item {
                        Text(
                            tr("这${if (span == ReviewSpan.MONTH) "个月" else "一年"}还没有骑行记录", "No rides this ${if (span == ReviewSpan.MONTH) "month" else "year"}"),
                            Modifier.fillMaxWidth().padding(32.dp),
                            color = InkSoft,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    return@LazyColumn
                }
                item { ReviewTotals(r) }
                item { ReviewFacts(r) }
                r.longest?.let { ride -> item { Highlight(tr("最远的一次", "Longest ride"), ride, Format.distance(ride.distanceMeters.toInt())) { onOpen(ride.id) } } }
                r.fastest?.takeIf { it.id != r.longest?.id || r.rides == 1 }?.let { ride ->
                    item { Highlight(tr("最快的一次", "Fastest ride"), ride, Format.speed(ride.averageSpeed)) { onOpen(ride.id) } }
                }
            }
        }
    }
}

@Composable
private fun ReviewTotals(r: Review) {
    GlassPanel(Modifier.fillMaxWidth(), shape = GlassShapes.Card, tone = GlassTone.Tinted(RandoGreen)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                TotalStat(Format.distance(r.meters.toInt()), tr("${r.rides} 次", ridesEn(r.rides)))
                TotalStat(Format.clock(r.movingSeconds), tr("骑行时间", "Ride time"))
                TotalStat(tr("${r.ascendMeters.toInt()} 米", "${r.ascendMeters.toInt()} m"), tr("爬升", "Climb"))
            }
            val unit = if (r.span == ReviewSpan.MONTH) tr("上个月", "last month") else tr("去年", "last year")
            Text(
                if (r.previousMeters > 0) tr("比$unit ${compare(r.meters - r.previousMeters)}", "${compare(r.meters - r.previousMeters)} vs $unit")
                else tr("${unit}没有记录", "No rides $unit"),
                Modifier.padding(top = 10.dp),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
        }
    }
}

/** 骑了几天、连着几天、最常什么时候骑、新点亮多少公园连道 */
@Composable
private fun ReviewFacts(r: Review) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Fact(tr("骑车的日子", "Days ridden"), tr("${r.days} 天", daysEn(r.days)))
        if (r.streak >= 2) Fact(tr("最长连着骑", "Longest streak"), tr("${r.streak} 天", daysEn(r.streak)))
        r.favoriteTime?.let { Fact(tr("最常出门", "Usual time"), it) }
        Fact(tr("平均每次", "Average ride"), Format.distance((r.meters / r.rides).toInt()))
        r.newPcnMeters?.let { Fact(tr("新点亮的公园连道", "New PCN ridden"), if (it >= 50) Format.distance(it.toInt()) else tr("没有", "None")) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = InkSoft, fontSize = 15.sp)
        Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Highlight(label: String, ride: Ride, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(Color.White.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = InkSoft, fontSize = 13.sp)
            Text("${DATE.format(Date(ride.startedAt))} ${ride.title}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(value, color = RandoGreenDeep, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

private fun daysEn(n: Int): String = if (n == 1) "1 day" else "$n days"
