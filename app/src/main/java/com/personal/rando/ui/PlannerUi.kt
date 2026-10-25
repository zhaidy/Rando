package com.personal.rando.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.personal.rando.routing.Pace
import androidx.compose.material.icons.filled.ArrowDropDown
import com.personal.rando.places.SavedRoute
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LinearProgressIndicator
import com.personal.rando.ride.Settings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.personal.rando.places.ParkedBike
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.rando.nav.Geo
import com.personal.rando.nav.VoiceChoice
import com.personal.rando.nav.VoiceOption
import com.personal.rando.routing.LatLon
import androidx.compose.material3.Switch
import com.personal.rando.places.MrtHome
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.places.SavedPlace
import com.personal.rando.ride.RecordingState
import com.personal.rando.ride.Ride
import com.personal.rando.routing.ReturnTrip
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.CrossingType
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.Route
import com.personal.rando.routing.RoutePrefs
import com.personal.rando.routing.StructureKind
import com.personal.rando.routing.breakdown
import com.personal.rando.routing.crossings
import com.personal.rando.routing.pushBetweenEnds
import com.personal.rando.routing.structures
import com.personal.rando.routing.elevationProfile
import com.personal.rando.nav.DaylightAlerts
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.AmenityType
import com.personal.rando.weather.RouteWeather
import com.personal.rando.weather.RouteWind
import com.personal.rando.places.CoveredStretch
import com.personal.rando.routing.RideTime
import com.personal.rando.routing.shadeShare
import com.personal.rando.routing.litShare
import com.personal.rando.weather.Climate
import com.personal.rando.routing.steepestClimb
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import com.personal.rando.search.Place
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassIconButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.Motion
import com.personal.rando.ui.glass.PrimaryButton
import com.personal.rando.ui.glass.liquidGlass
import com.personal.rando.ui.glass.pressScale

/** 玻璃上的文字颜色：深墨绿，比纯黑柔和 */
val Ink = Color(0xFF13241B)
val InkSoft = Color(0xFF4F6157)

fun kindColor(hex: String) = Color(android.graphics.Color.parseColor(hex))

// ───────────────────────── 顶部：搜索框 ─────────────────────────

