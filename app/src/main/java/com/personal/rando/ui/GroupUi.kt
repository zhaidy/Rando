package com.personal.rando.ui

import androidx.compose.foundation.background
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
import com.personal.rando.group.GroupState
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

/** 建队或者加入：先填名字，再选建一个新队，或者输入别人的队伍码 */
@Composable
fun GroupDialog(
    configured: Boolean,
    name: String,
    onCreate: (name: String) -> Unit,
    onJoin: (code: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var myName by remember { mutableStateOf(name) }
    var joining by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val normalized = TeamCode.normalize(code)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("一起骑") },
        text = {
            if (!configured) {
                Text("一起骑的服务器还没部署好，部署好以后更新一下 App 就能用。")
                return@AlertDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("队友之间互相看到位置，掉队了会提醒。同一个队伍码的人在一个队里。", color = InkSoft, fontSize = 14.sp)
                OutlinedTextField(value = myName, onValueChange = { myName = it.take(20) }, label = { Text("你的名字") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !joining, onClick = { joining = false }, label = { Text("建一个队") })
                    FilterChip(selected = joining, onClick = { joining = true }, label = { Text("输入队伍码") })
                }
                if (joining) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(12) },
                        label = { Text("队伍码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            }
        },
        confirmButton = {
            if (configured) {
                TextButton(
                    enabled = myName.isNotBlank() && (!joining || normalized != null),
                    onClick = {
                        if (joining) normalized?.let { onJoin(it, myName.trim()) } else onCreate(myName.trim())
                    },
                ) { Text(if (joining) "加入" else "建队") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (configured) "取消" else "知道了") } },
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
) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(GlassShapes.Card, GlassTone.Tinted(Color(0xFF6A3FA0)))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        GroupHeader(state, onShare, onLeave)
        Row(Modifier.padding(top = 4.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                walkieText(walkie),
                Modifier.weight(1f),
                color = if (walkie.problem != null) Color(0xFFFFD08A) else Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            GlassButton(
                onClick = onToggleTalk,
                tone = if (walkie.on) GlassTone.Tinted(RandoGreen) else GlassTone.Light,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(if (walkie.on) "对讲开着" else "开对讲", color = if (walkie.on) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (walkie.on) {
            // 开着对讲时另起一行：实时 / 整句、灵敏度、试一下
            Row(Modifier.padding(top = 4.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassButton(
                    onClick = { onMode(WalkieMode.entries[(walkie.mode.ordinal + 1) % WalkieMode.entries.size]) },
                    tone = GlassTone.Light,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(walkie.mode.label, color = Ink, fontSize = 13.sp) }
                GlassButton(
                    onClick = { onLevel(VoxLevel.entries[(walkie.level.ordinal + 1) % VoxLevel.entries.size]) },
                    tone = GlassTone.Light,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("灵敏度：${walkie.level.label}", color = Ink, fontSize = 13.sp) }
                GlassButton(
                    onClick = onTest,
                    tone = if (walkie.testing) GlassTone.Tinted(RandoGreen) else GlassTone.Light,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("试一下", color = if (walkie.testing) Color.White else Ink, fontSize = 13.sp) }
            }
        }
    }
}

/** 对讲现在怎样：一句话 */
fun walkieText(w: WalkieState): String = when {
    w.problem != null -> "对讲出问题了：${w.problem}"
    w.talking.isNotEmpty() && w.sending -> "${w.talking.joinToString("、")} 和你都在说"
    w.talking.isNotEmpty() -> "${w.talking.joinToString("、")} 在说话…"
    !w.on -> "对讲没开：点「开对讲」队友才听得到你"
    w.testing && w.sending -> "在录，说完会放给你自己听"
    w.testing -> "试一下：说一句话，会放给你自己听"
    w.sending && w.mode == WalkieMode.CLIP -> "在录，说完停一下就发出去"
    w.sending -> "队友正在听你说"
    else -> "在听 ${micBars(w.micBars)} · ${w.device ?: "手机"}"
}

/** 麦克风音量：▁▃▅▇ 亮几格 */
private fun micBars(n: Int): String = "▁▃▅▇".mapIndexed { i, c -> if (i < n) c else '·' }.joinToString("")

@Composable
private fun GroupHeader(state: GroupState, onShare: () -> Unit, onLeave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.connected) Color(0xFF7CFFB2) else Color(0xFFBDBDBD)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("一起骑 ${state.code}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (state.connected) "队里 ${state.teammates.size + 1} 人" else "正在连接…",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }
        GlassButton(onClick = onShare, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Text("分享", color = Ink, fontSize = 14.sp)
        }
        Spacer(Modifier.width(6.dp))
        GlassButton(onClick = onLeave, tone = GlassTone.Light, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Text("退出", color = RandoRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
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
                m.location == null -> "${m.name} 还没定位"
                stale -> "${m.name} 信号断了"
                d != null -> "${m.name} ${Format.distance(d.toInt())}"
                else -> m.name
            }
        }
    return "队友：" + parts.joinToString(" · ")
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
    AlertDialog(
        // 骑车时容易误碰，点外面不关，要明确选一个
        onDismissRequest = {},
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("${route.name}分享了路线") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sharedRouteText(route), color = Ink, fontSize = 16.sp)
                if (navigating) Text("跟着骑会换掉你现在的导航", color = InkSoft, fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(onClick = onFollow) { Text("跟着骑", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDecline) { Text("不用", color = InkSoft) } },
    )
}

/** 「去 Marina Bay Sands · 13.6 公里 · 公园连道、不上马路」 */
fun sharedRouteText(route: SharedRoute): String {
    val options = listOfNotNull(
        route.profile.label,
        "不上马路".takeIf { route.avoidRoads },
        "不走天桥".takeIf { route.avoidBridges },
    ).joinToString("、")
    val where = if (route.title.startsWith("骑一圈") || route.title == "原路返回") route.title else "去 ${route.title}"
    return listOfNotNull(where, route.meters.takeIf { it > 0 }?.let { Format.distance(it) }, options).joinToString(" · ")
}

/** 检测到可能摔车：倒计时，我没事 / 马上通知队友 */
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
    AlertDialog(
        onDismissRequest = {},
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text(if (alert.sent) "已经通知队友" else "你没事吧？") },
        text = {
            Text(
                if (alert.sent) "队友会看到你的位置。没事的话点「我没事」，队友那边会撤掉。"
                else "检测到你可能摔车了，$left 秒后把位置发给队友。",
                color = Ink,
                fontSize = 16.sp,
            )
        },
        confirmButton = { TextButton(onClick = onOk) { Text("我没事", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = if (alert.sent) null else ({ TextButton(onClick = onSendNow) { Text("马上通知队友", color = RandoRed) } }),
    )
}

/** 队友可能摔车了：离我多远，导航过去 / 知道了 */
@Composable
fun SosDialog(alert: SosAlert, me: LatLon?, onGo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text("${alert.name}可能摔车了", color = RandoRed) },
        text = {
            val far = me?.let { "离你 ${Format.distance(Geo.distance(it, alert.location).toInt())}。" }.orEmpty()
            Text("${far}TA 的手机检测到可能摔车，还没有回应。", color = Ink, fontSize = 16.sp)
        },
        confirmButton = { TextButton(onClick = onGo) { Text("导航过去", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("知道了", color = InkSoft) } },
    )
}

/** 点了地图上的路况：是什么、谁标的、几天前；可以删掉 */
@Composable
fun HazardDialog(hazard: Hazard, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassShapes.Sheet,
        containerColor = Color(0xF7F7FAF8),
        title = { Text(hazard.type.label) },
        text = {
            val days = ((System.currentTimeMillis() - hazard.createdAt) / 86_400_000L).toInt()
            val whenText = if (days == 0) "今天" else "$days 天前"
            val who = hazard.by?.let { "${it}标的" } ?: "你标的"
            val effect = if (hazard.type.avoid) "算路时会绕开这里，30 天后自动失效。" else "骑到附近会提醒你减速。"
            Text("$who，$whenText。$effect", color = Ink, fontSize = 16.sp)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好", color = RandoGreen, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDelete) { Text("已经好了，删掉", color = RandoRed) } },
    )
}
