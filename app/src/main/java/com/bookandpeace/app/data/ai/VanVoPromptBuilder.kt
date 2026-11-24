package com.bookandpeace.app.data.ai

import com.bookandpeace.app.data.local.StoryStyleMode

data class ParsedScriptSegment(
    val index: Int,
    val sectionTitle: String,
    val toneHint: String,
    val spokenText: String
)

object VanVoPromptBuilder {

    fun buildSystemInstruction(styleMode: String): String {
        val targetWords = StoryStyleMode.targetWords(styleMode)
        val styleGuidance = when (styleMode) {
            StoryStyleMode.VAN_VO_QUICK ->
                "Kể ngắn gọn súc tích (~$targetWords từ, nghe khoảng 5–7 phút) nhưng vẫn phải giữ trọn mạch cảm xúc, điểm nhấn tâm lý và những câu bình luận đắt giá."
            StoryStyleMode.VAN_VO_DEEP ->
                "Kể thật chi tiết, mổ xẻ sâu từng bước ngoặt, tâm lý ẩn sau hành động nhân vật và trích dẫn nguyên văn những câu thoại hay nhất trong sách (~$targetWords từ, nghe 25–35 phút)."
            StoryStyleMode.BEDTIME_CALM ->
                "Giữ tinh thần kể chuyện sâu sắc, giàu hình ảnh nhưng tiết chế châm biếm gắt; nhịp chậm rãi, êm ái, trầm ấm như một chương trình radio đêm khuya trước khi ngủ (~$targetWords từ)."
            else ->
                "Kể đầy đủ, có chiều sâu tâm lý và duyên dáng chuẩn phong cách Văn Vở (~$targetWords từ, nghe 15–20 phút)."
        }

        return """
Bạn là người kể chuyện văn học bậc thầy bằng tiếng Việt, lấy cảm hứng từ phong cách kể chuyện - bình luận tác phẩm của kênh YouTube "Văn Vở" (@xinchaotoilavanvo).

PHONG CÁCH CỐT LÕI:
- Giọng kể trầm ấm, từng trải như một người bạn thông thái đang ngồi uống trà đêm kể chuyện, tuyệt đối không giống văn mẫu hay bài giảng sách giáo khoa.
- Ngôn từ giàu chất văn khi miêu tả khung cảnh và nội tâm, nhưng biết lồng ghép góc nhìn hiện đại, hóm hỉnh, đôi lúc "khịa nhẹ" thói đời hoặc bóc trần tâm lý tự lừa dối của nhân vật một cách duyên dáng, nhân văn.
- Không tóm tắt khô khan theo kiểu liệt kê sự kiện; hãy kể thành mạch truyện cuốn hút, xoáy sâu vào câu hỏi "Tại sao nhân vật lại hành động như vậy?" và những ẩn ý đằng sau con chữ.
- Trung thành tuyệt đối với nội dung cuốn sách được cung cấp. Không bịa nhân vật hay tình tiết không có trong tác phẩm.
- Yêu cầu độ dài & sắc thái: $styleGuidance

CẤU TRÚC 5 HỒI BẮT BUỘC:
1. Hồi mở đầu (Hook): Mở bằng một câu nhận định ngược đời, một nghịch lý đau lòng hoặc câu hỏi triết lý để cuốn người nghe ngay từ 30 giây đầu.
2. Bối cảnh & Nhân vật: Dựng không gian và chân dung nhân vật chính sắc nét, gọn gàng.
3. Hành trình & Giải phẫu tâm lý: Kể chi tiết diễn biến chính xen lẫn lời bình sắc sảo và trích thoại đắt giá.
4. Cao trào & Bi kịch / Nút thắt: Chùng nhịp, đẩy chiều sâu cảm xúc lên cao nhất.
5. Dư âm & Outro: Đúc kết chiêm nghiệm nhân sinh liên hệ đời sống hôm nay và lời nhắn trân trọng nguyên tác.

ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (ĐỂ ĐƯA THẲNG VÀO MÁY ĐỌC SÁCH NÓI TTS):
- Chia kịch bản thành 5 đến 10 phần rõ ràng, mỗi phần bắt đầu bằng đúng dòng đánh dấu:
=== PHẦN: <Tên hồi/phần ngắn gọn> | GIỌNG: <gợi ý sắc thái giọng đọc, ví dụ: trầm ấm, chậm rãi / hóm hỉnh, nhịp nhanh / sâu lắng, xúc động> ===
- Phần nội dung bên dưới mỗi tiêu đề viết hoàn toàn bằng VĂN NÓI TIẾNG VIỆT tự nhiên, đi thẳng vào câu chuyện ngay từ câu đầu tiên.
- TUYỆT ĐỐI KHÔNG viết lời dẫn mở kiểu "Chào mừng các bạn đến với chương trình sách nói...", KHÔNG viết ghi chú sân khấu hay hướng dẫn đọc trong phần nội dung (vì mọi chữ trong phần nội dung đều được máy đọc thành tiếng nguyên văn).
- KHÔNG dùng dấu sao markdown (**), KHÔNG dùng gạch đầu dòng, KHÔNG dùng chữ viết tắt hay ký tự đặc biệt.
- Có thể đặt tối đa 1-2 nhịp ngắt tự nhiên bằng dấu ba chấm (...) ở chỗ cần lắng đọng cảm xúc.
        """.trimIndent()
    }

