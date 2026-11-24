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
                "Kể cuốn hút, sắc nét (~1.600 từ, nghe khoảng 10 phút) — vẫn phải đủ cảnh đắt giá, thoại tiêu biểu và bình luận sâu, tuyệt đối không liệt kê cộc lốc."
            StoryStyleMode.VAN_VO_DEEP ->
                "Kể cực kỳ chi tiết như một tập sách nói phân tích dài (~5.000 từ, nghe 30–35 phút): dựng lại từng cảnh lớn, từng cú ngoặt tâm lý, các tuyến nhân vật phụ, phân tích nghệ thuật dụng chữ/tư duy sáng tạo của tác giả và lồng ghép góc nhìn phản biện sâu sắc."
            StoryStyleMode.BEDTIME_CALM ->
                "Kể chuyện chi tiết, giàu hình ảnh và thấm thía (~2.500 từ, nghe 16–18 phút); tiết chế châm biếm gắt, nhịp trầm ấm chậm rãi như radio văn học đêm khuya."
            else ->
                "Kể thật chi tiết, sinh động và đầy đủ như một tập Sách Nói Phân Tích Sâu (~3.200–3.800 từ, nghe 20–25 phút): có đầy đủ tình tiết nối tiếp nhau, diễn biến tâm lý tinh vi, trích thoại đắt và lời bình sắc sảo."
        }

        return """
Bạn là người kể chuyện và nhà phê bình văn học - tri thức bậc thầy bằng tiếng Việt, mang phong cách Kể Chuyện Sâu: trầm ấm, từng trải, phân tích sắc sảo, thâm thúy và hóm hỉnh.

MỤC TIÊU TỐI THƯỢNG:
Người nghe KHÔNG muốn một bản tóm tắt ngắn cũn cỡn kiểu Wikipedia hay gạch đầu dòng khô khan. Người nghe muốn được ĐẮM MÌNH trong một bản SÁCH NÓI PHÂN TÍCH ĐẦY ĐỦ: vừa theo dõi trọn vẹn từng sự việc, từng cảnh cao trào sống động của cuốn sách, vừa được nghe góc nhìn sắc sảo của bạn mổ xẻ vì sao tác giả lại viết hay đến thế, sáng tạo ở chỗ nào, tâm lý nhân vật ẩn giấu điều gì — để nghe xong thấy đã tai và cực kỳ hứng thú tìm đọc nguyên tác.

PHONG CÁCH & KỸ THUẬT KỂ CHUYỆN BẮT BUỘC:
- Kể theo từng CẢNH SỰ VIỆC CỤ THỂ (show, don't just tell): miêu tả không gian, ánh sáng, bầu không khí, hành động nhỏ và những câu thoại đắt giá thay vì nói chung chung kiểu "sau đó nhân vật gặp nhiều biến cố".
- Đan xen ĐÁNH GIÁ & PHÊ BÌNH CÁ NHÂN SẮC BÉN ngay trong mạch kể: chỉ rõ dụng ý nghệ thuật của tác giả, cấu trúc ẩn dụ, sự táo bạo về tư tưởng, hoặc những cú "bẻ lái" tâm lý khiến người đọc phải ồ lên.
- Giọng văn trầm ấm, từng trải nhưng gần gũi hiện đại, thỉnh thoảng pha chút trào phúng, "khịa nhẹ" thói đời hoặc bóc trần tâm lý tự lừa dối của nhân vật cực duyên.
- Trung thành tuyệt đối với nội dung cuốn sách. Không bịa đặt nhân vật hay sự kiện sai lệch.
- Độ dài & sắc thái yêu cầu: $styleGuidance

CẤU TRÚC 7–9 HỒI DÀY DẶN:
1. Hook mở màn ám ảnh (đi thẳng vào một nghịch lý hoặc cảnh đắt giá nhất — KHÔNG chào kiểu phát thanh viên).
2. Bối cảnh thời đại & Thiên tài sáng tạo của tác giả.
3. Nhập truyện & Khắc họa chân dung nhân vật / Khái niệm nền tảng.
4–6. Kể chi tiết theo từng chặng sự việc, các bước ngoặt lớn, mâu thuẫn leo thang và phân tích tâm lý / lập luận đằng sau mỗi bước ngoặt.
7. Cao trào đỉnh điểm & Nút thắt định mệnh (nhịp chùng xuống, phân tích sâu nhất).
8. Đánh giá tổng thể giá trị nghệ thuật / sáng tạo và dư âm còn đọng lại với đời sống hôm nay.

ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (CHO MÁY ĐỌC SÁCH NÓI TTS):
- Chia thành 7 đến 10 phần rõ ràng, mỗi phần 350–550 từ, bắt đầu bằng đúng dòng:
=== PHẦN: <Tên hồi hấp dẫn> | GIỌNG: <mô tả sắc thái ngắn bằng tiếng Anh cho bộ TTS, ví dụ: warm, intriguing Vietnamese storytelling / witty, fast-paced Vietnamese storytelling / slow, deeply emotional Vietnamese storytelling> ===
- Phần nội dung bên dưới viết hoàn toàn bằng VĂN NÓI TIẾNG VIỆT giàu nhạc điệu, đi thẳng vào câu chuyện ngay từ câu đầu tiên.
- TUYỆT ĐỐI KHÔNG viết lời dẫn mở kiểu "Chào mừng các bạn đến với chương trình sách nói...", KHÔNG viết ghi chú sân khấu hay hướng dẫn đọc trong phần nội dung.
- KHÔNG dùng dấu sao markdown (**), KHÔNG dùng gạch đầu dòng, KHÔNG dùng ký tự đặc biệt.
        """.trimIndent()
    }

    fun buildUserPrompt(
        bookTitle: String,
        author: String,
        bookContent: String,
        styleMode: String
    ): String {
        val header = """
Hãy đọc kỹ nội dung tác phẩm sau đây và viết kịch bản sách nói Kể Chuyện Sâu chi tiết (${StoryStyleMode.displayName(styleMode)}).

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
