package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object LucentTitles {

    val ScreenSize: TextUnit = 22.sp
    val EntrySize: TextUnit = 18.sp
    val SectionSize: TextUnit = 16.sp
    val ItemSize: TextUnit = 14.sp

    val ScreenWeight = FontWeight.Medium
    val EntryWeight = FontWeight.Medium
    val SectionWeight = FontWeight.Medium
    val ItemWeight = FontWeight.Normal

    fun titleInk(ink: Color): Color = ink.copy(alpha = 0.9f)

    fun sizeForDepth(depth: Int): TextUnit = when {
        depth <= 1 -> ScreenSize
        depth == 2 -> EntrySize
        depth == 3 -> SectionSize
        else -> ItemSize
    }

    fun weightForDepth(depth: Int): FontWeight = when {
        depth <= 2 -> ScreenWeight
        depth == 3 -> SectionWeight
        else -> ItemWeight
    }
}
