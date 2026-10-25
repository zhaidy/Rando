package com.personal.rando.ui

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOn
import com.personal.rando.ui.glass.NeuButton
import com.personal.rando.ui.glass.LocalOnSolid
import com.personal.rando.ui.glass.neu
import com.personal.rando.ui.glass.neuInset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.platform.LocalContext
import com.personal.rando.ride.ThemeMode
import com.personal.rando.ui.glass.LocalHazeState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.DisposableEffect
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
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.Language
import com.personal.rando.i18n.tr
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
import com.personal.rando.routing.roadOnTheWay
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
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    onOfflineMaps: () -> Unit = {},
    onSettings: () -> Unit = {},
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
                    cursorBrush = SolidColor(RandoAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconTap(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = tr("清空", "Clear"), tint = InkSoft) }
            } else {
                Box {
                    IconTap(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = tr("更多", "More"), tint = InkSoft) }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = GlassShapes.Card,
                        containerColor = DialogColor,
                    ) {
                        // 骑行记录在右边竖条上、坐 MRT 回家在卡片的收藏那一行，这里只放不常用的
                        DropdownMenuItem(text = { Text(tr("离线地图", "Offline maps")) }, onClick = { menuOpen = false; onOfflineMaps() })
                        DropdownMenuItem(text = { Text(tr("设置", "Settings")) }, onClick = { menuOpen = false; onSettings() })
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(tr("检查更新", "Check for updates"))
                                    Text(tr("现在是 build $build", "Now on build $build"), color = InkSoft, fontSize = 12.sp)
                                }
                            },
                            onClick = { menuOpen = false; onCheckUpdate() },
                        )
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(place) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).neu(GlassShapes.Tile), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AccentInk, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * 路线偏好：车型是一个下拉的玻璃胶囊（不用左右滑），公园连道的几个开关收在它旁边的「偏好」里，
 * 选中公园连道时展开出来，点开是个弹窗
 */
@Composable
fun PrefsRow(prefs: RoutePrefs, onChange: (RoutePrefs) -> Unit, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GlassButton(onClick = { menu = true }, contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)) {
                Text(prefs.profile.label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = tr("车型", "Bike type"), tint = InkSoft, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = GlassShapes.Card, containerColor = DialogColor) {
                BikeProfile.entries.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.label, color = Ink, fontWeight = if (profile == prefs.profile) FontWeight.SemiBold else FontWeight.Normal) },
                        trailingIcon = { if (profile == prefs.profile) Icon(Icons.Default.Check, contentDescription = null, tint = AccentInk) },
                        onClick = {
                            menu = false
                            if (profile != prefs.profile) onChange(prefs.copy(profile = profile))
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = prefs.profile == BikeProfile.PCN,
            enter = expandHorizontally(Motion.bouncy()) + fadeIn(),
            exit = shrinkHorizontally(Motion.smooth()) + fadeOut(),
        ) {
            PcnPrefsChip(prefs) { dialog = true }
        }
    }
    if (dialog) PcnPrefsDialog(prefs, onSave = { dialog = false; if (it != prefs) onChange(it) }, onDismiss = { dialog = false })
}

/** 「偏好」胶囊：打开了「不上马路」「不走天桥」时直接写出来，免得忘了开着 */
@Composable
private fun PcnPrefsChip(prefs: RoutePrefs, onClick: () -> Unit) {
    val avoiding = listOfNotNull(tr("不上马路", "No roads").takeIf { prefs.avoidRoads }, tr("不走天桥", "No overpasses").takeIf { prefs.avoidBridges })
    val on = avoiding.isNotEmpty()
    GlassButton(
        onClick = onClick,
        tone = if (on) GlassTone.Tinted(RandoAccent) else GlassTone.Light,
        contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Text(avoiding.joinToString(tr("、", ", ")).ifEmpty { tr("偏好", "Preferences") }, color = if (on) Color.White else Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ArrowDropDown, contentDescription = tr("路线偏好", "Route preferences"), tint = if (on) Color.White else InkSoft, modifier = Modifier.size(20.dp))
    }
}

