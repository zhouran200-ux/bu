package com.lucent.app.ui

internal fun notebookSourceCount(raw: Int?): Int = (raw ?: 0).coerceAtLeast(0)
