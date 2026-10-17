package com.lucent.app.data

internal data class IndexedSource(
    val noteId: Long,
    val revision: String,
    val title: String,
    val body: String,
    val tagged: SourceTagging.Tagged
)

internal object NovelSourceIndex {
    private val cache = LinkedHashMap<Long, IndexedSource>(4, 0.75f, true)
    private const val MAX_CACHED_CHARS = 2_000_000L
    private var invalidation = 0L

    fun get(note: Note): IndexedSource {
        val observed: IndexedSource?
        val epoch: Long
        synchronized(this) {
            observed = cache[note.id]
            observed?.takeIf { it.title == note.title && it.body == note.body }?.let { return it }
            epoch = invalidation
        }
        val index = IndexedSource(note.id, SourceTagging.revisionOf(note.body, note.title), note.title,
            note.body, SourceTagging.tag(note.title, note.body))
        synchronized(this) {
            val current = cache[note.id]
            current?.takeIf { it.title == note.title && it.body == note.body }?.let { return it }
            if (epoch != invalidation || current !== observed) return index
            cache.remove(note.id)
            if (weight(index) > MAX_CACHED_CHARS) return index
            cache[note.id] = index
            while (cache.size > 4 || cache.values.sumOf { weight(it) } > MAX_CACHED_CHARS) {
                cache.remove(cache.keys.first())
            }
        }
        return index
    }

    private fun weight(index: IndexedSource): Long =
        index.body.length.toLong() + index.title.length + index.tagged.text.length +
            index.tagged.slices.size * 32L + index.tagged.chapters.sumOf { it.title.length.toLong() + 32L }

    @Synchronized
    fun evict(noteId: Long) {
        invalidation++
        cache.remove(noteId)
    }
}
