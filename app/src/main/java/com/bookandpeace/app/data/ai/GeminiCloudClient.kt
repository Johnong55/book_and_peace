package com.bookandpeace.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class GeminiDiscoveryResult(
    val isValid: Boolean,
    val message: String,
    val recommendedTextModel: String? = null,
    val recommendedTtsModel: String? = null,
    val availableTextModels: List<String> = emptyList(),
    val availableTtsModels: List<String> = emptyList()
)

class GeminiCloudClient(
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SINGLE_PASS_CHAR_LIMIT = 350_000
        private const val CHUNK_SIZE_CHARS = 70_000

        val DEFAULT_TEXT_FALLBACKS = listOf(
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )

        val DEFAULT_TTS_FALLBACKS = listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-2.5-flash-preview-tts"
        )

        val PREBUILT_VOICES = listOf(
            "Charon" to "Charon — Trầm ấm, dẫn chuyện (Khuyên dùng cho Kể Chuyện Sâu)",
            "Orus" to "Orus — Trầm chắc, điềm tĩnh",
            "Fenrir" to "Fenrir — Truyền cảm, giàu năng lượng",
            "Puck" to "Puck — Tự nhiên, trẻ trung, hóm hỉnh",
            "Kore" to "Kore — Giọng nữ rõ ràng, dịu nhẹ",
            "Aoede" to "Aoede — Giọng nữ truyền cảm, êm ái",
            "Zephyr" to "Zephyr — Sáng, thanh thoát"
        )
    }

    suspend fun testConnectionAndDiscoverModels(apiKey: String): GeminiDiscoveryResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext GeminiDiscoveryResult(
                    isValid = false,
                    message = "Chưa nhập API Key."
                )
            }
            val request = Request.Builder()
                .url("$BASE_URL/models?pageSize=100")
                .header("x-goog-api-key", apiKey.trim())
                .get()
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(body) ?: "Mã lỗi HTTP ${response.code}"
                        return@withContext GeminiDiscoveryResult(
                            isValid = false,
                            message = "Kết nối thất bại: $errMsg"
                        )
                    }
                    val root = json.parseToJsonElement(body).jsonObject
                    val models = root["models"]?.jsonArray.orEmpty().mapNotNull { el ->
                        val obj = el.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content?.removePrefix("models/") ?: return@mapNotNull null
                        val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull {
                            it.jsonPrimitive.content
                        }.orEmpty()
                        if ("generateContent" in methods) name else null
                    }

                    val ttsModels = models.filter { it.contains("tts", ignoreCase = true) }
                    val textModels = models.filter {
                        it.startsWith("gemini") &&
                            !it.contains("tts", ignoreCase = true) &&
                            !it.contains("image", ignoreCase = true) &&
                            !it.contains("embedding", ignoreCase = true) &&
                            !it.contains("live", ignoreCase = true)
                    }

                    val bestText = DEFAULT_TEXT_FALLBACKS.firstOrNull { it in textModels }
                        ?: textModels.firstOrNull { it.contains("flash", ignoreCase = true) }
                        ?: textModels.firstOrNull()
                    val bestTts = DEFAULT_TTS_FALLBACKS.firstOrNull { it in ttsModels }
                        ?: ttsModels.firstOrNull()

                    GeminiDiscoveryResult(
                        isValid = true,
                        message = "Kết nối thành công! Tìm thấy ${textModels.size} model văn bản và ${ttsModels.size} model giọng nói TTS.",
                        recommendedTextModel = bestText,
                        recommendedTtsModel = bestTts,
                        availableTextModels = textModels.take(12),
                        availableTtsModels = ttsModels
                    )
                }
            }.getOrElse { ex ->
                GeminiDiscoveryResult(
                    isValid = false,
                    message = "Lỗi mạng: ${ex.localizedMessage ?: ex.javaClass.simpleName}"
                )
            }
        }

    suspend fun generateVanVoScript(
        apiKey: String,
        preferredModel: String,
        bookTitle: String,
        author: String,
        fullText: String,
        styleMode: String,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Chưa cấu hình Google AI Studio API Key" }

        val preparedBookContent = if (fullText.length <= SINGLE_PASS_CHAR_LIMIT) {
            onProgress("Đang đọc toàn bộ tác phẩm với ngữ cảnh 1M token của Gemini...")
            fullText
        } else {
            val chunks = fullText.chunked(CHUNK_SIZE_CHARS)
            val summaries = mutableListOf<String>()
            chunks.forEachIndexed { idx, chunk ->
                onProgress("Sách rất dày — đang tóm lược phần ${idx + 1}/${chunks.size}...")
                val chunkPrompt = VanVoPromptBuilder.buildChunkSummaryPrompt(
                    bookTitle = bookTitle,
                    author = author,
                    chunkIndex = idx + 1,
                    totalChunks = chunks.size,
                    chunkText = chunk
                )
                val summary = callGenerateTextWithFallback(
                    apiKey = apiKey,
                    preferredModel = preferredModel,
                    systemInstruction = "Bạn là trợ lý biên tập văn học chuyên ghi chép trung thực tình tiết, tâm lý và thoại đắt giá từ sách.",
                    userPrompt = chunkPrompt,
                    temperature = 0.4
                )
                summaries.add("### TÓM LƯỢC PHẦN ${idx + 1}/${chunks.size}\n$summary")
            }
            summaries.joinToString("\n\n")
        }

        onProgress("Đang viết kịch bản Kể Chuyện Sâu...")
        val systemInstruction = VanVoPromptBuilder.buildSystemInstruction(styleMode)
        val userPrompt = VanVoPromptBuilder.buildUserPrompt(
            bookTitle = bookTitle,
            author = author,
            bookContent = preparedBookContent,
            styleMode = styleMode
        )

        callGenerateTextWithFallback(
            apiKey = apiKey,
            preferredModel = preferredModel,
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            temperature = 0.85
        )
    }

    private fun callGenerateTextWithFallback(
        apiKey: String,
        preferredModel: String,
        systemInstruction: String,
        userPrompt: String,
        temperature: Double
    ): String {
        val candidates = buildList {
            if (preferredModel.isNotBlank()) add(preferredModel)
            DEFAULT_TEXT_FALLBACKS.forEach { if (it !in this) add(it) }
        }

        var lastError: Exception? = null
        for (modelId in candidates) {
            try {
                return callSingleTextModel(
                    apiKey = apiKey,
                    modelId = modelId,
                    systemInstruction = systemInstruction,
                    userPrompt = userPrompt,
                    temperature = temperature
                )
            } catch (e: ModelNotAvailableException) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Không gọi được Gemini Text API")
    }

    private fun callSingleTextModel(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        userPrompt: String,
        temperature: Double
    ): String {
        val payload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemInstruction) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", userPrompt) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", 16384)
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL/models/$modelId:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 404 || response.code == 400) {
                val msg = extractErrorMessage(body) ?: "Model $modelId không khả dụng (${response.code})"
                if (response.code == 404 || msg.contains("not found", true) || msg.contains("not supported", true)) {
                    throw ModelNotAvailableException(msg)
                }
                throw IllegalStateException(msg)
            }
            if (response.code == 429) {
                throw IllegalStateException("Vượt giới hạn tốc độ Free Tier của Google AI Studio (429). Vui lòng đợi khoảng 1 phút rồi thử lại.")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(extractErrorMessage(body) ?: "Gemini API lỗi ${response.code}")
            }

            val root = json.parseToJsonElement(body).jsonObject
            val text = root["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                ?.joinToString("")
                ?.trim()

            if (text.isNullOrBlank()) {
                throw IllegalStateException("Gemini không trả về nội dung kịch bản")
            }
            return text
        }
    }

    private fun extractErrorMessage(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body).jsonObject
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()

    private class ModelNotAvailableException(message: String) : Exception(message)
}
