package com.bookandpeace.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookandpeace.app.data.AppContainer
import com.bookandpeace.app.data.local.AppSettings
import com.bookandpeace.app.data.local.BookSourceType
import com.bookandpeace.app.data.local.ChapterEntity
import com.bookandpeace.app.data.local.ReaderThemeMode
import com.bookandpeace.app.data.parser.EpubParser
import com.bookandpeace.app.ui.theme.ReaderPalette
import com.bookandpeace.app.ui.theme.readerPaletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private sealed class ReaderBlock {
    data class Heading(val text: String) : ReaderBlock()
    data class Paragraph(val text: String) : ReaderBlock()
    data class InlineImage(val filePath: String) : ReaderBlock()
}

private fun parseChapterBlocks(chapter: ChapterEntity): List<ReaderBlock> {
    val result = mutableListOf<ReaderBlock>()
    result.add(ReaderBlock.Heading(chapter.title))
    for (raw in chapter.content.split("\n\n")) {
        val clean = raw.trim()
        if (clean.isBlank()) continue
        if (clean.startsWith(EpubParser.IMAGE_MARKER_PREFIX) && clean.endsWith(EpubParser.IMAGE_MARKER_SUFFIX)) {
            val path = clean.removePrefix(EpubParser.IMAGE_MARKER_PREFIX)
                .removeSuffix(EpubParser.IMAGE_MARKER_SUFFIX)
                .trim()
            if (path.isNotBlank()) result.add(ReaderBlock.InlineImage(path))
        } else {
            result.add(ReaderBlock.Paragraph(clean))
        }
    }
    return result
}

