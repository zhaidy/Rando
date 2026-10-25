package com.personal.rando.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.rando.ui.glass.GlassShapes
import com.personal.rando.ui.glass.neuInset
import com.personal.rando.ui.glass.solidFill

/** 弹窗左上角的图标：带颜色的小方块，一眼看出是哪类事 */
class DialogBadge(val icon: ImageVector, val color: Color)

/** 弹窗里的按钮是主按钮（实心）还是次要的（只有字），由放在哪个位置决定 */
private enum class DialogRole { PRIMARY, SECONDARY }

private val LocalDialogRole = staticCompositionLocalOf { DialogRole.SECONDARY }

/**
 * 统一样式的弹窗：浅色圆角卡片，左上可以有个图标方块，标题加粗；
 * 下面的按钮靠右，确认的是实心主色按钮（[DialogButton] 的 danger 是红色），取消的只有字。
 * 参数和 AlertDialog 一样，[shape]、[containerColor] 留着兼容，不再用
 */
@Composable
fun RandoDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    badge: DialogBadge? = null,
    /** 按钮一个一行、占满宽（「继续骑 / 只停导航 / 结束」这种要选清楚的） */
    stacked: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val card = RoundedCornerShape(30.dp)
        Column(
            modifier
                .fillMaxWidth()
                .dropShadow(card, Shadow(radius = 40.dp, color = Color.Black.copy(alpha = 0.28f), offset = DpOffset(0.dp, 20.dp)))
                .clip(card)
                .background(DialogColor)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (badge != null || title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    badge?.let {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(it.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(it.icon, contentDescription = null, tint = it.color.onGlass(), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    title?.let {
                        CompositionLocalProvider(LocalContentColor provides Ink) {
                            ProvideTextStyle(TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)) { it() }
                        }
                    }
                }
            }
            text?.let {
                CompositionLocalProvider(LocalContentColor provides InkSoft) {
                    ProvideTextStyle(LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp)) { it() }
                }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompositionLocalProvider(LocalDialogRole provides DialogRole.PRIMARY) { confirmButton() }
                    dismissButton?.let { CompositionLocalProvider(LocalDialogRole provides DialogRole.SECONDARY) { it() } }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.let { CompositionLocalProvider(LocalDialogRole provides DialogRole.SECONDARY) { it() } }
                    CompositionLocalProvider(LocalDialogRole provides DialogRole.PRIMARY) { confirmButton() }
                }
            }
        }
    }
}

/**
 * 弹窗按钮：放在确认的位置是实心主色（[danger] 时实心红色），放在取消的位置只有字（[danger] 时红字）。
 * [primary] 强制做成实心（取消那边也想要一个醒目的按钮时）
 */
@Composable
fun DialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    primary: Boolean? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val filled = primary ?: (LocalDialogRole.current == DialogRole.PRIMARY)
    val shape = GlassShapes.Button
    val fill = if (danger) RandoRed else RandoAccent
    val base = modifier
        .heightIn(min = 46.dp)
        .alpha(if (enabled) 1f else 0.4f)
        .clip(shape)
    val styled = when {
        filled -> base.background(solidFill(fill))
        else -> base
    }
    Row(
        styled
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = when {
            filled -> Color.White
            danger -> RandoRed.onGlass()
            else -> Ink
        }
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)) { content() }
        }
    }
}

/** 弹窗里的凹槽小卡片：放几行说明（坐 MRT 的路线、更新内容） */
@Composable
fun DialogInset(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().neuInset(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        CompositionLocalProvider(LocalContentColor provides Ink) { content() }
    }
}
