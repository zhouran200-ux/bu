package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.i18n.S

@Composable
internal fun PaletteSwatch(colors: List<Color>) {
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    val preview = if (colors.size >= 2) colors else listOf(
        colors.firstOrNull() ?: fallback,
        colors.firstOrNull() ?: fallback
    )
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .width(44.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(preview))
    )
}


@Composable
internal fun NavCard(
    title: String,
    subtitle: String,
    depth: Int = 1,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableGlass(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = LucentTitles.titleInk(onGradient),
                fontSize = LucentTitles.sizeForDepth(depth + 1),
                fontWeight = LucentTitles.weightForDepth(depth + 1)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = onGradientMuted, fontSize = 13.sp)
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = onGradientMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun LockNote(note: String) {
    if (note.isBlank()) return
    Text(
        note,
        color = LocalOnGradientMuted.current,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

internal object SettingsTrail {

    fun parent(route: SettingsRoute): SettingsRoute? = if (route == SettingsRoute.Root) null else SettingsRoute.Root

    fun trail(route: SettingsRoute): List<SettingsRoute> {
        val out = ArrayList<SettingsRoute>()
        var cursor: SettingsRoute? = route
        var guard = 0
        while (cursor != null && guard++ < 8) {
            out.add(0, cursor)
            cursor = parent(cursor)
        }
        if (out.firstOrNull() != SettingsRoute.Root) out.add(0, SettingsRoute.Root)
        return out
    }

    fun title(route: SettingsRoute): String = when (route) {
        SettingsRoute.Root -> S.tabSettings
        SettingsRoute.Theme -> S.settingsThemeTitle
        SettingsRoute.Background -> S.settingsBackgroundTitle
        SettingsRoute.Font -> S.fontTypographyTitle
        SettingsRoute.Api -> S.settingsApiConfigTitle
    }
}

@Composable
internal fun SettingsBreadcrumb(
    route: SettingsRoute,
    onNavigate: (SettingsRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val crumbs = remember(route) { SettingsTrail.trail(route) }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val titles = crumbs.map { SettingsTrail.title(it) }
        val rootValue = LucentTitles.ScreenSize.value.toDouble()
        val crumbValues = titles.indices.map { LucentTitles.sizeForDepth(it + 1).value.toDouble() }
        val textWidth = titles.sumIndexed { index, title ->
            title.sumOf { ch -> if (ch.code > 0x2E7F) 1.0 else 0.52 } * crumbValues[index]
        } + (crumbs.size - 1).coerceAtLeast(0) * (0.3 * rootValue + 8.0)
        val available = (maxWidth.value - 28f).coerceAtLeast(1f).toDouble()
        val scale = if (textWidth > 0.0) {
            (available / textWidth).toFloat().coerceIn(0.7f, 1f)
        } else {
            1f
        }
        val separatorSize = (rootValue * scale / 2.0).sp
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(
                        "\u2013",
                        color = onGradientMuted,
                        fontSize = separatorSize,
                        modifier = Modifier.padding(horizontal = 4.dp).alignByBaseline()
                    )
                }
                val isLast = index == crumbs.lastIndex
                Text(
                    text = titles[index],
                    color = if (isLast) onGradient else onGradientMuted,
                    fontSize = LucentTitles.sizeForDepth(index + 1) * scale,
                    fontWeight = LucentTitles.weightForDepth(index + 1),
                    maxLines = 1,
                    modifier = Modifier
                        .alignByBaseline()
                        .clickable(enabled = !isLast) {
                            Haptics.tick(context)
                            onNavigate(crumb)
                        }
                )
            }
        }
    }
}

private inline fun <T> List<T>.sumIndexed(block: (Int, T) -> Double): Double {
    var total = 0.0
    forEachIndexed { index, item -> total += block(index, item) }
    return total
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiModelPickerDialog(
    names: List<String>,
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(names) { mutableStateOf(selected) }
    val drawer = rememberModelPickerDrawer(sheetState, onDismiss = onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassPanelColor(),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = drawer.modifier,
        properties = DrawerSheetProperties,
        sheetGesturesEnabled = false,
        dragHandle = null
    ) {
        drawer.handle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().then(drawer.dragModifier)
            ) {
                Text(
                    text = S.apiModelsTitle,
                    fontSize = LucentTitles.SectionSize,
                    fontWeight = LucentTitles.SectionWeight,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = S.settingsModelCount(names.size),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = S.settingsSelectedModels(draft.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(glassControlColor())
                        .clickable { draft = draft + names }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = S.selectAll,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(glassControlColor())
                        .clickable { draft = emptySet() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = S.clearAllSelection,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (names.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = S.apiModelsEmpty,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = minOf(340.dp, sheetContentHeightLimit())),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(names, key = { it }) { name ->
                            val checked = name in draft
                            val itemBg = glassControlColor(selected = checked)
                            val itemBorder = if (checked) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                Color.Transparent
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(itemBg)
                                    .then(
                                        if (checked) Modifier.border(1.dp, itemBorder, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        draft = if (checked) draft - name else draft + name
                                    }
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = LucentTitles.ItemSize,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { drawer.close { onDone(draft) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = S.settingsConfirmSelection(draft.size),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