@Composable
fun SearchBar(
    query: String,
    hint: String,
    results: List<Place>,
    build: Long,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
    onOpenRides: () -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    onOfflineMaps: () -> Unit = {},
    onSettings: () -> Unit = {},
    onMrtHome: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .liquidGlass(GlassShapes.Card)
            .animateContentSize(Motion.smooth()),
    ) {
        Row(Modifier.height(56.dp).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = InkSoft)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(hint, color = InkSoft, fontSize = 17.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 17.sp),
                    cursorBrush = SolidColor(RandoGreen),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconTap(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = "清空", tint = InkSoft) }
            } else {
                Box {
                    IconTap(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = InkSoft) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = GlassShapes.Card,
                        containerColor = Color(0xF7F7FAF8),
                    ) {
                        DropdownMenuItem(text = { Text("骑行记录") }, onClick = { menuOpen = false; onOpenRides() })
                        DropdownMenuItem(text = { Text("离线地图") }, onClick = { menuOpen = false; onOfflineMaps() })
                        DropdownMenuItem(text = { Text("坐 MRT 回家") }, onClick = { menuOpen = false; onMrtHome() })
                        DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onSettings() })
                        DropdownMenuItem(text = { Text("检查更新") }, onClick = { menuOpen = false; onCheckUpdate() })
                        DropdownMenuItem(text = { Text("当前版本 build $build") }, onClick = {}, enabled = false)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = results.isNotEmpty(),
            enter = expandVertically(Motion.smooth()) + fadeIn(),
            exit = shrinkVertically(Motion.smooth()) + fadeOut(),
        ) {
            LazyColumn(Modifier.heightIn(max = 320.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(results) { place ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(place) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(place.name, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(place.distanceMeters?.let { Format.distance(it) }, place.detail.ifEmpty { null }).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(detail, color = InkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** 没有底色的小图标按钮，按下缩一下 */
@Composable
fun IconTap(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .pressScale(interaction, pressed = 0.85f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ───────────────────────── 路线偏好 ─────────────────────────

/**
 * 路线偏好：一排玻璃胶囊。公园连道的几个开关收在它旁边的「偏好」里，
 * 选中公园连道时展开出来，点开是个弹窗
 */
@Composable
fun PrefsRow(prefs: RoutePrefs, onChange: (RoutePrefs) -> Unit, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf(false) }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BikeProfile.entries.forEach { profile ->
            GlassChip(profile.label, selected = profile == prefs.profile) {
                if (profile != prefs.profile) onChange(prefs.copy(profile = profile))
            }
            if (profile == BikeProfile.PCN) {
                AnimatedVisibility(
                    visible = prefs.profile == BikeProfile.PCN,
                    enter = expandHorizontally(Motion.bouncy()) + fadeIn(),
                    exit = shrinkHorizontally(Motion.smooth()) + fadeOut(),
                ) {
                    PcnPrefsChip(prefs) { dialog = true }
                }
            }
        }
    }
    if (dialog) PcnPrefsDialog(prefs, onSave = { dialog = false; if (it != prefs) onChange(it) }, onDismiss = { dialog = false })
}

/** 「偏好」胶囊：打开了「不上马路」「不走天桥」时直接写出来，免得忘了开着 */
@Composable
private fun PcnPrefsChip(prefs: RoutePrefs, onClick: () -> Unit) {
    val avoiding = listOfNotNull("不上马路".takeIf { prefs.avoidRoads }, "不走天桥".takeIf { prefs.avoidBridges })
    val on = avoiding.isNotEmpty()
    GlassButton(
        onClick = onClick,
        tone = if (on) GlassTone.Tinted(RandoGreenDeep) else GlassTone.Light,
        contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Text(avoiding.joinToString("、").ifEmpty { "偏好" }, color = if (on) Color.White else Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.ArrowDropDown, contentDescription = "路线偏好", tint = if (on) Color.White else InkSoft, modifier = Modifier.size(20.dp))
    }
}

/** 公园连道的偏好：按「好」才重新算路 */
@Composable
fun PcnPrefsDialog(prefs: RoutePrefs, onSave: (RoutePrefs) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(prefs) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("公园连道偏好") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrefSwitch("不上马路", "尽量避开马路，有人行道的走人行道", edited.avoidRoads) { edited = edited.copy(avoidRoads = it) }
                PrefSwitch("不走天桥", "绕开人行天桥，公园连道、自行车道上的桥照走", edited.avoidBridges) { edited = edited.copy(avoidBridges = it) }
                PrefSwitch("中午挑有树荫的路", "11 点到 4 点算路时，宁可绕一点也走树多的路", edited.shadeAtNoon) { edited = edited.copy(shadeAtNoon = it) }
                PrefSwitch("天黑挑有路灯的路", "天黑或快黑时，少走没灯的小路", edited.litAtNight) { edited = edited.copy(litAtNight = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(edited) }) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PrefSwitch(label: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, color = Ink, fontSize = 16.sp)
            Text(detail, color = InkSoft, fontSize = 13.sp)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val text by animateColorAsState(if (selected) Color.White else Ink, Motion.smooth(), label = "chip")
    GlassButton(
        onClick = onClick,
        tone = if (selected) GlassTone.Tinted(RandoGreen) else GlassTone.Light,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, color = text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** 橙色提示条，比如「长按地图选起点」；有 [onCancel] 时右边带个「取消」 */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null) {
    GlassPanel(modifier, shape = GlassShapes.Pill, tone = GlassTone.Tinted(RandoOrange)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f, fill = false).padding(start = 16.dp, end = if (onCancel == null) 16.dp else 4.dp, top = 10.dp, bottom = 10.dp), color = Color.White, fontSize = 14.sp)
            if (onCancel != null) {
                Row(
                    Modifier
                        .clip(GlassShapes.Pill)
                        .clickable(onClick = onCancel)
                        .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("取消", Modifier.padding(start = 2.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ───────────────────────── 右侧按钮 ─────────────────────────

/** 右侧一根竖着的玻璃条：骑行记录、开始记录、回到我的位置 */
@Composable
fun SideRail(
    showLocation: Boolean,
    showRecord: Boolean,
    onRides: () -> Unit,
    onRecord: () -> Unit,
    onLocate: () -> Unit,
    /** 一起骑；null 就不显示 */
    onGroup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 语音指令；null 就不显示 */
    onVoice: (() -> Unit)? = null,
    listening: Boolean = false,
) {
    Column(
        modifier
            .liquidGlass(GlassShapes.Pill)
            .animateContentSize(Motion.bouncy())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTap(onClick = onRides) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "骑行记录", tint = RandoGreen) }
        if (showLocation && showRecord) {
            IconTap(onClick = onRecord) { Icon(Icons.Default.PlayArrow, contentDescription = "开始记录", tint = RandoOrange) }
        }
        onGroup?.let {
            IconTap(onClick = it) { Icon(Icons.Default.Person, contentDescription = "一起骑", tint = Color(0xFF6A3FA0)) }
        }
        onVoice?.let {
            IconTap(onClick = it) { Icon(MicIcon, contentDescription = "语音指令", tint = if (listening) RandoRed else RandoBlue) }
        }
        if (showLocation) {
            IconTap(onClick = onLocate) { Icon(Icons.Default.LocationOn, contentDescription = "回到我的位置", tint = RandoGreen) }
        }
    }
}

// ───────────────────────── 底部：规划卡片 ─────────────────────────

/** 规划卡片上的各种操作 */
class PlannerActions(
    val onChooseSaved: (SavedPlace) -> Unit = {},
    val onToggleSave: (Place, SavedPlace?) -> Unit = { _, _ -> },
    val onClear: () -> Unit = {},
    val onPickStart: () -> Unit = {},
    val onUseMyLocation: () -> Unit = {},
    val onAddVia: () -> Unit = {},
    val onRemoveVia: (Place) -> Unit = {},
    val onLoop: (km: Int) -> Unit = {},
    val onAnotherLoop: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onShowOnMap: (NoteTarget) -> Unit = {},
    val onPace: (Pace) -> Unit = {},
    /** 换成第几条可选的路线 */
    val onChooseOption: (Int) -> Unit = {},
    /** 收藏现在这条路线（弹框起名字） */
    val onSaveRoute: () -> Unit = {},
    val onChooseRoute: (SavedRoute) -> Unit = {},
    /** 长按收藏的路线：删掉 */
    val onRemoveRoute: (SavedRoute) -> Unit = {},
    /** 点了「我的车」 */
    val onParked: () -> Unit = {},
)

/** 卡片顶上那行：还没选、去某个地方、骑一圈 */
private sealed interface SheetHeader {
    data object Empty : SheetHeader
    data class Destination(val place: Place) : SheetHeader
    data class Loop(val km: Int) : SheetHeader
}

/**
 * 底部的玻璃卡片。内容在「选目的地」「定位中」「算路中」「算好了」「失败」之间切换时
 * 交叉淡入并带着尺寸一起形变；内容太多时卡片里可以上下滑
 */
@Composable
fun PlannerSheet(
    state: PlannerState,
    saved: List<SavedPlace>,
    actions: PlannerActions,
    modifier: Modifier = Modifier,
    savedRoutes: List<SavedRoute> = emptyList(),
    /** 车停在哪儿、我在哪儿（算离车多远） */
    parked: ParkedBike? = null,
    here: LatLon? = null,
) {
    val header = when {
        state.loop != null -> SheetHeader.Loop(state.loop.targetMeters / 1000)
        state.destination != null -> SheetHeader.Destination(state.destination)
        else -> SheetHeader.Empty
    }
    // 像谷歌地图那样：卡片上哪儿都能拖，往下拖收起、往上拖展开。
    // 下面的内容能上下滑：往上先把卡片拉满再滑内容，往下先滑回顶再收卡片
    var size by rememberSaveable { mutableStateOf(SheetSize.HALF) }
    val screen = LocalConfiguration.current.screenHeightDp
    val maxHeight = (screen * if (size == SheetSize.FULL) 0.75f else 0.6f).dp
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }
    val settle = {
        val step = with(density) { 40.dp.toPx() }
        size = when {
            drag > step -> size.smaller()
            drag < -step -> size.bigger()
            else -> size
        }
        drag = 0f
    }
    val dragState = rememberDraggableState { drag += it }
    val sheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0 || size == SheetSize.FULL) return Offset.Zero
                drag += available.y
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0) return Offset.Zero
                drag += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag == 0f) return Velocity.Zero
                settle()
                return available
            }
        }
    }
    GlassPanel(modifier.fillMaxWidth().heightIn(max = maxHeight).animateContentSize(Motion.smooth())) {
        Column(
            Modifier
                .nestedScroll(sheetScroll)
                .draggable(dragState, Orientation.Vertical, onDragStarted = { drag = 0f }, onDragStopped = { settle() })
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
          Column {
            DragHandle { size = if (size == SheetSize.PEEK) SheetSize.HALF else SheetSize.PEEK }
            AnimatedContent(
                targetState = header,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(Motion.smooth()) { it / 4 })
                        .togetherWith(fadeOut(tween(120)))
                        .using(SizeTransform(clip = false) { _, _ -> Motion.smooth() })
                },
                label = "destination",
            ) { h ->
                when (h) {
                    SheetHeader.Empty -> Column {
                        Text("想去哪儿骑？", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("搜索目的地，或者长按地图选一个点", color = InkSoft, fontSize = 14.sp)
                    }
                    is SheetHeader.Destination -> {
                        val dest = h.place
                        val savedHere = saved.firstOrNull { it.isAt(dest.location) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dest.name,
                                Modifier.weight(1f),
                                color = Ink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StarButton(savedHere != null) { actions.onToggleSave(dest, savedHere) }
                            IconTap(onClick = actions.onClear) { Icon(Icons.Default.Close, contentDescription = "取消路线", tint = InkSoft) }
                        }
                    }
                    is SheetHeader.Loop -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("骑一圈 · 约 ${h.km} 公里", Modifier.weight(1f), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinkText("换一条", actions.onAnotherLoop)
                        IconTap(onClick = actions.onClear) { Icon(Icons.Default.Close, contentDescription = "取消路线", tint = InkSoft) }
                    }
                }
            }
          }
          if (size == SheetSize.PEEK) {
            PeekLine(state, header, actions.onStart)
          } else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (header == SheetHeader.Empty) {
                parked?.let { ParkedRow(it, here, actions.onParked) }
                EmptyExtras(saved, savedRoutes, actions)
            }
            StartLine(state.start, state.pickingStart, actions.onPickStart, actions.onUseMyLocation)
            if (state.destination != null) ViaLines(state.via, state.pickingVia, actions.onAddVia, actions.onRemoveVia)
            if (header != SheetHeader.Empty) {
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = state.route,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(Motion.smooth(), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> Motion.smooth() })
                    },
                    contentKey = { it::class },
                    label = "route",
                ) { r ->
                    when (r) {
                        RouteState.Idle -> Spacer(Modifier.height(0.dp))
                        RouteState.WaitingForLocation -> Waiting("正在定位，定位到就开始算路")
                        RouteState.Loading -> Waiting(if (state.loop != null) "正在画一圈好骑的路" else "正在找一条好骑的路")
                        is RouteState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("算路失败：${r.message}", Modifier.weight(1f), color = RandoRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            GlassButton(onClick = actions.onRetry, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("重试", color = Ink)
                            }
                        }
                        is RouteState.Ready -> Column {
                            if (r.options.size > 1) RouteChoices(r.options, r.chosen, actions.onChooseOption)
                            val prefs = r.chosenPrefs ?: state.prefs
                            RouteSummary(
                                r.route, prefs, actions.onShowOnMap, state.weather, state.amenities,
                                climate = state.climate,
                                wind = state.wind,
                                covered = state.covered,
                                loop = state.loop != null,
                                expanded = size == SheetSize.FULL,
                                onToggleDetails = { size = if (size == SheetSize.FULL) SheetSize.HALF else SheetSize.FULL },
                                onPace = actions.onPace,
                                routeSaved = savedRoutes.any { it.same(r.stops, prefs) },
                                onSaveRoute = actions.onSaveRoute,
                            )
                        }
                    }
                }
            }
          }
          // 「开始导航」钉在卡片底下，不跟着内容滚：路线说明再长也不会被挤出去
          if (size != SheetSize.PEEK) {
              AnimatedVisibility(
                  visible = header != SheetHeader.Empty && state.route is RouteState.Ready,
                  enter = fadeIn(tween(260)) + expandVertically(Motion.smooth()),
                  exit = fadeOut(tween(120)) + shrinkVertically(Motion.smooth()),
              ) {
                  PrimaryButton(onClick = actions.onStart, color = RandoGreen, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                      Text("开始导航", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                  }
              }
          }
        }
    }
}

