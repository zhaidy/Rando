package com.personal.rando.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val RandoGreen = Color(0xFF2E7D4F)
val RandoGreenDeep = Color(0xFF1B5E3A)
val RandoBlue = Color(0xFF2F7DE1)
val RandoOrange = Color(0xFFE0672B)
val RandoAmber = Color(0xFFC98A1B)
val RandoRed = Color(0xFFC0392B)

/**
 * 跟着白天 / 夜间变的颜色：玻璃上的字、默认那种玻璃、对话框底色。
 * 绿、橙、红这些强调色两边一样
 */
data class Palette(
    val dark: Boolean,
    /** 玻璃上的字 */
    val ink: Color,
    val inkSoft: Color,
    /** 默认（GlassTone.Light）玻璃上压的那层颜色 */
    val glass: Color,
    /** 取不到背景时的磨砂色 */
    val glassFallback: Color,
    /** 对话框、菜单的底色 */
    val dialog: Color,
    /** 玻璃面板里再垫一块的小卡片（记录列表的一条一条） */
    val card: Color,
    /** 地图还没画出来时的底色 */
    val backdrop: Color,
)

val LightPalette = Palette(
    dark = false,
    ink = Color(0xFF13241B),
    inkSoft = Color(0xFF4F6157),
    glass = Color.White.copy(alpha = 0.50f),
    glassFallback = Color(0xFFF7FAF8).copy(alpha = 0.92f),
    dialog = Color(0xF7F7FAF8),
    card = Color.White.copy(alpha = 0.55f),
    backdrop = Color(0xFFF2F0EB),
)

/** 夜里：深色玻璃配浅色字，晚上骑车打开不刺眼 */
val DarkPalette = Palette(
    dark = true,
    ink = Color(0xFFE6EEE9),
    inkSoft = Color(0xFFA3B2AA),
    glass = Color(0xFF111614).copy(alpha = 0.62f),
    glassFallback = Color(0xFF181D1B).copy(alpha = 0.94f),
    dialog = Color(0xFA1C2220),
    card = Color.White.copy(alpha = 0.07f),
    backdrop = Color(0xFF1A1E1D),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** 玻璃上的文字颜色：白天是深墨绿（比纯黑柔和），夜里是浅灰绿 */
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkSoft: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSoft
/** 对话框、下拉菜单的底色 */
val DialogColor: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.dialog

val CardColor: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val BackdropColor: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.backdrop

/**
 * 强调色当字、图标用时：白天原样；夜里深色玻璃上太暗看不清，往白里调亮一些
 */
@Composable
@ReadOnlyComposable
fun Color.onGlass(): Color = if (LocalPalette.current.dark) lerp(this, Color.White, 0.45f) else this

/** 压在卫星图上的玻璃：照片又花又暗，默认那种玻璃要实一些，字才看得清 */
fun Palette.overImagery(): Palette = copy(glass = glass.copy(alpha = if (dark) 0.86f else 0.84f))

/** 跟着系统的深色模式走 */
@Composable
fun RandoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF6FCB94),
            secondary = RandoOrange,
            secondaryContainer = Color(0xFF24402F),
            onSecondaryContainer = Color(0xFFD5EEDD),
        )
    } else {
        lightColorScheme(
            primary = RandoGreen,
            secondary = RandoOrange,
            secondaryContainer = Color(0xFFDCEFE1),
            onSecondaryContainer = Color(0xFF14361F),
        )
    }
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
