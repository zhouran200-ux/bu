package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

internal const val DEFAULT_READING_LINE_SPACING = 1.2f
internal const val DEFAULT_READING_PARAGRAPH_SPACING = 1f

internal data class ReadingTypography(
    val fontScale: Float = 1f,
    val lineSpacing: Float = DEFAULT_READING_LINE_SPACING,
    val letterSpacing: Float = 0f,
    val paragraphSpacing: Float = DEFAULT_READING_PARAGRAPH_SPACING,
    val fontFamily: FontFamily? = null,
    val scoped: Boolean = false
) {
    val lineFactor: Float get() = lineSpacing / DEFAULT_READING_LINE_SPACING
}

internal val LocalReadingTypography = staticCompositionLocalOf { ReadingTypography() }

@Composable
internal fun ScopedReadingTypography(content: @Composable () -> Unit) {
    val typography = LocalReadingTypography.current
    if (!typography.scoped) {
        content()
        return
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * typography.fontScale)
    ) {
        content()
    }
}
