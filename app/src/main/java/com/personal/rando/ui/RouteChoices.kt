package com.personal.rando.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.i18n.Lang
import com.personal.rando.i18n.tr
import com.personal.rando.routing.BikeProfile
import com.personal.rando.routing.Measure
import com.personal.rando.routing.RouteOption
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.GlassTone
import com.personal.rando.ui.glass.liquidGlass

/**
 * 几条可选的路线并排成一行卡片（一样宽，不用左右滑）：叫什么、多远多久；
 * 下面一行小字说选中的那条为什么选它。点一下换成这条
 */
@Composable
fun RouteChoices(options: List<RouteOption>, chosen: Int, onChoose: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, option ->
                val selected = i == chosen
                val ink = if (selected) Color.White else Ink
                Column(
                    Modifier
                        .weight(1f)
                        .liquidGlass(GlassShapes.Tile, if (selected) GlassTone.Tinted(RandoAccent) else GlassTone.Light)
                        .clickable { onChoose(i) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(optionTitle(option, i), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Format.distance(option.route.distanceMeters), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(Format.duration(option.route.durationSeconds), color = if (selected) Color.White.copy(alpha = 0.9f) else InkSoft, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        options.getOrNull(chosen)?.let {
            Text(optionReason(it, chosen, options.first()), color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** 卡片标题：第一条是「推荐」，其余按它拿第一的那项叫，比如「最快」「马路最少」 */
fun optionTitle(option: RouteOption, index: Int): String = when {
    index == 0 -> tr("推荐", "Recommended")
    option.best.isNotEmpty() -> option.best.take(2).joinToString(" · ") { it.label }
    else -> tr("另一条", "Alternative")
}

/** 为什么选它：第一条说是按偏好算的；其余说怎么算的，比推荐的好在哪、差在哪 */
fun optionReason(option: RouteOption, index: Int, main: RouteOption): String {
    if (index == 0) {
        val best = option.best.take(2).joinToString(tr("、", ", ")) { it.label }
        return tr("按你选的「${describe(option)}」算的", "Based on your choice (${describe(option)})") +
            if (best.isNotEmpty()) tr("，也是$best", ", also ${best.lowercase()}") else ""
    }
    val how = when {
        option.prefs == main.prefs -> tr("同样的偏好，换一条路", "Same preferences, different way")
        else -> tr("按「${describe(option)}」算", "Using ${describe(option)}")
    }
    val better = option.better.entries.take(2).joinToString(tr("、", ", ")) { (m, d) -> diff(m, d, true) }
    val worse = option.worse.entries.take(2).joinToString(tr("、", ", ")) { (m, d) -> diff(m, d, false) }
    return tr("$how：", "$how: ") + listOf(better, worse).filter { it.isNotEmpty() }.joinToString(tr("，但", ", but "))
}

private fun describe(option: RouteOption): String {
    val p = option.prefs
    if (p.profile != BikeProfile.PCN) return p.profile.label
    return listOfNotNull(
        p.profile.label,
        tr("不上马路", "no roads").takeIf { p.avoidRoads },
        tr("不走天桥", "no overhead bridges").takeIf { p.avoidBridges },
    ).joinToString(tr("、", ", "))
}

private fun diff(m: Measure, d: Int, better: Boolean): String = if (Lang.english) diffEnglish(m, d, better) else when (m) {
    Measure.TIME -> (if (better) "快 " else "慢 ") + Format.duration(d)
    Measure.ROAD -> (if (better) "少 " else "多 ") + Format.distance(d) + "马路"
    Measure.CROSSINGS -> (if (better) "少过 " else "多过 ") + "$d 次马路"
    Measure.DISTANCE -> (if (better) "近 " else "远 ") + Format.distance(d)
    Measure.CLIMB -> (if (better) "少爬 " else "多爬 ") + "$d 米"
}

/** 「3 min faster」「200 m less road」「1 fewer road crossing」 */
private fun diffEnglish(m: Measure, d: Int, better: Boolean): String = when (m) {
    Measure.TIME -> Format.duration(d) + if (better) " faster" else " slower"
    Measure.ROAD -> Format.distance(d) + if (better) " less road" else " more road"
    Measure.CROSSINGS -> "$d " + (if (better) "fewer" else "more") + if (d == 1) " road crossing" else " road crossings"
    Measure.DISTANCE -> Format.distance(d) + if (better) " shorter" else " longer"
    Measure.CLIMB -> "$d m " + if (better) "less climbing" else "more climbing"
}
