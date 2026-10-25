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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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

/** 在不透明的浅灰卡片里面：默认那种玻璃换成新拟态的凸起，不去模糊后面的地图 */
val LocalOnSolid = staticCompositionLocalOf { false }

object GlassShapes {
    val Sheet = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(percent = 50)
    /** 主按钮、新拟态的小卡片 */
    val Button = RoundedCornerShape(20.dp)
    val Tile = RoundedCornerShape(18.dp)
}

/** 玻璃的底色：白玻璃、深色玻璃，或者带一点颜色（选中、导航条） */
sealed interface GlassTone {
    data object Light : GlassTone
    data object Dark : GlassTone
    /** 不透明的浅灰底（底部卡片这种大面积的），上面放新拟态的凸起卡片 */
    data object Solid : GlassTone
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
    GlassTone.Solid -> palette.card
    is GlassTone.Tinted -> color.copy(alpha = 0.86f)
}

/** 没有可取的背景（比如对话框、截图测试）时，用更实的磨砂色代替 */
private fun GlassTone.fallback(palette: Palette): Color = when (this) {
    GlassTone.Light -> palette.glassFallback
    GlassTone.Dark -> Color(0xFF15191A).copy(alpha = 0.92f)
    GlassTone.Solid -> palette.card
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
    if (LocalOnSolid.current && tone == GlassTone.Light) return this.neu(shape).clip(shape)
    val haze = LocalHazeState.current.takeUnless { LocalOnSolid.current }
    val palette = LocalPalette.current
    val fill = tone.fill(palette)
    val style = remember(shape, fill) {
        GlassStyle.regular.then {
            shape(shape)
            // 小按钮默认几乎不模糊，统一给足模糊，后面的地图字才不抢眼
            optics(refractionStrength = 0.75f, refractionDisplacement = 22.dp, blurRadius = 18.dp)
        }
    }
    val material = if (haze != null && tone != GlassTone.Solid) {
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
    Box(modifier.liquidGlass(shape, tone)) {
        if (tone == GlassTone.Solid) CompositionLocalProvider(LocalOnSolid provides true) { content() } else content()
    }
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

/** 主操作按钮：圆角的实心按钮，上面略亮、下面略深，底下一圈同色偏深的柔和投影 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = GlassShapes.Button
    Row(
        modifier
            .pressScale(interaction, pressed = 0.96f)
            .dropShadow(shape, Shadow(radius = 16.dp, color = color.darken(0.35f).copy(alpha = 0.32f), offset = DpOffset(0.dp, 6.dp)))
            .clip(shape)
            .background(solidFill(color))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.0f))), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        content = content,
    )
}

/**
 * 新拟态的凸起：和底色一样的面，左上一道白色高光、右下一道灰蓝色投影，像从底板里压出来的。
 * 只放在 [GlassTone.Solid] 那种不透明的浅灰底上，压在地图上的东西还是用玻璃
 */
@Composable
fun Modifier.neu(shape: Shape = GlassShapes.Tile, distance: Dp = 6.dp): Modifier {
    val palette = LocalPalette.current
    val dark = if (palette.dark) Color.Black.copy(alpha = 0.55f) else Color(0xFFA3B1C6).copy(alpha = 0.6f)
    val light = if (palette.dark) Color.White.copy(alpha = 0.06f) else Color.White
    return this
        .dropShadow(shape, Shadow(radius = distance * 2, color = light, offset = DpOffset(-distance, -distance)))
        .dropShadow(shape, Shadow(radius = distance * 2, color = dark, offset = DpOffset(distance, distance)))
        .background(palette.card, shape)
}

/** 新拟态的凹槽：输入框、分段选择的底槽 */
@Composable
fun Modifier.neuInset(shape: Shape = GlassShapes.Tile): Modifier {
    val palette = LocalPalette.current
    val dark = if (palette.dark) Color.Black.copy(alpha = 0.6f) else Color(0xFFA3B1C6).copy(alpha = 0.55f)
    val light = if (palette.dark) Color.White.copy(alpha = 0.05f) else Color.White
    return this
        .background(palette.card, shape)
        .innerShadow(shape, Shadow(radius = 8.dp, color = dark, offset = DpOffset(4.dp, 4.dp)))
        .innerShadow(shape, Shadow(radius = 8.dp, color = light, offset = DpOffset((-4).dp, (-4).dp)))
}

/** 新拟态的按钮：凸起的面，按下缩一下 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Tile,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressed = 0.95f)
            .neu(shape)
            .clip(shape)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun Color.lighten(amount: Float): Color =
    Color(red + (1 - red) * amount, green + (1 - green) * amount, blue + (1 - blue) * amount, alpha)

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

/**
 * 实心按钮的填色：同一个颜色上下只差一点点明暗，像有一点弧度的实体按键；
 * 不用两种颜色斜着渐变，那样很像模板
 */
fun solidFill(color: Color): Brush = Brush.verticalGradient(listOf(color.lighten(0.1f), color, color.darken(0.1f)))

/** 没有指定颜色时给个默认的 */
fun Color.orElse(other: Color): Color = if (isSpecified) this else other
