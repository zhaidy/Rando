package com.personal.rando.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.rando.ui.LocalPalette
import com.personal.rando.ui.Palette
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * 液态玻璃：浮在地图上的面板会折射、模糊后面的地图，边缘一圈高光。
 * 地图那一层用 hazeSource 登记到 [LocalHazeState]，面板用 [liquidGlass] 取它来画。
 * Android 13+ 有完整的折射；12 只有模糊；更老的手机退回半透明磨砂色。
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object GlassShapes {
    val Sheet = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** 玻璃的底色：白玻璃、深色玻璃，或者带一点颜色（选中、导航条） */
sealed interface GlassTone {
    data object Light : GlassTone
    data object Dark : GlassTone
    data class Tinted(val color: Color) : GlassTone
}

/** 动画手感：统一用弹簧，按下回弹、面板展开都带一点点过冲 */
object Motion {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)
    fun <T> smooth() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/** 玻璃上再压一层颜色：白玻璃磨一点砂，文字才不会被后面的地图字干扰；带色的要实一些。夜里默认那种是深色玻璃 */
private fun GlassTone.fill(palette: Palette): Color = when (this) {
    GlassTone.Light -> palette.glass
    GlassTone.Dark -> Color(0xFF0E1110).copy(alpha = 0.50f)
    is GlassTone.Tinted -> color.copy(alpha = 0.86f)
}

/** 没有可取的背景（比如对话框、截图测试）时，用更实的磨砂色代替 */
private fun GlassTone.fallback(palette: Palette): Color = when (this) {
    GlassTone.Light -> palette.glassFallback
    GlassTone.Dark -> Color(0xFF15191A).copy(alpha = 0.92f)
    is GlassTone.Tinted -> color.copy(alpha = 0.94f)
}

private fun GlassTone.rim(palette: Palette): Brush {
    val darkGlass = this == GlassTone.Dark || (this == GlassTone.Light && palette.dark)
    val strong = if (darkGlass) 0.30f else 0.85f
    val soft = if (darkGlass) 0.06f else 0.12f
    return Brush.linearGradient(
        listOf(Color.White.copy(alpha = strong), Color.White.copy(alpha = soft), Color.White.copy(alpha = strong * 0.55f))
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    tone: GlassTone = GlassTone.Light,
    interactionSource: InteractionSource? = null,
): Modifier {
    val haze = LocalHazeState.current
    val palette = LocalPalette.current
    val fill = tone.fill(palette)
    val style = remember(shape, fill) {
        GlassStyle.regular.then {
            shape(shape)
            // 小按钮默认几乎不模糊，统一给足模糊，后面的地图字才不抢眼
            optics(refractionStrength = 0.75f, refractionDisplacement = 22.dp, blurRadius = 18.dp)
        }
    }
    val material = if (haze != null) {
        Modifier
            .hazeGlass(HazeInput.Sources(haze), style, interactionSource = interactionSource)
            .background(fill, shape)
    } else {
        Modifier.background(tone.fallback(palette), shape)
    }
    return this
        .then(material)
        .clip(shape)
        .border(1.dp, tone.rim(palette), shape)
}

/** 按下时缩一点，松手弹回来 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.bouncy(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassShapes.Sheet,
    tone: GlassTone = GlassTone.Light,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidGlass(shape, tone), content = content)
}

/** 玻璃按钮：胶囊形，按下缩一下 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.Light,
    shape: RoundedCornerShape = GlassShapes.Pill,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .liquidGlass(shape, tone, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 圆形玻璃图标按钮 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tone: GlassTone = GlassTone.Light,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, pressed = 0.88f)
            .liquidGlass(GlassShapes.Pill, tone, interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 主操作按钮：实心的绿色胶囊，上半部分一层高光，像一颗糖 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, pressed = 0.96f)
            .clip(GlassShapes.Pill)
            .background(Brush.verticalGradient(listOf(color.lighten(0.18f), color)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), GlassShapes.Pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        content = content,
    )
}

private fun Color.lighten(amount: Float): Color =
    Color(red + (1 - red) * amount, green + (1 - green) * amount, blue + (1 - blue) * amount, alpha)

/** 没有指定颜色时给个默认的 */
fun Color.orElse(other: Color): Color = if (isSpecified) this else other
