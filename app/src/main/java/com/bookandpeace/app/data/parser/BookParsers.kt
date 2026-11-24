package com.bookandpeace.app.data.parser

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class ParsedChapter(
    val title: String,
    val content: String
)

data class ParsedBook(
    val title: String,
    val author: String,
    val description: String = "",
    val language: String = "vi",
    val coverBytes: ByteArray? = null,
    val chapters: List<ParsedChapter>
) {
    val totalWords: Int
        get() = chapters.sumOf { countWords(it.content) }

    companion object {
        fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
}

object EpubParser {

    const val IMAGE_MARKER_PREFIX = "[[IMG:"
    const val IMAGE_MARKER_SUFFIX = "]]"

    fun parseFile(
        epubFile: File,
        fallbackTitle: String = epubFile.nameWithoutExtension,
        mediaOutputDir: File? = null
    ): ParsedBook {
        ZipFile(epubFile).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalArgumentException("File EPUB không hợp lệ (thiếu container.xml)")
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
            val containerDoc = Jsoup.parse(containerXml, "", Parser.xmlParser())
            val opfPath = containerDoc.selectFirst("rootfile")?.attr("full-path")
                ?: throw IllegalArgumentException("Không tìm thấy đường dẫn OPF trong EPUB")

            val opfDir = opfPath.substringBeforeLast("/", "")
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalArgumentException("Không tìm thấy file OPF: $opfPath")
            val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())

            val title = opfDoc.selectFirst("dc\\:title, title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val author = opfDoc.select("dc\\:creator, creator")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Khuyết danh" }
            val description = opfDoc.selectFirst("dc\\:description, description")
                ?.text()?.let { Jsoup.parse(it).text().trim() }.orEmpty()
            val language = opfDoc.selectFirst("dc\\:language, language")
                ?.text()?.trim()?.lowercase()?.take(2)?.ifBlank { "vi" } ?: "vi"

            val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
            var ncxHref: String? = null
            var navHref: String? = null
            var coverItemId: String? = opfDoc.selectFirst("meta[name=cover]")?.attr("content")

            for (item in opfDoc.select("manifest > item")) {
                val id = item.attr("id")
                val href = item.attr("href")
                val mediaType = item.attr("media-type")
                val props = item.attr("properties")
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifest[id] = href to mediaType
                }
                if (mediaType.contains("dtbncx")) {
                    ncxHref = href
                }
                if (props.contains("nav")) {
                    navHref = href
                }
                if (coverItemId == null && props.contains("cover-image")) {
                    coverItemId = id
                }
            }

            val tocTitles = mutableMapOf<String, String>()
            if (ncxHref != null) {
                val fullNcxPath = resolveZipPath(opfDir, ncxHref)
                zip.getEntry(fullNcxPath)?.let { entry ->
                    val ncxText = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val ncxDoc = Jsoup.parse(ncxText, "", Parser.xmlParser())
                    for (navPoint in ncxDoc.select("navPoint")) {
                        val label = navPoint.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                        val src = navPoint.selectFirst("content")?.attr("src")?.substringBefore("#").orEmpty()
                        if (label.isNotBlank() && src.isNotBlank()) {
                            tocTitles.putIfAbsent(normalizePath(resolveZipPath(opfDir, src)), label)
                        }
                    }
                }
            }
            if (tocTitles.isEmpty() && navHref != null) {
                val fullNavPath = resolveZipPath(opfDir, navHref)
                zip.getEntry(fullNavPath)?.let { entry ->
                    val navHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val navDoc = Jsoup.parse(navHtml)
                    val navBase = fullNavPath.substringBeforeLast("/", "")
                    for (a in navDoc.select("nav a[href]")) {
                        val label = a.text().trim()
                        val href = a.attr("href").substringBefore("#")
                        if (label.isNotBlank() && href.isNotBlank()) {
                            tocTitles.putIfAbsent(normalizePath(resolveZipPath(navBase, href)), label)
                        }
                    }
                }
            }

            val coverBytes = coverItemId?.let { id ->
                manifest[id]?.first?.let { href ->
                    val coverPath = resolveZipPath(opfDir, href)
                    zip.getEntry(coverPath)?.let { entry ->
                        zip.getInputStream(entry).use { it.readBytes() }
                    }
                }
            }

            val spineIds = opfDoc.select("spine > itemref").map { it.attr("idref") }
            val chapters = mutableListOf<ParsedChapter>()
            val extractedImageCache = mutableMapOf<String, String>()
            var chapterCounter = 1
            var imageCounter = 0

            for (idref in spineIds) {
                val (href, mediaType) = manifest[idref] ?: continue
                if (!mediaType.contains("html") && !mediaType.contains("xml") &&
                    !href.endsWith(".xhtml", true) && !href.endsWith(".html", true) && !href.endsWith(".htm", true)
                ) continue

                val fullPath = normalizePath(resolveZipPath(opfDir, href.substringBefore("#")))
                val entry = zip.getEntry(fullPath)
                    ?: zip.getEntry(java.net.URLDecoder.decode(fullPath, "UTF-8"))
                    ?: continue

                val chapterDir = fullPath.substringBeforeLast("/", "")
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val extracted = extractReadableTextFromHtml(html) { imgSrc ->
                    if (mediaOutputDir == null) return@extractReadableTextFromHtml null
                    val imgZipPath = normalizePath(resolveZipPath(chapterDir, imgSrc.substringBefore("#")))
                    extractedImageCache[imgZipPath] ?: runCatching {
                        val imgEntry = zip.getEntry(imgZipPath)
                            ?: zip.getEntry(java.net.URLDecoder.decode(imgZipPath, "UTF-8"))
                            ?: return@runCatching null
                        mediaOutputDir.mkdirs()
                        val ext = imgZipPath.substringAfterLast('.', "jpg").take(5)
                        val outFile = File(mediaOutputDir, "img_${imageCounter++}.$ext")
                        zip.getInputStream(imgEntry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        outFile.absolutePath.also { extractedImageCache[imgZipPath] = it }
                    }.getOrNull()
                }
                if (extracted.body.length < 40 && !extracted.body.contains(IMAGE_MARKER_PREFIX)) continue

                val chapterTitle = tocTitles[fullPath]
                    ?: extracted.heading
                    ?: "Chương $chapterCounter"

                val lowerTitle = chapterTitle.lowercase()
                if (lowerTitle.contains("about this digital edition") ||
                    lowerTitle.contains("về phiên bản điện tử") ||
                    lowerTitle == "about" ||
                    (lowerTitle.contains("cover") && extracted.body.length < 250 && !extracted.body.contains(IMAGE_MARKER_PREFIX))
                ) continue

                chapters.add(ParsedChapter(title = chapterTitle, content = extracted.body))
                chapterCounter++
            }

            if (chapters.isEmpty()) {
                throw IllegalArgumentException("Không trích xuất được nội dung văn bản từ file EPUB này")
            }

            return ParsedBook(
                title = title,
                author = author,
                description = description,
                language = language,
                coverBytes = coverBytes,
                chapters = chapters
            )
        }
    }

    internal data class HtmlExtractResult(val heading: String?, val body: String)

    internal fun extractReadableTextFromHtml(
        html: String,
        resolveImage: (String) -> String? = { null }
    ): HtmlExtractResult {
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, sup.reference").remove()
        val heading = doc.selectFirst("h1, h2, h3, title")?.text()?.trim()?.takeIf { it.length in 2..140 }
        val blocks = doc.body().select("h1, h2, h3, h4, p, blockquote, li, img, image")
        val paragraphs = mutableListOf<String>()
        if (blocks.isNotEmpty()) {
            for (el in blocks) {
                val tag = el.tagName().lowercase()
                if (tag == "img" || tag == "image") {
                    val src = el.attr("src").ifBlank { el.attr("xlink:href") }.ifBlank { el.attr("href") }
                    if (src.isNotBlank()) {
                        resolveImage(src)?.let { absPath ->
                            paragraphs.add("$IMAGE_MARKER_PREFIX$absPath$IMAGE_MARKER_SUFFIX")
                        }
                    }
                } else {
                    val txt = el.text().replace("\u00A0", " ").trim()
                    if (txt.isNotBlank()) paragraphs.add(txt)
                }
            }
        } else {
            doc.body().text().trim().takeIf { it.isNotBlank() }?.let { paragraphs.add(it) }
        }
        return HtmlExtractResult(
            heading = heading,
            body = paragraphs.joinToString("\n\n")
        )
    }

    private fun resolveZipPath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.removePrefix("/")
        return if (baseDir.isBlank()) cleanHref else "$baseDir/$cleanHref"
    }

    private fun normalizePath(path: String): String {
        val parts = ArrayDeque<String>()
        for (segment in path.split("/")) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }
}

