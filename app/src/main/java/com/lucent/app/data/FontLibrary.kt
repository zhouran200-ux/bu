package com.lucent.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FontLibrary {

    suspend fun slots(context: Context): List<FontStore.FontSlot> =
        withContext(Dispatchers.IO) { FontStore.index(context).slots }

    suspend fun import(context: Context, source: PlatformFontSource, customName: String? = null): FontStore.FontSlot =
        withContext(Dispatchers.IO) { FontStore.import(context, source, customName) }

    suspend fun delete(context: Context, id: String) =
        withContext(Dispatchers.IO) { FontStore.delete(context, id) }
}
