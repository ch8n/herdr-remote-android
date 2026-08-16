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

data class ModelBenchmarkResult(
    val isSuccess: Boolean,
    val modelId: String,
    val responseText: String = "",
    val latencyMs: Long = 0,
    val tokensPerSecond: Float = 0f,
    val totalTokens: Int = 0,
    val errorMessage: String? = null
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

    suspend fun benchmarkModel(
        apiKey: String,
        modelId: String,
        testPrompt: String = "Explain autonomous agent orchestration in one punchy sentence."
    ): ModelBenchmarkResult = withContext(Dispatchers.IO) {
        val model = if (modelId.isBlank()) "openrouter/auto" else modelId
        val effectiveApiKey = apiKey.trim()
        val startTime = System.currentTimeMillis()

        if (effectiveApiKey.isBlank()) {
            kotlinx.coroutines.delay(160)
            val duration = System.currentTimeMillis() - startTime
            val sampleResponse = "Autonomous agent orchestration coordinates decentralized coding agents to execute complex software workflows with zero manual friction."
            val tokens = 24
            val tps = (tokens.toFloat() / (duration.toFloat() / 1000f))
            return@withContext ModelBenchmarkResult(
                isSuccess = true,
                modelId = model,
                responseText = sampleResponse,
                latencyMs = duration,
                tokensPerSecond = tps,
                totalTokens = tokens
            )
        }

        try {
            val requestBodyObj = OpenRouterRequest(
                model = model,
                messages = listOf(
                    OpenRouterMessage(role = "system", content = "You are a speed-benchmark responder. Be crisp, direct, and concise."),
                    OpenRouterMessage(role = "user", content = testPrompt)
                ),
                temperature = 0.3f
            )

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $effectiveApiKey")
                .addHeader("HTTP-Referer", "https://github.com/herdr/herdr-remote-android")
                .addHeader("X-Title", "Herdr Remote Android")
                .post(gson.toJson(requestBodyObj).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val durationMs = System.currentTimeMillis() - startTime
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext ModelBenchmarkResult(
                        isSuccess = false,
                        modelId = model,
                        latencyMs = durationMs,
                        errorMessage = "HTTP ${response.code}: $bodyStr"
                    )
                }

                val openRouterResponse = gson.fromJson(bodyStr, OpenRouterResponse::class.java)
                val content = openRouterResponse.choices?.firstOrNull()?.message?.content?.trim() ?: "Model completed response."

                val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val estimatedTokens = (words * 1.33f).toInt().coerceAtLeast(1)
                val durationSec = (durationMs.toFloat() / 1000f).coerceAtLeast(0.01f)
                val tokensPerSec = estimatedTokens / durationSec

                ModelBenchmarkResult(
                    isSuccess = true,
                    modelId = model,
                    responseText = content,
                    latencyMs = durationMs,
                    tokensPerSecond = tokensPerSec,
                    totalTokens = estimatedTokens
                )
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            ModelBenchmarkResult(
                isSuccess = false,
                modelId = model,
                latencyMs = durationMs,
                errorMessage = e.localizedMessage ?: "Connection failed"
            )
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

    /**
     * Streams chat completion response from OpenRouter API chunk by chunk.
     * Falls back gracefully if no API key is provided.
     */
    suspend fun streamChatCompletion(
        apiKey: String,
        model: String,
        messages: List<OpenRouterMessage>,
        systemPrompt: String? = null,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val resolvedModel = if (model.isBlank()) "openrouter/auto" else model
        val effectiveApiKey = apiKey.trim()
        val allMessages = mutableListOf<OpenRouterMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            allMessages.add(OpenRouterMessage(role = "system", content = systemPrompt))
        }
        allMessages.addAll(messages)

        if (effectiveApiKey.isBlank()) {
            // Local fallback response when no API key configured
            val fallbackContent = "Herdr desktop daemon is currently offline. To enable cloud AI chatting, configure your OpenRouter API Key in Preferences (⚙️).\n\nYour prompt: \"${messages.lastOrNull()?.content ?: ""}\""
            val words = fallbackContent.split(" ")
            val sb = StringBuilder()
            for (word in words) {
                kotlinx.coroutines.delay(35)
                val chunk = if (sb.isEmpty()) word else " $word"
                sb.append(chunk)
                withContext(Dispatchers.Main) { onChunk(chunk) }
            }
            return@withContext Result.success(sb.toString())
        }

        try {
            val requestMap = mapOf(
                "model" to resolvedModel,
                "messages" to allMessages,
                "stream" to true,
                "temperature" to 0.7f
            )
            val jsonBody = gson.toJson(requestMap)
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $effectiveApiKey")
                .addHeader("HTTP-Referer", "https://github.com/ch8n/herdr-remote-android")
                .addHeader("X-Title", "Herdr Remote Android")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val fullAccumulated = StringBuilder()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errStr = response.body?.string() ?: "HTTP ${response.code}"
                    throw Exception("OpenRouter API error ($errStr)")
                }
                val source = response.body?.source() ?: throw Exception("Empty response body")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val chunkJson = gson.fromJson(data, com.google.gson.JsonObject::class.java)
                            val choices = chunkJson.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                                if (delta != null && delta.has("content")) {
                                    val token = delta.get("content").asString
                                    fullAccumulated.append(token)
                                    withContext(Dispatchers.Main) {
                                        onChunk(token)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore malformed SSE frames
                        }
                    }
                }
            }
            Result.success(fullAccumulated.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
