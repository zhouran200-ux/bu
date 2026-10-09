package com.lucent.app.ui

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.lucent.app.data.FontStore

const val SYSTEM_FONT_KEY = "system"

object LucentFontResolver {

    private class Holder(val family: FontFamily?)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Holder>()

    fun resolve(context: Context, fontKey: String?): FontFamily? {
        if (fontKey.isNullOrBlank() || fontKey == SYSTEM_FONT_KEY) return null
        return cache.getOrPut(fontKey) {
            try {
                val file = FontStore.fontFile(context.applicationContext, fontKey)
                    ?: return@getOrPut Holder(null)
                Holder(FontFamily(Font(file)))
            } catch (_: Throwable) {
                Holder(null)
            }
        }.family
    }

    fun evict(fontKey: String) {
        cache.remove(fontKey)
    }

    fun evictAll() {
        cache.clear()
    }
}

@Composable
fun lucentTypography(
    fontKey: String,
    fontScale: Float = 1.0f,
    lineSpacing: Float = 1.2f,
    letterSpacing: Float = 0.0f
): Typography {
    val context = LocalContext.current
    val base = Typography()
    val family = remember(fontKey) { LucentFontResolver.resolve(context, fontKey) }

    fun TextStyle.applyCustom(): TextStyle {
        val customLineHeight = if (lineHeight.value > 0f) {
            (lineHeight.value * lineSpacing).sp
        } else {
            (fontSize.value * 1.35f * lineSpacing).sp
        }
        val extraLetterSpacing = letterSpacing.sp
        val finalLetterSpacing = if (this.letterSpacing.value != 0f) {
            (this.letterSpacing.value + letterSpacing).sp
        } else {
            extraLetterSpacing
        }

        return copy(
            fontFamily = family ?: fontFamily,
            lineHeight = customLineHeight,
            letterSpacing = finalLetterSpacing
        )
    }

    return Typography(
        displayLarge = base.displayLarge.applyCustom(),
        displayMedium = base.displayMedium.applyCustom(),
        displaySmall = base.displaySmall.applyCustom(),
        headlineLarge = base.headlineLarge.applyCustom(),
        headlineMedium = base.headlineMedium.applyCustom(),
        headlineSmall = base.headlineSmall.applyCustom(),
        titleLarge = base.titleLarge.applyCustom(),
        titleMedium = base.titleMedium.applyCustom(),
        titleSmall = base.titleSmall.applyCustom(),
        bodyLarge = base.bodyLarge.applyCustom(),
        bodyMedium = base.bodyMedium.applyCustom(),
        bodySmall = base.bodySmall.applyCustom(),
        labelLarge = base.labelLarge.applyCustom(),
        labelMedium = base.labelMedium.applyCustom(),
        labelSmall = base.labelSmall.applyCustom()
    )
}
