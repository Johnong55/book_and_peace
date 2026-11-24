package com.bookandpeace.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bookandpeace.app.BookAndPeaceApp
import com.bookandpeace.app.data.ai.GeminiNanoStatus
import com.bookandpeace.app.data.ai.ScriptSegmentParser
import com.bookandpeace.app.data.local.AiProviderType
import com.bookandpeace.app.data.local.AudioSegmentEntity
import com.bookandpeace.app.data.local.ScriptStatus
import com.bookandpeace.app.data.local.SegmentStatus
import java.io.File

class StoryGenerationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SCRIPT_ID = "script_id"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_USE_DEEP_SUMMARY = "use_deep_summary"
        const val CHANNEL_ID = "book_and_peace_ai_worker"
        private const val NOTIFICATION_ID = 4021

        fun enqueue(
            context: Context,
            scriptId: Long,
            audioOnly: Boolean,
            useDeepSummary: Boolean = true
        ) {
            val data = Data.Builder()
                .putLong(KEY_SCRIPT_ID, scriptId)
                .putBoolean(KEY_AUDIO_ONLY, audioOnly)
                .putBoolean(KEY_USE_DEEP_SUMMARY, useDeepSummary)
                .build()
            val request = OneTimeWorkRequestBuilder<StoryGenerationWorker>()
                .setInputData(data)
                .addTag("story_gen_$scriptId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "story_gen_$scriptId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val scriptId = inputData.getLong(KEY_SCRIPT_ID, -1L)
        val audioOnly = inputData.getBoolean(KEY_AUDIO_ONLY, false)
        val useDeepSummary = inputData.getBoolean(KEY_USE_DEEP_SUMMARY, true)
        if (scriptId <= 0L) return Result.failure()

        val container = (applicationContext as BookAndPeaceApp).container
        val scriptDao = container.database.storyScriptDao()
        val bookDao = container.database.bookDao()
        val chapterDao = container.database.chapterDao()

        val script = scriptDao.getScriptById(scriptId) ?: return Result.failure()
        val book = bookDao.getBookById(script.bookId) ?: return Result.failure()
        val settings = container.userPreferences.getSettings()

        ensureChannel()
        runCatching {
            setForeground(createForegroundInfo(book.title, "Đang khởi động AI kể chuyện..."))
        }

        return try {
            var rawScriptText = script.rawScript

            if (!audioOnly || rawScriptText.isBlank()) {
                val deepSummary = if (useDeepSummary) {
                    container.deepSummaryRepository.findForBook(book.title, book.sourceRef)
                } else null

                val sourceText: String
                if (deepSummary != null) {
                    scriptDao.updateStatus(
                        scriptId = scriptId,
                        status = ScriptStatus.GENERATING_SCRIPT,
                        message = "Dùng Bản Tóm Tắt Sâu có sẵn (tiết kiệm ~95% token API)..."
                    )
                    sourceText = deepSummary.toCompactAiSource()
                } else {
                    scriptDao.updateStatus(
                        scriptId = scriptId,
                        status = ScriptStatus.GENERATING_SCRIPT,
                        message = "Đang đọc nội dung sách..."
                    )
                    val chapters = chapterDao.getChaptersForBook(book.id)
                    sourceText = chapters.joinToString("\n\n") { ch ->
                        "### ${ch.title}\n${ch.content}"
                    }
                }
                if (sourceText.isBlank()) {
                    throw IllegalStateException("Cuốn sách này chưa có nội dung văn bản")
                }

                val selectedProvider = if (script.aiProvider == AiProviderType.AUTO) {
                    if (settings.hasGeminiKey) AiProviderType.GEMINI_CLOUD else AiProviderType.GEMINI_NANO
                } else {
                    script.aiProvider
                }

                rawScriptText = when (selectedProvider) {
                    AiProviderType.GEMINI_CLOUD -> {
                        if (!settings.hasGeminiKey) {
                            throw IllegalStateException("Chưa có Google AI Studio Free API Key. Vào Cài đặt để nạp key miễn phí hoặc chọn nghe thẳng Bản Tóm Tắt Sâu.")
                        }
                        container.geminiCloudClient.generateVanVoScript(
                            apiKey = settings.geminiApiKey,
                            preferredModel = settings.geminiTextModel,
                            bookTitle = book.title,
                            author = book.author,
                            fullText = sourceText,
                            styleMode = script.styleMode,
                            onProgress = { msg ->
                                updateProgressNotification(book.title, msg)
                            }
                        )
                    }

                    AiProviderType.GEMINI_NANO -> {
                        val nanoStatus = container.geminiNanoClient.checkAvailability()
                        if (nanoStatus == GeminiNanoStatus.UNAVAILABLE && settings.hasGeminiKey) {
                            container.geminiCloudClient.generateVanVoScript(
                                apiKey = settings.geminiApiKey,
                                preferredModel = settings.geminiTextModel,
                                bookTitle = book.title,
                                author = book.author,
                                fullText = sourceText,
                                styleMode = script.styleMode,
                                onProgress = { msg -> updateProgressNotification(book.title, msg) }
                            )
                        } else {
                            container.geminiNanoClient.generateVanVoScriptOnDevice(
                                bookTitle = book.title,
                                author = book.author,
                                fullText = sourceText,
                                styleMode = script.styleMode,
                                onProgress = { msg -> updateProgressNotification(book.title, msg) }
                            )
                        }
                    }

                    else -> throw IllegalStateException("Hãy dán kịch bản từ App Gemini trước khi thu âm")
                }

                val updatedScript = script.copy(
                    rawScript = rawScriptText,
                    status = ScriptStatus.GENERATING_AUDIO,
                    statusMessage = "Đã viết xong kịch bản! Đang chia hồi để thu âm...",
                    updatedAt = System.currentTimeMillis()
                )
                scriptDao.updateScript(updatedScript)
            } else {
                scriptDao.updateStatus(
                    scriptId = scriptId,
                    status = ScriptStatus.GENERATING_AUDIO,
                    message = "Đang tách kịch bản thành các đoạn thu âm..."
                )
            }

            // Parse segments & store cleanly
            val parsedSegments = ScriptSegmentParser.parseToSegments(rawScriptText)
            if (parsedSegments.isEmpty()) {
                throw IllegalStateException("Kịch bản trống, không thể tạo âm thanh")
            }

            scriptDao.deleteSegmentsForScript(scriptId)
            val entities = parsedSegments.map { seg ->
                AudioSegmentEntity(
                    scriptId = scriptId,
                    segmentIndex = seg.index,
                    sectionTitle = seg.sectionTitle,
                    toneHint = seg.toneHint,
                    spokenText = seg.spokenText,
                    status = SegmentStatus.PENDING
                )
            }
            scriptDao.insertSegments(entities)
            val savedSegments = scriptDao.getSegments(scriptId)

            val audioDir = File(applicationContext.filesDir, "audiobooks/script_$scriptId").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            var lastEngineUsed = ""
            savedSegments.forEachIndexed { idx, segment ->
                if (idx > 0) kotlinx.coroutines.delay(1200L)
                val statusMsg = "Đang thu âm đoạn ${idx + 1}/${savedSegments.size}: ${segment.sectionTitle}"
                scriptDao.updateStatus(scriptId, ScriptStatus.GENERATING_AUDIO, statusMsg)
                scriptDao.updateSegmentAudio(segment.id, null, 0L, SegmentStatus.SYNTHESIZING)
                updateProgressNotification(book.title, statusMsg, idx, savedSegments.size)

                val baseFile = File(audioDir, "seg_${String.format("%03d", idx)}.wav")
                val result = container.ttsRouter.synthesizeSegment(
                    settings = settings,
                    preferredOverride = script.ttsEngine,
                    text = segment.spokenText,
                    toneHint = segment.toneHint,
                    outputBaseFile = baseFile
                )
                lastEngineUsed = result.engineUsed
                scriptDao.updateSegmentAudio(
                    segmentId = segment.id,
                    path = result.audioFile.absolutePath,
                    durationMs = result.durationMs,
                    status = SegmentStatus.READY
                )
            }

            scriptDao.updateStatus(
                scriptId = scriptId,
                status = ScriptStatus.READY,
                message = "Sẵn sàng nghe (${savedSegments.size} đoạn • $lastEngineUsed)"
            )
            postCompletionNotification(book.title, "Sách nói Kể Chuyện Sâu đã hoàn tất (${savedSegments.size} đoạn)!")
            Result.success()
        } catch (ex: Exception) {
            val err = ex.localizedMessage ?: "Có lỗi xảy ra khi tạo sách nói"
            scriptDao.updateStatus(
                scriptId = scriptId,
                status = ScriptStatus.ERROR,
                message = err
            )
            postCompletionNotification(book.title, "Tạo sách nói tạm dừng: $err")
            Result.failure()
        }
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Book & Peace — AI Tạo Sách Nói",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hiển thị tiến độ tạo kịch bản Kể Chuyện Sâu và thu âm sách nói chạy ngầm"
        }
        manager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(title: String, message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Book & Peace • $title")
            .setContentText(message)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgressNotification(
        title: String,
        message: String,
        progress: Int = 0,
        max: Int = 0
    ) {
        runCatching {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Book & Peace • $title")
                .setContentText(message)
                .setOngoing(true)
                .setSilent(true)
            if (max > 0) {
                builder.setProgress(max, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }
            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun postCompletionNotification(title: String, message: String) {
        runCatching {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Book & Peace • $title")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID + 1, notification)
        }
    }
}
