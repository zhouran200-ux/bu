package com.lucent.app.ui

import android.content.Context
import com.lucent.app.data.CitationRef
import com.lucent.app.data.Note
import com.lucent.app.data.NovelSourceIndex
import com.lucent.app.data.SourceCitations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun openCitationTarget(
    scope: CoroutineScope,
    context: Context,
    sourceNotes: List<Note>,
    ref: CitationRef,
    onOpenSource: (Long, String, String) -> Unit
) {
    val target = sourceNotes.firstOrNull { it.id == ref.sourceId }
    scope.launch {
        val index = withContext(Dispatchers.Default) { target?.let { NovelSourceIndex.get(it) } }
        val reason = SourceCitations.rejectionReason(ref, index?.noteId, index?.revision)
        when {
            reason != null -> LucentToast.show(context, reason)
            index?.tagged?.sliceOf(ref.sliceId) == null ->
                LucentToast.show(context, "引用切片不存在，无法定位原文")
            else -> onOpenSource(index.noteId, ref.sliceId, index.revision)
        }
    }
}
