package com.bookandpeace.app.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GeminiNanoStatus(val label: String) {
    AVAILABLE("Sẵn sàng trên máy (Offline 0đ)"),
    DOWNLOADABLE("Hỗ trợ — có thể tải mô hình về máy"),
    UNAVAILABLE("Thiết bị chưa hỗ trợ Gemini Nano AICore")
}

class GeminiNanoClient {

    suspend fun checkAvailability(): GeminiNanoStatus = withContext(Dispatchers.IO) {
        runCatching {
            val model = Generation.getClient()
            try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> GeminiNanoStatus.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> GeminiNanoStatus.DOWNLOADABLE
                    else -> GeminiNanoStatus.UNAVAILABLE
                }
            } finally {
                model.close()
            }
        }.getOrDefault(GeminiNanoStatus.UNAVAILABLE)
    }

    suspend fun generateVanVoScriptOnDevice(
        bookTitle: String,
        author: String,
        fullText: String,
        styleMode: String,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val model = Generation.getClient()
        try {
            when (model.checkStatus()) {
                FeatureStatus.UNAVAILABLE ->
                    throw IllegalStateException("Điện thoại này chưa hỗ trợ Gemini Nano (AICore). Hãy dùng Google AI Studio Free API Key (0đ) hoặc nút Gửi sang App Gemini.")
                FeatureStatus.DOWNLOADABLE -> {
                    onProgress("Đang tải mô hình Gemini Nano về điện thoại...")
                    model.download().collect { dl ->
                        if (dl is DownloadStatus.DownloadProgress) {
                            val mb = dl.totalBytesDownloaded / (1024 * 1024)
                            onProgress("Đang tải Gemini Nano ($mb MB)...")
                        }
                    }
                }
                else -> Unit
            }

            // Gemini Nano has a compact on-device context window, so we digest in short chunks
            val chunks = fullText.chunked(3200).take(8)
            val notes = mutableListOf<String>()
            chunks.forEachIndexed { idx, chunk ->
                onProgress("Gemini Nano đang đọc đoạn ${idx + 1}/${chunks.size} ngay trên máy...")
                val prompt = """
Tóm tắt ngắn gọn tình tiết và tâm lý nhân vật chính bằng tiếng Việt từ trích đoạn ${idx + 1}/${chunks.size} của "$bookTitle" ($author):

$chunk
                """.trimIndent()
                val res = model.generateContent(prompt)
                val out = res.candidates.firstOrNull()?.text?.trim().orEmpty()
                if (out.isNotBlank()) notes.add(out)
            }

            onProgress("Gemini Nano đang dệt kịch bản kể chuyện phong cách Văn Vở...")
            val combinedNotes = notes.joinToString("\n\n").take(3200)
            val finalPrompt = """
Hãy viết kịch bản kể chuyện bằng tiếng Việt theo phong cách trầm ấm, hóm hỉnh, sâu sắc (giống kênh Văn Vở) cho cuốn sách "$bookTitle" của $author dựa trên ghi chép sau.
Chia rõ các phần bằng dòng: === PHẦN: <Tên hồi> | GIỌNG: <giọng đọc> ===

$combinedNotes
            """.trimIndent()

            val finalResp = model.generateContent(finalPrompt)
            val script = finalResp.candidates.firstOrNull()?.text?.trim()
            if (script.isNullOrBlank()) {
                throw IllegalStateException("Gemini Nano không tạo được kịch bản")
            }
            script
        } finally {
            runCatching { model.close() }
        }
    }
}
