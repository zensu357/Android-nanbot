package com.example.nanobot.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.ProviderType
import com.example.nanobot.core.model.VoiceEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SettingsConfigStore {
    val configFlow: Flow<AgentConfig>
    val skillsDirectoryUriFlow: Flow<String?>
    val skillRootsFlow: Flow<List<String>>
    val trustProjectSkillsFlow: Flow<Boolean>
    suspend fun save(config: AgentConfig)
    suspend fun saveSkillsDirectoryUri(uri: String?)
    suspend fun addSkillRootUri(uri: String)
    suspend fun removeSkillRootUri(uri: String)
    suspend fun setTrustProjectSkills(trusted: Boolean)
}

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context
) : SettingsConfigStore {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("nanobot_settings.preferences_pb") }
    )

    override val configFlow: Flow<AgentConfig> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences ->
            AgentConfig(
                providerType = ProviderType.from(preferences[PROVIDER_TYPE]),
                apiKey = preferences[API_KEY].orEmpty(),
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                model = preferences[MODEL] ?: DEFAULT_MODEL,
                maxTokens = preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
                maxToolIterations = preferences[MAX_TOOL_ITERATIONS] ?: DEFAULT_MAX_TOOL_ITERATIONS,
                memoryWindow = preferences[MEMORY_WINDOW] ?: DEFAULT_MEMORY_WINDOW,
                reasoningEffort = preferences[REASONING_EFFORT],
                enableTools = preferences[ENABLE_TOOLS] ?: DEFAULT_ENABLE_TOOLS,
                enableMemory = preferences[ENABLE_MEMORY] ?: DEFAULT_ENABLE_MEMORY,
                enableVisualMemory = preferences[ENABLE_VISUAL_MEMORY] ?: DEFAULT_ENABLE_VISUAL_MEMORY,
                enableBackgroundWork = preferences[ENABLE_BACKGROUND_WORK] ?: DEFAULT_ENABLE_BACKGROUND_WORK,
                enableTaskPlanning = preferences[ENABLE_TASK_PLANNING] ?: DEFAULT_ENABLE_TASK_PLANNING,
                enableBehaviorLearning = preferences[ENABLE_BEHAVIOR_LEARNING] ?: DEFAULT_ENABLE_BEHAVIOR_LEARNING,
                webSearchApiKey = preferences[WEB_SEARCH_API_KEY].orEmpty(),
                webProxy = preferences[WEB_PROXY].orEmpty(),
                restrictToWorkspace = preferences[RESTRICT_TO_WORKSPACE] ?: DEFAULT_RESTRICT_TO_WORKSPACE,
                presetId = preferences[PRESET_ID] ?: DEFAULT_PRESET,
                enabledSkillIds = preferences[ENABLED_SKILL_IDS]
                    ?.split(SKILL_ID_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    .orEmpty(),
                maxSubagentDepth = preferences[MAX_SUBAGENT_DEPTH] ?: DEFAULT_MAX_SUBAGENT_DEPTH,
                maxParallelSubagents = preferences[MAX_PARALLEL_SUBAGENTS] ?: DEFAULT_MAX_PARALLEL_SUBAGENTS,
                systemPrompt = preferences[SYSTEM_PROMPT] ?: DEFAULT_PROMPT,
                temperature = preferences[TEMPERATURE] ?: 0.2,
                voiceInputEnabled = preferences[VOICE_INPUT_ENABLED] ?: false,
                voiceAutoPlay = preferences[VOICE_AUTO_PLAY] ?: false,
                voiceEngine = VoiceEngineType.from(preferences[VOICE_ENGINE]),
                ttsSpeed = preferences[TTS_SPEED] ?: 1.0f,
                ttsLanguage = preferences[TTS_LANGUAGE] ?: "zh-CN"
            )
        }

    override val skillsDirectoryUriFlow: Flow<String?> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences -> preferences[SKILLS_DIRECTORY_URI] }

    override val skillRootsFlow: Flow<List<String>> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { preferences ->
            preferences[SKILL_ROOT_URIS]
                ?.split(SKILL_ID_SEPARATOR)
                ?.map { entry -> entry.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }

    override val trustProjectSkillsFlow: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) emit(emptyPreferences()) else throw it
        }
        .map { preferences -> preferences[TRUST_PROJECT_SKILLS] ?: false }

    override suspend fun save(config: AgentConfig) {
        dataStore.edit { preferences ->
            preferences[PROVIDER_TYPE] = config.providerType.wireValue
            preferences[API_KEY] = config.apiKey
            preferences[BASE_URL] = config.baseUrl
            preferences[MODEL] = config.model
            preferences[MAX_TOKENS] = config.maxTokens
            preferences[MAX_TOOL_ITERATIONS] = config.maxToolIterations
            preferences[MEMORY_WINDOW] = config.memoryWindow
            config.reasoningEffort?.let { preferences[REASONING_EFFORT] = it } ?: preferences.remove(REASONING_EFFORT)
            preferences[ENABLE_TOOLS] = config.enableTools
            preferences[ENABLE_MEMORY] = config.enableMemory
            preferences[ENABLE_VISUAL_MEMORY] = config.enableVisualMemory
            preferences[ENABLE_BACKGROUND_WORK] = config.enableBackgroundWork
            preferences[ENABLE_TASK_PLANNING] = config.enableTaskPlanning
            preferences[ENABLE_BEHAVIOR_LEARNING] = config.enableBehaviorLearning
            preferences[WEB_SEARCH_API_KEY] = config.webSearchApiKey
            preferences[WEB_PROXY] = config.webProxy
            preferences[RESTRICT_TO_WORKSPACE] = config.restrictToWorkspace
            preferences[PRESET_ID] = config.presetId
            preferences[ENABLED_SKILL_IDS] = config.enabledSkillIds.joinToString(SKILL_ID_SEPARATOR)
            preferences[MAX_SUBAGENT_DEPTH] = config.maxSubagentDepth
            preferences[MAX_PARALLEL_SUBAGENTS] = config.maxParallelSubagents
            preferences[SYSTEM_PROMPT] = config.systemPrompt
            preferences[TEMPERATURE] = config.temperature
            preferences[VOICE_INPUT_ENABLED] = config.voiceInputEnabled
            preferences[VOICE_AUTO_PLAY] = config.voiceAutoPlay
            preferences[VOICE_ENGINE] = config.voiceEngine.wireValue
            preferences[TTS_SPEED] = config.ttsSpeed
            preferences[TTS_LANGUAGE] = config.ttsLanguage
        }
    }

    override suspend fun saveSkillsDirectoryUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(SKILLS_DIRECTORY_URI)
            } else {
                preferences[SKILLS_DIRECTORY_URI] = uri
            }
        }
    }

    override suspend fun addSkillRootUri(uri: String) {
        val normalized = uri.trim()
        if (normalized.isBlank()) return
        dataStore.edit { preferences ->
            val current = preferences[SKILL_ROOT_URIS]
                ?.split(SKILL_ID_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val updated = (current + normalized).distinct()
            preferences[SKILL_ROOT_URIS] = updated.joinToString(SKILL_ID_SEPARATOR)
            preferences[SKILLS_DIRECTORY_URI] = normalized
        }
    }

    override suspend fun removeSkillRootUri(uri: String) {
        val normalized = uri.trim()
        dataStore.edit { preferences ->
            val current = preferences[SKILL_ROOT_URIS]
                ?.split(SKILL_ID_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val updated = current.filterNot { it == normalized }
            if (updated.isEmpty()) {
                preferences.remove(SKILL_ROOT_URIS)
            } else {
                preferences[SKILL_ROOT_URIS] = updated.joinToString(SKILL_ID_SEPARATOR)
            }
            if (preferences[SKILLS_DIRECTORY_URI] == normalized) {
                updated.lastOrNull()?.let { preferences[SKILLS_DIRECTORY_URI] = it } ?: preferences.remove(SKILLS_DIRECTORY_URI)
            }
        }
    }

    override suspend fun setTrustProjectSkills(trusted: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRUST_PROJECT_SKILLS] = trusted
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_MAX_TOKENS = 4096
        const val DEFAULT_MAX_TOOL_ITERATIONS = 8
        const val DEFAULT_MEMORY_WINDOW = 100
        const val DEFAULT_ENABLE_TOOLS = true
        const val DEFAULT_ENABLE_MEMORY = true
        const val DEFAULT_ENABLE_VISUAL_MEMORY = false
        const val DEFAULT_ENABLE_BACKGROUND_WORK = true
        const val DEFAULT_ENABLE_TASK_PLANNING = true
        const val DEFAULT_ENABLE_BEHAVIOR_LEARNING = true
        const val DEFAULT_RESTRICT_TO_WORKSPACE = false
        const val DEFAULT_PRESET = "assistant_default"
        const val DEFAULT_MAX_SUBAGENT_DEPTH = 3
        const val DEFAULT_MAX_PARALLEL_SUBAGENTS = 4
        const val DEFAULT_PROMPT = "You are Nanobot, a helpful Android-native assistant."
        const val SKILL_ID_SEPARATOR = ","

        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val MAX_TOOL_ITERATIONS = intPreferencesKey("max_tool_iterations")
        val MEMORY_WINDOW = intPreferencesKey("memory_window")
        val REASONING_EFFORT = stringPreferencesKey("reasoning_effort")
        val ENABLE_TOOLS = booleanPreferencesKey("enable_tools")
        val ENABLE_MEMORY = booleanPreferencesKey("enable_memory")
        val ENABLE_VISUAL_MEMORY = booleanPreferencesKey("enable_visual_memory")
        val ENABLE_BACKGROUND_WORK = booleanPreferencesKey("enable_background_work")
        val ENABLE_TASK_PLANNING = booleanPreferencesKey("enable_task_planning")
        val ENABLE_BEHAVIOR_LEARNING = booleanPreferencesKey("enable_behavior_learning")
        val WEB_SEARCH_API_KEY = stringPreferencesKey("web_search_api_key")
        val WEB_PROXY = stringPreferencesKey("web_proxy")
        val RESTRICT_TO_WORKSPACE = booleanPreferencesKey("restrict_to_workspace")
        val PRESET_ID = stringPreferencesKey("preset_id")
        val ENABLED_SKILL_IDS = stringPreferencesKey("enabled_skill_ids")
        val MAX_SUBAGENT_DEPTH = intPreferencesKey("max_subagent_depth")
        val MAX_PARALLEL_SUBAGENTS = intPreferencesKey("max_parallel_subagents")
        val SKILLS_DIRECTORY_URI = stringPreferencesKey("skills_directory_uri")
        val SKILL_ROOT_URIS = stringPreferencesKey("skill_root_uris")
        val TRUST_PROJECT_SKILLS = booleanPreferencesKey("trust_project_skills")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val VOICE_INPUT_ENABLED = booleanPreferencesKey("voice_input_enabled")
        val VOICE_AUTO_PLAY = booleanPreferencesKey("voice_auto_play")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")
    }
}
