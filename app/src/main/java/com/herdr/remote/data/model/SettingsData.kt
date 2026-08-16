package com.herdr.remote.data.model

data class SettingsData(
    val openRouterApiKey: String = "",
    val openRouterModel: String = "openrouter/auto",
    val herdrServerUrl: String = "ws://100.108.120.53:8765",
    val autoRephraseOnSpeech: Boolean = true,
    val rephraseSystemPrompt: String = DEFAULT_REPHRASE_PROMPT,
    val agentTemperature: Float = 0.7f,
    val speechLanguage: String = "en-US"
) {
    companion object {
        const val DEFAULT_REPHRASE_PROMPT = 
            "You are an expert prompt refiner for autonomous agents. " +
            "Take the user's spoken voice transcript, remove verbal fillers (like 'um', 'uh', 'you know', 'basically', stuttering), " +
            "fix misheard grammar or punctuation, and clarify the core instruction into a crisp, concise, high-impact prompt. " +
            "Return ONLY the refined prompt text without any preamble, quotes, or explanations."
    }
}
