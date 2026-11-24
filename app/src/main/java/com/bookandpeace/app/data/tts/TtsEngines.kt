package com.bookandpeace.app.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.bookandpeace.app.data.ai.GeminiCloudClient
import com.bookandpeace.app.data.local.AppSettings
import com.bookandpeace.app.data.local.TtsEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsSynthesisResult(
    val audioFile: File,
    val durationMs: Long,
    val engineUsed: String
)

class GeminiTtsEngine(
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun synthesize(
        apiKey: String,
        preferredModel: String,
        voiceName: String,
        text: String,
        toneHint: String,
        outputBaseFile: File
    ): TtsSynthesisResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Thiếu Google AI Studio API Key cho Gemini TTS" }

        val candidates = buildList {
            if (preferredModel.isNotBlank()) add(preferredModel)
            GeminiCloudClient.DEFAULT_TTS_FALLBACKS.forEach { if (it !in this) add(it) }
        }

        val cleanSpokenText = text.trim()
        val styleNote = toneHint.trim().ifBlank { "warm, calm, natural Vietnamese storytelling" }
        var lastException: Exception? = null

        for (modelId in candidates) {
            try {
                return@withContext callGeminiTts(
                    apiKey = apiKey,
                    modelId = modelId,
                    voiceName = voiceName.ifBlank { "Charon" },
                    spokenText = cleanSpokenText,
                    styleNote = styleNote,
                    outputBaseFile = outputBaseFile
                )
            } catch (e: ModelFallbackException) {
                lastException = e
            } catch (e: RateLimitException) {
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("Không gọi được Gemini TTS")
    }

    private fun callGeminiTts(
        apiKey: String,
        modelId: String,
        voiceName: String,
        spokenText: String,
        styleNote: String,
        outputBaseFile: File
    ): TtsSynthesisResult {
        val supportsSpeechMetadata = modelId.contains("3.", ignoreCase = true)
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", spokenText)
                            if (supportsSpeechMetadata) {
                                put("speechMetadata", buildJsonObject {
                                    put("style", styleNote)
                                })
                            }
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("AUDIO"))
                })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject {
                            put("voiceName", voiceName)
                        })
                    })
                })
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
                val msg = extractError(body) ?: "Model TTS $modelId không khả dụng"
                if (response.code == 404 || msg.contains("not found", true) || msg.contains("not supported", true)) {
                    throw ModelFallbackException(msg)
                }
                throw IllegalStateException(msg)
            }
            if (response.code == 429) {
                throw RateLimitException("Gemini TTS chạm giới hạn Free Tier (429)")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(extractError(body) ?: "Gemini TTS lỗi ${response.code}")
            }

            val root = json.parseToJsonElement(body).jsonObject
            val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray.orEmpty()

            val inlineData = parts.firstNotNullOfOrNull { it.jsonObject["inlineData"]?.jsonObject }
                ?: throw IllegalStateException("Gemini TTS không trả dữ liệu âm thanh")

            val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content.orEmpty()
            val b64 = inlineData["data"]?.jsonPrimitive?.content.orEmpty()
            val rawBytes = Base64.decode(b64, Base64.DEFAULT)

            outputBaseFile.parentFile?.mkdirs()
            return if (mimeType.contains("mpeg", true) || mimeType.contains("mp3", true)) {
                val mp3File = File(outputBaseFile.parentFile, "${outputBaseFile.nameWithoutExtension}.mp3")
                mp3File.writeBytes(rawBytes)
                val estDurationMs = (spokenText.length * 65L).coerceAtLeast(3000L)
                TtsSynthesisResult(mp3File, estDurationMs, "Gemini TTS ($modelId)")
            } else if (mimeType.contains("wav", true)) {
                val wavFile = File(outputBaseFile.parentFile, "${outputBaseFile.nameWithoutExtension}.wav")
                wavFile.writeBytes(rawBytes)
                TtsSynthesisResult(wavFile, WavUtils.estimateWavDurationMs(wavFile), "Gemini TTS ($modelId)")
            } else {
                val wavFile = File(outputBaseFile.parentFile, "${outputBaseFile.nameWithoutExtension}.wav")
                val sampleRate = WavUtils.parseSampleRateFromMime(mimeType, 24000)
                val duration = WavUtils.writePcmAsWav(rawBytes, wavFile, sampleRate = sampleRate)
                TtsSynthesisResult(wavFile, duration, "Gemini TTS ($modelId)")
            }
        }
    }

    private fun extractError(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()

    private class ModelFallbackException(msg: String) : Exception(msg)
    class RateLimitException(msg: String) : Exception(msg)
}

class AndroidSystemTtsEngine(private val context: Context) {

    suspend fun synthesize(
        text: String,
        outputFile: File
    ): TtsSynthesisResult = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val wavFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.wav")

