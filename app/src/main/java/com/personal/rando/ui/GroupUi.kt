package com.personal.rando.ui

import androidx.compose.material.icons.filled.Person
import com.personal.rando.ui.glass.PrimaryButton
import com.personal.rando.ui.glass.neu
import com.personal.rando.ride.CRASH_COUNTDOWN_MS
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.i18n.tr
import com.personal.rando.group.GroupState
import com.personal.rando.group.NearbyTeam
import com.personal.rando.places.Hazard
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.personal.rando.group.SosAlert
import com.personal.rando.ride.CrashAlert
import com.personal.rando.group.SharedRoute
import com.personal.rando.group.TeamCode
import com.personal.rando.group.Teammate
import com.personal.rando.group.VoxLevel
import com.personal.rando.group.WalkieState
import com.personal.rando.group.WalkieMode
import com.personal.rando.nav.Geo
import com.personal.rando.routing.LatLon
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.liquidGlass

/** 队友的颜色：按 id 分，同一个人每次一样 */
private val MATE_COLORS = listOf("#8E24AA", "#1E88E5", "#E53935", "#00897B", "#F4511E", "#3949AB", "#C0CA33", "#6D4C41")

fun mateColor(id: String): String = MATE_COLORS[Math.floorMod(id.hashCode(), MATE_COLORS.size)]

/**
 * 建队或者加入：先填名字；附近有队伍就列在上面，点一下加入；
 * 没有就建一个新队，或者输入别人的队伍码。[nearby] 是 null 时还在找
 */
@Composable
fun GroupDialog(
    configured: Boolean,
    name: String,
    onCreate: (name: String) -> Unit,
    onJoin: (code: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    nearby: List<NearbyTeam>? = emptyList(),
) {
    var myName by remember { mutableStateOf(name) }
    var joining by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val normalized = TeamCode.normalize(code)
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(Icons.Filled.Person, Color(0xFF7A5AF8)),
        title = { Text(tr("一起骑", "Group ride")) },
        text = {
            if (!configured) {
                Text(tr("一起骑的服务器还没部署好，部署好以后更新一下 App 就能用。", "The group ride server isn't set up yet. Update the app once it is."))
                return@RandoDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("队友之间互相看到位置，掉队了会提醒。同一个队伍码的人在一个队里。", "Riders see each other on the map and get alerted when someone falls behind. Everyone with the same team code is on one team."), color = InkSoft, fontSize = 14.sp)
                OutlinedTextField(value = myName, onValueChange = { myName = it.take(20) }, label = { Text(tr("你的名字", "Your name")) }, singleLine = true)
                when {
                    nearby == null -> Text(tr("正在找附近的队伍…", "Looking for teams nearby…"), color = InkSoft, fontSize = 14.sp)
                    nearby.isNotEmpty() -> {
                        Text(tr("附近的队伍", "Teams nearby"), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        nearby.forEach { team ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(team.namesText.ifEmpty { tr("队伍 ${team.code}", "Team ${team.code}") }, color = Ink, fontSize = 15.sp)
                                    Text("${team.code} · ${Format.distance(team.meters)}", color = InkSoft, fontSize = 13.sp)
                                }
                                DialogButton(enabled = myName.isNotBlank(), primary = true, onClick = { onJoin(team.code, myName.trim()) }) { Text(tr("加入", "Join")) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !joining, onClick = { joining = false }, label = { Text(tr("建一个队", "New team")) })
                    FilterChip(selected = joining, onClick = { joining = true }, label = { Text(tr("输入队伍码", "Enter code")) })
                }
                if (joining) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(12) },
                        label = { Text(tr("队伍码", "Team code")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            }
        },
        confirmButton = {
            if (configured) {
                DialogButton(
                    enabled = myName.isNotBlank() && (!joining || normalized != null),
                    onClick = {
                        if (joining) normalized?.let { onJoin(it, myName.trim()) } else onCreate(myName.trim())
                    },
                ) { Text(if (joining) tr("加入", "Join") else tr("建队", "Create")) }
            }
        },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(if (configured) tr("取消", "Cancel") else tr("知道了", "OK")) } },
    )
}

