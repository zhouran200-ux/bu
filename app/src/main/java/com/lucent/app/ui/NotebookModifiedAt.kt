package com.lucent.app.ui

import com.lucent.app.i18n.S
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun notebookModifiedAtLabel(
    millis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String = DateTimeFormatter.ofPattern(S.notebookModifiedAtPattern, locale)
    .withZone(zoneId)
    .format(Instant.ofEpochMilli(millis))
