package com.lucent.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SourceChapterBadge(title: String, modifier: Modifier = Modifier) {
    val reading = LocalReadingTypography.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = LocalOnGradient.current,
        shadowElevation = 0.dp,
        modifier = modifier.foregroundGlass(12.dp, reading = true)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
                fontFamily = reading.fontFamily
            ),
            fontWeight = LucentTitles.SectionWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