/**
 * 一起骑中的小胶囊：队伍码、几个人、连没连上；分享队伍码、退出。
 * 下面一行是声控对讲：开关、灵敏度（点一下换一档），在说话、在听谁说
 */
@Composable
fun GroupPill(
    state: GroupState,
    onShare: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    walkie: WalkieState = WalkieState(),
    onToggleTalk: () -> Unit = {},
    onLevel: (VoxLevel) -> Unit = {},
    onMode: (WalkieMode) -> Unit = {},
    onTest: () -> Unit = {},
    onMute: (Boolean) -> Unit = {},
) {
    val dark = LocalPalette.current.dark
    // 浅紫色的卡片，和地图、底部卡片分得开
    val lavender = if (dark) Color(0xFF262236) else Color(0xFFF4F1FF)
    val chip = if (dark) Color.White.copy(alpha = 0.08f) else Color.White
    Column(
        modifier
            .fillMaxWidth()
            .dropShadow(GlassShapes.Card, Shadow(radius = 24.dp, color = Color(0xFF3C288C).copy(alpha = 0.2f), offset = DpOffset(0.dp, 10.dp)))
            .clip(GlassShapes.Card)
            .background(lavender)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GroupHeader(state, onShare, onLeave, chip)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chip).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                walkieText(walkie),
                color = if (walkie.problem != null) RandoAmber.onGlass() else Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 对讲开关：一整条，开着是绿色
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(GlassShapes.Button)
                .background(if (walkie.on) RandoGreen else chip)
                .clickable(onClick = onToggleTalk),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MicIcon, contentDescription = null, tint = if (walkie.on) Color.White else Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (walkie.on) tr("对讲开着", "Talk on") else tr("开对讲", "Talk"), color = if (walkie.on) Color.White else Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (walkie.on) {
            // 开着对讲时另起一行：闭麦、实时 / 整句、灵敏度、试一下
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupKey(if (walkie.muted) tr("开麦", "Unmute") else tr("闭麦", "Mute"), if (walkie.muted) RandoAmber else chip, Modifier.weight(1f), if (walkie.muted) Color.White else Ink) { onMute(!walkie.muted) }
                GroupKey(walkie.mode.label, chip, Modifier.weight(1f)) { onMode(WalkieMode.entries[(walkie.mode.ordinal + 1) % WalkieMode.entries.size]) }
                GroupKey(tr("灵敏度：${walkie.level.label}", "Sensitivity: ${walkie.level.label}"), chip, Modifier.weight(1f)) { onLevel(VoxLevel.entries[(walkie.level.ordinal + 1) % VoxLevel.entries.size]) }
                GroupKey(tr("试一下", "Test"), if (walkie.testing) RandoGreen else chip, Modifier.weight(1f), if (walkie.testing) Color.White else Ink, onTest)
            }
        }
    }
}

