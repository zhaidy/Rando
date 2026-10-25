package com.personal.rando.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.i18n.tr
import com.personal.rando.nav.Geo
import com.personal.rando.ride.NavigationState
import com.personal.rando.routing.CrossingType
import com.personal.rando.places.CoveredStretch
import com.personal.rando.routing.LatLon
import com.personal.rando.routing.RoadKind
import com.personal.rando.routing.Route
import com.personal.rando.routing.crossings
import com.personal.rando.routing.pieces
import com.personal.rando.routing.structures
import com.personal.rando.routing.steepestClimb
import com.personal.rando.places.AmenityOnRoute
import com.personal.rando.places.AmenityType
import kotlin.math.roundToInt
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.Motion
import com.personal.rando.ui.glass.PrimaryButton

// ───────────────────────── 导航 ─────────────────────────

@Composable
fun NavigationOverlay(
    navigation: NavigationState,
    onRideView: () -> Unit,
    onStop: () -> Unit,
) {
    val progress = navigation.progress
    Column(Modifier.fillMaxSize()) {
        NavBanner(
            navigation,
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        GlassPanel(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            // 一行：剩余、还要，右边骑行视图、结束
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        RollingStat(Format.distance(progress.remainingMeters.toInt()), tr("剩余", "Left"))
                        RollingStat(Format.duration(progress.remainingSeconds), tr("还要", "ETA"))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                GlassButton(onClick = onRideView, shape = GlassShapes.Button, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
                    Text(tr("骑行视图", "Ride view"), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(onClick = onStop, color = RandoRed) {
                    Text(tr("结束", "End"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 数字变化时往上滚一格 */
@Composable
private fun RollingStat(value: String, label: String) {
    Column {
        AnimatedContent(
            targetState = value,
            transitionSpec = { rollUp() },
            label = "roll",
        ) { v -> Text(v, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1) }
        Text(label, color = InkSoft, fontSize = 13.sp)
    }
}

private fun rollUp(): ContentTransform = ContentTransform(
    targetContentEnter = slideInVertically(Motion.smooth()) { it / 2 } + fadeIn(tween(160)),
    initialContentExit = slideOutVertically(Motion.smooth()) { -it / 2 } + fadeOut(tween(120)),
    sizeTransform = SizeTransform(clip = false),
)

/** 导航时顶部的绿玻璃：箭头、离路口多远、怎么走；换路口时箭头从下面滑上来 */
@Composable
fun NavBanner(navigation: NavigationState, modifier: Modifier = Modifier, tone: GlassTone = GlassTone.Tinted(RandoAccent)) {
    val progress = navigation.progress
    GlassPanel(modifier, shape = GlassShapes.Card, tone = tone) {
        val key = when {
            progress == null -> "wait"
            progress.arrived -> "arrived"
            navigation.rerouting || progress.offRoute -> "off"
            else -> "turn:${progress.next?.pointIndex}"
        }
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                (slideInVertically(Motion.bouncy()) { it } + fadeIn(tween(180)) + scaleIn(Motion.bouncy(), initialScale = 0.9f))
                    .togetherWith(slideOutVertically(Motion.smooth()) { -it } + fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            label = "banner",
        ) { k ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    k == "wait" -> NavText(navigation.waitingText)
                    k == "arrived" -> NavText(tr("已到达目的地", "You have arrived"))
                    k == "off" -> NavText(tr("已偏离路线，正在重新规划…", "Off route, rerouting…"))
                    progress != null -> {
                        val next = progress.next
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            TurnIcon(next, size = 44.dp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                Format.distance(progress.distanceToNext.toInt()),
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(next?.text ?: tr("到达终点", "Arrive at destination"), color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp)
                            val following = progress.following
                            if (following != null && progress.distanceNextToFollowing <= 100) {
                                Text(tr("然后${following.text}", "Then ${following.text.replaceFirstChar { it.lowercase() }}"), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavText(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

// ───────────────────────── 点一段路 / 在地图上看 ─────────────────────────

/**
 * 地图上要单独指给你看的一处：点到的一段路，或者卡片上某条提示对应的那几处
 * （马路、路口、天桥、推车）。[points] 只有一个点时是个路口
 */
data class SegmentInfo(
    val title: String,
    val color: String,
    val lengthMeters: Int,
    /** 看实景就看这里 */
    val location: LatLon,
    val points: List<LatLon>,
    val detail: String?,
    /** 是一段路时是什么路；路口、补给点是 null */
    val kind: RoadKind? = null,
)

/**
 * 这段路能标成「其实是马路」/ 撤销：自行车道、公园连道、小路可以标；
 * 标过的已经按马路算了，再点是撤销。[marked]：是不是标过
 */
fun SegmentInfo.markLabel(marked: Boolean): String? = when {
    points.size < 2 || kind == null -> null
    marked && kind == RoadKind.ROAD -> tr("不是马路", "Not a road")
    kind in setOf(RoadKind.PCN, RoadKind.CYCLEWAY, RoadKind.PATH) -> tr("其实是马路", "Actually a road")
    else -> null
}

/** 卡片上能点的提示，点了在地图上一处处看 */
/** [AMENITIES] 的几处不在路线数据里，由界面用查到的补给点来填 */
enum class NoteTarget { ROAD, CROSSINGS, STRUCTURES, PUSH, CLIMB, AMENITIES, COVERED }

/** 离点击位置 [toleranceMeters] 以内最近的那段路；都太远就是 null */
fun Route.segmentAt(tap: LatLon, toleranceMeters: Double): SegmentInfo? {
    var best: Pair<Double, SegmentInfo>? = null
    for (piece in pieces()) {
        for (i in 0 until piece.points.size - 1) {
            val a = piece.points[i]
            val b = piece.points[i + 1]
            val (t, d) = Geo.project(tap, a, b)
            if (d > toleranceMeters || (best != null && d >= best.first)) continue
            val snapped = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            best = d to SegmentInfo(
                piece.kind.label, piece.kind.color, lengthOf(piece.points), snapped, piece.points, detailFor(piece.kind, snapped), piece.kind,
            )
        }
    }
    return best?.second
}

/** 某条提示对应的那几处，按路线先后排 */
fun Route.targets(target: NoteTarget): List<SegmentInfo> = when (target) {
    NoteTarget.ROAD -> piecesWithin(RoadKind.ROAD, skipEnds = false)
    NoteTarget.PUSH -> piecesWithin(RoadKind.PUSH, skipEnds = true)
    NoteTarget.CROSSINGS -> crossings().map {
        SegmentInfo(tr("过马路", "Road crossing"), RoadKind.CROSSING.color, 0, it.location, listOf(it.location), it.type.label)
    }
    NoteTarget.STRUCTURES -> structures().map {
        // 看实景看入口：桥上、通道里面多半没有街景
        SegmentInfo(it.kind.label, "#FFFFFF", it.lengthMeters, it.path.first(), it.path, it.access)
    }
    NoteTarget.AMENITIES, NoteTarget.COVERED -> emptyList()
    NoteTarget.CLIMB -> listOfNotNull(
        steepestClimb()?.let { c ->
            val path = points.subList(c.fromIndex, c.toIndex + 1)
            SegmentInfo(
                tr("上坡", "Climb"), CLIMB_COLOR, c.lengthMeters.toInt(), path[path.size / 2], path,
                tr("平均坡度 ${Format.percent(c.grade)}，爬 ${c.gainMeters.roundToInt()} 米", "Avg grade ${Format.percent(c.grade)}, ${c.gainMeters.roundToInt()} m up"),
            )
        }
    )
}

/** 上坡用琥珀色，和 RandoAmber 一样 */
private const val CLIMB_COLOR = "#C98A1B"

private fun Route.piecesWithin(kind: RoadKind, skipEnds: Boolean): List<SegmentInfo> {
    val all = pieces()
    val total = all.sumOf { lengthOf(it.points) }
    var along = 0
    val result = mutableListOf<SegmentInfo>()
    for (p in all) {
        val length = lengthOf(p.points)
        val nearEnds = along < com.personal.rando.routing.END_PUSH_METERS ||
            along + length > total - com.personal.rando.routing.END_PUSH_METERS
        if (p.kind == kind && !(skipEnds && nearEnds)) {
            result += SegmentInfo(kind.label, kind.color, length, p.points[p.points.size / 2], p.points, null, kind)
        }
        along += length
    }
    return result
}

/** 有顶棚的路一段段看 */
fun List<CoveredStretch>.coveredTargets(): List<SegmentInfo> = map {
    SegmentInfo(
        tr("有顶棚的路", "Covered path"), COVERED_COLOR, it.lengthMeters.toInt(), it.points[it.points.size / 2], it.points,
        tr("第 ${Format.km(it.startMeters)} 公里，下雨可以躲", "At km ${Format.km(it.startMeters)}, shelter from rain"),
    )
}

/** 和避雨亭一个颜色 */
const val COVERED_COLOR = "#6D4C41"

/** 沿途补给一处处看 */
fun List<AmenityOnRoute>.targets(): List<SegmentInfo> = map {
    val a = it.amenity
    SegmentInfo(
        a.name?.let { n -> "${a.type.label} · $n" } ?: a.type.label,
        a.type.color,
        0,
        a.location,
        listOf(a.location),
        if (a.type == AmenityType.PARKING) tr("离终点 ${it.offRouteMeters.toInt()} 米", "${it.offRouteMeters.toInt()} m from destination")
        else tr("第 ${Format.km(it.alongMeters)} 公里，离路线 ${it.offRouteMeters.toInt()} 米", "At km ${Format.km(it.alongMeters)}, ${it.offRouteMeters.toInt()} m off route"),
    )
}

private fun lengthOf(points: List<LatLon>): Int = points.zipWithNext { p, q -> Geo.distance(p, q) }.sum().toInt()

private fun Route.detailFor(kind: RoadKind, at: LatLon): String? {
    structures().firstOrNull { s -> s.path.any { Geo.distance(it, at) < 40 } }?.let { return "${it.kind.label} · ${it.access}" }
    if (kind == RoadKind.CROSSING) {
        val crossing = crossings().minByOrNull { Geo.distance(it.location, at) }?.takeIf { Geo.distance(it.location, at) < 60 }
        return crossing?.type?.let { if (it == CrossingType.UNKNOWN) tr("路口类型没标明", "Crossing type not tagged") else it.label }
    }
    return null
}

/**
 * 地图上指给你看时，底部的玻璃卡片：是什么、多长，第几处；
 * 「下一处」翻到下一个，「看实景」打开街景
 */
@Composable
fun SegmentCard(
    info: SegmentInfo,
    index: Int,
    count: Int,
    onNext: () -> Unit,
    onStreetView: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 「其实是马路」「不是马路」；null 不显示 */
    markLabel: String? = null,
    onMark: () -> Unit = {},
) {
    GlassPanel(modifier.fillMaxWidth(), shape = GlassShapes.Card) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(kindColor(info.color)).then(
                    if (info.color == "#FFFFFF") Modifier.background(InkSoft.copy(alpha = 0.3f), CircleShape) else Modifier
                ))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info.title + if (count > 1) tr(" · 第 ${index + 1}/$count 处", " · ${index + 1} of $count") else "",
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val parts = listOfNotNull(info.detail, info.lengthMeters.takeIf { it > 0 }?.let { tr("约 ${Format.distance(it)}", "about ${Format.distance(it)}") })
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = InkSoft, fontSize = 14.sp)
                }
                IconTap(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = tr("关闭", "Close"), tint = InkSoft) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (count > 1) {
                    GlassButton(onClick = onNext, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(tr("下一处", "Next"), color = Ink, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (markLabel != null) {
                    GlassButton(onClick = onMark, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(markLabel, color = Ink, fontSize = 15.sp)
                    }
                }
                PrimaryButton(onClick = onStreetView, color = RandoAccent) {
                    Text(tr("看实景", "Street view"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
