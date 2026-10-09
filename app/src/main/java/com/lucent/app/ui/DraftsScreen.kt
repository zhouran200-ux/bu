package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.data.Note

@Composable
fun DraftNotesScreen(onBack: () -> Unit, onOpen: (Note) -> Unit) {
    onBack()
}
