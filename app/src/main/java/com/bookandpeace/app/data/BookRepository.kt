package com.bookandpeace.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.bookandpeace.app.data.ai.DeepSummaryEntry
import com.bookandpeace.app.data.local.BookDatabase
import com.bookandpeace.app.data.local.BookEntity
import com.bookandpeace.app.data.local.BookSourceType
import com.bookandpeace.app.data.local.BookmarkEntity
import com.bookandpeace.app.data.local.ChapterEntity
import com.bookandpeace.app.data.local.OpdsSourceEntity
import com.bookandpeace.app.data.local.ScriptStatus
import com.bookandpeace.app.data.local.StoryScriptEntity
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.LocalBookImporter
import com.bookandpeace.app.data.parser.ParsedBook
import com.bookandpeace.app.data.parser.PdfParser
import com.bookandpeace.app.data.parser.TxtParser
import com.bookandpeace.app.data.remote.GutendexClient
import com.bookandpeace.app.data.remote.OpdsClient
import com.bookandpeace.app.data.remote.RemoteCatalogBook
import com.bookandpeace.app.data.remote.UrlBookDownloader
import com.bookandpeace.app.data.remote.WikisourceClient
import com.bookandpeace.app.worker.StoryGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val context: Context,
    private val db: BookDatabase,
    val wikisourceClient: WikisourceClient,
    val gutendexClient: GutendexClient,
    val opdsClient: OpdsClient,
    val urlBookDownloader: UrlBookDownloader
) {
    val bookDao = db.bookDao()
    val chapterDao = db.chapterDao()
    val bookmarkDao = db.bookmarkDao()
    val storyScriptDao = db.storyScriptDao()
    val opdsSourceDao = db.opdsSourceDao()

    suspend fun ensureInitialDefaults() = withContext(Dispatchers.IO) {
        if (opdsSourceDao.count() == 0) {
            opdsSourceDao.insert(
                OpdsSourceEntity(
                    name = "Standard Ebooks (Kinh điển Thế giới chuẩn đẹp)",
                    url = "https://standardebooks.org/feeds/opds",
                    description = "Kho sách EPUB kinh điển quốc tế được biên tập trình bày cực đẹp, miễn phí Public Domain."
                )
            )
            opdsSourceDao.insert(
                OpdsSourceEntity(
                    name = "Project Gutenberg OPDS",
                    url = "https://m.gutenberg.org/ebooks.opds/",
                    description = "Danh mục OPDS chính thức của hơn 70.000 cuốn sách Public Domain."
                )
            )
        }
    }

    suspend fun importLocalUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "Sach_ca_nhan"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val titleFallback = displayName.substringBeforeLast('.')

        var persistedFilePath: String? = null
        val (sourceType, parsed) = when (ext) {
            "epub" -> {
                val temp = LocalBookImporter.copyUriToTempFile(context, uri, "epub")
                val mediaDir = File(context.filesDir, "books/media/epub_${System.currentTimeMillis()}")
                try {
                    BookSourceType.LOCAL_EPUB to EpubParser.parseFile(temp, titleFallback, mediaOutputDir = mediaDir)
                } finally {
                    temp.delete()
                }
            }

            "pdf" -> {
                val pdfDir = File(context.filesDir, "books/files").apply { mkdirs() }
                val savedPdf = File(pdfDir, "pdf_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    savedPdf.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("Không mở được file PDF")
                persistedFilePath = savedPdf.absolutePath
                BookSourceType.LOCAL_PDF to PdfParser.parseFile(savedPdf, titleFallback)
            }

            "txt", "md" -> {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Không đọc được file văn bản")
                BookSourceType.LOCAL_TXT to TxtParser.parseText(text, titleFallback)
            }

            else -> {
                val mime = context.contentResolver.getType(uri).orEmpty()
                when {
                    mime.contains("epub", true) -> {
                        val temp = LocalBookImporter.copyUriToTempFile(context, uri, "epub")
                        val mediaDir = File(context.filesDir, "books/media/epub_${System.currentTimeMillis()}")
                        try {
                            BookSourceType.LOCAL_EPUB to EpubParser.parseFile(temp, titleFallback, mediaOutputDir = mediaDir)
                        } finally {
                            temp.delete()
                        }
                    }

                    mime.contains("pdf", true) -> {
                        val pdfDir = File(context.filesDir, "books/files").apply { mkdirs() }
                        val savedPdf = File(pdfDir, "pdf_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            savedPdf.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IllegalArgumentException("Không mở được file PDF")
                        persistedFilePath = savedPdf.absolutePath
                        BookSourceType.LOCAL_PDF to PdfParser.parseFile(savedPdf, titleFallback)
                    }

                    else -> {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: throw IllegalArgumentException("Định dạng file không hỗ trợ ($displayName). Hãy chọn .epub, .pdf hoặc .txt")
                        BookSourceType.LOCAL_TXT to TxtParser.parseText(text, titleFallback)
                    }
                }
            }
        }

        saveParsedBook(
            parsed = parsed,
            category = "Sách cá nhân",
            sourceType = sourceType,
            sourceRef = uri.toString(),
            localFilePath = persistedFilePath
        )
    }

    suspend fun importFolderUri(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        var importedCount = 0
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)

        while (queue.isNotEmpty() && importedCount < 100) {
            val current = queue.removeFirst()
            for (child in current.listFiles()) {
                if (child.isDirectory) {
                    queue.add(child)
                } else if (child.isFile) {
                    val name = child.name.orEmpty().lowercase()
                    if (name.endsWith(".epub") || name.endsWith(".pdf") || name.endsWith(".txt")) {
                        runCatching {
                            importLocalUri(child.uri)
                            importedCount++
                        }
                    }
                }
            }
        }
        importedCount
    }

    suspend fun importFromUrl(rawUrl: String): Long = withContext(Dispatchers.IO) {
        val downloaded = urlBookDownloader.downloadAndParse(rawUrl)
        saveParsedBook(
            parsed = downloaded.parsedBook,
            category = "Sách từ Link / Drive",
            sourceType = downloaded.sourceType,
            sourceRef = rawUrl.trim(),
            localFilePath = downloaded.savedLocalFilePath
        )
    }

    suspend fun downloadRemoteBook(item: RemoteCatalogBook): Long = withContext(Dispatchers.IO) {
        val existing = bookDao.findBySource(item.sourceType, item.sourceRef)
        if (existing != null) return@withContext existing.id

        val parsed = when (item.sourceType) {
            BookSourceType.WIKISOURCE -> wikisourceClient.downloadWork(item, lang = "vi")
            "EN_WIKISOURCE", "ARCHIVE_ORG", BookSourceType.GUTENBERG -> gutendexClient.downloadBook(item)
            BookSourceType.OPDS -> opdsClient.downloadEpub(context, item)
            else -> throw IllegalArgumentException("Nguồn sách không được hỗ trợ: ${item.sourceType}")
        }

        saveParsedBook(
            parsed = parsed,
            category = item.category,
            sourceType = item.sourceType,
            sourceRef = item.sourceRef,
            coverUrlFallback = item.coverUrl
        )
    }

    private suspend fun saveParsedBook(
        parsed: ParsedBook,
        category: String,
        sourceType: String,
        sourceRef: String,
        coverUrlFallback: String? = null,
        localFilePath: String? = null
    ): Long {
        var coverPath: String? = coverUrlFallback
        if (parsed.coverBytes != null && parsed.coverBytes.isNotEmpty()) {
            val coversDir = File(context.filesDir, "books/covers").apply { mkdirs() }
            val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
            coverFile.writeBytes(parsed.coverBytes)
            coverPath = coverFile.absolutePath
        }

        val bookId = bookDao.insertBook(
            BookEntity(
                title = parsed.title,
                author = parsed.author,
                description = parsed.description,
                language = parsed.language,
                category = category,
                sourceType = sourceType,
                sourceRef = sourceRef,
                coverPath = coverPath,
                localFilePath = localFilePath,
                totalChapters = parsed.chapters.size,
                wordCount = parsed.totalWords
            )
        )

        val chapterEntities = parsed.chapters.mapIndexed { index, ch ->
            ChapterEntity(
                bookId = bookId,
                chapterIndex = index,
                title = ch.title,
                content = ch.content
            )
        }
        chapterDao.insertChapters(chapterEntities)
        return bookId
    }

    suspend fun saveBookmark(
        bookId: Long,
        chapterIndex: Int,
        chapterTitle: String,
        excerpt: String,
        note: String = ""
    ) {
        bookmarkDao.insertBookmark(
            BookmarkEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                excerpt = excerpt.take(240),
                note = note
            )
        )
    }

    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        val scripts = storyScriptDao.getScriptById(bookId)
        val book = bookDao.getBookById(bookId)
        book?.coverPath?.let { path ->
            if (path.startsWith(context.filesDir.absolutePath)) {
                runCatching { File(path).delete() }
            }
        }
        book?.localFilePath?.let { path ->
            if (path.startsWith(context.filesDir.absolutePath)) {
                runCatching { File(path).delete() }
            }
        }
        bookDao.deleteBookById(bookId)
        scripts?.let {
            runCatching { File(context.filesDir, "audiobooks/script_${it.id}").deleteRecursively() }
        }
    }

    suspend fun startBackgroundStoryGeneration(
        bookId: Long,
        bookTitle: String,
        styleMode: String,
        aiProvider: String,
        ttsEngine: String,
        useDeepSummary: Boolean = true
    ): Long {
        val scriptId = storyScriptDao.insertScript(
            StoryScriptEntity(
                bookId = bookId,
                title = "Văn Vở: $bookTitle",
                styleMode = styleMode,
                aiProvider = aiProvider,
                ttsEngine = ttsEngine,
                status = ScriptStatus.GENERATING_SCRIPT,
                statusMessage = if (useDeepSummary) {
                    "Đang viết kịch bản Văn Vở (chế độ tiết kiệm token)..."
                } else {
                    "Đang xếp hàng chạy ngầm..."
                }
            )
        )
        StoryGenerationWorker.enqueue(
            context = context,
            scriptId = scriptId,
            audioOnly = false,
            useDeepSummary = useDeepSummary
        )
        return scriptId
    }

    suspend fun startZeroTokenAudioFromDeepSummary(
        bookId: Long,
        entry: DeepSummaryEntry,
        ttsEngine: String
    ): Long {
        val scriptId = storyScriptDao.insertScript(
            StoryScriptEntity(
                bookId = bookId,
                title = "Tóm tắt Sâu (0 token): ${entry.titleVi}",
                styleMode = "VAN_VO_STANDARD",
                aiProvider = "PREBUILT_DEEP_SUMMARY",
                ttsEngine = ttsEngine,
                rawScript = entry.toZeroTokenAudioScript(),
                status = ScriptStatus.GENERATING_AUDIO,
                statusMessage = "Đang thu âm trực tiếp Bản Tóm Tắt Sâu (0 token Text API)..."
            )
        )
        StoryGenerationWorker.enqueue(context, scriptId, audioOnly = true, useDeepSummary = true)
        return scriptId
    }

    suspend fun saveDeepSummaryAsBook(entry: DeepSummaryEntry): Long = withContext(Dispatchers.IO) {
        val existing = bookDao.findBySource(BookSourceType.BUILTIN, "deep_${entry.id}")
        if (existing != null) {
            // Upgrade old short 5-chapter entry if a richer full narration is now bundled
            if (entry.hasFullNarration && existing.wordCount < 1100) {
                deleteBook(existing.id)
            } else {
                return@withContext existing.id
            }
        }

        val chapters = if (entry.hasFullNarration) {
            entry.narrationParts.mapIndexed { idx, part ->
                com.bookandpeace.app.data.parser.ParsedChapter(
                    title = "Hồi ${idx + 1}: ${part.title}",
                    content = part.content
                )
            }
        } else {
            listOf(
                com.bookandpeace.app.data.parser.ParsedChapter("1. Bối cảnh & Tầm vóc", entry.context),
                com.bookandpeace.app.data.parser.ParsedChapter("2. Nhân vật / Khái niệm cốt lõi", entry.charactersOrCoreConcepts),
                com.bookandpeace.app.data.parser.ParsedChapter("3. Tóm tắt sâu & Phân tích chi tiết", entry.deepAnalysis),
                com.bookandpeace.app.data.parser.ParsedChapter("4. Chi tiết đắt giá & Trích dẫn", entry.keyDetailsAndQuotes),
                com.bookandpeace.app.data.parser.ParsedChapter("5. Góc nhìn chiêm nghiệm Văn Vở", entry.vanVoPerspective)
            )
        }
        saveParsedBook(
            parsed = ParsedBook(
                title = entry.titleVi,
                author = entry.author,
                description = if (entry.hasFullNarration) {
                    "Bản Sách nói Phân tích Đầy đủ (~${entry.estimatedMinutes} phút nghe). ${entry.context}"
                } else {
                    "Bản Tóm Tắt Sâu có sẵn. ${entry.context}"
                },
                language = "vi",
                chapters = chapters
            ),
            category = entry.category,
            sourceType = BookSourceType.BUILTIN,
            sourceRef = "deep_${entry.id}"
        )
    }

    suspend fun downloadPrioritySciencePack(limit: Int = 5): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for (item in GutendexClient.curatedScienceBooks.take(limit)) {
            runCatching {
                downloadRemoteBook(item)
                successCount++
            }
        }
        successCount
    }

    suspend fun createFromPastedScriptAndGenerateAudio(
        bookId: Long,
        bookTitle: String,
        styleMode: String,
        ttsEngine: String,
        pastedScript: String
    ): Long {
        val scriptId = storyScriptDao.insertScript(
            StoryScriptEntity(
                bookId = bookId,
                title = "Văn Vở (Từ App Gemini): $bookTitle",
                styleMode = styleMode,
                aiProvider = "GEMINI_APP_SHARE",
                ttsEngine = ttsEngine,
                rawScript = pastedScript.trim(),
                status = ScriptStatus.GENERATING_AUDIO,
                statusMessage = "Đang thu âm kịch bản chạy ngầm..."
            )
        )
        StoryGenerationWorker.enqueue(context, scriptId, audioOnly = true)
        return scriptId
    }

    suspend fun updateScriptAndRegenerateAudio(
        script: StoryScriptEntity,
        newRawScript: String,
        newTtsEngine: String
    ) {
        storyScriptDao.updateScript(
            script.copy(
                rawScript = newRawScript.trim(),
                ttsEngine = newTtsEngine,
                status = ScriptStatus.GENERATING_AUDIO,
                statusMessage = "Đang thu âm lại kịch bản đã chỉnh sửa...",
                updatedAt = System.currentTimeMillis()
            )
        )
        StoryGenerationWorker.enqueue(context, script.id, audioOnly = true)
    }

    suspend fun deleteScript(scriptId: Long) = withContext(Dispatchers.IO) {
        storyScriptDao.deleteScriptById(scriptId)
        runCatching { File(context.filesDir, "audiobooks/script_$scriptId").deleteRecursively() }
    }

    suspend fun getFullBookText(bookId: Long): String = withContext(Dispatchers.IO) {
        chapterDao.getChaptersForBook(bookId).joinToString("\n\n") { "### ${it.title}\n${it.content}" }
    }

    suspend fun clearAudioCacheBytes(): Long = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "audiobooks")
        val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        size
    }

    fun getAudioCacheSizeBytes(): Long {
        val dir = File(context.filesDir, "audiobooks")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
