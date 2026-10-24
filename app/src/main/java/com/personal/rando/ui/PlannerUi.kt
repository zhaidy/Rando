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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.places.SavedPlace
import com.personal.rando.ride.RecordingState
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
    pickingStart: Boolean,
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
                    Text(if (pickingStart) "搜索起点" else "去哪儿骑？", color = InkSoft, fontSize = 17.sp)
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
                        if (place.detail.isNotEmpty()) {
                            Text(place.detail, color = InkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** 橙色提示条，比如「长按地图选起点」 */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier) {
    GlassPanel(modifier, shape = GlassShapes.Pill, tone = GlassTone.Tinted(RandoOrange)) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp)
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
        if (showLocation) {
            IconTap(onClick = onLocate) { Icon(Icons.Default.LocationOn, contentDescription = "回到我的位置", tint = RandoGreen) }
        }
    }
}

// ───────────────────────── 底部：规划卡片 ─────────────────────────

/**
 * 底部的玻璃卡片。内容在「选目的地」「定位中」「算路中」「算好了」「失败」之间切换时
 * 交叉淡入并带着尺寸一起形变
 */
@Composable
fun PlannerSheet(
    destination: Place?,
    start: Place?,
    pickingStart: Boolean,
    route: RouteState,
    prefs: RoutePrefs,
    saved: List<SavedPlace>,
    onChooseSaved: (SavedPlace) -> Unit,
    onToggleSave: (Place, SavedPlace?) -> Unit,
    onClear: () -> Unit,
    onPickStart: () -> Unit,
    onUseMyLocation: () -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onShowOnMap: (NoteTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(Motion.smooth()) { it / 4 })
                        .togetherWith(fadeOut(tween(120)))
                        .using(SizeTransform(clip = false) { _, _ -> Motion.smooth() })
                },
                contentKey = { it?.location },
                label = "destination",
            ) { dest ->
                if (dest == null) {
                    Column {
                        Text("想去哪儿骑？", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("搜索目的地，或者长按地图选一个点", color = InkSoft, fontSize = 14.sp)
                        if (saved.isNotEmpty()) SavedRow(saved, onChooseSaved)
                    }
                } else {
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
                        StarButton(savedHere != null) { onToggleSave(dest, savedHere) }
                        IconTap(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "取消路线", tint = InkSoft) }
                    }
                }
            }
            StartLine(start, pickingStart, onPickStart, onUseMyLocation)
            if (destination != null) {
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = route,
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
                        RouteState.Loading -> Waiting("正在找一条好骑的路")
                        is RouteState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("算路失败：${r.message}", Modifier.weight(1f), color = RandoRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            GlassButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("重试", color = Ink)
                            }
                        }
                        is RouteState.Ready -> Column {
                            RouteSummary(r.route, prefs, onShowOnMap)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(onClick = onStart, color = RandoGreen, modifier = Modifier.fillMaxWidth()) {
                                Text("开始导航", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
fun RouteSummary(route: Route, prefs: RoutePrefs, onShowOnMap: (NoteTarget) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        BigStat(Format.distance(route.distanceMeters), "距离")
        BigStat(Format.duration(route.durationSeconds), "预计")
        BigStat("${route.ascendMeters} 米", "爬升")
    }
    val breakdown = route.breakdown()
    if (breakdown.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    RoadBar(route, breakdown)
    Legend(breakdown)
    Spacer(Modifier.height(10.dp))
    Notes(route, breakdown, prefs, onShowOnMap)
}

@Composable
private fun BigStat(value: String, label: String) {
    Column {
        Text(value, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = InkSoft, fontSize = 13.sp)
    }
}

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
private fun Notes(route: Route, breakdown: Map<RoadKind, Int>, prefs: RoutePrefs, onShowOnMap: (NoteTarget) -> Unit) {
    val road = breakdown[RoadKind.ROAD] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (road > 0) {
            val hint = when {
                prefs.profile != BikeProfile.PCN -> "；选「公园连道」再打开「不上马路」可以尽量避开"
                prefs.avoidRoads -> "，附近没有别的路可走"
                else -> "；打开「不上马路」可以尽量避开"
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

/** 记录中的小胶囊：红点呼吸，时间、距离、速度 */
@Composable
fun RecordingPill(recording: RecordingState, onDashboard: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
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
        GlassButton(onClick = onDashboard, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Text("码表", color = Ink, fontSize = 14.sp)
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
