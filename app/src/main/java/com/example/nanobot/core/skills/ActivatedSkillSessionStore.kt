package com.example.nanobot.core.skills

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

enum class ActivatedSkillSource {
    USER,
    MODEL
}

data class ActivatedSkillRecord(
    val sessionId: String,
    val skillName: String,
    val contentHash: String?,
    val source: ActivatedSkillSource
)

@Singleton
class ActivatedSkillSessionStore @Inject constructor() {
    private val activatedBySession = mutableMapOf<String, MutableMap<String, ActivatedSkillRecord>>()
    private val state = MutableStateFlow<Map<String, Map<String, ActivatedSkillRecord>>>(emptyMap())

    fun isActivated(sessionId: String, skillName: String, contentHash: String?): Boolean {
        val record = activatedBySession[sessionId]?.get(skillName.lowercase()) ?: return false
        return record.contentHash == contentHash
    }

    fun markActivated(sessionId: String, skillName: String, contentHash: String?, source: ActivatedSkillSource) {
        val byName = activatedBySession.getOrPut(sessionId) { linkedMapOf() }
        byName[skillName.lowercase()] = ActivatedSkillRecord(sessionId, skillName, contentHash, source)
        publish()
    }

    fun listActivated(sessionId: String): List<ActivatedSkillRecord> {
        return activatedBySession[sessionId].orEmpty().values.toList().sortedBy { it.skillName.lowercase() }
    }

    fun observeActivated(sessionId: String): Flow<List<ActivatedSkillRecord>> {
        return state.map { sessions ->
            sessions[sessionId].orEmpty().values.toList().sortedBy { it.skillName.lowercase() }
        }
    }

    fun deactivate(sessionId: String, skillName: String) {
        activatedBySession[sessionId]?.remove(skillName.lowercase())
        publish()
    }

    private fun publish() {
        state.value = activatedBySession.mapValues { it.value.toMap() }
    }
}
