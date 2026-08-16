package com.herdr.remote.data.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class OpenRouterMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class OpenRouterRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OpenRouterMessage>,
    @SerializedName("temperature") val temperature: Float = 0.3f
)

data class OpenRouterChoice(
    @SerializedName("message") val message: OpenRouterMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class OpenRouterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<OpenRouterChoice>?,
    @SerializedName("error") val error: OpenRouterError?
)

data class OpenRouterError(
    @SerializedName("message") val message: String?,
    @SerializedName("code") val code: Int?
)

class OpenRouterService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun rephrasePrompt(
        spokenText: String,
        apiKey: String,
        model: String,
        systemPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.success("")
        }

        // If no API key provided, use local smart heuristic cleaner
        if (apiKey.isBlank()) {
            val localCleaned = cleanTextLocally(trimmed)
            return@withContext Result.success(localCleaned)
        }

        try {
            val resolvedModel = if (model.isBlank()) "openrouter/auto" else model
            val requestBodyObj = OpenRouterRequest(
                model = resolvedModel,
                messages = listOf(
                    OpenRouterMessage(
                        role = "system",
                        content = systemPrompt
                    ),
                    OpenRouterMessage(
                        role = "user",
                        content = "Spoken transcription: \"$trimmed\"\n\nPlease refine and clarify into a direct, high-quality prompt."
                    )
                ),
                temperature = 0.2f
            )

            val jsonBody = gson.toJson(requestBodyObj)
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/herdr/herdr-remote-android")
                .addHeader("X-Title", "Herdr Remote Android")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // Fall back to local cleaner on API errors
                    val fallback = cleanTextLocally(trimmed)
                    return@withContext Result.success(fallback)
                }

                val openRouterResponse = gson.fromJson(responseBodyStr, OpenRouterResponse::class.java)
                val content = openRouterResponse.choices?.firstOrNull()?.message?.content?.trim()
                if (!content.isNullOrBlank()) {
                    // Strip surrounding quotes if model wrapped it
                    val cleaned = content.removeSurrounding("\"").removeSurrounding("'").trim()
                    Result.success(cleaned)
                } else {
                    Result.success(cleanTextLocally(trimmed))
                }
            }
        } catch (e: Exception) {
            // Graceful fallback to local cleaner so user flow is never broken
            Result.success(cleanTextLocally(trimmed))
        }
    }

    suspend fun testApiKey(apiKey: String, model: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty"))
        }

        try {
            val requestBodyObj = OpenRouterRequest(
                model = if (model.isBlank()) "openrouter/auto" else model,
                messages = listOf(
                    OpenRouterMessage(role = "user", content = "Ping")
                ),
                temperature = 0.1f
            )

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/herdr/herdr-remote-android")
                .addHeader("X-Title", "Herdr Remote Android")
                .post(gson.toJson(requestBodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("API returned HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchModels(apiKey: String?): Result<List<com.herdr.remote.data.model.OpenRouterModel>> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .addHeader("HTTP-Referer", "https://github.com/herdr/herdr-remote-android")
                .addHeader("X-Title", "Herdr Remote Android")

            if (!apiKey.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val listResponse = gson.fromJson(bodyStr, com.herdr.remote.data.model.OpenRouterModelsListResponse::class.java)
                    val list = listResponse.data
                    if (!list.isNullOrEmpty()) {
                        return@withContext Result.success(list)
                    }
                }
                Result.success(com.herdr.remote.data.model.OpenRouterModel.DEFAULT_MODELS)
            }
        } catch (e: Exception) {
            Result.success(com.herdr.remote.data.model.OpenRouterModel.DEFAULT_MODELS)
        }
    }

    /**
     * Local heuristic cleaner to remove filler words, repeated stutters, and normalize punctuation.
     */
    fun cleanTextLocally(raw: String): String {
        var text = raw

        // Filler phrases & words
        val fillers = listOf(
            "(?i)\\b(um+|uh+|erm+|ah+)\\b",
            "(?i)\\b(you know|like\\s*,?|so basically|basically|I mean|sort of|kind of|right\\?)\\b",
            "(?i)\\b(actually|literally|honestly)\\s*,?",
            "(?i)\\b(can you please|could you please|please go ahead and)\\b"
        )

        for (pattern in fillers) {
            text = text.replace(Regex(pattern), " ")
        }

        // Deduplicate repeated consecutive words (stutter: e.g. "check check the logs")
        val stutterRegex = Pattern.compile("(?i)\\b(\\w+)\\s+\\1\\b")
        var matcher = stutterRegex.matcher(text)
        while (matcher.find()) {
            text = matcher.replaceAll("$1")
            matcher = stutterRegex.matcher(text)
        }

        // Clean extra spaces & punctuation
        text = text.replace(Regex("\\s+"), " ").trim()
        text = text.replace(Regex("\\s+([.,?!])"), "$1")

        if (text.isNotEmpty()) {
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return text
    }
}
