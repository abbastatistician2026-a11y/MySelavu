package com.myselavu.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The handful of icons the app needs, drawn from path data (24×24 grid, Material-style).
 * Avoids the large material-icons-extended library to keep the APK small.
 */
object AppIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(PathParser().parsePathString(p).toNodes(), fill = SolidColor(Color.Black))
        }
        return b.build()
    }

    val Add by lazy { icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val CheckCircle by lazy {
        icon("check_circle", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z")
    }
    val Warning by lazy { icon("warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z") }
    val Error by lazy {
        icon("error", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2L11,7h2v6z")
    }
    val Calendar by lazy {
        icon("calendar", "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3,4.9 3,6v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM19,20H5V10h14V20zM19,8H5V6h14V8z")
    }
    val Flame by lazy {
        icon("flame", "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67z")
    }
}
