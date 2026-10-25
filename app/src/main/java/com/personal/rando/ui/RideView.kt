package com.personal.rando.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import com.personal.rando.i18n.tr
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import com.personal.rando.ride.Music
import com.personal.rando.ride.NowPlaying
import com.personal.rando.R
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.LocalHazeState
import com.personal.rando.ui.glass.Motion
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.rando.ride.NavigationState
import com.personal.rando.ride.Fix
import com.personal.rando.group.Teammate
import com.personal.rando.group.VoxLevel
import com.personal.rando.group.WalkieMode
import com.personal.rando.group.WalkieState
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.nextOfEach
import com.personal.rando.ride.Settings
import com.personal.rando.ride.Calories
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

/** 小字：户外太阳底下也要看得清，不能太灰 */
private val DIM = Color(0xFFD0D0D0)
private val CLOCK = SimpleDateFormat("HH:mm", Locale.CHINA)
/** 路口离这么近才在屏幕上提示 */
private const val CROSSING_SHOWN_METERS = 300.0

/**
 * 骑行视图：黑底大字，只留骑车时要看的——下一个路口、当前速度、还剩多久多远。
 * 不导航只记录时（[navigation] 是 null）换成骑了多久、多远。
 * 下面那块平时只留速度一行，点一下展开其余数据和按钮。
 * 地图倾斜跟着人走，只画道路轮廓
 */