/** 卡片三种高度：收成一小条、一半、展开详情 */
private enum class SheetSize {
    PEEK, HALF, FULL;

    fun smaller() = entries[(ordinal - 1).coerceAtLeast(0)]
    fun bigger() = entries[(ordinal + 1).coerceAtMost(entries.size - 1)]
}

/** 顶上的拖动条：拖，或者点一下收起 / 展开 */
@Composable
private fun DragHandle(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 40.dp, height = 5.dp).clip(GlassShapes.Pill).background(InkSoft.copy(alpha = 0.35f)))
    }
}

/** 收起时的一行：距离、时间、要不要上马路，旁边就是开始导航 */
@Composable
private fun PeekLine(state: PlannerState, header: SheetHeader, onStart: () -> Unit) {
    val route = (state.route as? RouteState.Ready)?.route
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val text = when {
            route != null -> {
                val road = route.breakdown()[RoadKind.ROAD] ?: 0
                "${Format.distance(route.distanceMeters)} · ${Format.duration(route.durationSeconds)} · " +
                    if (road > 0) "${Format.distance(road)} 马路" else "不上马路"
            }
            header == SheetHeader.Empty -> "往上拉看收藏的地方、骑一圈"
            state.route is RouteState.Failed -> "算路失败，往上拉看看"
            else -> "正在算路…"
        }
        Text(text, Modifier.weight(1f), color = InkSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (route != null) {
            PrimaryButton(onClick = onStart, color = RandoGreen) {
                Text("开始导航", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 还没选目的地时：收藏、骑一圈 */
@Composable
private fun EmptyExtras(saved: List<SavedPlace>, routes: List<SavedRoute>, actions: PlannerActions) {
    Column {
        if (saved.isNotEmpty()) SavedRow(saved, actions.onChooseSaved)
        if (routes.isNotEmpty()) SavedRoutesRow(routes, actions.onChooseRoute, actions.onRemoveRoute)
        LoopRow(actions.onLoop)
    }
}

/** 「我的车 · 1.2 公里 · 2 小时前」，点开看照片、导航过去 */
@Composable
private fun ParkedRow(bike: ParkedBike, here: LatLon?, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .clip(GlassShapes.Pill)
            .liquidGlass(GlassShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("P", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(RandoBlue).padding(horizontal = 6.dp, vertical = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text("我的车", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(" · " + parkedText(bike, here, System.currentTimeMillis()), color = InkSoft, fontSize = 14.sp, maxLines = 1)
    }
}

/** 「1.2 公里 · 2 小时前」 */
fun parkedText(bike: ParkedBike, here: LatLon?, now: Long): String {
    val minutes = ((now - bike.at) / 60_000).coerceAtLeast(0)
    val ago = when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
    return listOfNotNull(here?.let { Format.distance(Geo.distance(it, bike.location).toInt()) }, ago).joinToString(" · ")
}

/** 我的车：照片、在地图上看、走路过去、已取车 */
@Composable
fun ParkedDialog(bike: ParkedBike, here: LatLon?, onShow: () -> Unit, onWalk: () -> Unit, onPhoto: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val photo = remember(bike.photo) {
        bike.photo?.let { path ->
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("我的车") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("停在这里 · " + parkedText(bike, here, System.currentTimeMillis()), color = Ink, fontSize = 15.sp)
                if (photo != null) {
                    androidx.compose.foundation.Image(
                        photo, contentDescription = "车位照片",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    LinkText("拍一张车位照片", onPhoto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkText("在地图上看", onShow)
                    LinkText("走路过去", onWalk)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClear) { Text("已取车", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 收藏的路线：点一下再骑，长按删掉 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRoutesRow(routes: List<SavedRoute>, onChoose: (SavedRoute) -> Unit, onRemove: (SavedRoute) -> Unit) {
    var removing by remember { mutableStateOf<SavedRoute?>(null) }
    Row(
        Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = RandoGreen, modifier = Modifier.size(18.dp))
        Text("我的路线", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        routes.forEach { r ->
            Row(
                Modifier
                    .liquidGlass(GlassShapes.Pill)
                    .clip(GlassShapes.Pill)
                    .combinedClickable(onClick = { onChoose(r) }, onLongClick = { removing = r })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, color = Ink, fontSize = 15.sp, maxLines = 1)
                Text(" ${Format.distance(r.meters)}", color = InkSoft, fontSize = 13.sp)
            }
        }
    }
    removing?.let { r ->
        AlertDialog(
            onDismissRequest = { removing = null },
            shape = GlassShapes.Sheet,
            containerColor = Color(0xF7F7FAF8),
            title = { Text("不收藏「${r.name}」了？") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(r)
                    removing = null
                }) { Text("删掉", color = RandoRed) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }
}

/** 给现在这条路线起个名字收藏起来 */
@Composable
fun SaveRouteDialog(suggested: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("收藏这条路线") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下次在首页点一下就能再骑；同名的会覆盖。", color = InkSoft, fontSize = 14.sp)
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text("名字") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("收藏") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 不知道去哪儿：选个距离骑一圈回来 */
@Composable
private fun LoopRow(onLoop: (Int) -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = RandoGreen, modifier = Modifier.size(18.dp))
        Text("骑一圈", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        LOOP_KMS.forEach { km ->
            GlassButton(onClick = { onLoop(km) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                Text("$km 公里", color = Ink, fontSize = 15.sp)
            }
        }
    }
}

private val LOOP_KMS = listOf(5, 10, 20, 30, 50)

/** 途经点一个一行，可以删；下面一个「加途经点」 */
@Composable
private fun ViaLines(via: List<Place>, picking: Boolean, onAdd: () -> Unit, onRemove: (Place) -> Unit) {
    Column(Modifier.animateContentSize(Motion.smooth())) {
        via.forEach { place ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(RandoBlue))
                Spacer(Modifier.width(8.dp))
                Text("经过 ${place.name}", Modifier.weight(1f), color = InkSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconTap(onClick = { onRemove(place) }) {
                    Icon(Icons.Default.Close, contentDescription = "不经过这里", tint = InkSoft.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!picking && via.size < MAX_VIA) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = RandoGreen, modifier = Modifier.size(16.dp))
                LinkText("加途经点", onAdd)
            }
        }
    }
}

/** 途经点太多卡片放不下，也不好骑 */
private const val MAX_VIA = 5

/** 收藏星星：点亮时弹一下 */
@Composable
private fun StarButton(on: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (on) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, Motion.bouncy())
        }
    }
    val tint by animateColorAsState(if (on) RandoAmber else InkSoft.copy(alpha = 0.45f), Motion.smooth(), label = "star")
    IconTap(onClick = onClick) {
        Icon(
            Icons.Default.Star,
            contentDescription = if (on) "取消收藏" else "收藏",
            tint = tint,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

@Composable
private fun SavedRow(saved: List<SavedPlace>, onChoose: (SavedPlace) -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        saved.forEach { place ->
            GlassButton(onClick = { onChoose(place) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                Icon(
                    if (place.name == SavedPlace.HOME) Icons.Default.Home else Icons.Default.Star,
                    contentDescription = null,
                    tint = RandoAmber,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(place.name, color = Ink, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StartLine(start: Place?, pickingStart: Boolean, onPickStart: () -> Unit, onUseMyLocation: () -> Unit) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (start == null) RandoBlue else RandoGreen))
        Spacer(Modifier.width(8.dp))
        Text(
            "从${start?.name ?: "我的位置"}出发",
            Modifier.weight(1f),
            color = InkSoft,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            start != null -> LinkText("用我的位置", onUseMyLocation)
            !pickingStart -> LinkText("换起点", onPickStart)
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text,
        Modifier
            .pressScale(interaction)
            .clip(GlassShapes.Pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = RandoGreen,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 等待时三个小点依次跳动 */
@Composable
private fun Waiting(text: String) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(900, delayMillis = i * 150), RepeatMode.Restart),
                label = "dot$i",
            )
            val lift = if (y < 0.5f) y * 2 else (1 - y) * 2
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .graphicsLayer { translationY = -lift * 6.dp.toPx() }
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(RandoGreen.copy(alpha = 0.5f + lift * 0.5f))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = InkSoft, fontSize = 15.sp)
    }
}

// ───────────────────────── 路线概要 ─────────────────────────

@Composable
fun RouteSummary(
    route: Route,
    prefs: RoutePrefs,
    onShowOnMap: (NoteTarget) -> Unit,
    weather: RouteWeather? = null,
    amenities: List<AmenityOnRoute> = emptyList(),
    /** 是骑一圈：马路绕不开时提示换个方向，而不是说附近没路 */
    loop: Boolean = false,
    /** 展开详情：爬升图和各项提示；收着时只留最要紧的，地图不被挡住 */
    expanded: Boolean = false,
    onToggleDetails: () -> Unit = {},
    onPace: (Pace) -> Unit = {},
    climate: Climate? = null,
    wind: RouteWind? = null,
    covered: List<CoveredStretch> = emptyList(),
    routeSaved: Boolean = false,
    onSaveRoute: () -> Unit = {},
) {
    Row {
        BigStat(Format.distance(route.distanceMeters), "距离", Modifier.weight(1.2f))
        BigStat(Format.duration(route.durationSeconds), "预计", Modifier.weight(1.2f))
        BigStat("${route.ascendMeters} 米", "爬升", Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { PaceLine(prefs, onPace) }
        if (routeSaved) {
            Text("已收藏", color = InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        } else {
            LinkText("收藏路线", onSaveRoute)
        }
    }
    val breakdown = route.breakdown()
    if (breakdown.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        RoadBar(route, breakdown)
    }
    Column(Modifier.animateContentSize(Motion.smooth())) {
        if (expanded) {
            ElevationChart(route)
            if (breakdown.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Legend(breakdown)
                Spacer(Modifier.height(10.dp))
                Notes(route, breakdown, prefs, onShowOnMap, loop)
            }
            Spacer(Modifier.height(5.dp))
            Outlook(route, weather, amenities, onShowOnMap, climate, wind, prefs, loop, covered)
        } else {
            Spacer(Modifier.height(10.dp))
            Headline(breakdown, weather, onShowOnMap, climate, wind, covered)
        }
        DetailsToggle(expanded, onToggleDetails)
    }
}

/** 「预计」按什么速度算，点开换档 */
@Composable
private fun PaceLine(prefs: RoutePrefs, onPace: (Pace) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .padding(top = 4.dp)
                .clip(GlassShapes.Pill)
                .clickable { open = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预计时间${paceText(prefs.pace, prefs.mySpeed)}", color = InkSoft, fontSize = 13.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "换速度", tint = InkSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = GlassShapes.Card,
            containerColor = Color(0xF7F7FAF8),
        ) {
            Pace.entries.forEach { pace ->
                DropdownMenuItem(
                    text = { Text(paceText(pace, prefs.mySpeed).removePrefix("按")) },
                    leadingIcon = { if (pace == prefs.pace) Icon(Icons.Default.Check, contentDescription = null, tint = RandoGreen) },
                    onClick = {
                        open = false
                        if (pace != prefs.pace) onPace(pace)
                    },
                )
            }
        }
    }
}

/** 「按我的速度 18.2 km/h」「按悠闲的速度」 */
fun paceText(pace: Pace, mySpeed: Double?): String = when {
    pace != Pace.MINE -> "按${pace.label}的速度（${pace.watts} 瓦）"
    mySpeed == null -> "按我的速度（再骑几次才准）"
    else -> "按我的速度 ${Format.speed(mySpeed)}"
}

/** 收起时的一两句：要不要上马路，会不会下雨 */
@Composable
private fun Headline(
    breakdown: Map<RoadKind, Int>,
    weather: RouteWeather?,
    onShowOnMap: (NoteTarget) -> Unit,
    climate: Climate?,
    wind: RouteWind?,
    covered: List<CoveredStretch>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val road = breakdown[RoadKind.ROAD] ?: 0
        when {
            breakdown.isEmpty() -> Unit
            road > 0 -> Note(RandoRed, "有 ${Format.distance(road)} 要在马路上骑", strong = true) { onShowOnMap(NoteTarget.ROAD) }
            else -> Note(RandoGreen, "全程不用上马路", strong = true)
        }
        weather?.takeIf { !it.dry }?.let { Note(RandoBlue, it.summary, strong = true) }
        if (weather?.dry == false) CoveredNote(covered, onShowOnMap)
        climate?.warning?.let { Note(RandoAmber, it, strong = true) }
        wind?.takeIf { it.strongHeadwind }?.let { Note(RandoAmber, "${it.wind.name} ${it.wind.kmh.roundToInt()} km/h，全程大多顶风", strong = true) }
    }
}

@Composable
private fun DetailsToggle(expanded: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.smooth(), label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(GlassShapes.Pill)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) "收起" else "详情：爬升、过马路、天气、补给", color = RandoGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = RandoGreen,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        // 「106 米」这种也不折行：放不下就整体缩小一点
        Text(value, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 24.sp))
        Text(label, color = InkSoft, fontSize = 13.sp)
    }
}

/**
 * 海拔图：沿路线的高低起伏，最陡的那段坡涂成琥珀色；路线一出来从左往右画出来。
 * 没有海拔数据就不画
 */
@Composable
private fun ElevationChart(route: Route) {
    val profile = remember(route) { route.elevationProfile() }
    if (profile.size < 2) return
    val climb = remember(route) { route.steepestClimb() }
    val grow = remember(route) { Animatable(0f) }
    LaunchedEffect(route) { grow.animateTo(1f, tween(900)) }
    val low = profile.minOf { it.meters }
    val high = profile.maxOf { it.meters }
    // 新加坡很平，起伏不到 20 米时按 20 米画，免得一点小坡看起来像山
    val span = (high - low).coerceAtLeast(MIN_CHART_SPAN)
    val total = profile.last().alongMeters
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Canvas(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink.copy(alpha = 0.05f))
        ) {
            fun x(i: Int) = (profile[i].alongMeters / total * size.width).toFloat()
            fun y(i: Int) = (size.height - (profile[i].meters - low) / span * size.height * 0.85 - size.height * 0.08).toFloat()
            val shown = (profile.size * grow.value).toInt().coerceIn(2, profile.size)
            val area = Path().apply {
                moveTo(0f, size.height)
                for (i in 0 until shown) lineTo(x(i), y(i))
                lineTo(x(shown - 1), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(RandoGreen.copy(alpha = 0.55f), RandoGreen.copy(alpha = 0.08f))))
            if (climb != null && climb.fromIndex < shown) {
                val to = minOf(climb.toIndex, shown - 1)
                val hill = Path().apply {
                    moveTo(x(climb.fromIndex), size.height)
                    for (i in climb.fromIndex..to) lineTo(x(i), y(i))
                    lineTo(x(to), size.height)
                    close()
                }
                drawPath(hill, RandoAmber.copy(alpha = 0.75f))
            }
            val line = Path().apply {
                moveTo(x(0), y(0))
                for (i in 1 until shown) lineTo(x(i), y(i))
            }
            drawPath(line, RandoGreenDeep, style = Stroke(width = 1.5.dp.toPx()))
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("最高 ${high.roundToInt()} 米", color = InkSoft, fontSize = 12.sp)
            Text("最低 ${low.roundToInt()} 米", color = InkSoft, fontSize = 12.sp)
        }
    }
}

private const val MIN_CHART_SPAN = 20.0

/** 各种路的占比条：路线一出来就从左往右长出来 */
@Composable
private fun RoadBar(route: Route, breakdown: Map<RoadKind, Int>) {
    val grow = remember(route) { Animatable(0f) }
    LaunchedEffect(route) { grow.animateTo(1f, Motion.smooth()) }
    val total = breakdown.values.sum().coerceAtLeast(1).toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(GlassShapes.Pill)
            .background(Ink.copy(alpha = 0.08f))
    ) {
        var x = 0f
        val gap = 2.dp.toPx()
        breakdown.forEach { (kind, meters) ->
            val w = size.width * meters / total * grow.value
            if (w > 0.5f) {
                drawRoundRect(
                    kindColor(kind.color),
                    topLeft = Offset(x, 0f),
                    size = Size((w - gap).coerceAtLeast(1f), size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            x += w
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(breakdown: Map<RoadKind, Int>) {
    val total = breakdown.values.sum().coerceAtLeast(1)
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        breakdown.forEach { (kind, meters) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(kindColor(kind.color)))
                Spacer(Modifier.width(5.dp))
                Text("${kind.label} ${Format.share(meters.toDouble() / total)}", color = InkSoft, fontSize = 13.sp)
            }
        }
    }
}

/** 要不要上马路、过几次马路、天桥、推车：每条一行，点一下在地图上一处处看 */
@Composable
private fun Notes(route: Route, breakdown: Map<RoadKind, Int>, prefs: RoutePrefs, onShowOnMap: (NoteTarget) -> Unit, loop: Boolean) {
    val road = breakdown[RoadKind.ROAD] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (road > 0) {
            val hint = when {
                prefs.profile != BikeProfile.PCN -> "；选「公园连道」再打开「不上马路」可以尽量避开"
                !prefs.avoidRoads -> "；打开「不上马路」可以尽量避开"
                loop -> "；点「换一条」换个方向试试"
                else -> "，附近没有别的路可走"
            }
            Note(RandoRed, "有 ${Format.distance(road)} 要在马路上骑$hint", strong = true) { onShowOnMap(NoteTarget.ROAD) }
        } else {
            Note(RandoGreen, "全程不用上马路", strong = true)
        }
        val crossings = route.crossings()
        if (crossings.isNotEmpty()) {
            val byType = crossings.groupingBy { it.type }.eachCount()
            Note(
                kindColor(RoadKind.CROSSING.color),
                "过马路 ${crossings.size} 次：" +
                    CrossingType.entries.filter { (byType[it] ?: 0) > 0 }.joinToString(" · ") { "${it.label} ${byType[it]}" },
            ) { onShowOnMap(NoteTarget.CROSSINGS) }
        }
        val structures = route.structures()
        StructureKind.entries.forEach { kind ->
            val list = structures.filter { it.kind == kind }
            if (list.isEmpty()) return@forEach
            val byAccess = list.groupingBy { it.access }.eachCount()
            val detail = if (byAccess.size == 1) byAccess.keys.first() else byAccess.entries.joinToString(" · ") { "${it.key} ${it.value}" }
            val unavoidable = kind == StructureKind.OVERPASS && prefs.avoidBridges && prefs.profile == BikeProfile.PCN
            Note(InkSoft, "经过${kind.label} ${list.size} ${kind.unit}：$detail" + if (unavoidable) "（绕不开）" else "") {
                onShowOnMap(NoteTarget.STRUCTURES)
            }
        }
        // 起点、终点那头的推车（商场里、停车场）不算
        val push = route.pushBetweenEnds()
        if (push > 0) Note(kindColor(RoadKind.PUSH.color), "途中有 ${Format.distance(push)} 禁止骑车，要推车") { onShowOnMap(NoteTarget.PUSH) }
        route.steepestClimb()?.let { c ->
            Note(RandoAmber, "最陡的坡在第 ${Format.km(c.startMeters)} 公里，平均 ${Format.percent(c.grade)}") { onShowOnMap(NoteTarget.CLIMB) }
        }
    }
}

/** 天气、天黑、补给：路线本身以外，骑之前想知道的 */
@Composable
private fun Outlook(
    route: Route,
    weather: RouteWeather?,
    amenities: List<AmenityOnRoute>,
    onShowOnMap: (NoteTarget) -> Unit,
    climate: Climate?,
    wind: RouteWind?,
    prefs: RoutePrefs,
    loop: Boolean,
    covered: List<CoveredStretch>,
) {
    Column(Modifier.animateContentSize(Motion.smooth()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        weather?.let { Note(if (it.dry) RandoGreen else RandoBlue, it.summary, strong = !it.dry) }
        CoveredNote(covered, onShowOnMap)
        climate?.summary?.let { Note(if (climate.warning != null) RandoAmber else InkSoft, "现在 $it") }
        climate?.warning?.let { Note(RandoAmber, it, strong = true) }
        wind?.let { Note(if (it.strongHeadwind) RandoAmber else InkSoft, it.summary(loop)) }
        // 「公园连道」配置中午挑树荫、天黑挑路灯（算路时按时间自动开，偏好里可以关）
        if (prefs.profile == BikeProfile.PCN) {
            val now = remember(route) { System.currentTimeMillis() }
            if (prefs.shadeAtNoon && RideTime.shade(now)) Note(RandoGreen, "中午已尽量挑有树荫的路：树荫下约 ${Format.share(route.shadeShare())}")
            if (prefs.litAtNight && RideTime.night(now)) Note(RandoGreen, "天黑了，已尽量走有路灯的路：有灯的约 ${Format.share(route.litShare())}")
        }
        val dark = remember(route) { DaylightAlerts.plan(System.currentTimeMillis(), route.durationSeconds) }
        dark?.let { Note(RandoAmber, it) }
        val (parking, along) = amenities.partition { it.amenity.type == AmenityType.PARKING }
        if (along.isNotEmpty()) {
            val counts = along.groupingBy { it.amenity.type }.eachCount()
            Note(
                kindColor(AmenityType.WATER.color),
                "沿途补给：" + AmenityType.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(" · ") { "${it.label} ${counts[it]}" },
            ) { onShowOnMap(NoteTarget.AMENITIES) }
        }
        parking.minByOrNull { it.offRouteMeters }?.let { nearest ->
            Note(
                kindColor(AmenityType.PARKING.color),
                "终点附近有停车架 ${parking.size} 处，最近的离终点 ${Format.distance(nearest.offRouteMeters.toInt())}",
            ) { onShowOnMap(NoteTarget.AMENITIES) }
        }
    }
}

/** 「有顶棚的路 3 段，共 420 米，下雨可以躲」 */
@Composable
private fun CoveredNote(covered: List<CoveredStretch>, onShowOnMap: (NoteTarget) -> Unit) {
    if (covered.isEmpty()) return
    val total = covered.sumOf { it.lengthMeters }.toInt()
    Note(kindColor(COVERED_COLOR), "有顶棚的路 ${covered.size} 段，共 ${Format.distance(total)}，下雨可以躲") {
        onShowOnMap(NoteTarget.COVERED)
    }
}

/** 一条提示；能在地图上看的，末尾一个小箭头，整行可点 */
@Composable
private fun Note(color: Color, text: String, strong: Boolean = false, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interaction, pressed = 0.97f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            Modifier.weight(1f),
            color = if (strong) color else InkSoft,
            fontSize = if (strong) 15.sp else 14.sp,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "在地图上看",
                tint = InkSoft.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ───────────────────────── 记录中 ─────────────────────────

/**
 * 刚结束一次骑行：已经保存了，骑了多远多久；离出发的地方有段距离时可以原路返回
 */
@Composable
fun FinishedCard(
    ride: Ride,
    onReturn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 骑过的路：和以前比的那句 */
    record: String? = null,
    onShare: () -> Unit = {},
    kcal: Int? = null,
    /** 拍一张车位照片，回来好找车 */
    onParkPhoto: () -> Unit = {},
) {
    val canReturn = remember(ride) { ReturnTrip.stops(ride.points.map { it.location }) != null }
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Pill)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("已保存${ride.title}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${Format.distance(ride.distanceMeters.toInt())} · ${Format.clock(ride.movingSeconds)}" +
                    (kcal?.let { " · $it 千卡" } ?: ""),
                color = InkSoft, fontSize = 13.sp,
            )
            record?.let { Text(it, color = RandoGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2) }
        }
        if (canReturn) {
            GlassButton(onClick = onReturn, tone = GlassTone.Tinted(RandoGreen), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text("原路返回", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        IconTap(onClick = onParkPhoto) { Icon(CameraIcon, contentDescription = "拍车位照片", tint = InkSoft) }
        IconTap(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "分享", tint = InkSoft) }
        IconTap(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关掉", tint = InkSoft) }
    }
}

/** 记录中的小胶囊：红点呼吸，时间、距离、速度 */
@Composable
fun RecordingPill(recording: RecordingState, onRideView: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "rec")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Pill, GlassTone.Tinted(RandoOrange))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillStat(Format.clock(recording.movingSeconds))
            PillStat(Format.distance(recording.distanceMeters.toInt()))
            PillStat(Format.speed(recording.currentSpeed))
        }
        GlassButton(onClick = onRideView, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Text("骑行视图", color = Ink, fontSize = 14.sp)
        }
        Spacer(Modifier.width(6.dp))
        GlassButton(onClick = onStop, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Text("结束", color = RandoRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PillStat(value: String) {
    Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** 离线地图：没下载 / 下载中 / 下好了，可以更新、删掉 */
@Composable
fun OfflineMapsDialog(state: OfflineMapState, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("离线地图") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("把整个新加坡的地图存到手机里，没信号的地方地图也不会空白。大约 30–60 MB，最好连着 Wi-Fi 下。", color = InkSoft, fontSize = 14.sp)
                val progress = state.progress
                when {
                    progress != null -> {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = RandoGreen)
                        Text("正在下载 ${(progress * 100).toInt()}% · ${megabytes(state.bytes)}", color = Ink, fontSize = 15.sp)
                    }
                    state.downloadedAt != null -> Text(
                        "已下载 · ${java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(state.downloadedAt))} · ${megabytes(state.bytes)}",
                        color = Ink, fontSize = 15.sp,
                    )
                    else -> Text("还没下载", color = Ink, fontSize = 15.sp)
                }
                state.error?.let { Text("出错了：$it", color = RandoRed, fontSize = 13.sp) }
                Text("规划路线、骑行导航还是要联网（算路、搜索在服务器上）。", color = InkSoft, fontSize = 13.sp)
            }
        },
        confirmButton = {
            when {
                state.progress != null -> TextButton(onClick = onCancel) { Text("取消下载", color = RandoRed) }
                state.downloadedAt != null -> TextButton(onClick = onDownload) { Text("更新") }
                else -> TextButton(onClick = onDownload) { Text("下载", color = RandoGreen, fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {
            Row {
                if (state.downloadedAt != null && state.progress == null) TextButton(onClick = onDelete) { Text("删掉", color = RandoRed) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

private fun megabytes(bytes: Long): String = String.format(java.util.Locale.US, "%.0f MB", bytes / 1_048_576.0)

/** 设置：家、体重（算卡路里）、骑车自动开始记录、主页显示骑过的路、播报声音 */
@Composable
fun SettingsDialog(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onDismiss: () -> Unit,
    /** 收藏的家；改了马上生效，不用点保存 */
    home: LatLon? = null,
    /** 我现在的位置，算离家多远 */
    here: LatLon? = null,
    onHomeHere: () -> Unit = {},
    /** 关掉设置，去搜索或长按地图选 */
    onPickHome: () -> Unit = {},
    onRemoveHome: () -> Unit = {},
    /** 定位已经是「始终允许」 */
    backgroundLocation: Boolean = false,
    onAskBackgroundLocation: () -> Unit = {},
    /** 手机上的普通话声音，好的排前面 */
    voices: List<VoiceOption> = emptyList(),
    /** 试听；null 是自动挑的那个 */
    onPreviewVoice: (String?) -> Unit = {},
    /** 打开系统的文字转语音设置（换引擎、下载声音） */
    onSystemVoices: () -> Unit = {},
) {
    var weight by remember { mutableStateOf(settings.weightKg.toInt().toString()) }
    var autoStart by remember { mutableStateOf(settings.autoStart) }
    var showRidden by remember { mutableStateOf(settings.showRidden) }
    var voice by remember { mutableStateOf(settings.voice) }
    var voiceMenu by remember { mutableStateOf(false) }
    val kg = weight.toIntOrNull()?.takeIf { it in 25..250 }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("家", color = Ink, fontSize = 16.sp)
                    Text(homeLabel(home, here), color = InkSoft, fontSize = 13.sp)
                    Row {
                        LinkText("用现在的位置", onHomeHere)
                        LinkText("搜索或在地图上选", onPickHome)
                        if (home != null) LinkText("删掉", onRemoveHome)
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter(Char::isDigit).take(3) },
                    label = { Text("体重（公斤）") },
                    supportingText = { Text("用来算骑车消耗了多少千卡") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("骑车时自动开始记录", color = Ink, fontSize = 16.sp)
                        Text("停下来 10 分钟没动自动结束保存", color = InkSoft, fontSize = 13.sp)
                    }
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                }
                if (autoStart && !backgroundLocation) {
                    Text("定位不是「始终允许」时，检测到骑车会弹通知问一下，点了才开始。", color = InkSoft, fontSize = 13.sp)
                    LinkText("去设成始终允许", onAskBackgroundLocation)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("地图上显示骑过的路", color = Ink, fontSize = 16.sp)
                        Text("按骑过几次上色，垫在路线下面", color = InkSoft, fontSize = 13.sp)
                    }
                    Switch(checked = showRidden, onCheckedChange = { showRidden = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("播报声音", color = Ink, fontSize = 16.sp)
                        Text(voiceLabel(voices, voice), color = InkSoft, fontSize = 13.sp)
                    }
                    Box {
                        LinkText("换一个") { voiceMenu = true }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("自动（不联网里音质最好的）") },
                                onClick = { voice = null; voiceMenu = false; onPreviewVoice(null) },
                            )
                            voices.forEachIndexed { i, v ->
                                DropdownMenuItem(
                                    text = { Text("声音 ${i + 1}：${VoiceChoice.describe(v)}") },
                                    onClick = { voice = v.name; voiceMenu = false; onPreviewVoice(v.name) },
                                )
                            }
                        }
                    }
                }
                Row {
                    LinkText("试听") { onPreviewVoice(voice) }
                    LinkText("下载更多声音", onSystemVoices)
                }
                if (voices.none { it.quality >= 400 }) {
                    Text("手机上没有高音质的中文声音：在系统的文字转语音里把引擎换成 Google，再下载中文语音。", color = InkSoft, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = kg != null, onClick = { onSave(settings.copy(weightKg = kg!!.toDouble(), autoStart = autoStart, voice = voice, showRidden = showRidden)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 「已设好，离这里 3.2 km」：只有坐标，没有地址可显示 */
private fun homeLabel(home: LatLon?, here: LatLon?): String = when {
    home == null -> "还没设：设好后能一键回家、说「回家」、坐 MRT 回家"
    here == null -> "已设好"
    Geo.distance(here, home) < 100 -> "已设好，就在这里"
    else -> "已设好，离这里 ${Format.distance(Geo.distance(here, home).toInt())}"
}

/** 「自动 · 很高音质」「声音 2：高音质 · 要联网」 */
private fun voiceLabel(voices: List<VoiceOption>, name: String?): String {
    if (name == null) return VoiceChoice.best(voices)?.let { "自动 · ${VoiceChoice.describe(it)}" } ?: "自动"
    val i = voices.indexOfFirst { it.name == name }
    return if (i < 0) "选的声音不在了，用自动挑的" else "声音 ${i + 1}：${VoiceChoice.describe(voices[i])}"
}

/** 坐 MRT 回家的方案：骑到哪个站、怎么坐、再骑多远；可以直接导航到站 */
@Composable
fun MrtHomeDialog(plan: MrtHome, onGo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("坐 MRT 回家") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plan.text, color = Ink, fontSize = 16.sp)
                Text("只有折叠车折好（不超过 114×64×36 厘米）才能上 MRT；普通自行车不能上车。高峰时段人多，尽量避开。", color = InkSoft, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onGo) { Text("导航到${plan.board.zh}站", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
    )
}
