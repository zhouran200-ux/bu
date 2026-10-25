package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.TypographyScopes
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackgroundEnvironment
import com.lucent.app.ui.LocalBackgroundEnvironment
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun scopeAndParagraphControlsKeepAccessibleTargets() {
        val scope = mutableStateOf(TypographyScopes.GLOBAL)
        val changes = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalBackgroundEnvironment provides BackgroundEnvironment(active = true, motionEnabled = false),
                    LocalOnGradient provides Color.Black,
                    LocalOnGradientMuted provides Color.Black.copy(alpha = 0.65f)
                ) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        FontTypographySection(
                            fontKey = "system",
                            fontScale = 1f,
                            appliedFontScale = 1f,
                            lineSpacing = 1.2f,
                            letterSpacing = 0f,
                            paragraphSpacing = 1f,
                            typographyScope = scope.value,
                            onFontScaleChange = {}, onFontScaleCommit = {},
                            onLineSpacingChange = {}, onLineSpacingCommit = {},
                            onLetterSpacingChange = {}, onLetterSpacingCommit = {},
                            onParagraphSpacingChange = {}, onParagraphSpacingCommit = {},
                            onTypographyScopeChange = { scope.value = it; changes += it },
                            onResetDefaults = {}
                        )
                    }
                }
            }
        }
        val minHeight = with(rule.density) { 44.dp.toPx() }.toInt()
        assertTrue(rule.onNodeWithText(S.typographyScopeGlobal).fetchSemanticsNode().size.height >= minHeight)
        rule.onNodeWithText(S.typographyScopeGlobal).assertIsSelected()
        rule.onNodeWithText(S.typographyScopeText).performClick()
        rule.runOnIdle { assertEquals(listOf(TypographyScopes.TEXT), changes) }
        rule.onNodeWithText(S.typographyScopeText).assertIsSelected()
        rule.onNodeWithText(S.paragraphSpacingTitle).performScrollTo().assertIsDisplayed()
    }
}
