package com.personal.rando.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** material-icons-core 里没有的几个图标，按 Material 的路径自己画 */
object RandoIcons {
    /** 地铁（「坐 MRT 回家」） */
    val Train: ImageVector by lazy {
        icon(
            "Train",
            "M12,2c-4,0 -8,0.5 -8,4v9.5C4,17.43 5.57,19 7.5,19L6,20.5v0.5h2.23l2,-2H14l2,2h2v-0.5L16.5,19c1.93,0 3.5,-1.57 3.5,-3.5V6" +
                "c0,-3.5 -3.58,-4 -8,-4zM7.5,17c-0.83,0 -1.5,-0.67 -1.5,-1.5S6.67,14 7.5,14s1.5,0.67 1.5,1.5S8.33,17 7.5,17zM11,10H6V6h5v4z" +
                "M13,10V6h5v4h-5zM16.5,17c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
