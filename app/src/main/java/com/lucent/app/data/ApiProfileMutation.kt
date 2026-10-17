package com.lucent.app.data

internal data class ApiProfileSnapshot(val profiles: List<ApiProfile>, val selectedId: String?) {
    val selectedIndex: Int get() = profiles.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
}

internal sealed interface ApiProfileMutation {
    data class Select(val id: String) : ApiProfileMutation
    data class Add(val profile: ApiProfile) : ApiProfileMutation
    data class Delete(val id: String) : ApiProfileMutation
    data class Update(val profile: ApiProfile) : ApiProfileMutation
    data class Reorder(val ids: List<String>, val selectedId: String?) : ApiProfileMutation
}

internal fun ApiProfileSnapshot.mutate(command: ApiProfileMutation): ApiProfileSnapshot = when (command) {
    is ApiProfileMutation.Select -> if (profiles.any { it.id == command.id }) copy(selectedId = command.id) else this
    is ApiProfileMutation.Add -> if (profiles.size >= ApiProfiles.MAX || profiles.any { it.id == command.profile.id }) this
        else ApiProfileSnapshot(profiles + command.profile, command.profile.id)
    is ApiProfileMutation.Delete -> {
        val remaining = profiles.filterNot { it.id == command.id }
        val selected = if (remaining.any { it.id == selectedId }) selectedId else remaining.getOrNull(selectedIndex.coerceAtMost((remaining.size - 1).coerceAtLeast(0)))?.id
        ApiProfileSnapshot(remaining, selected)
    }
    is ApiProfileMutation.Update -> if (profiles.none { it.id == command.profile.id }) this
        else copy(profiles = profiles.map { if (it.id == command.profile.id) command.profile else it })
    is ApiProfileMutation.Reorder -> {
        val requested = command.ids.distinct().mapNotNull { id -> profiles.firstOrNull { it.id == id } }
        val ordered = requested + profiles.filter { it.id !in command.ids }
        copy(profiles = ordered)
    }
}
