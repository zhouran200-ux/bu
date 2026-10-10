package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object LucentTitles {

    val ScreenSize: TextUnit = 22.sp
    val EntrySize: TextUnit = 18.sp
    val SectionSize: TextUnit = 16.sp
    val ItemSize: TextUnit = 14.sp

    val ScreenWeight = FontWeight.SemiBold
    val EntryWeight = FontWeight.SemiBold
    val SectionWeight = FontWeight.SemiBold
    val ItemWeight = FontWeight.Medium

    @Composable
    fun screen(): TextStyle = TextStyle(fontSize = ScreenSize, fontWeight = ScreenWeight)

    @Composable
    fun entry(): TextStyle = TextStyle(fontSize = EntrySize, fontWeight = EntryWeight)

    @Composable
    fun section(): TextStyle = TextStyle(fontSize = SectionSize, fontWeight = SectionWeight)

    @Composable
    fun item(): TextStyle = TextStyle(fontSize = ItemSize, fontWeight = ItemWeight)
}
