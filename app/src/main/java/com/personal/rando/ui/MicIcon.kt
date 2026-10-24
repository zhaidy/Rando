package com.personal.rando.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 麦克风图标（Material 的 mic），基础图标库里没有，不为它引整个扩展图标库 */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
                    "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** 相机图标（Material 的 photo_camera） */
val CameraIcon: ImageVector by lazy {
    ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                    "M9,2L7.17,4L4,4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2L9,2zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
