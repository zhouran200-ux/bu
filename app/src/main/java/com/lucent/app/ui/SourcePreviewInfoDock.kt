package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

@Composable
internal fun SourcePreviewInfoDock(sourceLine: String, modifier: Modifier = Modifier) {
    val reading = LocalReadingTypography.current
    Column(modifier.foregroundGlass(12.dp, reading = true).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = S.sourceIndexPreviewTitle,
            color = LocalOnGradient.current,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = reading.fontFamily,
            fontWeight = LucentTitles.SectionWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sourceLine,
            color = LocalOnGradientMuted.current,
            fontSize = 11.sp,
            fontFamily = reading.fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
