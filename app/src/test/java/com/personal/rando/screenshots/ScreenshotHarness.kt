package com.personal.rando.screenshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.personal.rando.ui.RandoTheme
import com.personal.rando.ui.glass.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 截图用：拿真实的新加坡地图截图当背景，上面盖界面，玻璃取这张图。
 * [dark]：夜间模式，地图截图压成灰黑色，大致像深色样式（只看玻璃和字的对比度）
 */
@Composable
fun OverMap(map: String = "map_light.png", dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val bitmap = remember(map)
    val haze = rememberHazeState()
    RandoTheme(dark = dark) {
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().hazeSource(haze),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (dark) NightMap else null,
                )
                content()
            }
        }
    }
}

/** 先变灰再反过来压暗：亮的底变深灰，路和字变浅 */
private val NightMap = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.21f, -0.42f, -0.08f, 0f, 205f,
            -0.21f, -0.42f, -0.08f, 0f, 210f,
            -0.21f, -0.42f, -0.08f, 0f, 208f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

// Robolectric 的类加载器看不到测试资源，直接按文件读（测试在 app 目录下跑）
fun mapImage(name: String) = remember(name)

private fun remember(name: String) = OverMapCache.getOrPut(name) {
    BitmapFactory.decodeFile(java.io.File("src/test/resources/preview/$name").absolutePath).asImageBitmap()
}

private val OverMapCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
