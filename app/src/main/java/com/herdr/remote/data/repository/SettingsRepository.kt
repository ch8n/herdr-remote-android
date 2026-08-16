package com.herdr.remote.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.herdr.remote.data.model.SettingsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("herdr_remote_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SettingsData> = _settings.asStateFlow()

    private fun loadSettings(): SettingsData {
        return SettingsData(
            openRouterApiKey = prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "",
            openRouterModel = prefs.getString(KEY_OPENROUTER_MODEL, "openrouter/auto") ?: "openrouter/auto",
            herdrServerUrl = prefs.getString(KEY_HERDR_URL, "ws://100.x.y.z:8080/herdr/ws") ?: "ws://100.x.y.z:8080/herdr/ws",
            autoRephraseOnSpeech = prefs.getBoolean(KEY_AUTO_REPHRASE, true),
            rephraseSystemPrompt = prefs.getString(KEY_REPHRASE_PROMPT, SettingsData.DEFAULT_REPHRASE_PROMPT) ?: SettingsData.DEFAULT_REPHRASE_PROMPT,
            agentTemperature = prefs.getFloat(KEY_AGENT_TEMPERATURE, 0.7f),
            speechLanguage = prefs.getString(KEY_SPEECH_LANGUAGE, "en-US") ?: "en-US"
        )
    }

    fun updateSettings(newSettings: SettingsData) {
        prefs.edit()
            .putString(KEY_OPENROUTER_API_KEY, newSettings.openRouterApiKey.trim())
            .putString(KEY_OPENROUTER_MODEL, newSettings.openRouterModel.trim())
            .putString(KEY_HERDR_URL, newSettings.herdrServerUrl.trim())
            .putBoolean(KEY_AUTO_REPHRASE, newSettings.autoRephraseOnSpeech)
            .putString(KEY_REPHRASE_PROMPT, newSettings.rephraseSystemPrompt)
            .putFloat(KEY_AGENT_TEMPERATURE, newSettings.agentTemperature)
            .putString(KEY_SPEECH_LANGUAGE, newSettings.speechLanguage)
            .apply()

        _settings.value = newSettings
    }

    fun updateApiKey(apiKey: String) {
        val updated = _settings.value.copy(openRouterApiKey = apiKey.trim())
        updateSettings(updated)
    }

    fun updateModel(model: String) {
        val updated = _settings.value.copy(openRouterModel = model.trim())
        updateSettings(updated)
    }

    fun updateServerUrl(url: String) {
        val updated = _settings.value.copy(herdrServerUrl = url.trim())
        updateSettings(updated)
    }

    fun toggleAutoRephrase(enabled: Boolean) {
        val updated = _settings.value.copy(autoRephraseOnSpeech = enabled)
        updateSettings(updated)
    }

    fun getLastActiveSessionId(): String {
        return prefs.getString(KEY_LAST_ACTIVE_SESSION, "") ?: ""
    }

    fun getLastActiveSessionTitle(): String {
        return prefs.getString(KEY_LAST_ACTIVE_TITLE, "") ?: ""
    }

    fun saveLastActiveSession(sessionId: String, title: String = "") {
        if (sessionId.isNotBlank()) {
            val editor = prefs.edit().putString(KEY_LAST_ACTIVE_SESSION, sessionId)
            if (title.isNotBlank()) {
                editor.putString(KEY_LAST_ACTIVE_TITLE, title)
            }
            editor.apply()
        }
    }

    fun saveLastActiveSessionId(sessionId: String) {
        saveLastActiveSession(sessionId, "")
    }

    fun shouldShowSupportDialog(): Boolean {
        val dismissedUntil = prefs.getLong(KEY_SUPPORT_DIALOG_DISMISSED_UNTIL, 0L)
        return System.currentTimeMillis() >= dismissedUntil
    }

    fun dismissSupportDialog(dontShowFor7Days: Boolean) {
        val editor = prefs.edit()
        if (dontShowFor7Days) {
            val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000L
            editor.putLong(KEY_SUPPORT_DIALOG_DISMISSED_UNTIL, System.currentTimeMillis() + sevenDaysMillis)
        } else {
            editor.remove(KEY_SUPPORT_DIALOG_DISMISSED_UNTIL)
        }
        editor.apply()
    }

    companion object {
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
        private const val KEY_HERDR_URL = "herdr_server_url"
        private const val KEY_MOCK_MODE = "herdr_mock_mode"
        private const val KEY_AUTO_REPHRASE = "auto_rephrase_speech"
        private const val KEY_REPHRASE_PROMPT = "rephrase_system_prompt"
        private const val KEY_AGENT_TEMPERATURE = "agent_temperature"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_LAST_ACTIVE_SESSION = "last_active_session_id"
        private const val KEY_LAST_ACTIVE_TITLE = "last_active_session_title"
        private const val KEY_SUPPORT_DIALOG_DISMISSED_UNTIL = "support_dialog_dismissed_until"
    }
}
