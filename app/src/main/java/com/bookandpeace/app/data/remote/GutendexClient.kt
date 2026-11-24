package com.bookandpeace.app.data.remote

import android.content.Context
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.ParsedBook
import com.bookandpeace.app.data.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class GutendexClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val wikisourceClient: WikisourceClient
) {
    companion object {
        private const val ARCHIVE_SEARCH_URL = "https://archive.org/advancedsearch.php"
        private const val USER_AGENT = "BookAndPeace/1.0 (Personal Literature Reader; Android)"

        // 15 kiệt tác Khoa học & Tri thức — TÊN TIẾNG VIỆT 100%, ĐÃ KIỂM CHỨNG ĐỦ TOÀN VĂN EPUB
        val curatedScienceBooks: List<RemoteCatalogBook> = listOf(
            sciBook("Nguồn gốc các loài", "Charles Darwin", "Khai sinh thuyết tiến hóa bằng chọn lọc tự nhiên — thay đổi lịch sử sinh học nhân loại.", "On the Origin of Species (1859)"),
            sciBook("Thuyết tương đối: Lý thuyết hẹp và rộng", "Albert Einstein", "Chính Einstein giải thích thuyết tương đối, giãn nở thời gian và không-thời gian cong cực dễ hiểu.", "Relativity (1931)"),
            sciBook("Các nguyên lý toán học (Principia)", "Isaac Newton", "Ba định luật chuyển động và Định luật Vạn vật hấp dẫn đặt nền móng vật lý hiện đại.", "The Mathematical Principles of Natural Philosophy (1729)"),
            sciBook("Lịch sử Hóa học ngọn nến", "Michael Faraday", "6 bài giảng huyền thoại giải mã cả thế giới vật lý và hóa học từ một ngọn nến đang cháy.", "The Chemical History of a Candle"),
            sciBook("Hai ngành khoa học mới", "Galileo Galilei", "Nền tảng khoa học vật liệu và định luật rơi tự do viết bằng giọng đối thoại cực hóm hỉnh.", "Dialogues Concerning Two New Sciences"),
            sciBook("Chuyến hải trình tàu Beagle", "Charles Darwin", "Nhật ký 5 năm vòng quanh thế giới đầy kịch tính dẫn tới khám phá thuyết tiến hóa.", "Journal of researches into the natural history and geology of the countries visited during the voyage of H.M.S. Beagle round the world"),
            sciBook("Nguồn gốc loài người", "Charles Darwin", "Mở rộng thuyết tiến hóa sang lịch sử loài người và chọn lọc giới tính.", "The Descent of Man (Darwin)"),
            sciBook("Diễn giải giấc mơ", "Sigmund Freud", "Tác phẩm khai sinh Phân tâm học, mở cánh cửa khám phá thế giới vô thức.", "The Interpretation of Dreams (Freud)"),
            sciBook("Phương pháp luận", "René Descartes", "Tuyên ngôn tư duy khoa học duy lý và câu nói bất hủ 'Tôi tư duy, vậy tôi tồn tại'.", "Discourse on the Method"),
            sciBook("Của cải các quốc gia", "Adam Smith", "Nền tảng kinh tế học hiện đại về phân công lao động và bàn tay vô hình.", "The Wealth of Nations"),
            sciBook("Bàn về Tự do", "John Stuart Mill", "Kiệt tác triết học khoa học xã hội về tự do tư tưởng và tranh luận cởi mở.", "On Liberty"),
            sciBook("Binh pháp Tôn Tử", "Tôn Tử", "13 thiên kinh điển về khoa học chiến lược, tâm lý và nghệ thuật thắng không cần đánh.", "The Art of War (Sun)"),
            sciBook("Cỗ máy thời gian", "H. G. Wells", "Tiểu thuyết khoa học viễn tưởng kinh điển về hành trình du hành tới tương lai xa xôi.", "The Time Machine (Heinemann text)"),
            sciBook("Chiến tranh giữa các thế giới", "H. G. Wells", "Tác phẩm khoa học viễn tưởng bất hủ về cuộc đổ bộ của sinh vật sao Hỏa.", "The War of the Worlds (1898)"),
            RemoteCatalogBook(
                id = "sci_vi_khkct",
                title = "Khoa học không có tội",
                author = "Tư liệu Khoa học Việt Nam",
                description = "Tiểu luận tiếng Việt sắc sảo bàn về giá trị đạo đức và mục đích chân chính của khoa học.",
                category = "Khoa học",
                language = "vi",
                sourceType = "WIKISOURCE",
                sourceRef = "Khoa học không có tội"
            )
        )

        // 22 kiệt tác Văn học Thế giới — TÊN TIẾNG VIỆT 100%, ĐÃ KIỂM CHỨNG ĐỦ TOÀN VĂN 100%
        val curatedWorldClassics: List<RemoteCatalogBook> = listOf(
            worldBook("Những cuộc phiêu lưu của Sherlock Holmes", "Arthur Conan Doyle", "Trọn bộ 12 vụ án phá án kinh điển của thám tử lừng danh số 221B phố Baker.", "The Adventures of Sherlock Holmes (1892, US)"),
            worldBook("Con chó săn của dòng họ Baskerville", "Arthur Conan Doyle", "Vụ án trinh thám rùng rợn và nổi tiếng nhất về lời nguyền đồng hoang Dartmoor.", "The Hound of the Baskervilles (Newnes, 1902)"),
            worldBook("Bá tước Dracula", "Bram Stoker", "Kiệt tác kinh dị Gothic kinh điển viết dưới dạng nhật ký và thư từ.", "Dracula"),
            worldBook("Bác sĩ Jekyll và ông Hyde", "Robert Louis Stevenson", "Câu chuyện tâm lý kinh dị về hai mặt thiện – ác trong cùng một con người.", "The Strange Case of Dr. Jekyll and Mr. Hyde"),
            worldBook("Frankenstein", "Mary Shelley", "Bi kịch của nhà khoa học tạo ra sự sống và nỗi cô đơn của sinh vật bị ruồng bỏ.", "Frankenstein, or the Modern Prometheus (Revised Edition, 1831)"),
            worldBook("Gatsby Vĩ Đại", "F. Scott Fitzgerald", "Giấc mơ Mỹ hào nhoáng, tình yêu ám ảnh và ngọn đèn xanh bên vịnh.", "The Great Gatsby (1925)"),
            worldBook("Kiêu hãnh và Định kiến", "Jane Austen", "Bản tình ca hóm hỉnh, sắc sảo bậc nhất văn học Anh thế kỷ 19.", "Pride and Prejudice (1813)"),
            worldBook("Bức chân dung Dorian Gray", "Oscar Wilde", "Chàng trai bán linh hồn giữ mãi tuổi trẻ trong khi bức tranh thay anh mục rữa.", "The Picture of Dorian Gray (1891)"),
            worldBook("Đồi gió hú", "Emily Brontë", "Mối tình cuồng si và thù hận dữ dội giữa Heathcliff và Catherine.", "Wuthering Heights (1st edition)"),
            worldBook("Jane Eyre", "Charlotte Brontë", "Hành trình trưởng thành đầy kiêu hãnh và bản lĩnh của cô gái mồ côi Jane.", "Jane Eyre (1st edition)"),
            worldBook("Tiếng gọi nơi hoang dã", "Jack London", "Hành trình sinh tồn mãnh liệt của chú chó Buck giữa miền băng tuyết Yukon.", "The Call of the Wild (London)"),
            worldBook("Nanh trắng", "Jack London", "Hành trình từ con sói hoang dã dần cảm nhận tình thương nơi thế giới con người.", "White Fang"),
            worldBook("Những cuộc phiêu lưu của Tom Sawyer", "Mark Twain", "Tuổi thơ tinh nghịch, phóng khoáng bên bờ sông Mississippi.", "The Adventures of Tom Sawyer"),
            worldBook("80 ngày vòng quanh thế giới", "Jules Verne", "Cuộc đánh cược ngoạn mục đi vòng quanh địa cầu chỉ trong 80 ngày.", "Around the World in Eighty Days (Towle)"),
            worldBook("Alice ở xứ sở diệu kỳ", "Lewis Carroll", "Thế giới kỳ ảo đầy trí tưởng tượng bất tận sau cú ngã xuống hang thỏ.", "Alice's Adventures in Wonderland (1866)"),
            worldBook("Giáng sinh yêu thương", "Charles Dickens", "Đêm Giáng sinh thức tỉnh trái tim sắt đá của lão hà tiện Scrooge.", "A Christmas Carol (Dickens, 1843)"),
            worldBook("Oliver Twist", "Charles Dickens", "Cuộc phiêu lưu cảm động của cậu bé mồ côi giữa London thế kỷ 19.", "Oliver Twist (1838)"),
            worldBook("Ba người lính ngự lâm", "Alexandre Dumas", "Tinh thần hào hiệp bất hủ 'Một người vì mọi người, mọi người vì một người'.", "The Three Musketeers"),
            worldBook("Những người khốn khổ", "Victor Hugo", "Tượng đài nhân đạo bất hủ về hành trình hoàn lương của Jean Valjean.", "Les Misérables"),
            worldBook("Tội ác và Hình phạt", "Fyodor Dostoevsky", "Cuộc đấu trí nghẹt thở giữa lý trí kiêu ngạo và lương tâm của Raskolnikov.", "Crime and Punishment"),
            worldBook("Anna Karenina", "Leo Tolstoy", "Bi kịch tình yêu và bức tranh xã hội quý tộc Nga vĩ đại.", "Anna Karenina (Garnett)"),
            worldBook("Chiến tranh và Hòa bình", "Leo Tolstoy", "Đại sử thi vĩ đại về chiến tranh, số phận và ý nghĩa cuộc sống.", "War and Peace (Tolstoy)")
        )

        private fun sciBook(titleVi: String, author: String, desc: String, wikiPage: String) = RemoteCatalogBook(
            id = "sci_${wikiPage.hashCode()}",
            title = titleVi,
            author = author,
            description = desc,
            category = "Khoa học",
            language = "en",
            sourceType = "EN_WIKISOURCE",
            sourceRef = wikiPage,
            originalTitle = wikiPage
        )

        private fun worldBook(titleVi: String, author: String, desc: String, wikiPage: String) = RemoteCatalogBook(
            id = "world_${wikiPage.hashCode()}",
            title = titleVi,
            author = author,
            description = desc,
            category = "Thế giới",
            language = "en",
            sourceType = "EN_WIKISOURCE",
            sourceRef = wikiPage,
            originalTitle = wikiPage
        )
    }

    suspend fun searchAllRemote(query: String): List<RemoteCatalogBook> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val allCurated = curatedScienceBooks + WikisourceClient.curatedVietnameseClassics + curatedWorldClassics
        val localMatches = allCurated.filter {
            it.title.contains(clean, ignoreCase = true) ||
                it.author.contains(clean, ignoreCase = true) ||
                (it.originalTitle?.contains(clean, ignoreCase = true) == true) ||
                it.description.contains(clean, ignoreCase = true)
        }

        val viRemote = runCatching { wikisourceClient.searchWorks(clean, lang = "vi").take(12) }.getOrDefault(emptyList())
        val enRemote = runCatching { wikisourceClient.searchWorks(clean, lang = "en").take(8) }.getOrDefault(emptyList())
        val iaRemote = runCatching { searchInternetArchive(clean).take(6) }.getOrDefault(emptyList())

        (localMatches + viRemote + enRemote + iaRemote).distinctBy { it.title.lowercase() }
    }

    private fun searchInternetArchive(query: String): List<RemoteCatalogBook> {
        val cleanQuery = query.replace("\"", "").trim()
        val lucene = "($cleanQuery) AND mediatype:texts AND format:EPUB AND NOT access-restricted-item:true"
        val url = ARCHIVE_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", lucene)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator")
            .addQueryParameter("fl[]", "description")
            .addQueryParameter("rows", "8")
            .addQueryParameter("output", "json")
            .build()

        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val docs = root["response"]?.jsonObject?.get("docs")?.jsonArray ?: return emptyList()
            return docs.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["identifier"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = extractStringField(obj["title"]) ?: return@mapNotNull null
                val author = extractStringField(obj["creator"]) ?: "Internet Archive"
                val desc = extractStringField(obj["description"])?.take(200) ?: "Sách mở EPUB từ Internet Archive"
                RemoteCatalogBook(
                    id = "ia_$id",
                    title = title,
                    author = author,
                    description = desc,
                    category = "Thế giới",
                    language = "en",
                    sourceType = "ARCHIVE_ORG",
                    sourceRef = id
                )
            }
        }
    }

    private fun extractStringField(element: kotlinx.serialization.json.JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.content.trim().takeIf { it.isNotBlank() }
        is JsonArray -> element.firstOrNull()?.let { (it as? JsonPrimitive)?.content?.trim() }
        else -> null
    }

    suspend fun downloadBook(item: RemoteCatalogBook): ParsedBook = withContext(Dispatchers.IO) {
        when (item.sourceType) {
            "EN_WIKISOURCE" -> wikisourceClient.downloadWork(item, lang = "en")
            "WIKISOURCE" -> wikisourceClient.downloadWork(item, lang = "vi")
            "ARCHIVE_ORG" -> downloadFromInternetArchive(item)
            else -> {
                val url = item.downloadUrl ?: throw IllegalArgumentException("Không có link tải cho sách này")
                downloadDirectUrl(url, item)
            }
        }
    }

    private fun downloadFromInternetArchive(item: RemoteCatalogBook): ParsedBook {
        val identifier = item.sourceRef
        val metaReq = Request.Builder()
            .url("https://archive.org/metadata/$identifier/files")
            .header("User-Agent", USER_AGENT)
            .build()

        val epubFileName = httpClient.newCall(metaReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Internet Archive trả lỗi ${resp.code}")
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val files = root["result"]?.jsonArray.orEmpty()
            files.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                .firstOrNull { it.endsWith(".epub", ignoreCase = true) }
                ?: files.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                    .firstOrNull { it.endsWith("_djvu.txt", ignoreCase = true) || it.endsWith(".txt", ignoreCase = true) }
                ?: throw IllegalStateException("Không tìm thấy file EPUB/TXT khả dụng")
        }

        val encodedName = java.net.URLEncoder.encode(epubFileName, "UTF-8").replace("+", "%20")
        return downloadDirectUrl("https://archive.org/download/$identifier/$encodedName", item)
    }

    private fun downloadDirectUrl(url: String, item: RemoteCatalogBook): ParsedBook {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Tải sách thất bại (HTTP ${response.code})")
            val bytes = response.body?.bytes() ?: throw IllegalStateException("Dữ liệu sách trống")
            val isEpub = url.contains(".epub", ignoreCase = true) ||
                response.header("Content-Type").orEmpty().contains("epub", ignoreCase = true)

            return if (isEpub) {
                val temp = File.createTempFile("world_", ".epub", context.cacheDir)
                try {
                    temp.writeBytes(bytes)
                    val parsed = EpubParser.parseFile(temp, item.title)
                    parsed.copy(
                        title = item.title,
                        author = item.author,
                        description = parsed.description.ifBlank { item.description }
                    )
                } finally {
                    temp.delete()
                }
            } else {
                TxtParser.parseText(
                    rawText = bytes.toString(Charsets.UTF_8),
                    title = item.title,
                    author = item.author
                )
            }
        }
    }
}