@Composable
fun RideView(
    navigation: NavigationState?,
    recording: RecordingState?,
    onMap: () -> Unit,
    onStop: () -> Unit,
    /** 前方天气；不下雨就不显示，下雨也只亮一会儿 */
    weather: RouteWeather? = null,
    /** 导航路线两旁的补给点 */
    amenities: List<AmenityOnRoute> = emptyList(),
    /** 酷热、烟霾、紫外线很强时显示一行 */
    climate: Climate? = null,
    /** 一起骑时队友离我多远的一行 */
    teammates: String? = null,
    /** 一起骑时对讲的状态；不在队里是 null */
    walkie: WalkieState? = null,
    onToggleTalk: () -> Unit = {},
    onTalkLevel: (VoxLevel) -> Unit = {},
    onTalkMode: (WalkieMode) -> Unit = {},
    onTalkMute: (Boolean) -> Unit = {},
    /** 在现在的位置标一处路况 */
    onReport: (HazardType) -> Unit = {},
    /** 体重：算消耗了多少千卡 */
    weightKg: Double = Settings.DEFAULT_WEIGHT,
    onVoice: () -> Unit = {},
    /** 骑累了坐 MRT 回家 */
    onMrt: () -> Unit = {},
    /** 正在听语音指令：听到的字（还没听到是「请说…」）；没在听是 null */
    listening: String? = null,
    /** 地图；第二个参数是上下两块玻璃挡住的地方，镜头要避开 */
    map: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val progress = navigation?.progress
    val timeline = remember(navigation?.route) { navigation?.route?.let(::RouteTimeline) }
    val along = progress?.alongMeters ?: 0.0
    val haze = rememberHazeState()
    // 上面那块展开还是收成一行；下面那块三档：一行、速度加剩余、再加其余数据和按钮
    // 收没收记下来，下次进骑行视图还是一样
    val uiPrefs = UiPrefs(LocalContext.current)
    var topOpen by rememberSaveable { mutableStateOf(uiPrefs.rideTopOpen) }
    var bottom by rememberSaveable { mutableIntStateOf(uiPrefs.rideBottom.takeIf { it in BOTTOM_LINE..BOTTOM_MORE } ?: BOTTOM_NORMAL) }
    LaunchedEffect(topOpen, bottom) {
        uiPrefs.rideTopOpen = topOpen
        uiPrefs.rideBottom = bottom
    }
    var topDrag by remember { mutableFloatStateOf(0f) }
    var bottomDrag by remember { mutableFloatStateOf(0f) }
    val music = rememberMusic()
    FullBrightness()
    val density = LocalDensity.current
    // 上面那块的下沿、下面那块的上沿、整个屏幕的高，都是像素
    var topEdge by remember { mutableIntStateOf(0) }
    var bottomEdge by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    val cover = with(density) {
        val gap = (bottomEdge - topEdge).coerceAtLeast(0)
        // 人放在露出来那块的偏下面，前方的路多看一些
        PaddingValues(top = (topEdge + gap * 0.4f).toDp(), bottom = (height - bottomEdge).coerceAtLeast(0).toDp())
    }
    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { height = it.height }) {
            // 地图铺满，上下两块深色玻璃浮在上面
            map(Modifier.fillMaxSize().hazeSource(haze), cover)
            // 上面那块：不加框，黑色往下渐隐到地图里；往上滑收成一行，往下滑展开
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent))
                    .statusBarsPadding()
                    .draggable(
                        rememberDraggableState { topDrag += it },
                        Orientation.Vertical,
                        onDragStarted = { topDrag = 0f },
                        onDragStopped = {
                            val step = with(density) { SWIPE.toPx() }
                            if (topDrag < -step) topOpen = false else if (topDrag > step) topOpen = true
                        },
                    )
                    .animateContentSize(Motion.smooth())
                    .onGloballyPositioned { topEdge = it.boundsInParent().bottom.toInt() }
                    .padding(top = 8.dp),
            ) {
                if (topOpen) {
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
                    Extras(briefly(weather?.takeIf { !it.dry }?.summary), briefly(climate?.warning), amenities.takeIf { progress != null }.orEmpty(), along, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp))
                    teammates?.let { TeammatesRow(it, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp)) }
                    walkie?.let {
                        RideWalkieRow(it, onToggleTalk, onTalkLevel, onTalkMode, Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp).padding(top = 6.dp), onTalkMute)
                    }
                    listening?.let {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot("#E35B4F")
                            Text(tr("正在听：$it", "Listening: $it"), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    MusicBar(music, Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 10.dp))
                } else {
                    TopLine(navigation, recording, music, Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp))
                }
                Grabber(
                    label = if (topOpen) tr("收起上面", "Collapse top") else tr("展开上面", "Expand top"),
                    onClick = { topOpen = !topOpen },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            // 下面那块：三档，往下滑一档档收（最小只剩一行速度），往上滑一档档放（最大有其余数据和按钮）
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black))
                    .navigationBarsPadding()
                    .draggable(
                        rememberDraggableState { bottomDrag += it },
                        Orientation.Vertical,
                        onDragStarted = { bottomDrag = 0f },
                        onDragStopped = {
                            val step = with(density) { SWIPE.toPx() }
                            if (bottomDrag > step) bottom = (bottom - 1).coerceAtLeast(BOTTOM_LINE)
                            else if (bottomDrag < -step) bottom = (bottom + 1).coerceAtMost(BOTTOM_MORE)
                        },
                    )
                    .animateContentSize(Motion.smooth())
                    .onGloballyPositioned { bottomEdge = it.boundsInParent().top.toInt() }
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 只有这根横条能点；整块都能点的话，颠一下碰到就变了
                Grabber(
                    label = if (bottom == BOTTOM_MORE) tr("收起", "Less") else tr("更多", "More"),
                    onClick = { bottom = if (bottom == BOTTOM_MORE) BOTTOM_NORMAL else bottom + 1 },
                )
                val speed = String.format(Locale.US, "%.1f", (recording?.currentSpeed ?: 0.0) * 3.6)
                val left = if (navigation != null) {
                    (progress?.let { (it.remainingSeconds + 59) / 60 }?.toString() ?: "--") to tr("剩余 分", "min left")
                } else {
                    Format.clock(recording?.movingSeconds ?: 0) to tr("骑行时间", "Ride time")
                }
                val right = if (navigation != null) {
                    (progress?.let { String.format(Locale.US, "%.1f", it.remainingMeters / 1000) } ?: "--") to tr("剩余 公里", "km left")
                } else {
                    String.format(Locale.US, "%.1f", (recording?.distanceMeters ?: 0.0) / 1000) to tr("已骑 公里", "km ridden")
                }
                if (bottom == BOTTOM_LINE) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom) {
                        SmallStat(left.first, left.second, Modifier.weight(1f), Alignment.Start)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(speed, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
                            Text(" km/h", color = DIM, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        SmallStat(right.first, right.second, Modifier.weight(1f), Alignment.End, highlight = navigation != null)
                    }
                } else {
                    timeline?.let { RouteStrip(it, along, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 6.dp)) }
                    AnimatedContent(
                        targetState = speed,
                        transitionSpec = {
                            (slideInVertically(Motion.smooth()) { it / 3 } + fadeIn(tween(150)))
                                .togetherWith(slideOutVertically(Motion.smooth()) { -it / 3 } + fadeOut(tween(100)))
                        },
                        label = "speed",
                    ) { v -> Text(v, color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, lineHeight = 96.sp, letterSpacing = (-2).sp) }
                    Text(tr("公里/时", "km/h"), color = DIM, fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BigStat(left.first, left.second, Modifier.weight(1f))
                        Spacer(Modifier.weight(0.6f))
                        BigStat(right.first, right.second, Modifier.weight(1f), highlight = navigation != null)
                    }
                    AnimatedVisibility(bottom == BOTTOM_MORE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
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
                            Icon(MicIcon, contentDescription = tr("语音指令", "Voice command"), tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        GlassButton(onClick = onMap, modifier = Modifier.weight(1f), tone = GlassTone.Dark) {
                            Text(tr("地图", "Map"), color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        ReportButton(onReport, onMrt, Modifier.weight(1f))
                        GlassButton(onClick = onStop, modifier = Modifier.weight(1f), tone = GlassTone.Tinted(RandoRed)) {
                            Text(tr("结束", "End"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}

/** 面板边上的短横条：点一下收起 / 展开（也可以按住整块往上、往下滑） */
@Composable
private fun Grabber(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 120.dp, height = 28.dp)
            .clip(GlassShapes.Pill)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 44.dp, height = 5.dp).clip(GlassShapes.Pill).background(Color(0xFF6A707A)))
    }
}

/** 上面收起时只剩一行：箭头、距离、怎么走；在放歌的话右边两个小按钮 */
@Composable
private fun TopLine(navigation: NavigationState?, recording: RecordingState?, music: MusicState, modifier: Modifier) {
    Row(modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        val progress = navigation?.progress
        when {
            navigation == null -> Text(
                tr("自由骑行", "Free ride") + (recording?.let { tr(" · 爬升 ${it.ascendMeters.toInt()} 米", " · ${it.ascendMeters.toInt()} m climb") } ?: ""),
                Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            progress == null || progress.arrived || navigation.rerouting || progress.offRoute -> Text(
                when {
                    progress == null -> navigation.waitingText
                    progress.arrived -> tr("已到达目的地", "You have arrived")
                    else -> tr("已偏离路线，正在重新规划…", "Off route, rerouting…")
                },
                Modifier.weight(1f), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            else -> {
                TurnIcon(progress.next, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                val distance = Format.distance(progress.distanceToNext.toInt())
                Text(distance.substringBefore(' '), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
                Text(" " + distance.substringAfter(' ', ""), color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(progress.next?.text ?: tr("到达终点", "Arrive"), Modifier.weight(1f), color = DIM, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        music.now?.let { now ->
            RoundKey(if (now.playing) tr("暂停", "Pause") else tr("播放", "Play"), music::playPause) { MediaGlyph(if (now.playing) Glyph.PAUSE else Glyph.PLAY, Color.White) }
            Spacer(Modifier.width(6.dp))
            RoundKey(tr("下一首", "Next"), music::next) { MediaGlyph(Glyph.NEXT, Color.White) }
        }
    }
}

@Composable
private fun RoundKey(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1C1F24)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 上面那块里的音乐：封面色块、歌名、歌手 · App，上一首 / 播放暂停 / 下一首；没在放就不显示 */
@Composable
private fun MusicBar(music: MusicState, modifier: Modifier) {
    val now = music.now ?: return
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF15181D))
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (now.title != null) {
            Column(Modifier.weight(1f)) {
                Text(now.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(now.artist, now.app).joinToString(" · "), color = DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("音乐", "Music"), color = DIM, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                // 没有通知使用权看不到歌名；点一下去系统里打开
                Text(
                    tr("显示歌名", "Show song"),
                    Modifier.clip(GlassShapes.Pill).clickable(onClick = music::grant).padding(horizontal = 8.dp, vertical = 6.dp),
                    color = AccentGlow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = tr("上一首", "Previous"), onClick = music::previous), contentAlignment = Alignment.Center) { MediaGlyph(Glyph.PREVIOUS, Color.White) }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White)
                .clickable(onClickLabel = if (now.playing) tr("暂停", "Pause") else tr("播放", "Play"), onClick = music::playPause),
            contentAlignment = Alignment.Center,
        ) { MediaGlyph(if (now.playing) Glyph.PAUSE else Glyph.PLAY, Color.Black) }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = tr("下一首", "Next"), onClick = music::next), contentAlignment = Alignment.Center) { MediaGlyph(Glyph.NEXT, Color.White) }
    }
}

private enum class Glyph { PLAY, PAUSE, NEXT, PREVIOUS }

/** 播放、暂停、上一首、下一首：自己画，core 图标里只有播放 */
@Composable
private fun MediaGlyph(glyph: Glyph, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        fun tri(x0: Float, x1: Float) = Path().apply { moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h); close() }
        when (glyph) {
            Glyph.PLAY -> drawPath(tri(w * 0.15f, w * 0.95f), color)
            Glyph.PAUSE -> {
                drawRoundRect(color, Offset(w * 0.15f, 0f), Size(w * 0.24f, h), CornerRadius(2f))
                drawRoundRect(color, Offset(w * 0.61f, 0f), Size(w * 0.24f, h), CornerRadius(2f))
            }
            Glyph.NEXT -> {
                drawPath(tri(0f, w * 0.8f), color)
                drawRect(color, Offset(w * 0.8f, 0f), Size(w * 0.18f, h))
            }
            Glyph.PREVIOUS -> {
                drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.2f, h / 2); lineTo(w, h); close() }, color)
                drawRect(color, Offset(w * 0.02f, 0f), Size(w * 0.18f, h))
            }
        }
    }
}

/** 骑行视图里的音乐：每秒问一次在放什么 */
class MusicState(private val music: Music, private val context: android.content.Context) {
    var now by mutableStateOf<NowPlaying?>(null)
        internal set

    fun playPause() { music.playPause(); refreshSoon() }
    fun next() = music.next()
    fun previous() = music.previous()
    fun grant() { runCatching { context.startActivity(Music.settingsIntent()) } }

    /** 只知道「有声音」时连着几秒都有才算，提示音「嘀」一下不会冒出音乐条 */
    private var heard = 0

    internal fun refresh() {
        val got = runCatching { music.now() }.getOrNull()
        heard = if (got != null && got.title == null) heard + 1 else 0
        now = got?.takeIf { it.title != null || heard >= HEARD_SECONDS }
    }
    private fun refreshSoon() { now = now?.copy(playing = !(now?.playing ?: false)) }

    private companion object {
        const val HEARD_SECONDS = 3
    }
}

@Composable
fun rememberMusic(): MusicState {
    val context = LocalContext.current
    val state = remember { MusicState(Music(context), context) }
    LaunchedEffect(state) {
        while (true) {
            state.refresh()
            delay(1_000)
        }
    }
    return state
}

/** 往上、往下滑过这么多才收起 / 展开 */
private val SWIPE = 36.dp
private const val BOTTOM_LINE = 0
private const val BOTTOM_NORMAL = 1
private const val BOTTOM_MORE = 2

@Composable
private fun SmallStat(value: String, label: String, modifier: Modifier, align: Alignment.Horizontal, highlight: Boolean = false) {
    Column(modifier, horizontalAlignment = align) {
        Text(value, color = if (highlight) RandoYellow else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = DIM, fontSize = 12.sp)
    }
}

/** 骑行视图开着时屏幕调到最亮，户外看得清；退出时还给系统 */
@Composable
private fun FullBrightness() {
    val activity = LocalContext.current.let { c ->
        generateSequence(c) { (it as? ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? Activity }
    } ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = before } }
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
        if (navigating) add(String.format(Locale.US, "%.1f", recording.distanceMeters / 1000) to tr("已骑 公里", "km ridden"))
        add(kmh(average) to tr("均速", "avg km/h"))
        add("${Calories.of(recording.distanceMeters, recording.movingSeconds, recording.ascendMeters, weightKg)}" to tr("千卡", "kcal"))
        add("${recording.ascendMeters.toInt()}" to tr("爬升 米", "m climb"))
        if (!navigating) add(Format.clock(((now - recording.startedAt) / 1000).coerceAtLeast(0)) to tr("全程用时", "elapsed"))
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
                    append(tr("${ahead.current.kind.label} · 还有 ${Format.distance(ahead.remainingInCurrent.toInt())}", "${ahead.current.kind.label} · ${Format.distance(ahead.remainingInCurrent.toInt())} more"))
                    ahead.next?.let { append(tr("，然后${it.kind.label} ${Format.distance(it.length.toInt())}", ", then ${it.kind.label} ${Format.distance(it.length.toInt())}")) }
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
                    tr("前方 ${Format.distance(a.distance.toInt())} ${a.structure.kind.label} · ${a.structure.access}", "${a.structure.kind.label} in ${Format.distance(a.distance.toInt())} · ${a.structure.access}"),
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }
        crossing?.let { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(c.type.color)
                Text(
                    tr("前方 ${Format.distance(c.distance.toInt())} 过马路 · ${c.type.label}", "Road crossing in ${Format.distance(c.distance.toInt())} · ${c.type.label}"),
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
            progress.arrived -> RideMessage(tr("已到达目的地", "You have arrived"))
            navigation.rerouting || progress.offRoute -> RideMessage(tr("已偏离路线，正在重新规划…", "Off route, rerouting…"))
            else -> {
                val next = progress.next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TurnIcon(next, size = 72.dp)
                    Spacer(Modifier.width(20.dp))
                    val distance = Format.distance(progress.distanceToNext.toInt())
                    val number = distance.substringBefore(' ')
                    val unit = distance.substringAfter(' ', "")
                    Text(number, color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Spacer(Modifier.width(8.dp))
                    Text(unit, color = Color.White, fontSize = 28.sp)
                }
                Text(
                    next?.text ?: tr("到达终点", "Arrive at destination"),
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
private fun BigStat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (highlight) RandoYellow else Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
        Text(label, color = DIM, fontSize = 14.sp)
    }
}

/** 天气提醒、下雨都念过了，屏幕上只亮一会儿；变了再亮 */
@Composable
private fun briefly(text: String?): String? {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        shown = text
        delay(WARNING_SHOWN_MS)
        shown = null
    }
    return shown
}

private const val WARNING_SHOWN_MS = 30_000L

/** 前方最近的补给（五公里内，最多三种）和要下的雨 */
@Composable
private fun Extras(rain: String?, warning: String?, amenities: List<AmenityOnRoute>, along: Double, modifier: Modifier) {
    val next = amenities.nextOfEach(along).entries
        .filter { it.value <= AMENITY_SHOWN_METERS }
        .sortedBy { it.value }
        .take(3)
    if (next.isEmpty() && rain == null && warning == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (next.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(next.first().key.color)
                Text(
                    tr("前方 ", "Ahead: ") + next.joinToString(" · ") { "${it.key.label} ${Format.distance(it.value.toInt())}" },
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
                // 占一行就够了，长了字缩小
                Text(
                    it,
                    color = Color(0xFFFFD08A),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp),
                )
            }
        }
    }
}