object TxtParser {

    private val chapterHeaderRegex = Regex(
        pattern = "^(?:chương|hồi|phần|quyển|tập|chapter|part|book)\\s+[0-9ivxlcdm]+.*$",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun parseText(
        rawText: String,
        title: String,
        author: String = "Tác giả chưa rõ"
    ): ParsedBook {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        require(normalized.isNotBlank()) { "File văn bản rỗng" }

        val lines = normalized.lines()
        val chapters = mutableListOf<ParsedChapter>()
        var currentTitle = "Mở đầu"
        val currentBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length in 3..100 && chapterHeaderRegex.matches(trimmed)) {
                if (currentBuffer.toString().trim().length >= 80) {
                    chapters.add(ParsedChapter(currentTitle, currentBuffer.toString().trim()))
                    currentBuffer.clear()
                }
                currentTitle = trimmed
            } else {
                currentBuffer.append(line).append('\n')
            }
        }

        val tail = currentBuffer.toString().trim()
        if (tail.isNotBlank()) {
            chapters.add(ParsedChapter(currentTitle, tail))
        }

        val finalChapters = if (chapters.size <= 1 && normalized.length > 14000) {
            splitIntoBalancedChapters(normalized)
        } else {
            chapters
        }

        return ParsedBook(
            title = title.ifBlank { "Sách văn bản" },
            author = author,
            description = finalChapters.firstOrNull()?.content?.take(220).orEmpty(),
            language = "vi",
            chapters = finalChapters
        )
    }

    private fun splitIntoBalancedChapters(text: String, targetChars: Int = 10000): List<ParsedChapter> {
        val paragraphs = text.split(Regex("\n{2,}"))
        val result = mutableListOf<ParsedChapter>()
        val builder = StringBuilder()
        var index = 1

        for (p in paragraphs) {
            val clean = p.trim()
            if (clean.isBlank()) continue
            if (builder.length + clean.length > targetChars && builder.isNotEmpty()) {
                result.add(ParsedChapter("Phần $index", builder.toString().trim()))
                builder.clear()
                index++
            }
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(clean)
        }
        if (builder.isNotEmpty()) {
            result.add(ParsedChapter("Phần $index", builder.toString().trim()))
        }
        return result
    }
}

