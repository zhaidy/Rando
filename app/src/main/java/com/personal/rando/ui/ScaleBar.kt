package com.personal.rando.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.ui.glass.GlassPanel
import com.personal.rando.ui.glass.GlassShapes
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 比例尺：一小块玻璃上一条横线，上面写多长（50 米、200 米、1 公里这种整数）。
 * [metersPerDp] 是地图中心每 1 dp 代表多少米；不知道时（地图还没好）不画
 */
@Composable
fun ScaleBar(metersPerDp: Double, modifier: Modifier = Modifier) {
    if (metersPerDp <= 0 || metersPerDp.isNaN()) return
    val meters = ScaleBars.nice(metersPerDp * MAX_WIDTH_DP)
    val width = (meters / metersPerDp).toFloat().dp
    val ink = Ink
    GlassPanel(modifier, shape = GlassShapes.Pill) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(Format.distance(meters.toInt()), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Canvas(Modifier.width(width).height(6.dp)) {
                val stroke = 2.dp.toPx()
                val y = size.height - stroke / 2
                drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                drawLine(ink, Offset(0f, 0f), Offset(0f, y), stroke, StrokeCap.Round)
                drawLine(ink, Offset(size.width, 0f), Offset(size.width, y), stroke, StrokeCap.Round)
            }
        }
    }
}

/** 比例尺最长画多宽 */
private const val MAX_WIDTH_DP = 90.0

object ScaleBars {
    /** 不超过 [max] 米的最大「整」距离：1、2、5 乘以 10 的几次方 */
    fun nice(max: Double): Double {
        if (max < 1) return 1.0
        val power = 10.0.pow(floor(log10(max)))
        return listOf(5.0, 2.0, 1.0).map { it * power }.first { it <= max }
    }
}
