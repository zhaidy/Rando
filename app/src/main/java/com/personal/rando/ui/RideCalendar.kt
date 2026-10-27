package com.personal.rando.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.ride.MONTHS
import com.personal.rando.ride.Ride
import com.personal.rando.ride.RideStats
import com.personal.rando.ui.glass.GlassButton
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.neu
import java.util.Calendar

/**
 * 骑行日历：一个月一页，骑过的那天涂上颜色（骑得越远越深），点一天看那天骑的几次。
 * [month] 是这页那个月任意一天，[selected] 是选中那天零点
 */
@Composable
fun RideCalendar(
    byDay: Map<Long, List<Ride>>,
    month: Long,
    selected: Long?,
    now: Long,
    onMonth: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val c = Calendar.getInstance().apply { timeInMillis = month }
    val year = c.get(Calendar.YEAR)
    val m = c.get(Calendar.MONTH)
    val cells = RideStats.monthGrid(month)
    val today = RideStats.dayStart(now)
    val inMonth = cells.filterNotNull().mapNotNull { byDay[it] }
    // 这个月骑得最远的那天最深，其余按比例
    val most = byDay.filterKeys { it in cells }.values.maxOfOrNull { list -> list.sumOf { it.distanceMeters } } ?: 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .neu(GlassShapes.Card)
            .clip(GlassShapes.Card)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTap(onClick = { onMonth(RideStats.shiftMonth(month, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = tr("上个月", "Previous month"), tint = Ink)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("${year}年${m + 1}月", "${MONTHS[m]} $year"), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (inMonth.isEmpty()) tr("这个月没骑", "No rides this month")
                    else tr("骑了 ${inMonth.size} 天 · ${Format.distance(inMonth.sumOf { d -> d.sumOf { it.distanceMeters } }.toInt())}", "${inMonth.size} days · ${Format.distance(inMonth.sumOf { d -> d.sumOf { it.distanceMeters } }.toInt())}"),
                    color = InkSoft,
                    fontSize = 13.sp,
                )
            }
            // 还没到的月份不让翻过去
            val next = RideStats.shiftMonth(month, 1)
            IconTap(onClick = { if (next <= now) onMonth(next) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = tr("下个月", "Next month"),
                    tint = if (next <= now) Ink else InkSoft.copy(alpha = 0.35f),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(Modifier.fillMaxWidth()) {
            weekdays().forEach { Text(it, Modifier.weight(1f), color = InkSoft, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val rides = byDay[day].orEmpty()
                            val meters = rides.sumOf { it.distanceMeters }
                            val strength = if (most > 0) (0.35f + 0.65f * (meters / most).toFloat()) else 0f
                            val isSelected = day == selected
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .then(if (rides.isNotEmpty()) Modifier.background(RandoAccent.copy(alpha = strength)) else Modifier)
                                    .then(
                                        when {
                                            isSelected -> Modifier.border(2.5.dp, Ink, CircleShape)
                                            day == today -> Modifier.border(1.5.dp, RandoAccent, CircleShape)
                                            else -> Modifier
                                        }
                                    )
                                    .clickable(enabled = rides.isNotEmpty()) { onSelect(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                val date = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_MONTH)
                                Text(
                                    "$date",
                                    color = if (rides.isNotEmpty()) Color.White else if (day > now) InkSoft.copy(alpha = 0.4f) else Ink,
                                    fontSize = 15.sp,
                                    fontWeight = if (rides.isNotEmpty() || day == today) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 日历下面选中那天的标题：「10月26日 · 2 次 · 23.4 公里」，右边分享那天 */
@Composable
fun RideDayHeader(day: Long, rides: List<Ride>, onShare: () -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = day }
    val m = c.get(Calendar.MONTH)
    val d = c.get(Calendar.DAY_OF_MONTH)
    val km = Format.distance(rides.sumOf { it.distanceMeters }.toInt())
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr("${m + 1}月${d}日 · ${rides.size} 次 · $km", "${MONTHS[m]} $d · ${rides.size} ride${if (rides.size == 1) "" else "s"} · $km"),
            Modifier.weight(1f),
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        GlassButton(onClick = onShare, tone = GlassTone.Tinted(RandoAccent), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("分享这天", "Share day"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 周一开头的星期 */
private fun weekdays(): List<String> =
    if (Lang.english) listOf("M", "T", "W", "T", "F", "S", "S")
    else listOf("一", "二", "三", "四", "五", "六", "日")
