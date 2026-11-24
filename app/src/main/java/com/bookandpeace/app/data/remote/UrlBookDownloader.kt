package com.bookandpeace.app.data.remote

import android.content.Context
import com.bookandpeace.app.data.local.BookSourceType
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.ParsedBook
import com.bookandpeace.app.data.parser.PdfParser
import com.bookandpeace.app.data.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed class ResolvedDownloadUrl {
    data class DirectFile(val downloadUrl: String, val suggestedName: String) : ResolvedDownloadUrl()
    data class FolderOrWebPage(val originalUrl: String, val reason: String) : ResolvedDownloadUrl()
}

data class DownloadedUrlBook(
    val sourceType: String,
    val parsedBook: ParsedBook,
    val savedLocalFilePath: String? = null
)

class UrlBookDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val USER_AGENT = "BookAndPeace/1.0 (Personal Literature Reader; Android)"
        private val DRIVE_FILE_REGEX = Regex("drive\\.google\\.com/file/d/([a-zA-Z0-9_\\-]+)")
        private val DRIVE_ID_PARAM_REGEX = Regex("drive\\.google\\.com/(?:open|uc)\\?.*?(?:&|\\?)?id=([a-zA-Z0-9_\\-]+)")
        private val DRIVE_FOLDER_REGEX = Regex("drive\\.google\\.com/drive/(?:u/\\d+/)?folders/")
        private val GOOGLE_DOCS_REGEX = Regex("docs\\.google\\.com/document/d/([a-zA-Z0-9_\\-]+)")

        fun resolveUrl(rawUrl: String): ResolvedDownloadUrl {
            val trimmed = rawUrl.trim()
            require(trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                "Đường dẫn phải bắt đầu bằng http:// hoặc https://"
            }

            if (DRIVE_FOLDER_REGEX.containsMatchIn(trimmed)) {
                return ResolvedDownloadUrl.FolderOrWebPage(
                    originalUrl = trimmed,
                    reason = "Đây là link Thư mục Google Drive (chứa nhiều file). Hãy mở trong trình duyệt, bấm tải từng file EPUB/PDF rồi chọn Mở bằng Book & Peace hoặc dán link trực tiếp của 1 file."
                )
            }

            DRIVE_FILE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { fileId ->
                return ResolvedDownloadUrl.DirectFile(
                    downloadUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
                    suggestedName = "GoogleDrive_$fileId"
                )
            }

            DRIVE_ID_PARAM_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { fileId ->
                return ResolvedDownloadUrl.DirectFile(
                    downloadUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
                    suggestedName = "GoogleDrive_$fileId"
                )
            }

            GOOGLE_DOCS_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { docId ->
                return ResolvedDownloadUrl.DirectFile(
                    downloadUrl = "https://docs.google.com/document/d/$docId/export?format=epub",
                    suggestedName = "GoogleDoc_$docId"
                )
            }

            val cleanName = trimmed.substringBefore('?').substringAfterLast('/')
                .substringBeforeLast('.')
                .ifBlank { "Sach_tu_link" }

            return ResolvedDownloadUrl.DirectFile(
                downloadUrl = trimmed,
                suggestedName = cleanName
            )
        }
    }

    suspend fun downloadAndParse(rawUrl: String): DownloadedUrlBook = withContext(Dispatchers.IO) {
        val resolved = resolveUrl(rawUrl)
        if (resolved is ResolvedDownloadUrl.FolderOrWebPage) {
            throw IllegalArgumentException(resolved.reason)
        }
        val target = resolved as ResolvedDownloadUrl.DirectFile
        val request = Request.Builder()
            .url(target.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Không tải được file (HTTP ${response.code}). Kiểm tra quyền truy cập của link.")
            }

            val bodyStream = response.body?.byteStream() ?: throw IllegalStateException("File tải về bị rỗng")
            val contentDisposition = response.header("Content-Disposition").orEmpty()
            val headerFileName = extractFilenameFromHeader(contentDisposition)
            val fallbackTitle = headerFileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: target.suggestedName

            // Stream directly to disk so large 20-50MB PDFs never run out of RAM on Android
            val tempFile = File.createTempFile("url_book_", ".tmp", context.cacheDir)
            try {
                tempFile.outputStream().use { out -> bodyStream.copyTo(out) }
                if (tempFile.length() < 64) {
                    throw IllegalStateException("Nội dung tải về quá ngắn, không phải file sách")
                }

                val magic = ByteArray(4)
                tempFile.inputStream().use { it.read(magic) }
                val isZipMagic = magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
                val isPdfMagic = magic[0] == 0x25.toByte() && magic[1] == 0x50.toByte() &&
                    magic[2] == 0x44.toByte() && magic[3] == 0x46.toByte()
                val contentType = response.header("Content-Type").orEmpty().lowercase()

                when {
                    isZipMagic || contentType.contains("epub") || headerFileName?.endsWith(".epub", true) == true -> {
                        val mediaDir = File(context.filesDir, "books/media/epub_${System.currentTimeMillis()}")
                        DownloadedUrlBook(
                            sourceType = BookSourceType.LOCAL_EPUB,
                            parsedBook = EpubParser.parseFile(tempFile, fallbackTitle, mediaOutputDir = mediaDir)
                        )
                    }

                    isPdfMagic || contentType.contains("pdf") || headerFileName?.endsWith(".pdf", true) == true -> {
                        val pdfDir = File(context.filesDir, "books/files").apply { mkdirs() }
                        val savedPdf = File(pdfDir, "pdf_${System.currentTimeMillis()}.pdf")
                        tempFile.copyTo(savedPdf, overwrite = true)
                        DownloadedUrlBook(
                            sourceType = BookSourceType.LOCAL_PDF,
                            parsedBook = PdfParser.parseFile(savedPdf, fallbackTitle),
                            savedLocalFilePath = savedPdf.absolutePath
                        )
                    }

                    contentType.contains("text/html") -> {
                        throw IllegalArgumentException(
                            "Link này trả về trang web HTML (chưa phải file EPUB/PDF trực tiếp hoặc yêu cầu đăng nhập). Hãy tải file về máy rồi mở bằng Book & Peace!"
                        )
                    }

                    else -> {
                        val text = tempFile.readText(Charsets.UTF_8)
                        DownloadedUrlBook(
                            sourceType = BookSourceType.LOCAL_TXT,
                            parsedBook = TxtParser.parseText(text, fallbackTitle)
                        )
                    }
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun extractFilenameFromHeader(header: String): String? {
        val utf8Match = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (utf8Match != null) {
            return runCatching { java.net.URLDecoder.decode(utf8Match.groupValues[1], "UTF-8") }.getOrNull()
        }
        val simpleMatch = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)
        return simpleMatch?.groupValues?.getOrNull(1)?.trim()
    }
}