private fun paginateBlocks(
    blocks: List<ReaderBlock>,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    screenHeightDp: Int,
    screenWidthDp: Int,
    horizontalMarginDp: Float
): List<List<ReaderBlock>> {
    if (blocks.isEmpty()) return emptyList()
    val usableWidthDp = (screenWidthDp - horizontalMarginDp * 2f).coerceAtLeast(220f)
    // Reserve generous vertical room for status bar, nav bar, and quiet footer so text never clips
    val usableHeightDp = (screenHeightDp - 196).coerceAtLeast(320)
    val charsPerLine = (usableWidthDp / (fontSizeSp * 0.57f)).toInt().coerceIn(20, 62)
    val lineHeightDp = fontSizeSp * lineHeightMultiplier
    val maxLinesPerPage = (usableHeightDp / lineHeightDp).toInt().coerceIn(9, 30)
    val paragraphGapLines = (14f / lineHeightDp).coerceAtLeast(0.45f)

    val pages = mutableListOf<List<ReaderBlock>>()
    var currentPage = mutableListOf<ReaderBlock>()
    var usedLines = 0f

    for (block in blocks) {
        val blockLines: Float = when (block) {
            is ReaderBlock.Heading -> ((block.text.length / (charsPerLine * 0.78f)) + 2.2f).coerceAtLeast(2.4f)
            is ReaderBlock.InlineImage -> (maxLinesPerPage * 0.62f).coerceAtLeast(7f)
            is ReaderBlock.Paragraph -> ((block.text.length.toFloat() / charsPerLine) + 1f) + paragraphGapLines
        }

        if (block is ReaderBlock.Paragraph && blockLines > maxLinesPerPage) {
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedLines = 0f
            }
            val maxChars = (charsPerLine * (maxLinesPerPage - 2)).coerceAtLeast(260)
            val sentences = block.text.split(Regex("(?<=[.!?…])\\s+"))
            val chunk = StringBuilder()
            for (s in sentences) {
                if (chunk.length + s.length + 1 > maxChars && chunk.isNotEmpty()) {
                    pages.add(listOf(ReaderBlock.Paragraph(chunk.toString().trim())))
                    chunk.clear()
                }
                if (chunk.isNotEmpty()) chunk.append(' ')
                chunk.append(s)
            }
            if (chunk.isNotEmpty()) {
                currentPage.add(ReaderBlock.Paragraph(chunk.toString().trim()))
                usedLines = (chunk.length.toFloat() / charsPerLine) + 1.5f
            }
        } else {
            if (usedLines + blockLines > maxLinesPerPage && currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedLines = 0f
            }
            currentPage.add(block)
            usedLines += blockLines
        }
    }
    if (currentPage.isNotEmpty()) {
        pages.add(currentPage)
    }
    return pages
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenStoryteller: (Long) -> Unit
) {
    val book by container.bookRepository.bookDao.observeBook(bookId).collectAsState(initial = null)
    val chapterHeaders by container.bookRepository.chapterDao.observeChapterHeaders(bookId).collectAsState(initial = emptyList())
    val bookmarks by container.bookRepository.bookmarkDao.observeBookmarks(bookId).collectAsState(initial = emptyList())
    val settings by container.userPreferences.settingsFlow.collectAsState(initial = AppSettings())

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val config = LocalConfiguration.current

    val hasNativePdf = remember(book?.sourceType, book?.localFilePath) {
        book?.sourceType == BookSourceType.LOCAL_PDF &&
            !book?.localFilePath.isNullOrBlank() &&
            File(book!!.localFilePath!!).exists()
    }
    var useVisualPdfMode by remember(bookId, hasNativePdf) { mutableStateOf(hasNativePdf) }

    var currentChapterIndex by remember(bookId) { mutableIntStateOf(0) }
    var initializedProgress by remember(bookId) { mutableStateOf(false) }
    var currentChapter by remember(bookId) { mutableStateOf<ChapterEntity?>(null) }

    // Page tracking for both Text Paginated and PDF modes
    var textCurrentPage by remember(bookId) { mutableIntStateOf(0) }
    var textTotalPages by remember(bookId) { mutableIntStateOf(1) }
    var pendingPageTarget by remember(bookId) { mutableIntStateOf(0) } // -1 means jump to last page of chapter
    var pendingScrubPage by remember(bookId) { mutableStateOf<Int?>(null) }

    var pdfCurrentPage by remember(bookId) { mutableIntStateOf(0) }
    var pdfTotalPages by remember(bookId) { mutableIntStateOf(0) }
    var pdfZoomScale by remember(bookId) { mutableFloatStateOf(1f) }

    // Clean immersive default: start reading immediately without bars blocking the page
    var barsVisible by remember(bookId) { mutableStateOf(false) }
    var showTapGuide by remember(bookId) { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }

    // Auto-dismiss subtle tap-zone guide after 3 seconds
    LaunchedEffect(bookId) {
        delay(3200L)
        showTapGuide = false
    }

    LaunchedEffect(book?.id, chapterHeaders.size, hasNativePdf) {
        val loadedBook = book ?: return@LaunchedEffect
        if (!initializedProgress) {
            if (hasNativePdf) {
                pdfCurrentPage = loadedBook.currentScrollOffset.coerceAtLeast(0)
            } else {
                pendingPageTarget = loadedBook.currentScrollOffset.coerceAtLeast(0)
            }
            if (chapterHeaders.isNotEmpty()) {
                currentChapterIndex = loadedBook.currentChapterIndex.coerceIn(0, chapterHeaders.lastIndex)
            }
            initializedProgress = true
        }
    }

    // Load active chapter on Dispatchers.IO -> instant open, zero click lag
    LaunchedEffect(bookId, currentChapterIndex, chapterHeaders.size) {
        if (chapterHeaders.isNotEmpty()) {
            val ch = withContext(Dispatchers.IO) {
                container.bookRepository.chapterDao.getChapter(bookId, currentChapterIndex)
            }
            currentChapter = ch
        }
    }

    // Gentle 20-20-20 ophthalmology eye-rest reminder
    LaunchedEffect(settings.eyeRestReminderEnabled) {
        if (settings.eyeRestReminderEnabled) {
            while (true) {
                delay(20L * 60L * 1000L)
                snackbarHostState.showSnackbar("Quy tắc 20-20-20: Nghỉ mắt 20 giây nhìn xa 6m để giảm mỏi mắt nhé!")
            }
        }
    }

    val palette = readerPaletteFor(settings.readerTheme)

    // Native PDF engine & pager state lifted here so bottom slider & tap zones stay perfectly in sync
    val pdfEngine = if (useVisualPdfMode && hasNativePdf) {
        rememberPdfRenderEngine(book!!.localFilePath!!)
    } else null
    val pdfPageCount = pdfEngine?.pageCount ?: 1
    val pdfPagerState = rememberPagerState(
        initialPage = pdfCurrentPage.coerceIn(0, (pdfPageCount - 1).coerceAtLeast(0)),
        pageCount = { pdfPageCount.coerceAtLeast(1) }
    )

    // Sync initial PDF page when book progress loads
    LaunchedEffect(initializedProgress, pdfEngine) {
        if (initializedProgress && pdfEngine != null && pdfEngine.pageCount > 0) {
            val target = pdfCurrentPage.coerceIn(0, pdfEngine.pageCount - 1)
            if (pdfPagerState.currentPage != target) {
                pdfPagerState.scrollToPage(target)
            }
        }
    }

    // Parse & paginate active text chapter off the main thread
    var blocks by remember(currentChapter?.id) { mutableStateOf<List<ReaderBlock>>(emptyList()) }
    var pages by remember(
        currentChapter?.id,
        settings.readerFontSizeSp,
        settings.readerLineHeightMultiplier,
        settings.readerHorizontalMarginDp,
        settings.readerPaginatedMode
    ) {
        mutableStateOf<List<List<ReaderBlock>>>(emptyList())
    }

    LaunchedEffect(
        currentChapter?.id,
        settings.readerFontSizeSp,
        settings.readerLineHeightMultiplier,
        settings.readerHorizontalMarginDp,
        settings.readerPaginatedMode,
        useVisualPdfMode
    ) {
        val ch = currentChapter ?: return@LaunchedEffect
        if (useVisualPdfMode) return@LaunchedEffect
        val parsed = withContext(Dispatchers.Default) { parseChapterBlocks(ch) }
        blocks = parsed
        if (settings.readerPaginatedMode) {
            val paginated = withContext(Dispatchers.Default) {
                paginateBlocks(
                    blocks = parsed,
                    fontSizeSp = settings.readerFontSizeSp,
                    lineHeightMultiplier = settings.readerLineHeightMultiplier,
                    screenHeightDp = config.screenHeightDp,
                    screenWidthDp = config.screenWidthDp,
                    horizontalMarginDp = settings.readerHorizontalMarginDp
                )
            }
            pages = paginated
            textTotalPages = paginated.size.coerceAtLeast(1)
        }
    }

    fun goToPrevChapter(openAtLastPage: Boolean) {
        if (currentChapterIndex > 0) {
            pendingPageTarget = if (openAtLastPage) -1 else 0
            currentChapterIndex--
        } else {
            scope.launch { snackbarHostState.showSnackbar("Bạn đang ở trang đầu tiên của sách") }
        }
    }

    fun goToNextChapter() {
        if (currentChapterIndex < chapterHeaders.lastIndex) {
            pendingPageTarget = 0
            currentChapterIndex++
        } else {
            scope.launch { snackbarHostState.showSnackbar("Bạn đã đọc đến trang cuối của sách") }
        }
    }

    fun saveTextProgress(chapterIdx: Int, pageIdx: Int) {
        scope.launch(Dispatchers.IO) {
            container.bookRepository.bookDao.updateReadingProgress(
                bookId = bookId,
                chapterIndex = chapterIdx,
                scrollOffset = pageIdx,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    val progressFraction = remember(
        useVisualPdfMode,
        pdfCurrentPage,
        pdfTotalPages,
        currentChapterIndex,
        chapterHeaders.size,
        textCurrentPage,
        textTotalPages,
        settings.readerPaginatedMode
    ) {
        if (useVisualPdfMode && pdfTotalPages > 0) {
            (pdfCurrentPage + 1).toFloat() / pdfTotalPages.toFloat()
        } else if (chapterHeaders.isNotEmpty()) {
            val chapterShare = 1f / chapterHeaders.size.toFloat()
            val intraChapter = if (settings.readerPaginatedMode && textTotalPages > 0) {
                (textCurrentPage + 1).toFloat() / textTotalPages.toFloat()
            } else 1f
            ((currentChapterIndex + intraChapter) * chapterShare).coerceIn(0f, 1f)
        } else 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // =========================================================================
        // LAYER 1: FULL-SCREEN READING CANVAS (Never jumps when menus toggle!)
        // =========================================================================
        if (useVisualPdfMode && hasNativePdf) {
            PdfBookPagerCanvas(
                engine = pdfEngine,
                pdfFilePath = book!!.localFilePath!!,
                pagerState = pdfPagerState,
                themeMode = settings.readerTheme,
                palette = palette,
                zoomScale = pdfZoomScale,
                onTapLeft = {
                    showTapGuide = false
                    if (barsVisible) barsVisible = false
                    if (pdfPagerState.currentPage > 0) {
                        scope.launch { pdfPagerState.flipToPageSmooth(pdfPagerState.currentPage - 1) }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Bạn đang ở trang đầu tiên") }
                    }
                },
                onTapCenter = {
                    showTapGuide = false
                    barsVisible = !barsVisible
                },
                onTapRight = {
                    showTapGuide = false
                    if (barsVisible) barsVisible = false
                    if (pdfPagerState.currentPage < pdfPagerState.pageCount - 1) {
                        scope.launch { pdfPagerState.flipToPageSmooth(pdfPagerState.currentPage + 1) }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Bạn đã đến trang cuối") }
                    }
                },
                onPageChanged = { pageIdx, total ->
                    pdfCurrentPage = pageIdx
                    pdfTotalPages = total
                    scope.launch(Dispatchers.IO) {
                        val approxChapter = if (total > 0 && chapterHeaders.isNotEmpty()) {
                            (pageIdx * chapterHeaders.size / total).coerceIn(0, chapterHeaders.lastIndex)
                        } else 0
                        container.bookRepository.bookDao.updateReadingProgress(
                            bookId = bookId,
                            chapterIndex = approxChapter,
                            scrollOffset = pageIdx,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            )
        } else {
            val loadedChapter = currentChapter
            if (loadedChapter == null || blocks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = palette.accent)
                }
            } else if (settings.readerPaginatedMode && pages.isNotEmpty()) {
                PaginatedBookCanvas(
                    chapterId = loadedChapter.id,
                    pages = pages,
                    pendingPageTarget = pendingPageTarget,
                    pendingScrubPage = pendingScrubPage,
                    settings = settings,
                    palette = palette,
                    onPageResolved = { resolvedPage ->
                        textCurrentPage = resolvedPage
                        textTotalPages = pages.size
                        pendingPageTarget = resolvedPage
                        pendingScrubPage = null
                        saveTextProgress(currentChapterIndex, resolvedPage)
                    },
                    onTapLeft = { pagerState ->
                        showTapGuide = false
                        if (barsVisible) barsVisible = false
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.flipToPageSmooth(pagerState.currentPage - 1) }
                        } else {
                            goToPrevChapter(openAtLastPage = true)
                        }
                    },
                    onTapCenter = {
                        showTapGuide = false
                        barsVisible = !barsVisible
                    },
                    onTapRight = { pagerState ->
                        showTapGuide = false
                        if (barsVisible) barsVisible = false
                        if (pagerState.currentPage < pages.lastIndex) {
                            scope.launch { pagerState.flipToPageSmooth(pagerState.currentPage + 1) }
                        } else {
                            goToNextChapter()
                        }
                    }
                )
            } else {
                ScrollBookCanvas(
                    chapterId = loadedChapter.id,
                    blocks = blocks,
                    settings = settings,
                    palette = palette,
                    onTapLeft = {
                        showTapGuide = false
                        if (barsVisible) barsVisible = false
                    },
                    onTapCenter = {
                        showTapGuide = false
                        barsVisible = !barsVisible
                    },
                    onTapRight = {
                        showTapGuide = false
                        if (barsVisible) barsVisible = false
                    },
                    onBoundaryPrevChapter = { goToPrevChapter(openAtLastPage = false) },
                    onBoundaryNextChapter = { goToNextChapter() }
                )
            }
        }

        // =========================================================================
        // LAYER 2: QUIET E-READER FOOTER (Always crisp & unobtrusive when reading)
        // =========================================================================
        if (!barsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = settings.readerHorizontalMarginDp.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftLabel = if (useVisualPdfMode) {
                        book?.title.orEmpty()
                    } else {
                        currentChapter?.title ?: "Chương ${currentChapterIndex + 1}"
                    }
                    val rightLabel = if (useVisualPdfMode && pdfTotalPages > 0) {
                        "Trang ${pdfCurrentPage + 1}/$pdfTotalPages  •  ${(progressFraction * 100).roundToInt()}%"
                    } else if (settings.readerPaginatedMode) {
                        "Trang ${textCurrentPage + 1}/$textTotalPages  •  ${(progressFraction * 100).roundToInt()}%"
                    } else {
                        "Chương ${currentChapterIndex + 1}/${chapterHeaders.size.coerceAtLeast(1)}"
                    }
                    Text(
                        text = leftLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Text(
                        text = rightLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText.copy(alpha = 0.78f)
                    )
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = palette.accent.copy(alpha = 0.55f),
                    trackColor = palette.outline.copy(alpha = 0.28f)
                )
            }
        }

        // =========================================================================
        // SUBTLE ONE-TIME TAP-ZONE GUIDE PILL (Non-blocking, auto-fades out)
        // =========================================================================
        AnimatedVisibility(
            visible = showTapGuide && !barsVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(450)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 38.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = palette.barSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, palette.outline.copy(alpha = 0.5f)),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = "◀ Chạm trái: Lật lại   •   Giữa: Menu   •   Chạm phải: Lật tiếp ▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // =========================================================================
        // LAYER 3: CLEAN FLOATING TOP BAR OVERLAY
        // =========================================================================
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = palette.barSurface.copy(alpha = 0.97f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = palette.text
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = book?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (useVisualPdfMode && pdfTotalPages > 0) {
                                "Trang gốc PDF • ${pdfCurrentPage + 1}/$pdfTotalPages"
                            } else if (chapterHeaders.isNotEmpty()) {
                                "Chương ${currentChapterIndex + 1}/${chapterHeaders.size}" +
                                    if (settings.readerPaginatedMode) " • Trang ${textCurrentPage + 1}/$textTotalPages" else ""
                            } else ""
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (hasNativePdf) {
                            Surface(
                                color = palette.accentContainer,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        useVisualPdfMode = !useVisualPdfMode
                                        pdfZoomScale = 1f
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (useVisualPdfMode) Icons.Default.Description else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = palette.onAccentContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (useVisualPdfMode) "Bản chữ" else "Bản PDF",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onAccentContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        if (!useVisualPdfMode) {
                            IconButton(
                                onClick = {
                                    currentChapter?.let { ch ->
                                        scope.launch {
                                            container.bookRepository.saveBookmark(
                                                bookId = bookId,
                                                chapterIndex = currentChapterIndex,
                                                chapterTitle = ch.title,
                                                excerpt = ch.content.take(180)
                                            )
                                            snackbarHostState.showSnackbar("Đã đánh dấu: ${ch.title}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = "Đánh dấu trang",
                                    tint = palette.text
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = palette.outline.copy(alpha = 0.35f))
                }
            }
        }

        // =========================================================================
        // LAYER 4: THUMB-FRIENDLY FLOATING BOTTOM CONTROL PANEL
        // =========================================================================
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.barSurface.copy(alpha = 0.98f),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                border = BorderStroke(1.dp, palette.outline.copy(alpha = 0.35f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (useVisualPdfMode && pdfTotalPages > 0) {
                        // PDF Page Scrubber + Zoom cycle pill
                        var sliderVal by remember(pdfCurrentPage) { mutableIntStateOf(pdfCurrentPage) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trang ${sliderVal + 1} / $pdfTotalPages (${(progressFraction * 100).roundToInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.text
                            )
                            Surface(
                                color = palette.accentContainer,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        pdfZoomScale = when {
                                            pdfZoomScale < 1.3f -> 1.6f
                                            pdfZoomScale < 1.9f -> 2.15f
                                            else -> 1.0f
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ZoomIn,
                                        contentDescription = "Phóng to trang PDF",
                                        tint = palette.onAccentContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Thu phóng ${String.format("%.1fx", pdfZoomScale)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.onAccentContainer
                                    )
                                }
                            }
                        }
                        if (pdfTotalPages > 1) {
                            Slider(
                                value = sliderVal.toFloat(),
                                onValueChange = { sliderVal = it.roundToInt().coerceIn(0, pdfTotalPages - 1) },
                                onValueChangeFinished = {
                                    scope.launch { pdfPagerState.scrollToPage(sliderVal) }
                                },
                                valueRange = 0f..(pdfTotalPages - 1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent
                                )
                            )
                        }
                    } else {
                        // Text Mode: Quick chapter jump + page scrubber
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = currentChapterIndex > 0,
                                onClick = { goToPrevChapter(openAtLastPage = false) }
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "Chương trước",
                                    tint = if (currentChapterIndex > 0) palette.text else palette.secondaryText.copy(alpha = 0.35f)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentChapter?.title ?: "Chương ${currentChapterIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.readerPaginatedMode) {
                                        "Trang ${textCurrentPage + 1}/$textTotalPages  •  Chương ${currentChapterIndex + 1}/${chapterHeaders.size.coerceAtLeast(1)}  •  ${(progressFraction * 100).roundToInt()}%"
                                    } else {
                                        "Chương ${currentChapterIndex + 1}/${chapterHeaders.size.coerceAtLeast(1)}  •  ${(progressFraction * 100).roundToInt()}%"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.secondaryText
                                )
                            }
                            IconButton(
                                enabled = currentChapterIndex < chapterHeaders.lastIndex,
                                onClick = { goToNextChapter() }
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Chương sau",
                                    tint = if (currentChapterIndex < chapterHeaders.lastIndex) palette.text else palette.secondaryText.copy(alpha = 0.35f)
                                )
                            }
                        }

                        if (settings.readerPaginatedMode && textTotalPages > 1) {
                            var scrubVal by remember(textCurrentPage, currentChapterIndex) {
                                mutableIntStateOf(textCurrentPage)
                            }
                            Slider(
                                value = scrubVal.toFloat(),
                                onValueChange = { scrubVal = it.roundToInt().coerceIn(0, textTotalPages - 1) },
                                onValueChangeFinished = { pendingScrubPage = scrubVal },
                                valueRange = 0f..(textTotalPages - 1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = palette.outline.copy(alpha = 0.25f))

                    // 4 Big Thumb-Friendly Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!useVisualPdfMode) {
                            ReaderQuickAction(
                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                label = "Mục lục",
                                tint = palette.text,
                                onClick = { showTocSheet = true }
                            )
                        }
                        ReaderQuickAction(
                            icon = Icons.Default.RemoveRedEye,
                            label = "Chữ & Mắt",
                            tint = palette.text,
                            onClick = { showDisplaySheet = true }
                        )
                        ReaderQuickAction(
                            icon = if (settings.readerTheme.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (settings.readerTheme.isDark) "Ban ngày" else "Ban đêm",
                            tint = palette.text,
                            onClick = {
                                val nextTheme = if (settings.readerTheme.isDark) {
                                    ReaderThemeMode.SEPIA
                                } else {
                                    ReaderThemeMode.CHARCOAL_DARK
                                }
                                scope.launch { container.userPreferences.setReaderTheme(nextTheme) }
                            }
                        )
                        ReaderQuickAction(
                            icon = Icons.Default.AutoAwesome,
                            label = "Kể Chuyện",
                            tint = palette.secondaryAccent,
                            onClick = { onOpenStoryteller(bookId) }
                        )
                    }
                }
            }
        }

        // Snackbar Host floating nicely above bottom bar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (barsVisible) 180.dp else 36.dp)
        )
    }

    // =========================================================================
    // TABLE OF CONTENTS & BOOKMARKS SHEET
    // =========================================================================
    if (showTocSheet) {
        ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Mục lục (${chapterHeaders.size} chương)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(chapterHeaders, key = { _, ch -> ch.id }) { idx, ch ->
                        val isCurrent = idx == currentChapterIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable {
                                    pendingPageTarget = 0
                                    currentChapterIndex = idx
                                    showTocSheet = false
                                    barsVisible = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                    if (bookmarks.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(14.dp))
                            Text("Đã đánh dấu (${bookmarks.size})", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                        }
                        itemsIndexed(bookmarks, key = { _, bm -> bm.id }) { _, bm ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pendingPageTarget = 0
                                        currentChapterIndex = bm.chapterIndex.coerceIn(0, chapterHeaders.lastIndex)
                                        showTocSheet = false
                                        barsVisible = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 10.dp)
                            ) {
                                Text(bm.chapterTitle, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    bm.excerpt,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // =========================================================================
    // CLEAN DISPLAY & EYE-COMFORT SHEET (Tactile steppers + chips)
    // =========================================================================
    if (showDisplaySheet) {
        ModalBottomSheet(onDismissRequest = { showDisplaySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Giao diện đọc & Bảo vệ mắt", style = MaterialTheme.typography.titleLarge)

                // 1. Paper Color Swatches
                EyeCareColorSwatchRow(
                    selected = settings.readerTheme,
                    onSelect = { mode -> scope.launch { container.userPreferences.setReaderTheme(mode) } }
                )

                if (!useVisualPdfMode) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // 2. Easy A- / A+ Font Size Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cỡ chữ", style = MaterialTheme.typography.titleSmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val next = (settings.readerFontSizeSp - 1.5f).coerceAtLeast(14f)
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            next, settings.readerLineHeightMultiplier, settings.readerUseSerif, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text("A-", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                text = "${settings.readerFontSizeSp.roundToInt()} pt",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(48.dp),
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = {
                                    val next = (settings.readerFontSizeSp + 1.5f).coerceAtMost(27f)
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            next, settings.readerLineHeightMultiplier, settings.readerUseSerif, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text("A+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }

                    // 3. Reading Mode (Paginated vs Vertical Scroll)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Kiểu lật trang (chạm trái/phải luôn hoạt động)", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = settings.readerPaginatedMode,
                                onClick = {
                                    scope.launch { container.userPreferences.setReaderPaginatedMode(true) }
                                },
                                label = { Text("Lật trang ngang (Kindle)") }
                            )
                            FilterChip(
                                selected = !settings.readerPaginatedMode,
                                onClick = {
                                    scope.launch { container.userPreferences.setReaderPaginatedMode(false) }
                                },
                                label = { Text("Cuộn dọc liên tục") }
                            )
                        }
                    }

                    // 4. Font Style & Spacing Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kiểu chữ", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.readerUseSerif,
                                onClick = {
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            settings.readerFontSizeSp, settings.readerLineHeightMultiplier, true, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                label = { Text("Serif (Sách in)", fontFamily = FontFamily.Serif) }
                            )
                            FilterChip(
                                selected = !settings.readerUseSerif,
                                onClick = {
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            settings.readerFontSizeSp, settings.readerLineHeightMultiplier, false, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                label = { Text("Sans (Gọn)") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Độ thoáng dòng", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("Gọn", 1.5f, 18f),
                                Triple("Vừa", 1.75f, 24f),
                                Triple("Thoáng", 2.0f, 30f)
                            ).forEach { (label, lh, margin) ->
                                val selected = kotlin.math.abs(settings.readerLineHeightMultiplier - lh) < 0.12f
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        scope.launch {
                                            container.userPreferences.setReaderTypography(
                                                settings.readerFontSizeSp, lh, settings.readerUseSerif, margin
                                            )
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // 5. Blue-Light Filter & Night Dimmer
                Column {
                    Text(
                        "Lọc ánh sáng xanh ấm: ${(settings.blueLightWarmth * 100 / 0.42f).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.blueLightWarmth,
                        onValueChange = { w ->
                            scope.launch { container.userPreferences.setEyeComfortFilters(w, settings.screenDimmer) }
                        },
                        valueRange = 0f..0.42f
                    )
                }

                Column {
                    Text(
                        "Giảm độ chói ban đêm: ${(settings.screenDimmer * 100 / 0.48f).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.screenDimmer,
                        onValueChange = { d ->
                            scope.launch { container.userPreferences.setEyeComfortFilters(settings.blueLightWarmth, d) }
                        },
                        valueRange = 0f..0.48f
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ReaderQuickAction(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PaginatedBookCanvas(
    chapterId: Long,
    pages: List<List<ReaderBlock>>,
    pendingPageTarget: Int,
    pendingScrubPage: Int?,
    settings: AppSettings,
    palette: ReaderPalette,
    onPageResolved: (Int) -> Unit,
    onTapLeft: (PagerState) -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: (PagerState) -> Unit
) {
    val fontFamily = if (settings.readerUseSerif) FontFamily.Serif else FontFamily.SansSerif
    val initialIdx = remember(chapterId, pages.size, pendingPageTarget) {
        if (pendingPageTarget < 0) {
            (pages.size - 1).coerceAtLeast(0)
        } else {
            pendingPageTarget.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialIdx,
        pageCount = { pages.size.coerceAtLeast(1) }
    )

    LaunchedEffect(chapterId) {
        val target = if (pendingPageTarget < 0) {
            (pages.size - 1).coerceAtLeast(0)
        } else {
            pendingPageTarget.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
        onPageResolved(pagerState.currentPage)
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageResolved(pagerState.currentPage)
    }

    LaunchedEffect(pendingScrubPage) {
        val target = pendingScrubPage ?: return@LaunchedEffect
        pagerState.scrollToPage(target.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 28.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageBlocks = pages.getOrNull(pageIndex).orEmpty()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(chapterId, pageIndex, pages.size) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < width * 0.35f -> onTapLeft(pagerState)
                                offset.x > width * 0.65f -> onTapRight(pagerState)
                                else -> onTapCenter()
                            }
                        }
                    }
                    .padding(horizontal = settings.readerHorizontalMarginDp.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    pageBlocks.forEach { block ->
                        ReaderBlockView(
                            block = block,
                            fontFamily = fontFamily,
                            settings = settings,
                            palette = palette
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollBookCanvas(
    chapterId: Long,
    blocks: List<ReaderBlock>,
    settings: AppSettings,
    palette: ReaderPalette,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
    onBoundaryPrevChapter: () -> Unit,
    onBoundaryNextChapter: () -> Unit
) {
    val fontFamily = if (settings.readerUseSerif) FontFamily.Serif else FontFamily.SansSerif
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(chapterId) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(chapterId) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val pageStepPx = size.height * 0.80f
                    when {
                        offset.x < width * 0.35f -> {
                            onTapLeft()
                            if (!listState.canScrollBackward) {
                                onBoundaryPrevChapter()
                            } else {
                                scope.launch { listState.animateScrollBy(-pageStepPx, tween(220)) }
                            }
                        }

                        offset.x > width * 0.65f -> {
                            onTapRight()
                            if (!listState.canScrollForward) {
                                onBoundaryNextChapter()
                            } else {
                                scope.launch { listState.animateScrollBy(pageStepPx, tween(220)) }
                            }
                        }

                        else -> onTapCenter()
                    }
                }
            },
        contentPadding = PaddingValues(
            start = settings.readerHorizontalMarginDp.dp,
            end = settings.readerHorizontalMarginDp.dp,
            top = 18.dp,
            bottom = 44.dp
        )
    ) {
        itemsIndexed(
            items = blocks,
            key = { idx, _ -> "${chapterId}_$idx" },
            contentType = { _, block -> block::class.simpleName }
        ) { _, block ->
            ReaderBlockView(
                block = block,
                fontFamily = fontFamily,
                settings = settings,
                palette = palette
            )
        }
    }
}

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    fontFamily: FontFamily,
    settings: AppSettings,
    palette: ReaderPalette
) {
    when (block) {
        is ReaderBlock.Heading -> {
            Text(
                text = block.text,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = (settings.readerFontSizeSp + 3.5f).sp,
                lineHeight = (settings.readerFontSizeSp + 12f).sp,
                color = palette.text
            )
            Spacer(Modifier.height(18.dp))
        }

        is ReaderBlock.Paragraph -> {
            Text(
                text = block.text,
                fontFamily = fontFamily,
                fontSize = settings.readerFontSizeSp.sp,
                lineHeight = (settings.readerFontSizeSp * settings.readerLineHeightMultiplier).sp,
                letterSpacing = 0.25.sp,
                color = palette.text
            )
            Spacer(Modifier.height(14.dp))
        }

        is ReaderBlock.InlineImage -> {
            val file = remember(block.filePath) { File(block.filePath) }
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Hình minh họa sách",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun EyeCareColorSwatchRow(
    selected: ReaderThemeMode,
    onSelect: (ReaderThemeMode) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ReaderThemeMode.entries, key = { it.name }) { mode ->
            val pal = readerPaletteFor(mode)
            val isSelected = mode == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onSelect(mode) }
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(pal.background)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else pal.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = pal.text, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Aa", color = pal.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
