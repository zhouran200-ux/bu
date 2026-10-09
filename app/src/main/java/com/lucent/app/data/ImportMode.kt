package com.lucent.app.data

enum class ImportMode {
    PARALLEL,

    OVERWRITE;

    companion object {
        val DEFAULT = PARALLEL
    }
}

enum class ImportAction {
    INSERT,

    REPLACE,

    SKIP
}

object ImportDecision {

    fun noteKey(title: String): String = title.trim().lowercase()

    fun forNote(
        mode: ImportMode,
        localUpdatedAt: Long?,
        backupUpdatedAt: Long,
        exactDuplicate: Boolean
    ): ImportAction {
        if (exactDuplicate) return ImportAction.SKIP
        if (localUpdatedAt == null) return ImportAction.INSERT
        return when (mode) {
            ImportMode.PARALLEL -> ImportAction.INSERT
            ImportMode.OVERWRITE ->
                if (backupUpdatedAt > localUpdatedAt) ImportAction.REPLACE else ImportAction.SKIP
        }
    }
}
