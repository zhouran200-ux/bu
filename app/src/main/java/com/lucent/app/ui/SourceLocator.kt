package com.lucent.app.ui

import com.lucent.app.data.SourceTagging

internal object SourceLocator {

    fun blockIndexForOffset(blocks: List<RenderBlock>, offset: Int): Int {
        if (blocks.isEmpty()) return -1
        var found = -1
        for (index in blocks.indices) {
            if (blocks[index].startOffset <= offset) found = index else break
        }
        return found
    }

    fun blockIndexForSlice(title: String, body: String, blocks: List<RenderBlock>, sliceId: String): Int {
        val slice = SourceTagging.tag(title, body).sliceOf(sliceId) ?: return -1
        return blockIndexForOffset(blocks, slice.startOffset)
    }
}
