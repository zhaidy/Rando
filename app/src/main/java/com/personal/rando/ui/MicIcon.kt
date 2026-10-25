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

/** 定位准星（Material 的 my_location）：回到我的位置；大头针容易被当成「放个标记」 */
val MyLocationIcon: ImageVector by lazy {
    ImageVector.Builder("MyLocation", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
                    "M20.94,11c-0.46,-4.17 -3.77,-7.48 -7.94,-7.94L13,1h-2v2.06C6.83,3.52 3.52,6.83 3.06,11L1,11v2h2.06c0.46,4.17 3.77,7.48 7.94,7.94L11,23h2v-2.06c4.17,-0.46 7.48,-3.77 7.94,-7.94L23,13v-2h-2.06z" +
                    "M12,19c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** 两个人（Material 的 group）：一起骑 */
val GroupIcon: ImageVector by lazy {
    ImageVector.Builder("Group", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
                    "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
                    "M8,13c-2.33,0 -7,1.17 -7,3.5L1,19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                    "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45L17,19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** 指北针（Material 的 navigation，尖朝上）：地图转了以后点它转回正北 */
val NorthIcon: ImageVector by lazy {
    ImageVector.Builder("North", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString("M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** 两层叠着（Material 的 layers）：地图图层 */
val LayersIcon: ImageVector by lazy {
    ImageVector.Builder("Layers", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser().parsePathString(
                "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74z" +
                    "M12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
