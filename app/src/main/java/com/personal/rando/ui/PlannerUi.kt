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
 * 路线偏好：一排玻璃胶囊。「不上马路」「不走天桥」是公园连道的开关，
 * 选中公园连道时从它旁边展开出来
 */
@Composable
fun PrefsRow(prefs: RoutePrefs, onChange: (RoutePrefs) -> Unit, modifier: Modifier = Modifier) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassToggle("不上马路", prefs.avoidRoads) { onChange(prefs.copy(avoidRoads = !prefs.avoidRoads)) }
                        GlassToggle("不走天桥", prefs.avoidBridges) { onChange(prefs.copy(avoidBridges = !prefs.avoidBridges)) }
                    }
                }
            }
        }
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

/** 开关：选中时左边弹出一个对勾 */
@Composable
private fun GlassToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val text by animateColorAsState(if (on) Color.White else Ink, Motion.smooth(), label = "toggle")
    GlassButton(
        onClick = onClick,
        tone = if (on) GlassTone.Tinted(RandoGreenDeep) else GlassTone.Light,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        AnimatedVisibility(on, enter = expandHorizontally(Motion.bouncy()) + scaleIn(), exit = shrinkHorizontally() + fadeOut()) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 4.dp).size(16.dp))
        }
        Text(label, color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
          } else Column(Modifier.verticalScroll(rememberScrollState())) {
            if (header == SheetHeader.Empty) EmptyExtras(saved, actions)
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
                            RouteSummary(
                                r.route, state.prefs, actions.onShowOnMap, state.weather, state.amenities,
                                loop = state.loop != null,
                                expanded = size == SheetSize.FULL,
                                onToggleDetails = { size = if (size == SheetSize.FULL) SheetSize.HALF else SheetSize.FULL },
                            )
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(onClick = actions.onStart, color = RandoGreen, modifier = Modifier.fillMaxWidth()) {
                                Text("开始导航", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
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
private fun EmptyExtras(saved: List<SavedPlace>, actions: PlannerActions) {
    Column {
        if (saved.isNotEmpty()) SavedRow(saved, actions.onChooseSaved)
        LoopRow(actions.onLoop)
    }
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
) {
    Row {
        BigStat(Format.distance(route.distanceMeters), "距离", Modifier.weight(1.2f))
        BigStat(Format.duration(route.durationSeconds), "预计", Modifier.weight(1.2f))
        BigStat("${route.ascendMeters} 米", "爬升", Modifier.weight(1f))
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
            Outlook(route, weather, amenities, onShowOnMap)
        } else {
            Spacer(Modifier.height(10.dp))
            Headline(breakdown, weather, onShowOnMap)
        }
        DetailsToggle(expanded, onToggleDetails)
    }
}

/** 收起时的一两句：要不要上马路，会不会下雨 */
@Composable
private fun Headline(breakdown: Map<RoadKind, Int>, weather: RouteWeather?, onShowOnMap: (NoteTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val road = breakdown[RoadKind.ROAD] ?: 0
        when {
            breakdown.isEmpty() -> Unit
            road > 0 -> Note(RandoRed, "有 ${Format.distance(road)} 要在马路上骑", strong = true) { onShowOnMap(NoteTarget.ROAD) }
            else -> Note(RandoGreen, "全程不用上马路", strong = true)
        }
        weather?.takeIf { !it.dry }?.let { Note(RandoBlue, it.summary, strong = true) }
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
private fun Outlook(route: Route, weather: RouteWeather?, amenities: List<AmenityOnRoute>, onShowOnMap: (NoteTarget) -> Unit) {
    Column(Modifier.animateContentSize(Motion.smooth()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        weather?.let { Note(if (it.dry) RandoGreen else RandoBlue, it.summary, strong = !it.dry) }
        val dark = remember(route) { DaylightAlerts.plan(System.currentTimeMillis(), route.durationSeconds) }
        dark?.let { Note(RandoAmber, it) }
        if (amenities.isNotEmpty()) {
            val counts = amenities.groupingBy { it.amenity.type }.eachCount()
            Note(
                kindColor(AmenityType.WATER.color),
                "沿途补给：" + AmenityType.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(" · ") { "${it.label} ${counts[it]}" },
            ) { onShowOnMap(NoteTarget.AMENITIES) }
        }
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
fun FinishedCard(ride: Ride, onReturn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
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
            Text("${Format.distance(ride.distanceMeters.toInt())} · ${Format.clock(ride.movingSeconds)}", color = InkSoft, fontSize = 13.sp)
        }
        if (canReturn) {
            GlassButton(onClick = onReturn, tone = GlassTone.Tinted(RandoGreen), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text("原路返回", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
