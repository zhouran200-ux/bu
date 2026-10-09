package com.lucent.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.AppScope
import kotlinx.coroutines.launch

object DataCache {

    var notes by mutableStateOf<List<Note>>(emptyList())
        private set

    private var started = false

    fun warm(db: AppDatabase) {
        if (started) return
        started = true
        AppScope.io.launch {
            runCatching { db.noteDao().getAll().collect { notes = it } }
                .onFailure { android.util.Log.e("LucentDataCache", "note cache collector failed", it) }
        }
    }
}