/** 公园连道的偏好：按「好」才重新算路 */
@Composable
fun PcnPrefsDialog(prefs: RoutePrefs, onSave: (RoutePrefs) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(prefs) }
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        title = { Text(tr("公园连道偏好", "Park connector preferences")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrefSwitch(tr("不上马路", "Avoid roads"), tr("尽量避开马路，有人行道的走人行道", "Keep off roads where possible; use pavements where there are any"), edited.avoidRoads) { edited = edited.copy(avoidRoads = it) }
                PrefSwitch(tr("不走天桥", "Avoid overpasses"), tr("绕开人行天桥，公园连道、自行车道上的桥照走", "Go around pedestrian overpasses; bridges on park connectors and cycle paths are fine"), edited.avoidBridges) { edited = edited.copy(avoidBridges = it) }
                PrefSwitch(tr("中午挑有树荫的路", "Shade at midday"), tr("11 点到 4 点算路时，宁可绕一点也走树多的路", "Between 11am and 4pm, take a slightly longer route under more trees"), edited.shadeAtNoon) { edited = edited.copy(shadeAtNoon = it) }
                PrefSwitch(tr("天黑挑有路灯的路", "Lit paths after dark"), tr("天黑或快黑时，少走没灯的小路", "After or near dark, avoid unlit paths"), edited.litAtNight) { edited = edited.copy(litAtNight = it) }
            }
        },
        confirmButton = { DialogButton(onClick = { onSave(edited) }) { Text(tr("好", "OK")) } },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
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
        tone = if (selected) GlassTone.Tinted(RandoAccent) else GlassTone.Light,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, color = text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** 黄色提示条，比如「长按地图选起点」；有 [onCancel] 时右边带个「取消」 */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null) {
    GlassPanel(modifier, shape = GlassShapes.Pill, tone = GlassTone.Tinted(RandoYellow)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f, fill = false).padding(start = 16.dp, end = if (onCancel == null) 16.dp else 4.dp, top = 10.dp, bottom = 10.dp), color = HINT_INK, fontSize = 14.sp)
            if (onCancel != null) {
                Row(
                    Modifier
                        .clip(GlassShapes.Pill)
                        .clickable(onClick = onCancel)
                        .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = HINT_INK, modifier = Modifier.size(16.dp))
                    Text(tr("取消", "Cancel"), Modifier.padding(start = 2.dp), color = HINT_INK, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
    /** 竖条最上面的「图层」按钮；null 就不显示 */
    layers: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .liquidGlass(GlassShapes.Pill)
            .animateContentSize(Motion.bouncy())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        layers?.invoke()
        // 图标统一用墨色；只有「开始记录」是橙色（主操作），在听语音时麦克风变红
        IconTap(onClick = onRides) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = tr("骑行记录", "Rides"), tint = Ink) }
        if (showLocation && showRecord) {
            IconTap(onClick = onRecord) { Icon(Icons.Default.PlayArrow, contentDescription = tr("开始记录", "Start recording"), tint = AccentInk) }
        }
        onGroup?.let {
            IconTap(onClick = it) { Icon(GroupIcon, contentDescription = tr("一起骑", "Ride together"), tint = Ink) }
        }
        onVoice?.let {
            IconTap(onClick = it) { Icon(MicIcon, contentDescription = tr("语音指令", "Voice command"), tint = if (listening) RandoRed else Ink) }
        }
        if (showLocation) {
            IconTap(onClick = onLocate) { Icon(MyLocationIcon, contentDescription = tr("回到我的位置", "My location"), tint = RandoBlue.onGlass()) }
        }
    }
}

/**
 * 「图层」按钮：点开一个小菜单，地图 / 卫星图二选一，公园连道网络、骑过的路各一个开关。
 * 改了马上生效，也记在设置里
 */
