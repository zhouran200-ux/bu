package com.lucent.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.lucent.app.i18n.S
import com.lucent.app.ui.GlassButton

@Composable
internal fun ApiProfileSaveButton(dragState: State<ProfileDrag?>, onClick: () -> Unit) {
    GlassButton(
        text = S.saveApi,
        enabled = dragState.value == null,
        onClick = onClick
    )
}
