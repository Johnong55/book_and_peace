package com.bookandpeace.app.data.remote

import android.content.Context
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.ParsedBook
import com.bookandpeace.app.data.parser.ParsedChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File

data class RemoteCatalogBook(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val category: String,
    val language: String,
    val sourceType: String,
    val sourceRef: String,
    val coverUrl: String? = null,
    val downloadUrl: String? = null,
    val originalTitle: String? = null
)

class WikisourceClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val USER_AGENT = "BookAndPeace/1.0 (Personal Literature Reader; Android)"
        private const val WS_EXPORT_URL = "https://ws-export.wmcloud.org/"

        private fun apiUrlForLang(lang: String) = "https://$lang.wikisource.org/w/api.php"

        // 42 tác phẩm Văn học Việt Nam đã kiểm chứng tồn tại trực tiếp trên vi.wikisource.org
        val curatedVietnameseClassics: List<RemoteCatalogBook> = listOf(
            // Nam Cao (10 tác phẩm kiểm chứng đầy đủ nội dung)
            wsVi("Chí Phèo", "Nam Cao", "Bi kịch tha hóa và khát khao làm người lương thiện ở làng Vũ Đại.", "Văn học VN"),
            wsVi("Lão Hạc", "Nam Cao", "Người nông dân nghèo, cậu Vàng và lòng tự trọng nghẹn lòng.", "Văn học VN"),
            wsVi("Đời thừa", "Nam Cao", "Bi kịch của nhà văn Hộ giữa lý tưởng nghệ thuật và gánh nặng cơm áo.", "Văn học VN"),
            wsVi("Sống mòn", "Nam Cao", "Tiểu thuyết dài khắc họa nỗi ngột ngạt của kiếp người mòn mỏi trước 1945.", "Văn học VN"),
            wsVi("Một bữa no", "Nam Cao", "Truyện ngắn xót xa đến ám ảnh về cái đói của bà lão nghèo.", "Văn học VN"),
            wsVi("Trăng sáng", "Nam Cao", "Tuyên ngôn nghệ thuật vị nhân sinh qua góc nhìn nhà văn Điền.", "Văn học VN"),
            wsVi("Dì Hảo", "Nam Cao", "Số phận tủi cực, cam chịu của người phụ nữ nông thôn xưa.", "Văn học VN"),
            wsVi("Tư cách mõ", "Nam Cao", "Sự tha hóa của anh cu Lộ dưới sức ép khinh miệt của cả làng.", "Văn học VN"),
            wsVi("Mua nhà", "Nam Cao", "Góc nhìn chân thực, châm biếm nhẹ nhàng về cuộc sống trí thức nghèo.", "Văn học VN"),
            wsVi("Nghèo", "Nam Cao", "Bức tranh chân thực không tô hồng về cái nghèo cùng cực.", "Văn học VN"),

            // Thạch Lam (7)
            wsVi("Hai đứa trẻ", "Thạch Lam", "Phố huyện nghèo và chuyến tàu đêm chở theo ước mơ mong manh.", "Văn học VN"),
            wsVi("Gió lạnh đầu mùa", "Thạch Lam", "Tình thương trong trẻo của hai chị em Sơn và Lan với bé Hiên.", "Văn học VN"),
            wsVi("Nhà mẹ Lê", "Thạch Lam", "Cảnh đời lam lũ nhưng chan chứa tình mẫu tử nơi phố chợ Đoàn Thôn.", "Văn học VN"),
            wsVi("Dưới bóng hoàng lan", "Thạch Lam", "Chuyện tình nhẹ nhàng, trong veo dưới khu vườn cổ đầy hương hoa.", "Văn học VN"),
            wsVi("Nắng trong vườn", "Thạch Lam", "Tuyển tập truyện ngắn tinh tế giàu chất thơ của Thạch Lam.", "Văn học VN"),
            wsVi("Hà Nội băm sáu phố phường", "Thạch Lam", "Tập bút ký tuyệt đẹp về ẩm thực, phố cổ và hồn cốt Hà Nội xưa.", "Văn học VN"),
            wsVi("Cô hàng xén", "Thạch Lam", "Đời sống tảo tần hy sinh thầm lặng của cô Tâm bán hàng xén.", "Văn học VN"),

            // Ngô Tất Tố (3)
            wsVi("Tắt đèn", "Ngô Tất Tố", "Số phận chị Dậu giữa mùa sưu thuế đen tối làng Đông Xá.", "Văn học VN"),
            wsVi("Lều chõng", "Ngô Tất Tố", "Tiểu thuyết phóng sự đặc sắc về khoa cử nho học xưa.", "Văn học VN"),
            wsVi("Việc làng", "Ngô Tất Tố", "Phóng sự sắc sảo phơi bày các hủ tục nông thôn cũ.", "Văn học VN"),

            // Hồ Biểu Chánh (5)
            wsVi("Cha con nghĩa nặng", "Hồ Biểu Chánh", "Tiểu thuyết Nam Bộ xúc động sâu sắc về tình phụ tử và nhân quả.", "Văn học VN"),
            wsVi("Ngọn cỏ gió đùa", "Hồ Biểu Chánh", "Cuộc đời sóng gió hướng thiện lấy cảm hứng từ Những người khốn khổ.", "Văn học VN"),
            wsVi("Cay đắng mùi đời", "Hồ Biểu Chánh", "Bức tranh thế thái nhân tình thấm đẫm nghĩa khí miền Nam.", "Văn học VN"),
            wsVi("Chúa tàu Kim Quy", "Hồ Biểu Chánh", "Phiên bản Nam Bộ cực cuốn lấy cảm hứng từ Bá tước Monte Cristo.", "Văn học VN"),
            wsVi("Con nhà nghèo", "Hồ Biểu Chánh", "Số phận và nghị lực vươn lên giữa những bất công chốn miệt vườn.", "Văn học VN"),

            // Vũ Trọng Phụng, Khái Hưng, Nguyễn Công Hoan, Phạm Duy Tốn, Hoàng Ngọc Phách (7)
            wsVi("Làm đĩ", "Vũ Trọng Phụng", "Tiểu thuyết hiện thực tâm lý táo bạo về bi kịch giáo dục và định kiến.", "Văn học VN"),
            wsVi("Lục sì", "Vũ Trọng Phụng", "Phóng sự điều tra sắc lạnh về mặt tối xã hội thành thị xưa.", "Văn học VN"),
            wsVi("Nửa chừng xuân", "Khái Hưng", "Tình yêu tự do, phẩm giá và nghị lực của cô gái tên Mai.", "Văn học VN"),
            wsVi("Anh phải sống", "Khái Hưng", "Truyện ngắn nghẹn ngào về sự hy sinh cao cả của người vợ giữa dòng lũ.", "Văn học VN"),
            wsVi("Tinh thần thể dục", "Nguyễn Công Hoan", "Truyện trào phúng cười ra nước mắt về phong trào bắt dân đi xem bóng đá.", "Văn học VN"),
            wsVi("Sống chết mặc bay", "Phạm Duy Tốn", "Kiệt tác tương phản châm biếm quan phụ mẫu đánh tổ tôm giữa đêm vỡ đê.", "Văn học VN"),
            wsVi("Tố Tâm", "Hoàng Ngọc Phách", "Tiểu thuyết lãng mạn đầu tiên của văn học hiện đại Việt Nam.", "Văn học VN"),

            // Văn học Cổ điển & Lịch sử Việt Nam (9 tác phẩm đã kiểm chứng ấn bản đầy đủ)
            wsVi("Truyện Kiều", "Nguyễn Du", "3.254 câu thơ lục bát bất hủ về 15 năm lưu lạc của Thúy Kiều.", "Văn học VN"),
            wsVi("Truyện Lục Vân Tiên", "Nguyễn Đình Chiểu", "Bản anh hùng ca về đạo lý trọng nghĩa khinh tài đất Nam Bộ (2.082 câu).", "Văn học VN", pageRef = "Lục Vân Tiên (bản Quốc ngữ 2082 câu)"),
            wsVi("Truyền kỳ mạn lục", "Nguyễn Dữ", "Tuyển tập truyện kỳ ảo trung đại (Người con gái Nam Xương, Đền Tản Viên...).", "Văn học VN"),
            wsVi("Hoàng Lê nhất thống chí", "Ngô gia văn phái", "Tiểu thuyết lịch sử chương hồi hào hùng về thời Tây Sơn — Quang Trung.", "Văn học VN"),
            wsVi("Chinh phụ ngâm", "Đặng Trần Côn & Đoàn Thị Điểm", "Khúc ngâm tuyệt tác về nỗi nhớ nhung và khát vọng hòa bình.", "Văn học VN"),
            wsVi("Cung oán ngâm khúc", "Nguyễn Gia Thiều", "Tiếng lòng ai oán và triết lý nhân sinh sâu sắc chốn cung cấm.", "Văn học VN", pageRef = "Cung oán ngâm khúc (bản phổ biến)"),
            wsVi("Thượng kinh ký sự", "Hải Thượng Lãn Ông", "Ký sự quý giá về chuyến lên kinh chữa bệnh và y đức bậc danh y.", "Văn học VN"),
            wsVi("Vũ trung tùy bút", "Phạm Đình Hổ", "Tập tùy bút phong phú về phong tục, văn hóa và lịch sử Thăng Long.", "Văn học VN"),
            wsVi("Bình Ngô đại cáo", "Nguyễn Trãi", "Bản tuyên ngôn độc lập hào hùng bất hủ của dân tộc.", "Văn học VN")
        )

        private fun wsVi(
            title: String,
            author: String,
            desc: String,
            category: String,
            pageRef: String = title
        ) = RemoteCatalogBook(
            id = "ws_vi_${pageRef.hashCode()}",
            title = title,
            author = author,
            description = desc,
            category = category,
            language = "vi",
            sourceType = "WIKISOURCE",
            sourceRef = pageRef
        )
    }

    suspend fun searchWorks(query: String, lang: String = "vi"): List<RemoteCatalogBook> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() && lang == "vi") return@withContext curatedVietnameseClassics
            val url = apiUrlForLang(lang).toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "search")
                .addQueryParameter("srsearch", query)
                .addQueryParameter("srnamespace", "0")
                .addQueryParameter("srlimit", "20")
                .addQueryParameter("format", "json")
                .build()

            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Wikisource trả về mã lỗi ${response.code}")
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["query"]?.jsonObject?.get("search")?.jsonArray ?: return@withContext emptyList()
                results.mapNotNull { item ->
                    val obj = item.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (title.contains("/")) return@mapNotNull null
                    val snippet = obj["snippet"]?.jsonPrimitive?.content?.let { Jsoup.parse(it).text() }.orEmpty()
                    RemoteCatalogBook(
                        id = "ws_${lang}_${title.hashCode()}",
                        title = title,
                        author = if (lang == "vi") "Wikisource Tiếng Việt" else "Wikisource Thế giới",
                        description = snippet.ifBlank {
                            if (lang == "vi") "Tác phẩm tiếng Việt từ Wikisource"
                            else "Tác phẩm kinh điển thế giới (hỗ trợ Sách nói Kể Chuyện Sâu tiếng Việt)"
                        },
                        category = if (lang == "vi") "Văn học VN" else "Thế giới",
                        language = lang,
                        sourceType = if (lang == "vi") "WIKISOURCE" else "EN_WIKISOURCE",
                        sourceRef = title
                    )
                }
            }
        }

    suspend fun downloadWork(catalogBook: RemoteCatalogBook, lang: String = catalogBook.language.ifBlank { "vi" }): ParsedBook =
        withContext(Dispatchers.IO) {
            val pageTitle = catalogBook.sourceRef

            val exported = tryDownloadViaWsExport(pageTitle, lang, catalogBook)
            if (exported != null && exported.totalWords >= 1200) {
                return@withContext exported
            }

            val subpages = fetchSubpages(pageTitle, lang)
            val chapters = mutableListOf<ParsedChapter>()

            if (subpages.isNotEmpty()) {
                val limitedSubpages = subpages.take(35)
                for ((index, subTitle) in limitedSubpages.withIndex()) {
                    if (index > 0) delay(150L)
                    val text = fetchPageCleanText(subTitle, lang)
                    if (text.length >= 60) {
                        val cleanTitle = subTitle.removePrefix("$pageTitle/").ifBlank { "Phần ${index + 1}" }
                        chapters.add(ParsedChapter(title = cleanTitle, content = text))
                    }
                }
            }

            if (chapters.isEmpty()) {
                val mainText = fetchPageCleanText(pageTitle, lang)
                if (mainText.length < 40) {
                    if (exported != null && exported.chapters.isNotEmpty()) return@withContext exported
                    throw IllegalStateException("Trang \"$pageTitle\" trên Wikisource chưa có đủ văn bản để tải")
                }
                if (mainText.length > 14000) {
                    val paragraphs = mainText.split("\n\n")
                    val buf = StringBuilder()
                    var part = 1
                    for (p in paragraphs) {
                        if (buf.length + p.length > 10000 && buf.isNotEmpty()) {
                            chapters.add(ParsedChapter("Phần $part", buf.toString().trim()))
                            buf.clear()
                            part++
                        }
                        if (buf.isNotEmpty()) buf.append("\n\n")
                        buf.append(p)
                    }
                    if (buf.isNotEmpty()) {
                        chapters.add(ParsedChapter("Phần $part", buf.toString().trim()))
                    }
                } else {
                    chapters.add(ParsedChapter(catalogBook.title, mainText))
                }
            }

            ParsedBook(
                title = catalogBook.title,
                author = catalogBook.author,
                description = catalogBook.description,
                language = lang,
                chapters = chapters
            )
        }

    private fun tryDownloadViaWsExport(
        pageTitle: String,
        lang: String,
        catalogBook: RemoteCatalogBook
    ): ParsedBook? = runCatching {
        val url = WS_EXPORT_URL.toHttpUrl().newBuilder()
            .addQueryParameter("format", "epub-3")
            .addQueryParameter("lang", lang)
            .addQueryParameter("images", "false")
            .addQueryParameter("page", pageTitle.replace(' ', '_'))
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            if (bytes.size < 8000) return null
            val temp = File.createTempFile("wsexport_", ".epub", context.cacheDir)
            try {
                temp.writeBytes(bytes)
                val parsed = EpubParser.parseFile(temp, catalogBook.title)
                parsed.copy(
                    title = catalogBook.title,
                    author = catalogBook.author,
                    description = catalogBook.description.ifBlank { parsed.description },
                    language = lang
                )
            } finally {
                temp.delete()
            }
        }
    }.getOrNull()

    private fun fetchSubpages(parentTitle: String, lang: String): List<String> {
        val url = apiUrlForLang(lang).toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "prefixsearch")
            .addQueryParameter("pssearch", "$parentTitle/")
            .addQueryParameter("pslimit", "40")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val list = root["query"]?.jsonObject?.get("prefixsearch")?.jsonArray ?: return emptyList()
                list.mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.content }
                    .filter { it.startsWith("$parentTitle/") }
                    .sortedWith(naturalChapterComparator())
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchPageCleanText(pageTitle: String, lang: String): String {
        val url = apiUrlForLang(lang).toHttpUrl().newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", pageTitle)
            .addQueryParameter("prop", "text")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val html = root["parse"]?.jsonObject?.get("text")?.jsonObject?.get("*")?.jsonPrimitive?.content
                ?: return ""
            return cleanWikisourceHtml(html)
        }
    }

    internal fun cleanWikisourceHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select(
            ".ws-noexport, .noprint, .mw-editsection, .toc, table, .headertemplate, " +
                ".footertemplate, #headerContainer, sup.reference, .reference, style, script"
        ).remove()

        val blocks = doc.select("p, .poem, blockquote, h2, h3, dd")
        val lines = if (blocks.isNotEmpty()) {
            blocks.map { elem ->
                if (elem.hasClass("poem")) {
                    elem.html()
                        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                        .let { Jsoup.parse(it).wholeText() }
                        .lines()
                        .joinToString("\n") { it.trim() }
                        .trim()
                } else {
                    elem.text().replace("\u00A0", " ").trim()
                }
            }.filter { it.isNotBlank() }
        } else {
            listOf(doc.text().trim()).filter { it.isNotBlank() }
        }
        return lines.joinToString("\n\n")
    }

    private fun naturalChapterComparator(): Comparator<String> {
        val numRegex = Regex("\\d+")
        return Comparator { a, b ->
            val numA = numRegex.find(a)?.value?.toIntOrNull()
            val numB = numRegex.find(b)?.value?.toIntOrNull()
            when {
                numA != null && numB != null && numA != numB -> numA.compareTo(numB)
                else -> a.compareTo(b, ignoreCase = true)
            }
        }
    }
}
