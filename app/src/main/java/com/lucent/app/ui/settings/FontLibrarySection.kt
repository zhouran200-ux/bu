package com.lucent.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.FontStore
import com.lucent.app.i18n.S
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.SYSTEM_FONT_KEY
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.rememberLucentFont



@Composable
internal fun FontLibrarySection(
    fontKey: String,
    fonts: List<FontStore.FontSlot>,
    error: String,
    importing: Boolean,
    canImportMore: Boolean,
    onSelectSystem: () -> Unit,
    onSelectFont: (String) -> Unit,
    onRequestDelete: (FontStore.FontSlot) -> Unit,
    onImportClick: () -> Unit
): Unit {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.settingsFontTitle,
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight
            )
            Text(
                text = S.settingsFontSub,
                color = onGradientMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (error.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        val isSystemSelected = fontKey.isBlank() || fontKey == SYSTEM_FONT_KEY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSystemSelected) onGradient.copy(alpha = 0.08f) else Color.Transparent
                )
                .border(
                    1.dp,
                    if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else onGradient.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    Haptics.tick(context)
                    onSelectSystem()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else onGradient.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    color = if (isSystemSelected) MaterialTheme.colorScheme.primary else onGradient,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = S.fontSystemLabel,
                color = onGradient,
                fontSize = LucentTitles.ItemSize,
                fontWeight = if (isSystemSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSystemSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        fonts.forEach { slot ->
            val isSelected = fontKey == slot.id
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) onGradient.copy(alpha = 0.08f) else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else onGradient.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        Haptics.tick(context)
                        onSelectFont(slot.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fontSlotFamily = rememberLucentFont(slot.id)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else onGradient.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        color = if (isSelected) MaterialTheme.colorScheme.primary else onGradient,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontSlotFamily,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = slot.name,
                    color = onGradient,
                    fontSize = LucentTitles.ItemSize,
                    fontFamily = fontSlotFamily,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = {
                        Haptics.tick(context)
                        onRequestDelete(slot)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = S.fontDeleteA11y,
                        tint = onGradientMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = onGradient.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, onGradient.copy(alpha = 0.16f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = canImportMore && !importing) {
                    Haptics.tick(context)
                    onImportClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = onGradient,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S.fontImporting, color = onGradient, fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = onGradient,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (canImportMore) S.fontImportButton else S.fontSlotsFullHint(FontStore.MAX_FONTS),
                        color = onGradient,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