        withTimeout(120_000L) {
            val tts = initTts()
            try {
                configureVietnameseVoice(tts)
                synthesizeSingleUtterance(tts, text, wavFile)
                val duration = WavUtils.estimateWavDurationMs(wavFile)
                    .takeIf { it > 0L } ?: (text.length * 68L).coerceAtLeast(2500L)
                TtsSynthesisResult(
                    audioFile = wavFile,
                    durationMs = duration,
                    engineUsed = "Android TTS (Tiếng Việt Offline)"
                )
            } finally {
                runCatching {
                    tts.stop()
                    tts.shutdown()
                }
            }
        }
    }

    private suspend fun initTts(): TextToSpeech = suspendCancellableCoroutine { cont ->
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (!cont.isActive) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                cont.resume(engine)
            } else {
                cont.resumeWithException(IllegalStateException("Không khởi tạo được bộ đọc giọng nói hệ thống Android (mã $status)"))
            }
        }
        cont.invokeOnCancellation {
            runCatching { engine.shutdown() }
        }
    }

    private fun configureVietnameseVoice(tts: TextToSpeech) {
        val viLocale = Locale("vi", "VN")
        val langResult = tts.setLanguage(viLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        } else {
            runCatching {
                val viVoices = tts.voices?.filter {
                    it.locale.language.equals("vi", ignoreCase = true)
                }.orEmpty()
                // Prefer high-quality Vietnamese voice if available on device
                val preferred = viVoices
                    .sortedByDescending { it.quality }
                    .firstOrNull { !it.isNetworkConnectionRequired }
                    ?: viVoices.maxByOrNull { it.quality }
                if (preferred != null) {
                    tts.voice = preferred
                }
            }
        }
        tts.setSpeechRate(0.96f)
        tts.setPitch(0.95f)
    }

    private suspend fun synthesizeSingleUtterance(
        tts: TextToSpeech,
        text: String,
        targetFile: File
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Android TTS không thể ghi file âm thanh"))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Android TTS gặp lỗi mã $errorCode"))
                }
            }
        })

        val cleanText = text.replace(Regex("<[^>]+>"), " ").trim()
        val params = Bundle()
        val res = tts.synthesizeToFile(cleanText, params, targetFile, utteranceId)
        if (res != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resumeWithException(IllegalStateException("Không gửi được yêu cầu đọc cho Android TTS"))
        }
    }
}

class CustomTtsEngine(private val httpClient: OkHttpClient) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun synthesize(
        endpointUrl: String,
        apiKey: String,
        voice: String,
        text: String,
        outputBaseFile: File
    ): TtsSynthesisResult = withContext(Dispatchers.IO) {
        require(endpointUrl.isNotBlank()) { "Chưa nhập URL cho Custom TTS Endpoint" }
        val payload = buildJsonObject {
            put("model", "tts-1")
            put("input", text)
            put("voice", voice.ifBlank { "alloy" })
            put("response_format", "mp3")
        }
        val reqBuilder = Request.Builder()
            .url(endpointUrl.trim())
            .post(payload.toString().toRequestBody(jsonMedia))
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Custom TTS trả lỗi HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw IllegalStateException("Custom TTS trả dữ liệu rỗng")
            outputBaseFile.parentFile?.mkdirs()
            val mp3File = File(outputBaseFile.parentFile, "${outputBaseFile.nameWithoutExtension}.mp3")
            mp3File.writeBytes(bytes)
            TtsSynthesisResult(
                audioFile = mp3File,
                durationMs = (text.length * 65L).coerceAtLeast(3000L),
                engineUsed = "Custom TTS"
            )
        }
    }
}

class TtsRouter(
    private val geminiTtsEngine: GeminiTtsEngine,
    private val androidSystemTtsEngine: AndroidSystemTtsEngine,
    private val customTtsEngine: CustomTtsEngine
) {
    suspend fun synthesizeSegment(
        settings: AppSettings,
        preferredOverride: String?,
        text: String,
        toneHint: String,
        outputBaseFile: File
    ): TtsSynthesisResult {
        val selected = preferredOverride?.takeIf { it.isNotBlank() } ?: settings.preferredTtsEngine
        return when (selected) {
            TtsEngineType.ANDROID_TTS -> androidSystemTtsEngine.synthesize(text, outputBaseFile)

            TtsEngineType.CUSTOM_TTS -> {
                if (settings.customTtsEndpoint.isNotBlank()) {
                    customTtsEngine.synthesize(
                        endpointUrl = settings.customTtsEndpoint,
                        apiKey = settings.customTtsApiKey,
                        voice = settings.customTtsVoice,
                        text = text,
                        outputBaseFile = outputBaseFile
                    )
                } else {
                    androidSystemTtsEngine.synthesize(text, outputBaseFile)
                }
            }

            TtsEngineType.GEMINI_TTS -> {
                geminiTtsEngine.synthesize(
                    apiKey = settings.geminiApiKey,
                    preferredModel = settings.geminiTtsModel,
                    voiceName = settings.geminiVoiceName,
                    text = text,
                    toneHint = toneHint,
                    outputBaseFile = outputBaseFile
                )
            }

            else -> {
                // AUTO: Prefer Gemini Flash TTS when Free Key is configured, automatically fall back to free offline Android TTS
                if (settings.hasGeminiKey) {
                    runCatching {
                        geminiTtsEngine.synthesize(
                            apiKey = settings.geminiApiKey,
                            preferredModel = settings.geminiTtsModel,
                            voiceName = settings.geminiVoiceName,
                            text = text,
                            toneHint = toneHint,
                            outputBaseFile = outputBaseFile
                        )
                    }.getOrElse {
                        androidSystemTtsEngine.synthesize(text, outputBaseFile)
                    }
                } else {
                    androidSystemTtsEngine.synthesize(text, outputBaseFile)
                }
            }
        }
    }
}
