package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileModelPickerSheet(
    profiles: List<ApiProfile>,
    currentProfileId: String,
    currentModel: String,
    onSelect: (ApiProfile, String) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean = true,
    onManageApi: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialPosition = remember {
        modelPickerPosition(profiles, currentProfileId, currentModel)
    }
    var expandedProfileId by remember { mutableStateOf(initialPosition.expandedProfileId) }
    val listState = remember { LazyListState(initialPosition.firstVisibleItemIndex, 0) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val ink = LocalOnGradient.current
    val accent = MaterialTheme.colorScheme.primary
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = { latestDismiss() },
        sheetState = sheetState,
        containerColor = lucentPanelBackground(),
        sheetGesturesEnabled = true,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "title") {
                Text("模型选择", fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight,
                    color = ink, modifier = Modifier.padding(vertical = 12.dp))
            }
            if (profiles.isEmpty()) {
                item(key = "empty") { Text("暂无 API 方案，请先添加", color = ink, modifier = Modifier.padding(12.dp)) }
            }
            profiles.forEach { profile ->
                val models = ApiProfiles.availableModels(profile)
                val selected = profile.id == currentProfileId
                val expanded = expandedProfileId == profile.id
                item(key = "profile:${profile.id}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable(enabled = models.isNotEmpty(), role = Role.Button) {
                                expandedProfileId = if (expanded) null else profile.id
                            }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                            contentDescription = if (models.isNotEmpty()) if (expanded) "收起模型列表" else "展开模型列表" else null,
                            tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontSize = 16.sp, color = ink,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                models.isEmpty() -> "未配置模型，请到 API 设置添加"
                                models.size == 1 -> models.single()
                                selected -> currentModel
                                else -> "点击展开模型列表"
                            }, color = ink.copy(alpha = 0.65f), fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(models.size.toString(), color = ink.copy(alpha = 0.6f))
                        if (selected && models.size == 1) Icon(Icons.Default.Check, "当前模型", tint = accent, modifier = Modifier.size(18.dp))
                    }
                }
                if (expanded && models.isNotEmpty()) {
                    models.forEachIndexed { index, model ->
                        item(key = "model:${profile.id}:$index") {
                            val modelSelected = selected && currentModel == model
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(selected = modelSelected, role = Role.RadioButton) {
                                        onSelect(profile, model)
                                    }
                                    .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(model, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    color = if (modelSelected) accent else ink,
                                    fontWeight = if (modelSelected) FontWeight.Bold else FontWeight.Normal)
                                if (modelSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Check, "当前模型", tint = accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "manage") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.06f))
                        .clickable(role = Role.Button) {
                            onManageApi()
                        }.heightIn(min = 48.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, null, tint = ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("管理API配置", modifier = Modifier.weight(1f), color = ink)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "前往 API 设置", tint = ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
