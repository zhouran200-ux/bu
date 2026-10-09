package com.lucent.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TemplateIcon { JOURNAL, MEETING, IDEA }

enum class NoteTemplate(val iconName: TemplateIcon) {

    JOURNAL(TemplateIcon.JOURNAL),
    MEETING(TemplateIcon.MEETING),
    IDEA(TemplateIcon.IDEA);

    val label: String
        get() = when (this) {
            JOURNAL -> com.lucent.app.i18n.S.tplJournal
            MEETING -> com.lucent.app.i18n.S.tplMeeting
            IDEA -> com.lucent.app.i18n.S.tplIdea
        }

    fun prefill(): Prefill {
        val today = LocalDate.now()
        val locale = com.lucent.app.i18n.lucentLocale()
        val longDate = today.format(DateTimeFormatter.ofPattern(com.lucent.app.i18n.S.tplLongDatePattern, locale))
        val shortDate = today.format(DateTimeFormatter.ofPattern(com.lucent.app.i18n.S.tplShortDatePattern, locale))

        return when (this) {
            JOURNAL -> Prefill(
                title = longDate,
                body = com.lucent.app.i18n.S.tplJournalBody
            )

            MEETING -> Prefill(
                title = com.lucent.app.i18n.S.tplMeetingTitle(shortDate),
                tags = setOf(com.lucent.app.i18n.S.tagWork),
                body = com.lucent.app.i18n.S.tplMeetingBody(shortDate)
            )

            IDEA -> Prefill(
                title = "",
                body = com.lucent.app.i18n.S.tplIdeaBody
            )
        }
    }

    data class Prefill(
        val title: String = "",
        val body: String = "",
        val tags: Set<String> = emptySet()
    )
}
