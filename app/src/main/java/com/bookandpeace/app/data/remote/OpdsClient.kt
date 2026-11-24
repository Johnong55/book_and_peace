package com.bookandpeace.app.data.remote

import android.content.Context
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI

data class OpdsFeedPage(
    val title: String,
    val navigationLinks: List<OpdsNavLink>,
    val books: List<RemoteCatalogBook>
)

data class OpdsNavLink(
    val title: String,
    val href: String,
    val summary: String = ""
)

class OpdsClient(private val httpClient: OkHttpClient) {

    suspend fun fetchFeed(feedUrl: String): OpdsFeedPage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "BookAndPeaceAndroid/1.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("OPDS nguồn trả lỗi ${response.code}")
            }
            val xml = response.body?.string().orEmpty()
            parseOpdsXml(xml, feedUrl)
        }
    }

    internal fun parseOpdsXml(xml: String, baseUrl: String): OpdsFeedPage {
        val doc = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        val feedTitle = doc.selectFirst("feed > title")?.text()?.trim().orEmpty().ifBlank { "Danh mục OPDS" }
        val navLinks = mutableListOf<OpdsNavLink>()
        val books = mutableListOf<RemoteCatalogBook>()

        for (entry in doc.select("feed > entry")) {
            val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isBlank()) continue
            val author = entry.select("author > name")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Tác giả OPDS" }
            val summary = entry.selectFirst("summary, content")?.text()?.let { Jsoup.parse(it).text().trim() }.orEmpty()
            val links = entry.select("link")

            val epubLink = links.firstOrNull { link ->
                val type = link.attr("type")
                val rel = link.attr("rel")
                type.contains("epub", ignoreCase = true) ||
                    (rel.contains("acquisition") && link.attr("href").contains(".epub", ignoreCase = true))
            }

            if (epubLink != null) {
                val downloadUrl = resolveUrl(baseUrl, epubLink.attr("href"))
                val coverLink = links.firstOrNull { link ->
                    val rel = link.attr("rel")
                    rel.contains("image") || rel.contains("thumbnail")
                }?.attr("href")?.let { resolveUrl(baseUrl, it) }

                books.add(
                    RemoteCatalogBook(
                        id = "opds_${downloadUrl.hashCode()}",
                        title = title,
                        author = author,
                        description = summary,
                        category = "OPDS Catalog",
                        language = entry.selectFirst("dc\\:language, dcterms\\:language")?.text()?.trim() ?: "en",
                        sourceType = "OPDS",
                        sourceRef = downloadUrl,
                        coverUrl = coverLink,
                        downloadUrl = downloadUrl
                    )
                )
            } else {
                val navLink = links.firstOrNull { link ->
                    val type = link.attr("type")
                    type.contains("atom+xml") || link.attr("rel") == "subsection" || link.attr("href").isNotBlank()
                }
                if (navLink != null) {
                    navLinks.add(
                        OpdsNavLink(
                            title = title,
                            href = resolveUrl(baseUrl, navLink.attr("href")),
                            summary = summary
                        )
                    )
                }
            }
        }

        return OpdsFeedPage(title = feedTitle, navigationLinks = navLinks, books = books)
    }

    suspend fun downloadEpub(context: Context, item: RemoteCatalogBook): ParsedBook =
        withContext(Dispatchers.IO) {
            val url = item.downloadUrl ?: throw IllegalArgumentException("Không có link tải EPUB")
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Tải EPUB thất bại (${response.code})")
                val bytes = response.body?.bytes() ?: throw IllegalStateException("File EPUB rỗng")
                val temp = File.createTempFile("opds_", ".epub", context.cacheDir)
                try {
                    temp.writeBytes(bytes)
                    EpubParser.parseFile(temp, item.title)
                } finally {
                    temp.delete()
                }
            }
        }

    private fun resolveUrl(baseUrl: String, href: String): String {
        return runCatching { URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
    }
}