@Composable
fun LayersButton(settings: Settings, onChange: (Settings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconTap(onClick = { open = true }) { Icon(LayersIcon, contentDescription = tr("地图图层", "Map layers"), tint = Ink) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GlassShapes.Card, containerColor = DialogColor) {
            // 菜单是单独的弹出窗口，后面没有地图可取，玻璃退回实色
            CompositionLocalProvider(LocalHazeState provides null) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(tr("地图", "Map"), selected = !settings.satellite) { onChange(settings.copy(satellite = false)) }
                    GlassChip(tr("卫星图", "Satellite"), selected = settings.satellite) { onChange(settings.copy(satellite = true)) }
                }
            }
            LayerSwitch(tr("公园连道网络", "Park connector network"), settings.showPcn) { onChange(settings.copy(showPcn = it)) }
            LayerSwitch(tr("骑过的路", "Roads I've ridden"), settings.showRidden) { onChange(settings.copy(showRidden = it)) }
            if (settings.satellite) {
                Text(
                    tr("卫星图：", "Satellite: ") + MapLayers.SATELLITE_CREDIT,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = InkSoft,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LayerSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.padding(end = 12.dp), color = Ink, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

// ───────────────────────── 整体排布 ─────────────────────────

/** 规划页上下两摞东西挡住了地图哪里：上面那摞的下沿、底部卡片的上沿、整个高度，都是像素 */
data class PlannerInsets(val top: Int = 0, val sheetTop: Int = 0, val height: Int = 0) {
    val measured: Boolean get() = height > 0 && sheetTop > top
}

/**
 * 规划页的排布：上面搜索框一摞，下面右侧竖条、各种胶囊、底部卡片一摞。
 * 下面那摞最高只到上面那摞的下沿，放不下时卡片自己缩、里面上下滑，两摞不会叠在一起。
 * [onInsets] 报告挡住的位置，镜头框路线时用
 */
@Composable
fun PlannerFrame(
    top: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    sheet: @Composable (Modifier) -> Unit,
    onInsets: (PlannerInsets) -> Unit = {},
    /** 卡片拉满了：竖条、比例尺先让开，卡片才长得上去 */
    sheetExpanded: Boolean = false,
) {
    val density = LocalDensity.current
    var insets by remember { mutableStateOf(PlannerInsets()) }
    LaunchedEffect(insets) { onInsets(insets) }
    Box(Modifier.fillMaxSize().onSizeChanged { insets = insets.copy(height = it.height) }) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onGloballyPositioned { insets = insets.copy(top = it.boundsInParent().bottom.toInt()) },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = top,
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(top = with(density) { insets.top.toDp() })
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                !sheetExpanded,
                enter = fadeIn() + expandVertically(Motion.smooth()),
                exit = fadeOut() + shrinkVertically(Motion.smooth()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = bottom)
            }
            sheet(Modifier.weight(1f, fill = false).onGloballyPositioned { insets = insets.copy(sheetTop = it.positionInRoot().y.toInt()) })
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
    /** 骑累了坐 MRT 回家 */
    val onMrtHome: () -> Unit = {},
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
    /** 拉满（看路线详情）时告诉外面，好让竖条让位 */
    onExpandedChange: (Boolean) -> Unit = {},
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
    val expanded = size == SheetSize.FULL && header != SheetHeader.Empty
    LaunchedEffect(expanded) { onExpandedChange(expanded) }
    DisposableEffect(Unit) { onDispose { onExpandedChange(false) } }
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
    GlassPanel(modifier.fillMaxWidth().heightIn(max = maxHeight).animateContentSize(Motion.smooth()), tone = GlassTone.Solid) {
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
                    // 上面的搜索框已经写着「去哪儿骑？」，这里不再重复
                    SheetHeader.Empty -> Spacer(Modifier.height(0.dp))
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
                            IconTap(onClick = actions.onClear) { Icon(Icons.Default.Close, contentDescription = tr("取消路线", "Clear route"), tint = InkSoft) }
                        }
                    }
                    is SheetHeader.Loop -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("骑一圈 · 约 ${h.km} 公里", "Loop · about ${h.km} km"), Modifier.weight(1f), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinkText(tr("换一条", "Another one"), actions.onAnotherLoop)
                        IconTap(onClick = actions.onClear) { Icon(Icons.Default.Close, contentDescription = tr("取消路线", "Clear route"), tint = InkSoft) }
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
                        RouteState.WaitingForLocation -> Waiting(tr("正在定位，定位到就开始算路", "Finding your location; routing starts once found"))
                        RouteState.Loading -> Waiting(if (state.loop != null) tr("正在画一圈好骑的路", "Drawing a nice loop") else tr("正在找一条好骑的路", "Finding a nice route"))
                        is RouteState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("算路失败：${r.message}", "Routing failed: ${r.message}"), Modifier.weight(1f), color = RandoRed.onGlass(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            GlassButton(onClick = actions.onRetry, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(tr("重试", "Retry"), color = Ink)
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
                                routeSaved = savedRoutes.any { it.same(r.stops, prefs, r.chosenAlternative) },
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
                  PrimaryButton(onClick = actions.onStart, color = RandoAccent, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                      Text(tr("开始导航", "Start navigation"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                    if (road > 0) tr("${Format.distance(road)} 马路", "${Format.distance(road)} on roads") else tr("不上马路", "no roads")
            }
            header == SheetHeader.Empty -> tr("往上拉看收藏的地方、骑一圈", "Pull up for saved places and loops")
            state.route is RouteState.Failed -> tr("算路失败，往上拉看看", "Routing failed; pull up for details")
            else -> tr("正在算路…", "Routing…")
        }
        Text(text, Modifier.weight(1f), color = InkSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (route != null) {
            PrimaryButton(onClick = onStart, color = RandoAccent) {
                Text(tr("开始导航", "Start navigation"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 还没选目的地时：收藏、骑一圈 */
@Composable
private fun EmptyExtras(saved: List<SavedPlace>, routes: List<SavedRoute>, actions: PlannerActions) {
    Column {
        SavedRow(saved, actions.onChooseSaved, actions.onMrtHome)
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
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("P", color = HINT_INK, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RandoBlue).padding(horizontal = 8.dp, vertical = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(tr("我的车", "My bike"), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(" · " + parkedText(bike, here, System.currentTimeMillis()), Modifier.weight(1f), color = InkSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkSoft, modifier = Modifier.size(18.dp))
    }
}

/** 「1.2 公里 · 2 小时前」 */
fun parkedText(bike: ParkedBike, here: LatLon?, now: Long): String {
    val minutes = ((now - bike.at) / 60_000).coerceAtLeast(0)
    val ago = when {
        minutes < 1 -> tr("刚刚", "just now")
        minutes < 60 -> tr("$minutes 分钟前", "$minutes min ago")
        minutes < 24 * 60 -> tr("${minutes / 60} 小时前", "${minutes / 60} h ago")
        else -> (minutes / (24 * 60)).let { d -> tr("$d 天前", if (d == 1L) "1 day ago" else "$d days ago") }
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
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(Icons.Filled.Build, RandoYellow),
        title = { Text(tr("我的车", "My bike")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("停在这里 · ", "Parked here · ") + parkedText(bike, here, System.currentTimeMillis()), color = Ink, fontSize = 15.sp)
                if (photo != null) {
                    androidx.compose.foundation.Image(
                        photo, contentDescription = tr("车位照片", "Parking photo"),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    LinkText(tr("拍一张车位照片", "Take a parking photo"), onPhoto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkText(tr("在地图上看", "Show on map"), onShow)
                    LinkText(tr("走路过去", "Walk there"), onWalk)
                }
            }
        },
        confirmButton = { DialogButton(onClick = onClear) { Text(tr("已取车", "Got my bike"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("关闭", "Close")) } },
    )
}

/** 收藏的路线：点一下再骑，长按删掉 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SavedRoutesRow(routes: List<SavedRoute>, onChoose: (SavedRoute) -> Unit, onRemove: (SavedRoute) -> Unit) {
    var removing by remember { mutableStateOf<SavedRoute?>(null) }
    // 多了只先放前几条，最后一个「更多」点开看全部；放不下就换行，不用左右滑
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all || routes.size <= ROUTES_SHOWN + 1) routes else routes.take(ROUTES_SHOWN)
    FlowRow(
        Modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.padding(end = 2.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = RandoRed.onGlass(), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(tr("我的路线", "My routes"), color = RandoRed.onGlass(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        shown.forEach { r ->
            Row(
                Modifier
                    .liquidGlass(GlassShapes.Pill)
                    .clip(GlassShapes.Pill)
                    .combinedClickable(onClick = { onChoose(r) }, onLongClick = { removing = r })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, Modifier.weight(1f, fill = false), color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(" ${Format.distance(r.meters)}", color = InkSoft, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (routes.size > shown.size || all && routes.size > ROUTES_SHOWN + 1) {
            Text(
                if (all) tr("收起", "Less") else tr("更多 ${routes.size - shown.size} 条", "${routes.size - shown.size} more"),
                Modifier.clip(GlassShapes.Pill).clickable { all = !all }.padding(horizontal = 12.dp, vertical = 9.dp),
                color = AccentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
    removing?.let { r ->
        RandoDialog(
            onDismissRequest = { removing = null },
            shape = GlassShapes.Sheet,
            containerColor = DialogColor,
            title = { Text(tr("不收藏「${r.name}」了？", "Remove “${r.name}” from saved?")) },
            confirmButton = {
                DialogButton(danger = true, onClick = {
                    onRemove(r)
                    removing = null
                }) { Text(tr("删掉", "Delete")) }
            },
            dismissButton = { DialogButton(onClick = { removing = null }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

private fun themeLabel(t: ThemeMode): String = when (t) {
    ThemeMode.SYSTEM -> tr("跟随系统", "Follow system")
    ThemeMode.LIGHT -> tr("浅色", "Light")
    ThemeMode.DARK -> tr("深色", "Dark")
}

/** 骑行视图上显示歌名要「通知使用权」：没开时给个入口 */
@Composable
private fun MusicAccessRow() {
    val context = LocalContext.current
    val music = remember { com.personal.rando.ride.Music(context) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("骑行时显示正在放的歌", "Show what's playing while riding"), color = Ink, fontSize = 16.sp)
            Text(
                if (music.granted) tr("已打开：能看到歌名、切歌", "On: song names and controls")
                else tr("要在系统里给 Rando「通知使用权」，只读播放器，不读通知内容", "Needs notification access in system settings; only the player is read, never your notifications"),
                color = InkSoft, fontSize = 13.sp,
            )
        }
        if (!music.granted) LinkText(tr("去打开", "Turn on")) { runCatching { context.startActivity(com.personal.rando.ride.Music.settingsIntent()) } }
    }
}

/** 给现在这条路线起个名字收藏起来 */
@Composable
fun SaveRouteDialog(suggested: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        title = { Text(tr("收藏这条路线", "Save this route")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("下次在首页点一下就能再骑；同名的会覆盖。", "Ride it again with one tap from the home screen; a route with the same name is replaced."), color = InkSoft, fontSize = 14.sp)
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text(tr("名字", "Name")) }, singleLine = true)
            }
        },
        confirmButton = { DialogButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(tr("收藏", "Save")) } },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
    )
}

/** 不知道去哪儿：选个距离骑一圈回来 */
@Composable
private fun LoopRow(onLoop: (Int) -> Unit) {
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = RandoAmber.onGlass(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(tr("骑一圈", "Loop"), color = RandoAmber.onGlass(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        // 五个距离一样宽，排在一条凹槽里
        Row(Modifier.weight(1f).neuInset(GlassShapes.Tile).padding(4.dp)) {
            LOOP_KMS.forEach { km ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onLoop(km) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$km", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(tr("公里", "km"), color = InkSoft, fontSize = 13.sp)
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
                Text(tr("经过 ${place.name}", "Via ${place.name}"), Modifier.weight(1f), color = InkSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconTap(onClick = { onRemove(place) }) {
                    Icon(Icons.Default.Close, contentDescription = tr("不经过这里", "Remove stop"), tint = InkSoft.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!picking && via.size < MAX_VIA) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentInk, modifier = Modifier.size(16.dp))
                LinkText(tr("加途经点", "Add stop"), onAdd)
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
            contentDescription = if (on) tr("取消收藏", "Unsave") else tr("收藏", "Save"),
            tint = tint,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

@Composable
private fun SavedRow(saved: List<SavedPlace>, onChoose: (SavedPlace) -> Unit, onMrtHome: () -> Unit) {
    // 「坐 MRT 回家」跟在「家」后面（没收藏家时放最前面，点了会提示先收藏），一行里滑不到最后也看得见
    val homeFirst = saved.sortedByDescending { it.name == SavedPlace.HOME }
    // 一行四个方块，多了换行，不用左右滑
    val tiles = buildList<@Composable RowScope.() -> Unit> {
        if (homeFirst.firstOrNull()?.name != SavedPlace.HOME) add { MrtHomeTile(onMrtHome) }
        homeFirst.forEach { place ->
            add {
                PlaceTile(place.name, if (place.name == SavedPlace.HOME) Icons.Default.Home else Icons.Default.Star, if (place.name == SavedPlace.HOME) AccentInk else RandoYellow) { onChoose(place) }
            }
            if (place.name == SavedPlace.HOME) add { MrtHomeTile(onMrtHome) }
        }
    }
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(PLACES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { it() }
                repeat(PLACES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val PLACES_PER_ROW = 4
/** 我的路线先放这么多条，多了收在「更多」里 */
private const val ROUTES_SHOWN = 4

/** 收藏的地方：凸起的小方块，上面图标下面名字 */
@Composable
private fun RowScope.PlaceTile(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    NeuButton(onClick = onClick, modifier = Modifier.weight(1f).height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(name, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun RowScope.MrtHomeTile(onClick: () -> Unit) {
    PlaceTile(tr("坐MRT回家", "MRT home"), RandoIcons.Train, RandoRed.onGlass(), onClick)
}

@Composable
private fun StartLine(start: Place?, pickingStart: Boolean, onPickStart: () -> Unit, onUseMyLocation: () -> Unit) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (start == null) RandoBlue else RandoAccent))
        Spacer(Modifier.width(8.dp))
        Text(
            start?.name.let { n -> if (n == null) tr("从我的位置出发", "From my location") else tr("从${n}出发", "From $n") },
            Modifier.weight(1f),
            color = InkSoft,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            start != null -> LinkText(tr("用我的位置", "Use my location"), onUseMyLocation)
            !pickingStart -> LinkText(tr("换起点", "Change start"), onPickStart)
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
        color = AccentInk,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
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
                    .background(RandoAccent.copy(alpha = 0.5f + lift * 0.5f))
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
        BigStat(Format.distance(route.distanceMeters), tr("距离", "Distance"), Modifier.weight(1.2f))
        BigStat(Format.duration(route.durationSeconds), tr("预计", "Time"), Modifier.weight(1.2f))
        BigStat(tr("${route.ascendMeters} 米", "${route.ascendMeters} m"), tr("爬升", "Climb"), Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { PaceLine(prefs, onPace) }
        if (routeSaved) {
            Text(tr("已收藏", "Saved"), color = InkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        } else {
            LinkText(tr("收藏路线", "Save route"), onSaveRoute)
        }
    }
    val breakdown = route.breakdown()
    val onTheWay = route.roadOnTheWay()
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
                Notes(route, breakdown, onTheWay, prefs, onShowOnMap, loop)
            }
            Spacer(Modifier.height(5.dp))
            Outlook(route, weather, amenities, onShowOnMap, climate, wind, prefs, loop, covered)
        } else {
            Spacer(Modifier.height(10.dp))
            Headline(breakdown, onTheWay, weather, onShowOnMap, climate, wind, covered)
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
                // 文字折成两行时胶囊形的圆角会切掉行首的字，用小圆角
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("预计时间", "Est. time ") + paceText(prefs.pace, prefs.mySpeed),
                color = InkSoft,
                fontSize = 13.sp,
                // 不占满，留出下拉箭头的位置，否则长文字会把箭头挤没
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = tr("换速度", "Change pace"), tint = InkSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = GlassShapes.Card,
            containerColor = DialogColor,
        ) {
            Pace.entries.forEach { pace ->
                DropdownMenuItem(
                    text = { Text(paceText(pace, prefs.mySpeed).removePrefix(tr("按", "at ")).replaceFirstChar(Char::uppercase)) },
                    leadingIcon = { if (pace == prefs.pace) Icon(Icons.Default.Check, contentDescription = null, tint = AccentInk) },
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
    pace != Pace.MINE -> tr("按${pace.label}的速度（${pace.watts} 瓦）", "at ${pace.label.lowercase()} pace (${pace.watts} W)")
    mySpeed == null -> tr("按我的速度（再骑几次才准）", "at my pace (accurate after a few more rides)")
    else -> tr("按我的速度 ${Format.speed(mySpeed)}", "at my pace, ${Format.speed(mySpeed)}")
}

/** 收起时的一两句：要不要上马路，会不会下雨 */
@Composable
private fun Headline(
    breakdown: Map<RoadKind, Int>,
    /** 途中要上马路的米数，见 [roadOnTheWay] */
    onTheWay: Int,
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
            onTheWay > 0 -> Note(RandoRed, tr("有 ${Format.distance(onTheWay)} 要在马路上骑", "${Format.distance(onTheWay)} on roads"), strong = true) { onShowOnMap(NoteTarget.ROAD) }
            road > 0 -> Note(RandoGreen, tr("途中不用上马路", "No roads on the way"), strong = true) { onShowOnMap(NoteTarget.ROAD) }
            else -> Note(RandoGreen, tr("全程不用上马路", "No roads at all"), strong = true)
        }
        weather?.takeIf { !it.dry }?.let { Note(RandoBlue, it.summary, strong = true) }
        if (weather?.dry == false) CoveredNote(covered, onShowOnMap)
        climate?.warning?.let { Note(RandoAmber, it, strong = true) }
        wind?.takeIf { it.strongHeadwind }?.let { Note(RandoAmber, tr("${it.wind.name} ${it.wind.kmh.roundToInt()} km/h，全程大多顶风", "${it.wind.name} ${it.wind.kmh.roundToInt()} km/h, headwind most of the way"), strong = true) }
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
        Text(if (expanded) tr("收起", "Less") else tr("详情：爬升、过马路、天气、补给", "Details: climbs, crossings, weather, supplies"), color = AccentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = AccentInk,
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
    // 起点终点几乎同一处：全长是 0，除下去全是 NaN，画不出来就不画
    if (total <= 0) return
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
            Text(tr("最高 ${high.roundToInt()} 米", "High ${high.roundToInt()} m"), color = InkSoft, fontSize = 12.sp)
            Text(tr("最低 ${low.roundToInt()} 米", "Low ${low.roundToInt()} m"), color = InkSoft, fontSize = 12.sp)
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
private fun Notes(route: Route, breakdown: Map<RoadKind, Int>, onTheWay: Int, prefs: RoutePrefs, onShowOnMap: (NoteTarget) -> Unit, loop: Boolean) {
    val road = breakdown[RoadKind.ROAD] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (onTheWay > 0) {
            val hint = when {
                prefs.profile != BikeProfile.PCN -> tr("；选「公园连道」再打开「不上马路」可以尽量避开", "; choose “Park connector” and turn on “Avoid roads” to keep off them")
                !prefs.avoidRoads -> tr("；打开「不上马路」可以尽量避开", "; turn on “Avoid roads” to keep off them")
                loop -> tr("；点「换一条」换个方向试试", "; tap “Another one” to try another direction")
                else -> tr("，附近没有别的路可走", ", no other way around here")
            }
            Note(RandoRed, tr("有 ${Format.distance(onTheWay)} 要在马路上骑$hint", "${Format.distance(onTheWay)} on roads$hint"), strong = true) { onShowOnMap(NoteTarget.ROAD) }
        } else if (road > 0) {
            // 组屋楼下的车道、停车场：出门进门绕不开，地图上照样画红的，点了能看到在哪
            Note(RandoGreen, tr("途中不用上马路；起点、终点那头有 ${Format.distance(road)} 小区车道、停车场", "No roads on the way; ${Format.distance(road)} of driveway or car park at the ends"), strong = true) {
                onShowOnMap(NoteTarget.ROAD)
            }
        } else {
            Note(RandoGreen, tr("全程不用上马路", "No roads at all"), strong = true)
        }
        val crossings = route.crossings()
        if (crossings.isNotEmpty()) {
            val byType = crossings.groupingBy { it.type }.eachCount()
            Note(
                kindColor(RoadKind.CROSSING.color),
                tr("过马路 ${crossings.size} 次：", "Road crossings ${crossings.size}: ") +
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
            Note(InkSoft, tr("经过${kind.label} ${list.size} ${kind.unit}：$detail", "${kind.label} × ${list.size}: $detail") + if (unavoidable) tr("（绕不开）", " (unavoidable)") else "") {
                onShowOnMap(NoteTarget.STRUCTURES)
            }
        }
        // 起点、终点那头的推车（商场里、停车场）不算
        val push = route.pushBetweenEnds()
        if (push > 0) Note(kindColor(RoadKind.PUSH.color), tr("途中有 ${Format.distance(push)} 禁止骑车，要推车", "${Format.distance(push)} where cycling is banned; push your bike")) { onShowOnMap(NoteTarget.PUSH) }
        route.steepestClimb()?.let { c ->
            Note(RandoAmber, tr("最陡的坡在第 ${Format.km(c.startMeters)} 公里，平均 ${Format.percent(c.grade)}", "Steepest climb at km ${Format.km(c.startMeters)}, averaging ${Format.percent(c.grade)}")) { onShowOnMap(NoteTarget.CLIMB) }
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
        climate?.summary?.let { Note(if (climate.warning != null) RandoAmber else InkSoft, tr("现在 $it", "Now $it")) }
        climate?.warning?.let { Note(RandoAmber, it, strong = true) }
        wind?.let { Note(if (it.strongHeadwind) RandoAmber else InkSoft, it.summary(loop)) }
        // 「公园连道」配置中午挑树荫、天黑挑路灯（算路时按时间自动开，偏好里可以关）
        if (prefs.profile == BikeProfile.PCN) {
            val now = remember(route) { System.currentTimeMillis() }
            if (prefs.shadeAtNoon && RideTime.shade(now)) Note(RandoGreen, tr("中午已尽量挑有树荫的路：树荫下约 ${Format.share(route.shadeShare())}", "Midday, so shaded paths preferred: about ${Format.share(route.shadeShare())} in shade"))
            if (prefs.litAtNight && RideTime.night(now)) Note(RandoGreen, tr("天黑了，已尽量走有路灯的路：有灯的约 ${Format.share(route.litShare())}", "It's dark, so lit paths preferred: about ${Format.share(route.litShare())} lit"))
        }
        val dark = remember(route) { DaylightAlerts.plan(System.currentTimeMillis(), route.durationSeconds) }
        dark?.let { Note(RandoAmber, it) }
        val (parking, along) = amenities.partition { it.amenity.type == AmenityType.PARKING }
        if (along.isNotEmpty()) {
            val counts = along.groupingBy { it.amenity.type }.eachCount()
            Note(
                kindColor(AmenityType.WATER.color),
                tr("沿途补给：", "Along the way: ") + AmenityType.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(" · ") { "${it.label} ${counts[it]}" },
            ) { onShowOnMap(NoteTarget.AMENITIES) }
        }
        parking.minByOrNull { it.offRouteMeters }?.let { nearest ->
            Note(
                kindColor(AmenityType.PARKING.color),
                tr("终点附近有停车架 ${parking.size} 处，最近的离终点 ${Format.distance(nearest.offRouteMeters.toInt())}", "${parking.size} bike ${if (parking.size == 1) "rack" else "racks"} near the end; nearest ${Format.distance(nearest.offRouteMeters.toInt())} away"),
            ) { onShowOnMap(NoteTarget.AMENITIES) }
        }
    }
}

/** 「有顶棚的路 3 段，共 420 米，下雨可以躲」 */
@Composable
private fun CoveredNote(covered: List<CoveredStretch>, onShowOnMap: (NoteTarget) -> Unit) {
    if (covered.isEmpty()) return
    val total = covered.sumOf { it.lengthMeters }.toInt()
    Note(kindColor(COVERED_COLOR), tr("有顶棚的路 ${covered.size} 段，共 ${Format.distance(total)}，下雨可以躲", "${covered.size} covered ${if (covered.size == 1) "stretch" else "stretches"}, ${Format.distance(total)} in total, shelter from rain")) {
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
            color = if (strong) color.onGlass() else InkSoft,
            fontSize = if (strong) 15.sp else 14.sp,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = tr("在地图上看", "Show on map"),
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
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Card, GlassTone.Solid)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(RandoAccent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AccentInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("已保存${ride.title}", "Saved ${ride.title}"), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Format.distance(ride.distanceMeters.toInt())} · ${Format.clock(ride.movingSeconds)}" +
                        (kcal?.let { tr(" · $it 千卡", " · $it kcal") } ?: ""),
                    color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            IconTap(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = tr("关掉", "Close"), tint = InkSoft) }
        }
        record?.let { Text(it, color = RandoGreen.onGlass(), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
        // 三个一样宽的按钮：原路返回（离起点够远才有）、拍车位、分享
        CompositionLocalProvider(LocalOnSolid provides true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canReturn) {
                    FinishedAction(tr("原路返回", "Ride back"), onReturn) { Icon(Icons.Default.Refresh, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)) }
                }
                FinishedAction(tr("拍车位", "Parking"), onParkPhoto) { Icon(CameraIcon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp)) }
                PrimaryButton(onClick = onShare, color = RandoAccent, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("分享", "Share"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RowScope.FinishedAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    NeuButton(onClick = onClick, modifier = Modifier.weight(1f).height(52.dp), shape = GlassShapes.Button) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** 黄色提示条上的字：黄底配深色字才看得清 */
private val HINT_INK = Color(0xFF1C2330)

/** 记录中的小胶囊：白点呼吸，时间、距离、速度 */
@Composable
fun RecordingPill(recording: RecordingState, onRideView: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "rec")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Pill, GlassTone.Tinted(RandoAccent))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillStat(Format.clock(recording.movingSeconds))
            PillStat(Format.distance(recording.distanceMeters.toInt()))
            // 胶囊里放不下「公里/时」，写短一点
            PillStat(String.format(java.util.Locale.US, "%.1f km/h", recording.currentSpeed * 3.6))
        }
        GlassButton(onClick = onRideView, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(tr("骑行视图", "Ride view"), color = Ink, fontSize = 14.sp)
        }
        Spacer(Modifier.width(6.dp))
        GlassButton(onClick = onStop, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(tr("结束", "End"), color = RandoRed.onGlass(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RowScope.PillStat(value: String) {
    // 窄屏上三个数挤不下时一起缩小，不截断
    Text(value, Modifier.weight(1f, fill = false), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp))
}

/** 离线地图：没下载 / 下载中 / 下好了，可以更新、删掉 */
@Composable
fun OfflineMapsDialog(state: OfflineMapState, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(Icons.Filled.LocationOn, RandoAccent),
        title = { Text(tr("离线地图", "Offline maps")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("把整个新加坡的地图存到手机里，没信号的地方地图也不会空白。大约 30–60 MB，最好连着 Wi-Fi 下。", "Save the whole Singapore map on your phone so it isn't blank where there's no signal. About 30–60 MB; best on Wi-Fi."), color = InkSoft, fontSize = 14.sp)
                val progress = state.progress
                when {
                    progress != null -> {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = RandoAccent)
                        Text(tr("正在下载 ${(progress * 100).toInt()}% · ${megabytes(state.bytes)}", "Downloading ${(progress * 100).toInt()}% · ${megabytes(state.bytes)}"), color = Ink, fontSize = 15.sp)
                    }
                    state.downloadedAt != null -> Text(
                        tr("已下载", "Downloaded") + " · ${java.text.SimpleDateFormat(tr("M月d日", "d MMM"), Lang.locale).format(java.util.Date(state.downloadedAt))} · ${megabytes(state.bytes)}",
                        color = Ink, fontSize = 15.sp,
                    )
                    else -> Text(tr("还没下载", "Not downloaded"), color = Ink, fontSize = 15.sp)
                }
                state.error?.let { Text(tr("出错了：$it", "Error: $it"), color = RandoRed.onGlass(), fontSize = 13.sp) }
                Text(tr("规划路线、骑行导航还是要联网（算路、搜索在服务器上）。", "Planning and navigation still need internet (routing and search run on servers)."), color = InkSoft, fontSize = 13.sp)
            }
        },
        confirmButton = {
            when {
                state.progress != null -> DialogButton(danger = true, onClick = onCancel) { Text(tr("取消下载", "Cancel download")) }
                state.downloadedAt != null -> DialogButton(onClick = onDownload) { Text(tr("更新", "Update")) }
                else -> DialogButton(onClick = onDownload) { Text(tr("下载", "Download"), fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {
            Row {
                if (state.downloadedAt != null && state.progress == null) DialogButton(danger = true, onClick = onDelete) { Text(tr("删掉", "Delete")) }
                DialogButton(onClick = onDismiss) { Text(tr("关闭", "Close")) }
            }
        },
    )
}

private fun megabytes(bytes: Long): String = String.format(java.util.Locale.US, "%.0f MB", bytes / 1_048_576.0)

/** 设置：家、体重（算卡路里）、主页显示骑过的路、播报声音 */
@OptIn(ExperimentalLayoutApi::class)
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
    /** 手机上的普通话声音，好的排前面 */
    voices: List<VoiceOption> = emptyList(),
    /** 试听；null 是自动挑的那个 */
    onPreviewVoice: (String?) -> Unit = {},
    /** 打开系统的文字转语音设置（换引擎、下载声音） */
    onSystemVoices: () -> Unit = {},
) {
    var weight by remember { mutableStateOf(settings.weightKg.toInt().toString()) }
    var showRidden by remember { mutableStateOf(settings.showRidden) }
    var voice by remember { mutableStateOf(settings.voice) }
    var theme by remember { mutableStateOf(settings.theme) }
    var voiceMenu by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(settings.language) }
    var languageMenu by remember { mutableStateOf(false) }
    val kg = weight.toIntOrNull()?.takeIf { it in 25..250 }
    // 整页的设置：顶上返回、保存，下面一组组凸起的卡片
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(BackdropColor).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(onClick = onDismiss) { Text("‹", color = Ink, fontSize = 30.sp) }
                Text(tr("设置", "Settings"), Modifier.weight(1f), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                PrimaryButton(
                    onClick = { if (kg != null) onSave(settings.copy(weightKg = kg.toDouble(), voice = voice, showRidden = showRidden, language = language, theme = theme)) },
                    modifier = Modifier.padding(end = 8.dp).alpha(if (kg != null) 1f else 0.4f),
                    color = RandoAccent,
                ) { Text(tr("保存", "Save"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsCard(tr("外观", "Appearance")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 语言名字不翻译：看不懂现在的语言时也找得到自己的
                    Text("语言 · Language", Modifier.weight(1f), color = Ink, fontSize = 16.sp)
                    Box {
                        LinkText(languageLabel(language)) { languageMenu = true }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            Language.entries.forEach { l ->
                                DropdownMenuItem(text = { Text(languageLabel(l)) }, onClick = { language = l; languageMenu = false })
                            }
                        }
                    }
                }
                    // 跟随系统 / 浅色 / 深色：三段直接点，不用打开菜单
                    Row(Modifier.fillMaxWidth().neuInset(GlassShapes.Pill).padding(4.dp)) {
                        ThemeMode.entries.forEach { t ->
                            val on = t == theme
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(GlassShapes.Pill)
                                    .background(if (on) RandoAccent else Color.Transparent)
                                    .clickable { theme = t },
                                contentAlignment = Alignment.Center,
                            ) { Text(themeLabel(t), color = if (on) Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                SettingsCard(tr("家", "Home")) {
                Column {
                    Text(homeLabel(home, here), color = InkSoft, fontSize = 13.sp)
                    // 三个按钮一行放不下时整个换到下一行，不要把「删掉」挤成竖排
                    FlowRow {
                        LinkText(tr("用现在的位置", "Use current location"), onHomeHere)
                        LinkText(tr("搜索或在地图上选", "Search or pick on map"), onPickHome)
                        if (home != null) LinkText(tr("删掉", "Delete"), onRemoveHome)
                    }
                }
                }
                SettingsCard(tr("骑行", "Riding")) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter(Char::isDigit).take(3) },
                    label = { Text(tr("体重（公斤）", "Weight (kg)")) },
                    supportingText = { Text(tr("用来算骑车消耗了多少千卡", "Used to estimate calories burned")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("地图上显示骑过的路", "Show ridden roads on map"), color = Ink, fontSize = 16.sp)
                        Text(tr("按骑过几次上色，垫在路线下面", "Coloured by how often you've ridden them, under the route"), color = InkSoft, fontSize = 13.sp)
                    }
                    Switch(checked = showRidden, onCheckedChange = { showRidden = it })
                }
                }
                SettingsCard(tr("声音和音乐", "Sound & music")) {
                MusicAccessRow()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("播报声音", "Voice"), color = Ink, fontSize = 16.sp)
                        Text(voiceLabel(voices, voice), color = InkSoft, fontSize = 13.sp)
                    }
                    Box {
                        LinkText(tr("换一个", "Change")) { voiceMenu = true }
                        DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("自动（不联网里音质最好的）", "Auto (best offline voice)")) },
                                onClick = { voice = null; voiceMenu = false; onPreviewVoice(null) },
                            )
                            voices.forEachIndexed { i, v ->
                                DropdownMenuItem(
                                    text = { Text(tr("声音 ${i + 1}：${VoiceChoice.describe(v)}", "Voice ${i + 1}: ${VoiceChoice.describe(v)}")) },
                                    onClick = { voice = v.name; voiceMenu = false; onPreviewVoice(v.name) },
                                )
                            }
                        }
                    }
                }
                Row {
                    LinkText(tr("试听", "Preview")) { onPreviewVoice(voice) }
                    LinkText(tr("下载更多声音", "Get more voices"), onSystemVoices)
                }
                if (voices.none { it.quality >= 400 }) {
                    Text(tr("手机上没有高音质的中文声音：在系统的文字转语音里把引擎换成 Google，再下载中文语音。", "No high-quality English voice on this phone: in system text-to-speech settings, switch the engine to Google and download an English voice."), color = InkSoft, fontSize = 13.sp)
                }
                }
            }
        }
    }
}

/** 设置页里的一组：小标题加一张凸起的卡片 */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 6.dp), color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column(
            Modifier.fillMaxWidth().neu(GlassShapes.Card).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private fun languageLabel(l: Language): String = when (l) {
    Language.SYSTEM -> tr("跟随系统", "System default")
    Language.ZH -> "中文"
    Language.EN -> "English"
}

/** 「已设好，离这里 3.2 km」：只有坐标，没有地址可显示 */
private fun homeLabel(home: LatLon?, here: LatLon?): String = when {
    home == null -> tr("还没设：设好后能一键回家、说「回家」、坐 MRT 回家", "Not set: once set you can go home in one tap, say “go home”, or take the MRT home")
    here == null -> tr("已设好", "Set")
    Geo.distance(here, home) < 100 -> tr("已设好，就在这里", "Set, you're here")
    else -> tr("已设好，离这里 ${Format.distance(Geo.distance(here, home).toInt())}", "Set, ${Format.distance(Geo.distance(here, home).toInt())} from here")
}

/** 「自动 · 很高音质」「声音 2：高音质 · 要联网」 */
private fun voiceLabel(voices: List<VoiceOption>, name: String?): String {
    if (name == null) return VoiceChoice.best(voices)?.let { tr("自动 · ${VoiceChoice.describe(it)}", "Auto · ${VoiceChoice.describe(it)}") } ?: tr("自动", "Auto")
    val i = voices.indexOfFirst { it.name == name }
    return if (i < 0) tr("选的声音不在了，用自动挑的", "The chosen voice is gone; using auto") else tr("声音 ${i + 1}：${VoiceChoice.describe(voices[i])}", "Voice ${i + 1}: ${VoiceChoice.describe(voices[i])}")
}

/** 坐 MRT 回家的方案：骑到哪个站、怎么坐、再骑多远；可以直接导航到站 */
@Composable
fun MrtHomeDialog(plan: MrtHome, onGo: () -> Unit, onDismiss: () -> Unit) {
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(RandoIcons.Train, RandoAccent),
        title = { Text(tr("坐 MRT 回家", "MRT home")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogInset { Text(plan.text, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Text(tr("只有折叠车折好（不超过 114×64×36 厘米）才能上 MRT；普通自行车不能上车。高峰时段人多，尽量避开。", "Only folded foldable bikes (up to 114×64×36 cm) are allowed on the MRT; regular bikes aren't. Avoid peak hours."), color = InkSoft, fontSize = 13.sp)
            }
        },
        confirmButton = { DialogButton(onClick = onGo) { Text(tr("导航到${plan.board.label}站", "Navigate to ${plan.board.label} station"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("算了", "Never mind")) } },
    )
}
