package com.bookandpeace.app

import com.bookandpeace.app.data.ai.DeepSummaryEntry
import com.bookandpeace.app.data.ai.DeepSummaryRepository
import com.bookandpeace.app.data.ai.ScriptSegmentParser
import com.bookandpeace.app.data.ai.ShareToGeminiHelper
import com.bookandpeace.app.data.ai.VanVoPromptBuilder
import com.bookandpeace.app.data.local.StoryStyleMode
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.data.parser.TxtParser
import com.bookandpeace.app.data.remote.GutendexClient
import com.bookandpeace.app.data.remote.OpdsClient
import com.bookandpeace.app.data.remote.ResolvedDownloadUrl
import com.bookandpeace.app.data.remote.UrlBookDownloader
import com.bookandpeace.app.data.remote.WikisourceClient
import com.bookandpeace.app.data.tts.WavUtils
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookAndPeaceCoreTests {

    @Test
    fun epubParser_parsesValidArchiveMetadataAndChapters() {
        val tempEpub = File.createTempFile("test_book_", ".epub")
        try {
            ZipOutputStream(FileOutputStream(tempEpub)).use { zip ->
                fun putEntry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                putEntry(
                    "META-INF/container.xml",
                    """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """.trimIndent()
                )
                putEntry(
                    "OEBPS/content.opf",
                    """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Mắt Biếc (Bản Test)</dc:title>
                        <dc:creator>Tác Giả Mẫu</dc:creator>
                        <dc:language>vi</dc:language>
                      </metadata>
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="ncx">
                        <itemref idref="ch1"/>
                        <itemref idref="ch2"/>
                      </spine>
                    </package>
                    """.trimIndent()
                )
                putEntry(
                    "OEBPS/toc.ncx",
                    """
                    <ncx>
                      <navMap>
                        <navPoint id="n1"><navLabel><text>Chương 1: Làng Đo Đo</text></navLabel><content src="ch1.xhtml"/></navPoint>
                        <navPoint id="n2"><navLabel><text>Chương 2: Mùa Hạ Xanh</text></navLabel><content src="ch2.xhtml"/></navPoint>
                      </navMap>
                    </ncx>
                    """.trimIndent()
                )
                putEntry(
                    "OEBPS/ch1.xhtml",
                    "<html><body><h1>Mở đầu</h1><p>Đoạn văn thứ nhất dài đủ để trình phân tích nhận diện nội dung văn học tiếng Việt chuẩn xác.</p></body></html>"
                )
                putEntry(
                    "OEBPS/ch2.xhtml",
                    "<html><body><p>Đoạn văn chương hai với những hoài niệm tuổi thơ trong trẻo và sâu lắng bên đồi sim tím.</p></body></html>"
                )
            }

            val parsed = EpubParser.parseFile(tempEpub)
            assertEquals("Mắt Biếc (Bản Test)", parsed.title)
            assertEquals("Tác Giả Mẫu", parsed.author)
            assertEquals("vi", parsed.language)
            assertEquals(2, parsed.chapters.size)
            assertEquals("Chương 1: Làng Đo Đo", parsed.chapters[0].title)
            assertTrue(parsed.chapters[1].content.contains("đồi sim tím"))
        } finally {
            tempEpub.delete()
        }
    }

    @Test
    fun txtParser_splitsVietnameseChaptersCleanly() {
        val sample = """
            Chương 1: Khởi hành
            ${"Đây là nội dung chương một rất dài với nhiều câu chuyện tuổi thơ êm đềm. ".repeat(5)}

            Chương 2: Rẽ lối
            ${"Đây là nội dung chương hai tiếp nối hành trình trưởng thành của nhân vật. ".repeat(5)}
        """.trimIndent()

        val book = TxtParser.parseText(sample, "Truyện Thử Nghiệm", "Tác Giả")
        assertEquals(2, book.chapters.size)
        assertEquals("Chương 1: Khởi hành", book.chapters[0].title)
        assertEquals("Chương 2: Rẽ lối", book.chapters[1].title)
    }

    @Test
    fun scriptSegmentParser_parsesVanVoSectionsAndStripsMarkdown() {
        val rawScript = """
            === PHẦN: Hook mở đầu | GIỌNG: trầm ấm, bí ẩn ===
            **Cuộc đời này ngộ lắm**, có những người chỉ muốn làm người lương thiện nhưng xã hội lại không cho họ cơ hội...

            === PHẦN: Cao trào bi kịch | GIỌNG: chậm rãi, xúc động ===
            - Tiếng chửi vang lên giữa làng Vũ Đại.
            Và rồi bi kịch cũng ập đến như một lẽ tất yếu.
        """.trimIndent()

        val segments = ScriptSegmentParser.parseToSegments(rawScript)
        assertEquals(2, segments.size)
        assertEquals("Hook mở đầu", segments[0].sectionTitle)
        assertEquals("trầm ấm, bí ẩn", segments[0].toneHint)
        assertTrue(!segments[0].spokenText.contains("**"))
        assertEquals("Cao trào bi kịch", segments[1].sectionTitle)
        assertTrue(!segments[1].spokenText.startsWith("- "))
    }

    @Test
    fun wavUtils_writesValidWavHeaderAndDuration() {
        val tempWav = File.createTempFile("test_audio_", ".wav")
        try {
            // 24000 Hz * 2 bytes/sample * 1 channel = 48000 bytes for exactly 1 second
            val oneSecondPcm = ByteArray(48000)
            val durationMs = WavUtils.writePcmAsWav(oneSecondPcm, tempWav, sampleRate = 24000)
            assertEquals(1000L, durationMs)
            assertEquals(48044L, tempWav.length())
            assertEquals(1000L, WavUtils.estimateWavDurationMs(tempWav))
            assertEquals(24000, WavUtils.parseSampleRateFromMime("audio/L16;codec=pcm;rate=24000"))
        } finally {
            tempWav.delete()
        }
    }

    @Test
    fun opdsClient_parsesNavigationAndEpubEntries() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Test OPDS</title>
              <entry>
                <title>Kho Trinh Thám</title>
                <link rel="subsection" type="application/atom+xml;profile=opds-catalog" href="/detective.xml"/>
              </entry>
              <entry>
                <title>Sherlock Holmes</title>
                <author><name>Arthur Conan Doyle</name></author>
                <summary>Thám tử lừng danh phố Baker.</summary>
                <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/books/sherlock.epub"/>
              </entry>
            </feed>
        """.trimIndent()

        val client = OpdsClient(OkHttpClient())
        val page = client.parseOpdsXml(xml, "https://example.org/opds/root.xml")
        assertEquals("Test OPDS", page.title)
        assertEquals(1, page.navigationLinks.size)
        assertEquals("https://example.org/detective.xml", page.navigationLinks[0].href)
        assertEquals(1, page.books.size)
        assertEquals("Sherlock Holmes", page.books[0].title)
        assertEquals("https://example.org/books/sherlock.epub", page.books[0].downloadUrl)
    }

    @Test
    fun shareHelper_extractsApiKeyAndPromptBuildsCleanly() {
        val syntheticKey = "AIza" + "TestKey0123456789abcdefghijk"
        val extracted = ShareToGeminiHelper.extractGeminiApiKeyFromText("My key is $syntheticKey thanks")
        assertNotNull(extracted)
        assertEquals(syntheticKey, extracted)

        val prompt = VanVoPromptBuilder.buildSystemInstruction(StoryStyleMode.VAN_VO_STANDARD)
        assertTrue(prompt.contains("Văn Vở"))
        assertTrue(prompt.contains("=== PHẦN:"))
    }

    @Test
    fun deepSummariesJson_validAndParsesIntoZeroTokenAudioSegments() {
        val assetFile = File("src/main/assets/deep_summaries_vi.json")
        assertTrue("deep_summaries_vi.json must exist", assetFile.exists())
        val raw = assetFile.readText(Charsets.UTF_8)
        val entries = Json { ignoreUnknownKeys = true }.decodeFromString<List<DeepSummaryEntry>>(raw)
        assertTrue("Should contain at least 30 deep summaries", entries.size >= 30)

        // Verify Vietnamese, Nguyen Nhat Anh, Science, and World entries exist
        assertTrue(entries.any { it.id == "chi_pheo" })
        assertTrue(entries.any { it.id == "mat_biec" })
        assertTrue(entries.any { it.id == "nguon_goc_cac_loai" })
        assertTrue(entries.any { it.id == "thuyet_tuong_doi" })
        assertTrue(entries.any { it.id == "sherlock_holmes" })
        assertTrue(entries.any { it.id == "sapiens_luoc_su_loai_nguoi" })
        assertTrue(entries.any { it.id == "suoi_nguon_fountainhead" })

        // Verify normalization & zero-token script splits cleanly into 5 spoken segments
        val darwin = entries.first { it.id == "nguon_goc_cac_loai" }
        assertEquals("nguon goc cac loai", DeepSummaryRepository.normalize("Nguồn gốc các loài"))
        val segments = ScriptSegmentParser.parseToSegments(darwin.toZeroTokenAudioScript())
        assertEquals(5, segments.size)

        // Verify rich curated catalogs (all audited full-text books!)
        assertTrue(WikisourceClient.curatedVietnameseClassics.size >= 40)
        assertTrue(GutendexClient.curatedScienceBooks.size >= 15)
        assertTrue(GutendexClient.curatedWorldClassics.size >= 22)
        // Verify Sherlock Holmes points to verified full-text 1892 US edition
        val sherlock = GutendexClient.curatedWorldClassics.first { it.title.contains("Sherlock Holmes") }
        assertEquals("The Adventures of Sherlock Holmes (1892, US)", sherlock.sourceRef)
    }

    @Test
    fun urlBookDownloader_resolvesGoogleDriveDocsAndFoldersCleanly() {
        val driveFile = UrlBookDownloader.resolveUrl("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrSt/view?usp=sharing")
        assertTrue(driveFile is ResolvedDownloadUrl.DirectFile)
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1AbCdEfGhIjKlMnOpQrSt&export=download&confirm=t",
            (driveFile as ResolvedDownloadUrl.DirectFile).downloadUrl
        )

        val googleDoc = UrlBookDownloader.resolveUrl("https://docs.google.com/document/d/9XyZ12345/edit")
        assertTrue(googleDoc is ResolvedDownloadUrl.DirectFile)
        assertEquals(
            "https://docs.google.com/document/d/9XyZ12345/export?format=epub",
            (googleDoc as ResolvedDownloadUrl.DirectFile).downloadUrl
        )

        val folder = UrlBookDownloader.resolveUrl("https://drive.google.com/drive/folders/1FolderIdHere")
        assertTrue(folder is ResolvedDownloadUrl.FolderOrWebPage)
    }
}
