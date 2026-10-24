package com.personal.rando.screenshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.personal.rando.ui.RandoTheme
import com.personal.rando.ui.glass.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 截图用：拿真实的新加坡地图截图当背景，上面盖界面，玻璃取这张图 */
@Composable
fun OverMap(map: String = "map_light.png", content: @Composable BoxScope.() -> Unit) {
    val bitmap = remember(map)
    val haze = rememberHazeState()
    RandoTheme {
        CompositionLocalProvider(LocalHazeState provides haze) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().hazeSource(haze),
                    contentScale = ContentScale.Crop,
                )
                content()
            }
        }
    }
}

// Robolectric 的类加载器看不到测试资源，直接按文件读（测试在 app 目录下跑）
fun mapImage(name: String) = remember(name)

private fun remember(name: String) = OverMapCache.getOrPut(name) {
    BitmapFactory.decodeFile(java.io.File("src/test/resources/preview/$name").absolutePath).asImageBitmap()
}

private val OverMapCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
