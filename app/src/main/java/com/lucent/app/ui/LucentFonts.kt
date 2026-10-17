package com.lucent.app.ui

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    internal fun cached(fontKey: String?): FontFamily? = fontKey?.let { cache[it]?.family }

    fun evict(fontKey: String) {
        cache.remove(fontKey)
    }
}

@Composable
internal fun rememberLucentFont(fontKey: String?): FontFamily? {
    val context = LocalContext.current.applicationContext
    var family by remember(fontKey) { mutableStateOf(LucentFontResolver.cached(fontKey)) }
    LaunchedEffect(context, fontKey) {
        family = withContext(Dispatchers.IO) { LucentFontResolver.resolve(context, fontKey) }
    }
    return family
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
    val family = rememberLucentFont(fontKey)

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
