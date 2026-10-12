package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val IconStrokeWidth = 2f

private fun lineIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = IconStrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}

object LucentIcons {

    val Notebook: ImageVector by lazy {
        lineIcon(
            "Notebook",
            "M12 5v16",
            "M20.001 19A2 2 0 0 0 22 17V5a2 2 0 0 0 -1.999 -2L16 3.002A5 5 0 0 0 12 5a5 5 0 0 0 -4 -2H4a2 2 0 0 0 -2 2v12a2 2 0 0 0 1.999 2H8a5 5 0 0 1 4 2 5 5 0 0 1 4 -2z"
        )
    }

    val Settings: ImageVector by lazy {
        lineIcon(
            "Settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1 -2.33 4.033 2.34 2.34 0 0 0 -3.319 1.915 2.34 2.34 0 0 1 -4.659 0 2.34 2.34 0 0 0 -3.32 -1.915 2.34 2.34 0 0 1 -2.33 -4.033 2.34 2.34 0 0 0 0 -3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319 -1.915",
            "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0 -6"
        )
    }

    val Create: ImageVector by lazy {
        lineIcon(
            "Create",
            "M12 3H5a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2 -2v-7",
            "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1 -0.853 0.505l-2.873 0.84a0.5 0.5 0 0 1 -0.62 -0.62l0.84 -2.873a2 2 0 0 1 0.506 -0.852z"
        )
    }

    val Model: ImageVector by lazy {
        lineIcon(
            "Model",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0 -1.594 1.594l-1.051 5.558a1 1 0 0 1 -1.966 0l-1.051 -5.558a2 2 0 0 0 -1.594 -1.594l-5.558 -1.051a1 1 0 0 1 0 -1.966l5.558 -1.051a2 2 0 0 0 1.594 -1.594z"
        )
    }

    val PresetAuto: ImageVector by lazy {
        lineIcon(
            "PresetAuto",
            "M12 15v5s3.03 -0.55 4 -2c1.08 -1.62 0 -5 0 -5",
            "M4.5 16.5c-1.5 1.26 -2 5 -2 5s3.74 -0.5 5 -2c0.71 -0.84 0.7 -2.13 -0.09 -2.91a2.18 2.18 0 0 0 -2.91 -0.09",
            "M9 12a22 22 0 0 1 2 -3.95A12.88 12.88 0 0 1 22 2c0 2.72 -0.78 7.5 -6 11a22.4 22.4 0 0 1 -4 2z",
            "M9 12H4s0.55 -3.03 2 -4c1.62 -1.08 5 0.05 5 0.05"
        )
    }

    val PresetFast: ImageVector by lazy {
        lineIcon(
            "PresetFast",
            "M15.914 4a1.5 1.5 0 0 0 -2.474 -1.561l-9 9A1.5 1.5 0 0 0 5.5 14h4.002a0.5 0.5 0 0 1 0.471 0.666L8.086 20a1.5 1.5 0 0 0 2.475 1.56l9 -9A1.5 1.5 0 0 0 18.5 10h-3.997a0.5 0.5 0 0 1 -0.472 -0.667z"
        )
    }

    val PresetBalanced: ImageVector by lazy {
        lineIcon(
            "PresetBalanced",
            "M12 3v18",
            "M3 7h18",
            "M7 7l-4 7a4 4 0 0 0 8 0z",
            "M17 7l-4 7a4 4 0 0 0 8 0z",
            "M8 21h8"
        )
    }

    val PresetDeep: ImageVector by lazy {
        lineIcon(
            "PresetDeep",
            "M15 14c0.2 -1 0.7 -1.7 1.5 -2.5 1 -0.9 1.5 -2.2 1.5 -3.5A6 6 0 0 0 6 8c0 1 0.2 2.2 1.5 3.5 0.7 0.7 1.3 1.5 1.5 2.5",
            "M9 18h6",
            "M10 22h4"
        )
    }

    val PresetMaximum: ImageVector by lazy {
        lineIcon(
            "PresetMaximum",
            "m17 11 -5 -5 -5 5",
            "m17 18 -5 -5 -5 5"
        )
    }

    fun presetIcon(preset: com.lucent.app.data.ReasoningPreset): ImageVector = when (preset) {
        com.lucent.app.data.ReasoningPreset.AUTO -> PresetAuto
        com.lucent.app.data.ReasoningPreset.FAST -> PresetFast
        com.lucent.app.data.ReasoningPreset.BALANCED -> PresetBalanced
        com.lucent.app.data.ReasoningPreset.DEEP -> PresetDeep
        com.lucent.app.data.ReasoningPreset.MAXIMUM -> PresetMaximum
    }
}