/** 「路况」：点开选施工、封路、路面不好，标在现在的位置 */
@Composable
private fun ReportButton(onReport: (HazardType) -> Unit, onMrt: () -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), tone = GlassTone.Dark) {
            Text(tr("路况", "Report"), color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GlassShapes.Card, containerColor = Color(0xF2222624)) {
            HazardType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(tr("这里${type.label}", type.label), color = Color.White, fontSize = 17.sp) },
                    leadingIcon = { Dot(type.color) },
                    onClick = {
                        open = false
                        onReport(type)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(tr("骑累了，坐 MRT 回家", "Tired? Take the MRT home"), color = Color.White, fontSize = 17.sp) },
                leadingIcon = { Dot("#D42E12") },
                onClick = {
                    open = false
                    onMrt()
                },
            )
        }
    }
}

/** 补给点在这么远以内才在骑行视图上说 */
private const val AMENITY_SHOWN_METERS = 5000.0

/** 不导航时顶上那块：在记录，骑了多久 */
@Composable
private fun FreeRide(recording: RecordingState?, modifier: Modifier) {
    // 只占一行：骑了多远、多久下面那块已经有了
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(tr("自由骑行", "Free ride"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text(
            recording?.let {
                tr("爬升 ${it.ascendMeters.toInt()} 米", "${it.ascendMeters.toInt()} m climb") +
                    // 停下来不算时间，告诉人一声
                    if (it.movingSeconds > 0 && it.currentSpeed == 0.0) tr(" · 已自动暂停", " · Auto-paused") else ""
            } ?: tr("记录中", "Recording"),
            color = DIM,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
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
    /** 被上下玻璃挡住的地方，跟着人走时把人放在露出来的那块里 */
    cover: PaddingValues = PaddingValues(),
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
                // 骑着是绿箭头，朝前进方向；停着是白边绿点，外面的小三角指手机朝向。
                // 不用 foregroundTintColor：它把骑行时那张图整个染绿，箭头就看不见了
                val puck = LocationComponentOptions.builder(context)
                    .gpsDrawable(R.drawable.ride_puck_arrow)
                    .foregroundDrawable(R.drawable.ride_puck_dot)
                    .bearingTintColor(android.graphics.Color.parseColor("#3DDC84"))
                    .minZoomIconScale(1f)
                    .maxZoomIconScale(1.2f)
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
        // 断了信号的队友不会再发位置，列表不变；隔一会儿重画一次，过时的点才会变灰
        while (true) {
            MapLayers.showTeam(s, teammates)
            delay(MapLayers.TEAM_REFRESH_MS)
        }
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
    val density = LocalDensity.current
    LaunchedEffect(follow, cover) {
        val top = with(density) { cover.calculateTopPadding().toPx() }.toDouble()
        val bottom = with(density) { cover.calculateBottomPadding().toPx() }.toDouble()
        follow.padding = doubleArrayOf(0.0, top, 0.0, bottom)
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        RecenterButton(follow, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), dark = true)
    }
}

private const val RIDE_ZOOM = 17.5
private const val RIDE_TILT = 55.0
