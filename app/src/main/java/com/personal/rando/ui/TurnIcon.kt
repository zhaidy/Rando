package com.personal.rando.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.rando.routing.Maneuver
import com.personal.rando.routing.Turn
import kotlin.math.cos
import kotlin.math.sin

/**
 * 导航里的转向图标：自己画，不靠字体里的箭头字符（有的手机没有 ⟳ 这类字，大字号下也很细）。
 * 「稍向左」是一根斜着的箭头，「靠左走」是分叉、左边那支亮；环岛中间写第几个出口。
 * [maneuver] 是 null 时画终点的旗子
 */
@Composable
fun TurnIcon(maneuver: Maneuver?, modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = Color.White) {
    val turn = maneuver?.turn
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            when (turn) {
                null -> flag(color)
                Turn.STRAIGHT, Turn.BEELINE -> arrow(0f, color)
                Turn.LEFT -> arrow(-90f, color)
                Turn.RIGHT -> arrow(90f, color)
                Turn.SLIGHT_LEFT -> arrow(-45f, color)
                Turn.SLIGHT_RIGHT -> arrow(45f, color)
                Turn.SHARP_LEFT -> arrow(-135f, color)
                Turn.SHARP_RIGHT -> arrow(135f, color)
                Turn.KEEP_LEFT, Turn.EXIT_LEFT -> fork(-1f, color)
                Turn.KEEP_RIGHT, Turn.EXIT_RIGHT -> fork(1f, color)
                Turn.U_TURN_LEFT, Turn.U_TURN -> uTurn(-1f, color)
                Turn.U_TURN_RIGHT -> uTurn(1f, color)
                Turn.ROUNDABOUT, Turn.ROUNDABOUT_LEFT -> roundabout(color)
            }
        }
        val exit = maneuver?.exit ?: 0
        if (turn?.isRoundabout == true && exit > 0) {
            // 圈心在图标偏左的地方，字跟着往左挪
            Text(
                "$exit",
                Modifier.offset(x = -(size * 0.08f)),
                color = color,
                fontSize = (size.value * 0.26f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// 下面的坐标都按图标边长的比例写，0..1；side 是 -1 往左、1 往右，画右边的就是左边的镜像

private fun DrawScope.p(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun DrawScope.line(color: Color, points: List<Offset>) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 箭头尖在 [tip]，朝 [dx],[dy] 的方向；返回箭头底边的中点，线画到这里 */
private fun DrawScope.head(tip: Offset, dx: Float, dy: Float, color: Color, halfWidth: Float = 0.17f): Offset {
    val length = size.width * 0.22f
    val half = size.width * halfWidth
    val base = Offset(tip.x - dx * length, tip.y - dy * length)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x - dy * half, base.y + dx * half)
        lineTo(base.x + dy * half, base.y - dx * half)
        close()
    }
    drawPath(path, color)
    return base
}

/** 从下面上来，在路口拐 [degrees] 度（0 是直行，负的往左） */
private fun DrawScope.arrow(degrees: Float, color: Color) {
    val sharp = kotlin.math.abs(degrees) > 90
    val side = if (degrees < 0) -1f else 1f
    // 急转弯的箭头往回折，竖线挪到另一边、路口抬高，免得和箭头挤在一起
    val joint = if (sharp) p(0.5f - side * 0.2f, 0.34f) else p(0.5f, 0.62f)
    val length = size.width * if (sharp) 0.46f else 0.40f
    val rad = Math.toRadians(degrees.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val tip = Offset(joint.x + dx * length, joint.y + dy * length)
    val base = head(tip, dx, dy, color, halfWidth = if (sharp) 0.15f else 0.17f)
    line(color, listOf(Offset(joint.x, size.height * 0.94f), joint, base))
}

/** 分叉：要走的那支亮，另一支淡 */
private fun DrawScope.fork(side: Float, color: Color) {
    val joint = p(0.5f, 0.6f)
    val other = Math.toRadians(-side * 30.0)
    line(
        color.copy(alpha = 0.35f),
        listOf(joint, Offset(joint.x + sin(other).toFloat() * size.width * 0.36f, joint.y - cos(other).toFloat() * size.width * 0.36f)),
    )
    val rad = Math.toRadians(side * 30.0)
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val tip = Offset(joint.x + dx * size.width * 0.4f, joint.y + dy * size.width * 0.4f)
    val base = head(tip, dx, dy, color)
    line(color, listOf(p(0.5f, 0.94f), joint, base))
}

/** 掉头：从一边上去，绕个弯从另一边下来 */
private fun DrawScope.uTurn(side: Float, color: Color) {
    val up = 0.5f - side * 0.18f
    val down = 0.5f + side * 0.18f
    val tip = p(down, 0.80f)
    val base = head(tip, 0f, 1f, color)
    val path = Path().apply {
        val start = p(up, 0.94f)
        moveTo(start.x, start.y)
        val top = p(up, 0.42f)
        lineTo(top.x, top.y)
        arcTo(Rect(p(0.32f, 0.24f), p(0.68f, 0.60f)), if (side < 0) 0f else 180f, if (side < 0) -180f else 180f, false)
        lineTo(base.x, base.y)
    }
    drawPath(path, color, style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 环岛：一个圈，从下面进，往右边出 */
private fun DrawScope.roundabout(color: Color) {
    val center = p(0.42f, 0.5f)
    val r = size.width * 0.2f
    drawCircle(color, r, center, style = Stroke(width = size.width * 0.10f))
    line(color, listOf(p(0.42f, 0.94f), Offset(center.x, center.y + r)))
    val out = Offset(center.x + r, center.y)
    val base = head(p(0.96f, 0.5f), 1f, 0f, color, halfWidth = 0.14f)
    line(color, listOf(out, base))
}

/** 终点：一面小旗 */
private fun DrawScope.flag(color: Color) {
    line(color, listOf(p(0.32f, 0.92f), p(0.32f, 0.14f)))
    val path = Path().apply {
        val a = p(0.32f, 0.12f)
        val b = p(0.84f, 0.30f)
        val c = p(0.32f, 0.48f)
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, color)
}
