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
    private const val MAX_CACHED_CHARS = 2_000_000

    @Synchronized
    fun get(note: Note): IndexedSource {
        cache[note.id]?.takeIf { it.title == note.title && it.body == note.body }?.let { return it }
        val index = IndexedSource(note.id, SourceTagging.revisionOf(note.body, note.title), note.title,
            note.body, SourceTagging.tag(note.title, note.body))
        cache[note.id] = index
        while (cache.size > 1 && (cache.size > 4 || cache.values.sumOf { it.body.length.toLong() } > MAX_CACHED_CHARS)) {
            cache.remove(cache.keys.first())
        }
        return index
    }

    @Synchronized
    fun evict(noteId: Long) { cache.remove(noteId) }
}