@Composable
private fun GroupKey(label: String, background: Color, modifier: Modifier, color: Color = Ink, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(14.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** 对讲现在怎样：一句话 */
fun walkieText(w: WalkieState): String = when {
    w.problem != null -> tr("对讲出问题了：${w.problem}", "Talk problem: ${w.problem}")
    w.talking.isNotEmpty() && w.sending -> tr("${w.talking.joinToString("、")} 和你都在说", "You and ${w.talking.joinToString(", ")} are talking")
    w.talking.isNotEmpty() -> tr("${w.talking.joinToString("、")} 在说话…", "${w.talking.joinToString(", ")} ${if (w.talking.size == 1) "is" else "are"} talking…")
    !w.on -> tr("对讲没开：点「开对讲」队友才听得到你", "Talk is off: tap Talk so your team can hear you")
    w.muted && !w.testing -> tr("闭麦了：你听得到队友，队友听不到你", "Muted: you can hear your team, they can't hear you") + directText(w)
    w.testing && w.sending -> tr("在录 3 秒：说一句话，录完放给你自己听", "Recording 3 s: say something and it plays back to you")
    w.testing -> tr("试一下：说一句话，会放给你自己听", "Test: say something and it plays back to you")
    w.sending && w.mode == WalkieMode.CLIP -> tr("在录，说完停一下就发出去", "Recording; pause when done and it sends")
    w.sending -> tr("队友正在听你说", "Your team can hear you")
    else -> tr("在听 ${micBars(w.micBars)} · ${w.device ?: "手机"}", "Listening ${micBars(w.micBars)} · ${w.device ?: "Phone"}") + directText(w)
}

/** 英文的「2 riders」 */
private fun riders(n: Int): String = if (n == 1) "1 rider" else "$n riders"

/** 「 · 直连 2 人」：离得近的队友直接用蓝牙、Wi-Fi 连上了 */
private fun directText(w: WalkieState): String = if (w.on && w.direct > 0) tr(" · 直连 ${w.direct} 人", " · ${riders(w.direct)} direct") else ""

/** 骑行视图上的对讲：有事（有人在说、你在说、出问题）说事，平常只说开没开、直连几个人 */
fun rideWalkieText(w: WalkieState): String =
    walkieText(w).takeIf { w.problem != null || w.talking.isNotEmpty() || w.sending || w.testing }
        ?: when {
            !w.on -> tr("对讲没开", "Talk off")
            w.muted -> tr("闭麦了", "Muted")
            w.direct > 0 -> tr("直连 ${w.direct} 人", "${riders(w.direct)} direct")
            else -> tr("对讲开着", "Talk on")
        }

/**
 * 骑行视图上的一行对讲：状态，开着时灵敏度、实时 / 整句点一下换一档，最右边开关。
 * 按钮做大一点，骑车时戴手套也点得中
 */
@Composable
fun RideWalkieRow(
    walkie: WalkieState,
    onToggle: () -> Unit,
    onLevel: (VoxLevel) -> Unit,
    onMode: (WalkieMode) -> Unit,
    modifier: Modifier = Modifier,
    onMute: (Boolean) -> Unit = {},
) {
    val pad = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.padding(end = 2.dp).size(10.dp).clip(CircleShape).background(
                when {
                    walkie.problem != null -> Color(0xFFFFB74D)
                    walkie.sending || walkie.talking.isNotEmpty() -> Color(0xFF7CFFB2)
                    walkie.muted -> Color(0xFFFFB74D)
                    walkie.on -> Color(0xFFB388FF)
                    else -> Color(0xFF8A8A8A)
                }
            )
        )
        Text(
            rideWalkieText(walkie),
            Modifier.weight(1f),
            color = if (walkie.problem != null) Color(0xFFFFD08A) else Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (walkie.on) {
            GlassButton(
                onClick = { onMute(!walkie.muted) },
                tone = if (walkie.muted) GlassTone.Tinted(RandoAmber) else GlassTone.Dark,
                contentPadding = pad,
            ) { Text(if (walkie.muted) tr("开麦", "Unmute") else tr("闭麦", "Mute"), color = Color.White, fontSize = 15.sp) }
            GlassButton(
                onClick = { onLevel(VoxLevel.entries[(walkie.level.ordinal + 1) % VoxLevel.entries.size]) },
                tone = GlassTone.Dark,
                contentPadding = pad,
            ) { Text(walkie.level.label, color = Color.White, fontSize = 15.sp) }
            GlassButton(
                onClick = { onMode(WalkieMode.entries[(walkie.mode.ordinal + 1) % WalkieMode.entries.size]) },
                tone = GlassTone.Dark,
                contentPadding = pad,
            ) { Text(walkie.mode.label, color = Color.White, fontSize = 15.sp) }
        }
        GlassButton(
            onClick = onToggle,
            tone = if (walkie.on) GlassTone.Tinted(RandoGreen) else GlassTone.Dark,
            contentPadding = pad,
        ) {
            Text(if (walkie.on) tr("关对讲", "Talk off") else tr("开对讲", "Talk"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 麦克风音量：▁▃▅▇ 亮几格 */
private fun micBars(n: Int): String = "▁▃▅▇".mapIndexed { i, c -> if (i < n) c else '·' }.joinToString("")

@Composable
private fun GroupHeader(state: GroupState, onShare: () -> Unit, onLeave: () -> Unit, chip: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.connected) RandoGreen else Color(0xFFBDBDBD)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("一起骑 ${state.code}", "Group ride ${state.code}"), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (state.connected) tr("队里 ${state.teammates.size + 1} 人", "${riders(state.teammates.size + 1)} on the team") else tr("正在连接…", "Connecting…"),
                color = InkSoft,
                fontSize = 13.sp,
            )
        }
        GroupKey(tr("分享", "Share"), chip, Modifier.padding(start = 6.dp), onClick = onShare)
        GroupKey(tr("退出", "Leave"), chip, Modifier.padding(start = 6.dp), RandoRed.onGlass(), onLeave)
    }
}

/** 队友离我多远，近的在前；没位置的、信号断了的放后面 */
fun teammatesText(mates: List<Teammate>, me: LatLon?, now: Long = System.currentTimeMillis()): String? {
    if (mates.isEmpty()) return null
    val parts = mates
        .map { m ->
            val d = if (me != null && m.location != null) Geo.distance(me, m.location) else null
            Triple(m, d, m.stale(now))
        }
        .sortedWith(compareBy({ it.third || it.second == null }, { it.second ?: Double.MAX_VALUE }))
        .map { (m, d, stale) ->
            when {
                m.location == null -> tr("${m.name} 还没定位", "${m.name} no location yet")
                stale -> tr("${m.name} 信号断了", "${m.name} lost signal")
                d != null -> "${m.name} ${Format.distance(d.toInt())}"
                else -> m.name
            }
        }
    return tr("队友：", "Team: ") + parts.joinToString(" · ")
}

/** 骑行视图上的一行队友 */
@Composable
fun TeammatesRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(Color(0xFFB388FF)))
        Text(text, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 队友分享了路线：跟着骑，还是不用 */
@Composable
fun SharedRouteDialog(route: SharedRoute, navigating: Boolean, onFollow: () -> Unit, onDecline: () -> Unit) {
    RandoDialog(
        // 骑车时容易误碰，点外面不关，要明确选一个
        onDismissRequest = {},
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        title = { Text(tr("${route.name}分享了路线", "${route.name} shared a route")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sharedRouteText(route), color = Ink, fontSize = 16.sp)
                if (navigating) Text(tr("跟着骑会换掉你现在的导航", "Following replaces your current navigation"), color = InkSoft, fontSize = 14.sp)
            }
        },
        confirmButton = { DialogButton(onClick = onFollow) { Text(tr("跟着骑", "Follow"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { DialogButton(onClick = onDecline) { Text(tr("不用", "No thanks")) } },
    )
}

/** 「去 Marina Bay Sands · 13.6 公里 · 公园连道、不上马路」 */
fun sharedRouteText(route: SharedRoute): String {
    val options = listOfNotNull(
        route.profile.label,
        tr("不上马路", "No roads").takeIf { route.avoidRoads },
        tr("不走天桥", "No overhead bridges").takeIf { route.avoidBridges },
    ).joinToString(tr("、", ", "))
    // 标题是队友手机按 TA 的语言起的，两种都认
    val loopOrBack = route.title.startsWith("骑一圈") || route.title == "原路返回" ||
        route.title.startsWith("Loop") || route.title == "Back the way you came"
    val where = if (loopOrBack) route.title else tr("去 ${route.title}", "To ${route.title}")
    return listOfNotNull(where, route.meters.takeIf { it > 0 }?.let { Format.distance(it) }, options).joinToString(" · ")
}

/**
 * 检测到可能摔车：整屏提醒，大字倒计时。刚摔完手在抖、可能还戴着手套，
 * 所以两个按钮做得又大又隔得开：「我没事」在最下面拇指够得着的地方
 */
@Composable
fun CrashDialog(alert: CrashAlert, onOk: () -> Unit, onSendNow: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alert) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val left = ((alert.deadline - now + 999) / 1000).coerceAtLeast(0)
    val progress = ((alert.deadline - now).toFloat() / CRASH_COUNTDOWN_MS).coerceIn(0f, 1f)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        // 还在倒计时：浅色整屏，红色圆环倒数；已经通知了：整屏红色，只剩一个「我没事」
        val sent = alert.sent
        Column(
            Modifier
                .fillMaxSize()
                .background(if (sent) Brush.verticalGradient(listOf(Color(0xFFE5483A), Color(0xFFB8261A))) else SolidColor(BackdropColor))
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (!sent) {
                Box(Modifier.size(240.dp).neu(CircleShape), contentAlignment = Alignment.Center) {
                    val track = Ink.copy(alpha = 0.08f)
                    Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                        val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(track, 0f, 360f, false, style = stroke)
                        drawArc(RandoRed, -90f, 360f * progress, false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$left", color = RandoRed.onGlass(), fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 96.sp)
                        Text(tr("秒", "sec"), color = InkSoft, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(tr("你没事吧？", "Are you OK?"), color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(tr("倒数完会把位置发给队友", "When the countdown ends your location is sent to the team"), color = InkSoft, fontSize = 18.sp, textAlign = TextAlign.Center)
            } else {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(tr("已经通知队友", "Team notified"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("队友会看到你的位置。\n没事的话点「我没事」，队友那边会撤掉。", "Your team can see your location.\nIf you're fine, tap I'm OK and the alert is withdrawn."),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!sent) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(GlassShapes.Button)
                        .border(2.dp, RandoRed.onGlass(), GlassShapes.Button)
                        .clickable(onClick = onSendNow),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("马上通知队友", "Notify team now"), color = RandoRed.onGlass(), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(40.dp))
                PrimaryButton(onClick = onOk, modifier = Modifier.fillMaxWidth().height(96.dp), color = RandoAccent) {
                    Text(tr("我没事", "I'm OK"), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .dropShadow(GlassShapes.Button, Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.25f), offset = DpOffset(0.dp, 10.dp)))
                        .clip(GlassShapes.Button)
                        .background(Color.White)
                        .clickable(onClick = onOk),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("我没事", "I'm OK"), color = Color(0xFFB8261A), fontSize = 32.sp, fontWeight = FontWeight.Black) }
            }
        }
    }
}

/** 队友可能摔车了：离我多远，导航过去 / 知道了 */
@Composable
fun SosDialog(alert: SosAlert, me: LatLon?, onGo: () -> Unit, onDismiss: () -> Unit) {
    RandoDialog(
        onDismissRequest = {},
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(Icons.Filled.Warning, RandoRed),
        title = { Text(tr("${alert.name}可能摔车了", "${alert.name} may have crashed"), color = RandoRed.onGlass()) },
        text = {
            val far = me?.let { tr("离你 ${Format.distance(Geo.distance(it, alert.location).toInt())}。", "${Format.distance(Geo.distance(it, alert.location).toInt())} away. ") }.orEmpty()
            Text(tr("${far}TA 的手机检测到可能摔车，还没有回应。", "${far}Their phone detected a possible crash and they haven't responded."), color = Ink, fontSize = 16.sp)
        },
        confirmButton = { DialogButton(onClick = onGo) { Text(tr("导航过去", "Navigate there"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { DialogButton(onClick = onDismiss) { Text(tr("知道了", "OK")) } },
    )
}

/** 点了地图上的路况：是什么、谁标的、几天前；可以删掉 */
@Composable
fun HazardDialog(hazard: Hazard, onDelete: () -> Unit, onDismiss: () -> Unit) {
    RandoDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = DialogColor,
        badge = DialogBadge(Icons.Filled.Warning, RandoAmber),
        title = { Text(hazard.type.label) },
        text = {
            val days = ((System.currentTimeMillis() - hazard.createdAt) / 86_400_000L).toInt()
            val whenText = if (days == 0) tr("今天", "today") else tr("$days 天前", if (days == 1) "1 day ago" else "$days days ago")
            val who = hazard.by?.let { tr("${it}标的", "Marked by $it") } ?: tr("你标的", "Marked by you")
            val effect = if (hazard.type.avoid) tr("算路时会绕开这里，30 天后自动失效。", "Routes avoid this spot; it expires after 30 days.") else tr("骑到附近会提醒你减速。", "You'll be told to slow down when you ride nearby.")
            Text(tr("$who，$whenText。$effect", "$who $whenText. $effect"), color = Ink, fontSize = 16.sp)
        },
        confirmButton = { DialogButton(onClick = onDismiss) { Text(tr("好", "OK"), fontWeight = FontWeight.SemiBold) } },
        dismissButton = { DialogButton(danger = true, onClick = onDelete) { Text(tr("已经好了，删掉", "Cleared, remove it")) } },
    )
}
