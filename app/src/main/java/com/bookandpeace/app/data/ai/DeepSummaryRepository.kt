package com.bookandpeace.app.data.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class DeepSummaryEntry(
    val id: String,
    val titleVi: String,
    val author: String,
    val category: String,
    val matchKeys: List<String>,
    val context: String,
    val charactersOrCoreConcepts: String,
    val deepAnalysis: String,
    val keyDetailsAndQuotes: String,
    val vanVoPerspective: String
) {
    fun toCompactAiSource(): String = buildString {
        appendLine("Tác phẩm: $titleVi — Tác giả: $author ($category)")
        appendLine()
        appendLine("1. BỐI CẢNH & GIÁ TRỊ:")
        appendLine(context)
        appendLine()
        appendLine("2. NHÂN VẬT / KHÁI NIỆM CỐT LÕI:")
        appendLine(charactersOrCoreConcepts)
        appendLine()
        appendLine("3. TÓM TẮT SÂU & DIỄN BIẾN CHI TIẾT:")
        appendLine(deepAnalysis)
        appendLine()
        appendLine("4. CHI TIẾT ĐẮT GIÁ & TRÍCH DẪN KINH ĐIỂN:")
        appendLine(keyDetailsAndQuotes)
        appendLine()
        appendLine("5. GÓC NHÌN CHIÊM NGHIỆM (CHẤT VĂN VỞ):")
        appendLine(vanVoPerspective)
    }.trim()

    fun toZeroTokenAudioScript(): String = buildString {
        appendLine("=== PHẦN: Bối cảnh tác phẩm | GIỌNG: warm, calm, natural Vietnamese storytelling ===")
        appendLine("$titleVi, tác phẩm của $author.")
        appendLine(context)
        appendLine()
        appendLine("=== PHẦN: Nhân vật & Cốt lõi | GIỌNG: clear, thoughtful Vietnamese storytelling ===")
        appendLine(charactersOrCoreConcepts)
        appendLine()
        appendLine("=== PHẦN: Diễn biến chi tiết | GIỌNG: expressive, engaging Vietnamese storytelling ===")
        appendLine(deepAnalysis)
        appendLine()
        appendLine("=== PHẦN: Điểm nhấn đắt giá | GIỌNG: slow, emotional Vietnamese storytelling ===")
        appendLine(keyDetailsAndQuotes)
        appendLine()
        appendLine("=== PHẦN: Dư âm | GIỌNG: reflective, deep calm Vietnamese storytelling ===")
        appendLine(vanVoPerspective)
    }.trim()
}

private data class IndexedSummary(
    val entry: DeepSummaryEntry,
    val normalizedKeys: List<String>
)

class DeepSummaryRepository(
    private val context: Context,
    private val json: Json
) {
    @Volatile
    private var indexedEntries: List<IndexedSummary>? = null
    private val lookupCache = ConcurrentHashMap<String, DeepSummaryEntry?>()
    private val missingSentinel = Any()
    private val rawLookupCache = ConcurrentHashMap<String, Any>()

    fun warmUp() {
        ensureLoaded()
    }

    private fun ensureLoaded(): List<IndexedSummary> {
        indexedEntries?.let { return it }
        return synchronized(this) {
            indexedEntries ?: runCatching {
                val raw = context.assets.open("deep_summaries_vi.json").bufferedReader().use { it.readText() }
                val decoded = json.decodeFromString<List<DeepSummaryEntry>>(raw)
                decoded.map { entry ->
                    IndexedSummary(
                        entry = entry,
                        normalizedKeys = entry.matchKeys.map { normalize(it) }.filter { it.isNotBlank() }
                    )
                }
            }.getOrDefault(emptyList()).also { indexedEntries = it }
        }
    }

    fun getAll(): List<DeepSummaryEntry> = ensureLoaded().map { it.entry }

    fun findForBook(title: String, sourceRef: String = ""): DeepSummaryEntry? {
        val cacheKey = "$title||$sourceRef"
        rawLookupCache[cacheKey]?.let { cached ->
            return if (cached === missingSentinel) null else cached as DeepSummaryEntry
        }
        val normTitle = normalize(title)
        val normRef = if (sourceRef.isNotBlank()) normalize(sourceRef) else ""
        val found = ensureLoaded().firstOrNull { item ->
            item.normalizedKeys.any { key ->
                normTitle.contains(key) || (normRef.isNotEmpty() && normRef.contains(key))
            }
        }?.entry
        rawLookupCache[cacheKey] = found ?: missingSentinel
        return found
    }

    fun hasSummary(title: String, sourceRef: String = ""): Boolean = findForBook(title, sourceRef) != null

    companion object {
        private val combiningMarks = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val nonAlphaNum = Regex("[^a-z0-9\\s]")
        private val multiSpace = Regex("\\s+")

        fun normalize(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            return decomposed
                .replace(combiningMarks, "")
                .replace('đ', 'd')
                .replace(nonAlphaNum, " ")
                .replace(multiSpace, " ")
                .trim()
        }
    }
}
