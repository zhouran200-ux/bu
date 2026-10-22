package com.lucent.app.ui

import com.lucent.app.data.SourceCitations

internal fun chatBodyForCopy(content: String): String =
    SourceCitations.marker.replace(content, "").trimEnd()
