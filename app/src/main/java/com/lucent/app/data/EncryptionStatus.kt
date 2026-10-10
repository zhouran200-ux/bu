package com.lucent.app.data


object EncryptionStatus {

    enum class State {
        UNKNOWN,

        ENCRYPTED,

        PLAINTEXT,

        LOCKED_OUT
    }

    @Volatile var database: State = State.UNKNOWN
        private set

    @Volatile var secrets: State = State.UNKNOWN
        private set

    @Volatile var attachments: State = State.UNKNOWN
        private set

    @Volatile var databaseReason: String? = null
        private set

    @Volatile var secretsReason: String? = null
        private set

    @Volatile var attachmentsReason: String? = null
        private set

    fun reportDatabase(state: State, reason: String? = null) {
        database = state
        databaseReason = reason
    }

}
