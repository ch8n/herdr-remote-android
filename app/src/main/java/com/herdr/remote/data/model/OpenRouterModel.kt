package com.herdr.remote.data.model

import com.google.gson.annotations.SerializedName

data class OpenRouterPricing(
    @SerializedName("prompt") val prompt: String?,
    @SerializedName("completion") val completion: String?
)

data class OpenRouterModel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("context_length") val contextLength: Int? = null,
    @SerializedName("pricing") val pricing: OpenRouterPricing? = null
) {
    val isFree: Boolean
        get() = id.contains(":free") || (pricing?.prompt == "0" && pricing?.completion == "0")

    val formattedContext: String
        get() {
            val length = contextLength ?: return ""
            return if (length >= 1000) "${length / 1000}k ctx" else "$length ctx"
        }

    companion object {
        val DEFAULT_MODELS = listOf(
            OpenRouterModel(
                id = "openrouter/auto",
                name = "OpenRouter: Auto (Best for prompt)",
                description = "Automatically routes to the highest-quality and fastest available model.",
                contextLength = 128000
            ),
            OpenRouterModel(
                id = "meta-llama/llama-3-8b-instruct:free",
                name = "Meta: Llama 3 8B Instruct (free)",
                description = "Free fast instruction model by Meta.",
                contextLength = 8192
            ),
            OpenRouterModel(
                id = "google/gemini-2.0-flash-exp:free",
                name = "Google: Gemini 2.0 Flash (free)",
                description = "High-speed, next-gen multimodal reasoning model by Google.",
                contextLength = 1048576
            ),
            OpenRouterModel(
                id = "google/gemini-2.0-flash-001",
                name = "Google: Gemini 2.0 Flash 001",
                description = "Production-grade low-latency multimodal reasoning.",
                contextLength = 1048576
            ),
            OpenRouterModel(
                id = "mistralai/mistral-7b-instruct:free",
                name = "Mistral: Mistral 7B Instruct (free)",
                description = "Fast, efficient general instruction follower.",
                contextLength = 32768
            ),
            OpenRouterModel(
                id = "deepseek/deepseek-r1",
                name = "DeepSeek: R1 (Reasoning)",
                description = "Open reasoning model with strong coding and math capabilities.",
                contextLength = 64000
            ),
            OpenRouterModel(
                id = "anthropic/claude-3.5-sonnet",
                name = "Anthropic: Claude 3.5 Sonnet",
                description = "Industry benchmark for coding and complex agent workflows.",
                contextLength = 200000
            )
        )
    }
}

data class OpenRouterModelsListResponse(
    @SerializedName("data") val data: List<OpenRouterModel>?
)