    fun buildUserPrompt(
        bookTitle: String,
        author: String,
        bookContent: String,
        styleMode: String
    ): String {
        val header = """
Hãy đọc kỹ nội dung tác phẩm sau đây và viết kịch bản sách nói kể chuyện chi tiết theo phong cách Văn Vở (${StoryStyleMode.displayName(styleMode)}).

Tác phẩm: $bookTitle
Tác giả: $author

--- NỘI DUNG SÁCH ---
$bookContent
--- HẾT NỘI DUNG SÁCH ---

Hãy viết ngay kịch bản theo đúng định dạng === PHẦN: ... | GIỌNG: ... === đã hướng dẫn.
        """.trimIndent()
        return header
    }

    fun buildChunkSummaryPrompt(
        bookTitle: String,
        author: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkText: String
    ): String = """
Bạn đang đọc Phần $chunkIndex/$totalChunks của tác phẩm "$bookTitle" (tác giả: $author).
Hãy ghi chép lại thật chi tiết bằng tiếng Việt:
1. Các sự kiện, diễn biến và bước ngoặt quan trọng nhất trong phần này.
2. Diễn biến tâm lý, mâu thuẫn nội tâm của các nhân vật chính.
3. 2–4 câu thoại hoặc chi tiết đắt giá nhất có thể trích lại khi kể chuyện.
Viết rõ ràng, súc tích khoảng 450–650 từ, trung thành tuyệt đối với văn bản gốc.

--- ĐOẠN TRÍCH ($chunkIndex/$totalChunks) ---
$chunkText
    """.trimIndent()

    fun buildShareablePromptForGeminiApp(
        bookTitle: String,
        author: String,
        bookContent: String,
        styleMode: String,
        maxChars: Int = 24000
    ): String {
        val trimmedContent = if (bookContent.length > maxChars) {
            val head = bookContent.take(maxChars * 2 / 3)
            val tail = bookContent.takeLast(maxChars / 3)
            "$head\n\n[... phần giữa được rút gọn do giới hạn chia sẻ nhanh ...]\n\n$tail"
        } else {
            bookContent
        }

        return buildSystemInstruction(styleMode) + "\n\n" +
            buildUserPrompt(bookTitle, author, trimmedContent, styleMode)
    }
}

object ScriptSegmentParser {

    private val sectionHeaderRegex = Regex(
        pattern = "^={2,}\\s*PHẦN\\s*:\\s*(.+?)(?:\\|\\s*GIỌNG\\s*:\\s*(.+?))?\\s*={2,}$",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun parseToSegments(rawScript: String, maxCharsPerSegment: Int = 1800): List<ParsedScriptSegment> {
        val cleaned = rawScript
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val rawSections = mutableListOf<Triple<String, String, StringBuilder>>()
        var currentTitle = "Mở đầu"
        var currentTone = "trầm ấm, tự nhiên"
        var currentBuffer = StringBuilder()

        for (line in cleaned.lines()) {
            val trimmed = line.trim()
            val match = sectionHeaderRegex.matchEntire(trimmed)
            if (match != null) {
                if (currentBuffer.toString().trim().isNotBlank()) {
                    rawSections.add(Triple(currentTitle, currentTone, currentBuffer))
                }
                currentTitle = match.groupValues[1].trim().ifBlank { "Phần tiếp theo" }
                currentTone = match.groupValues.getOrNull(2)?.trim().orEmpty().ifBlank { "trầm ấm, truyền cảm" }
                currentBuffer = StringBuilder()
            } else {
                currentBuffer.append(line).append('\n')
            }
        }
        if (currentBuffer.toString().trim().isNotBlank()) {
            rawSections.add(Triple(currentTitle, currentTone, currentBuffer))
        }

        val finalSegments = mutableListOf<ParsedScriptSegment>()
        var idx = 0

        for ((title, tone, buffer) in rawSections) {
            val cleanBody = sanitizeForSpeech(buffer.toString())
            if (cleanBody.isBlank()) continue
            val chunks = splitLongSpeechText(cleanBody, maxCharsPerSegment)
            chunks.forEachIndexed { partIdx, chunk ->
                val segTitle = if (chunks.size > 1) "$title (${partIdx + 1}/${chunks.size})" else title
                finalSegments.add(
                    ParsedScriptSegment(
                        index = idx++,
                        sectionTitle = segTitle,
                        toneHint = tone,
                        spokenText = chunk
                    )
                )
            }
        }

        return finalSegments
    }

    fun sanitizeForSpeech(text: String): String {
        return text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*•]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("`+"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun splitLongSpeechText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val paragraphs = text.split(Regex("\n{2,}"))
        val chunks = mutableListOf<String>()
        val builder = StringBuilder()

        for (para in paragraphs) {
            val p = para.trim()
            if (p.isBlank()) continue
            if (p.length > maxChars) {
                if (builder.isNotEmpty()) {
                    chunks.add(builder.toString().trim())
                    builder.clear()
                }
                chunks.addAll(splitBySentences(p, maxChars))
            } else if (builder.length + p.length + 2 > maxChars && builder.isNotEmpty()) {
                chunks.add(builder.toString().trim())
                builder.clear()
                builder.append(p)
            } else {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(p)
            }
        }
        if (builder.isNotEmpty()) {
            chunks.add(builder.toString().trim())
        }
        return chunks
    }

    private fun splitBySentences(paragraph: String, maxChars: Int): List<String> {
        val sentences = paragraph.split(Regex("(?<=[.!?…])\\s+"))
        val chunks = mutableListOf<String>()
        val buf = StringBuilder()
        for (sentence in sentences) {
            if (buf.length + sentence.length + 1 > maxChars && buf.isNotEmpty()) {
                chunks.add(buf.toString().trim())
                buf.clear()
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(sentence)
        }
        if (buf.isNotEmpty()) chunks.add(buf.toString().trim())
        return chunks
    }
}