object PdfParser {

    fun parseFile(pdfFile: File, fallbackTitle: String): ParsedBook {
        PDDocument.load(pdfFile).use { document ->
            return extractFromDocument(document, fallbackTitle)
        }
    }

    fun parseStream(
        inputStream: InputStream,
        fallbackTitle: String
    ): ParsedBook {
        PDDocument.load(inputStream).use { document ->
            return extractFromDocument(document, fallbackTitle)
        }
    }

    private fun extractFromDocument(document: PDDocument, fallbackTitle: String): ParsedBook {
        val info = document.documentInformation
        val title = info?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = info?.author?.takeIf { it.isNotBlank() } ?: "Khuyết danh"
        val totalPages = document.numberOfPages
        if (totalPages == 0) {
            throw IllegalArgumentException("File PDF không có trang nào")
        }

        val stripper = PDFTextStripper()
        val pagesPerSection = 12
        val chapters = mutableListOf<ParsedChapter>()

        var startPage = 1
        var sectionIndex = 1
        while (startPage <= totalPages) {
            val endPage = minOf(startPage + pagesPerSection - 1, totalPages)
            stripper.startPage = startPage
            stripper.endPage = endPage
            val sectionText = stripper.getText(document)
                .replace("\r\n", "\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            if (sectionText.isNotBlank()) {
                chapters.add(
                    ParsedChapter(
                        title = "Phần $sectionIndex (Trang $startPage–$endPage)",
                        content = sectionText
                    )
                )
                sectionIndex++
            }
            startPage = endPage + 1
        }

        if (chapters.isEmpty()) {
            // Allow scanned/image-heavy PDFs to still open cleanly in the native visual PDF Reader!
            chapters.add(
                ParsedChapter(
                    title = "Sách PDF ảnh/tranh ($totalPages trang)",
                    content = "Cuốn sách PDF này gồm $totalPages trang gốc có hình ảnh/scan. Hãy đọc trực tiếp ở chế độ Trang gốc PDF (hỗ trợ lật trang như máy đọc sách, phóng to và lọc dịu mắt)."
                )
            )
        }

        return ParsedBook(
            title = title,
            author = author,
            description = chapters.first().content.take(220),
            language = "vi",
            chapters = chapters
        )
    }
}

object LocalBookImporter {
    fun copyUriToTempFile(context: Context, uri: Uri, extension: String): File {
        val tempFile = File.createTempFile("import_", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Không đọc được file từ bộ nhớ máy")
        return tempFile
    }
}
