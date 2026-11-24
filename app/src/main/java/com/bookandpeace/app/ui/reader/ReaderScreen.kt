package com.bookandpeace.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val usableHeightDp = (screenHeightDp - 140).coerceAtLeast(360)
    val charsPerLine = (usableWidthDp / (fontSizeSp * 0.54f)).toInt().coerceIn(22, 68)
    val lineHeightDp = fontSizeSp * lineHeightMultiplier
    val maxLinesPerPage = (usableHeightDp / lineHeightDp).toInt().coerceIn(10, 32)

    val pages = mutableListOf<List<ReaderBlock>>()
    var currentPage = mutableListOf<ReaderBlock>()
    var usedLines = 0

    for (block in blocks) {
        val blockLines = when (block) {
            is ReaderBlock.Heading -> ((block.text.length / (charsPerLine * 0.8f)).toInt() + 2).coerceAtLeast(2)
            is ReaderBlock.InlineImage -> (maxLinesPerPage * 0.65f).toInt().coerceAtLeast(8)
            is ReaderBlock.Paragraph -> ((block.text.length / charsPerLine) + 1) + 1
        }

        if (block is ReaderBlock.Paragraph && blockLines > maxLinesPerPage) {
            // Split extra-long paragraph across pages gracefully
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedLines = 0
            }
            val maxChars = (charsPerLine * (maxLinesPerPage - 2)).coerceAtLeast(300)
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
                usedLines = ((chunk.length / charsPerLine) + 2)
            }
        } else {
            if (usedLines + blockLines > maxLinesPerPage && currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                usedLines = 0
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
    // Lightweight headers ONLY (never loads megabytes of all chapters into Compose state!)
    val chapterHeaders by container.bookRepository.chapterDao.observeChapterHeaders(bookId).collectAsState(initial = emptyList())
    val bookmarks by container.bookRepository.bookmarkDao.observeBookmarks(bookId).collectAsState(initial = emptyList())
    val settings by container.userPreferences.settingsFlow.collectAsState(initial = AppSettings())

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasNativePdf = remember(book?.sourceType, book?.localFilePath) {
        book?.sourceType == BookSourceType.LOCAL_PDF &&
            !book?.localFilePath.isNullOrBlank() &&
            File(book!!.localFilePath!!).exists()
    }
    var useVisualPdfMode by remember(bookId, hasNativePdf) { mutableStateOf(hasNativePdf) }

    var currentChapterIndex by remember(bookId) { mutableIntStateOf(0) }
    var initializedProgress by remember(bookId) { mutableStateOf(false) }
    var currentChapter by remember(bookId) { mutableStateOf<ChapterEntity?>(null) }
    var pdfCurrentPage by remember(bookId) { mutableIntStateOf(0) }
    var pdfTotalPages by remember(bookId) { mutableIntStateOf(0) }

    var barsVisible by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showEyeComfortSheet by remember { mutableStateOf(false) }

    LaunchedEffect(book?.id, chapterHeaders.size, hasNativePdf) {
        val loadedBook = book ?: return@LaunchedEffect
        if (!initializedProgress) {
            if (hasNativePdf) {
                pdfCurrentPage = loadedBook.currentScrollOffset.coerceAtLeast(0)
            }
            if (chapterHeaders.isNotEmpty()) {
                currentChapterIndex = loadedBook.currentChapterIndex.coerceIn(0, chapterHeaders.lastIndex)
            }
            initializedProgress = true
        }
    }

    // Load ONLY the active chapter asynchronously on Dispatchers.IO -> instant open, zero click lag!
    LaunchedEffect(bookId, currentChapterIndex, chapterHeaders.size) {
        if (chapterHeaders.isNotEmpty()) {
            val ch = withContext(Dispatchers.IO) {
                container.bookRepository.chapterDao.getChapter(bookId, currentChapterIndex)
            }
            currentChapter = ch
            if (!useVisualPdfMode) {
                container.bookRepository.bookDao.updateReadingProgress(
                    bookId = bookId,
                    chapterIndex = currentChapterIndex,
                    scrollOffset = 0,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    // Gentle 20-20-20 ophthalmology eye-rest reminder
    LaunchedEffect(settings.eyeRestReminderEnabled) {
        if (settings.eyeRestReminderEnabled) {
            while (true) {
                delay(20L * 60L * 1000L)
                snackbarHostState.showSnackbar("Quy tắc 20-20-20: Nghỉ mắt 20 giây nhìn xa 6m để giảm điều tiết mắt nhé!")
            }
        }
    }

    val palette = readerPaletteFor(settings.readerTheme)

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = barsVisible, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.barSurface,
                        titleContentColor = palette.text,
                        navigationIconContentColor = palette.text,
                        actionIconContentColor = palette.text
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = book?.title.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = if (useVisualPdfMode && pdfTotalPages > 0) {
                                "Trang gốc PDF • ${pdfCurrentPage + 1}/$pdfTotalPages"
                            } else if (chapterHeaders.isNotEmpty()) {
                                "Chương ${currentChapterIndex + 1}/${chapterHeaders.size}"
                            } else ""
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.labelSmall, color = palette.secondaryText)
                            }
                        }
                    },
                    actions = {
                        if (hasNativePdf) {
                            IconButton(onClick = { useVisualPdfMode = !useVisualPdfMode }) {
                                Icon(
                                    if (useVisualPdfMode) Icons.Default.Description else Icons.Default.Image,
                                    contentDescription = if (useVisualPdfMode) "Chuyển chế độ Văn bản" else "Xem trang gốc PDF có ảnh"
                                )
                            }
                        }
                        IconButton(onClick = { onOpenStoryteller(bookId) }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Kể Chuyện Sâu", tint = palette.secondaryAccent)
                        }
                        IconButton(onClick = { showEyeComfortSheet = true }) {
                            Icon(Icons.Default.RemoveRedEye, contentDescription = "Bảo vệ mắt & Máy đọc sách")
                        }
                        if (!useVisualPdfMode) {
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Đánh dấu")
                            }
                            IconButton(onClick = { showTocSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Mục lục")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!useVisualPdfMode) {
                AnimatedVisibility(visible = barsVisible, enter = fadeIn(), exit = fadeOut()) {
                    Surface(color = palette.barSurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = currentChapterIndex > 0,
                                onClick = { currentChapterIndex-- }
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = palette.text)
                                Text("Chương trước", color = palette.text)
                            }
                            Text(
                                text = "${if (chapterHeaders.isEmpty()) 0 else ((currentChapterIndex + 1) * 100 / chapterHeaders.size)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryText
                            )
                            TextButton(
                                enabled = currentChapterIndex < chapterHeaders.lastIndex,
                                onClick = { currentChapterIndex++ }
                            ) {
                                Text("Chương sau", color = palette.text)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.text)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (useVisualPdfMode && hasNativePdf) {
            PdfBookPager(
                pdfFilePath = book!!.localFilePath!!,
                initialPage = pdfCurrentPage,
                themeMode = settings.readerTheme,
                palette = palette,
                barsVisible = barsVisible,
                onToggleBars = { barsVisible = !barsVisible },
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
                },
                modifier = Modifier.padding(top = if (barsVisible) padding.calculateTopPadding() else 0.dp)
            )
        } else {
            val loadedChapter = currentChapter
            if (loadedChapter == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = palette.accent)
                }
            } else {
                TextAndIllustrationReader(
                    chapter = loadedChapter,
                    chapterIndex = currentChapterIndex,
                    totalChapters = chapterHeaders.size,
                    settings = settings,
                    palette = palette,
                    barsVisible = barsVisible,
                    contentPadding = padding,
                    onToggleBars = { barsVisible = !barsVisible },
                    onPrevChapter = { if (currentChapterIndex > 0) currentChapterIndex-- },
                    onNextChapter = { if (currentChapterIndex < chapterHeaders.lastIndex) currentChapterIndex++ }
                )
            }
        }
    }

    if (showTocSheet) {
        ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Mục lục (${chapterHeaders.size} chương)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    itemsIndexed(chapterHeaders, key = { _, ch -> ch.id }) { idx, ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentChapterIndex = idx
                                    showTocSheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (idx == currentChapterIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (idx == currentChapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider()
                    }
                    if (bookmarks.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Đã đánh dấu (${bookmarks.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        itemsIndexed(bookmarks, key = { _, bm -> bm.id }) { _, bm ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentChapterIndex = bm.chapterIndex.coerceIn(0, chapterHeaders.lastIndex)
                                        showTocSheet = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
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
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showEyeComfortSheet) {
        ModalBottomSheet(onDismissRequest = { showEyeComfortSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Máy đọc sách & Bảo vệ mắt", style = MaterialTheme.typography.titleLarge)

                EyeCareColorSwatchRow(
                    selected = settings.readerTheme,
                    onSelect = { mode -> scope.launch { container.userPreferences.setReaderTheme(mode) } }
                )

                if (!useVisualPdfMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lật trang ngang (kiểu Kindle)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Vuốt ngang hoặc chạm mép trái/phải để lật trang",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.readerPaginatedMode,
                            onCheckedChange = { paged ->
                                scope.launch { container.userPreferences.setReaderPaginatedMode(paged) }
                            }
                        )
                    }
                }

                Text(
                    "Lọc ánh sáng xanh ấm: ${(settings.blueLightWarmth * 100 / 0.42f).toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = settings.blueLightWarmth,
                    onValueChange = { w ->
                        scope.launch { container.userPreferences.setEyeComfortFilters(w, settings.screenDimmer) }
                    },
                    valueRange = 0f..0.42f
                )

                Text(
                    "Giảm độ chói (Đọc tối): ${(settings.screenDimmer * 100 / 0.48f).toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = settings.screenDimmer,
                    onValueChange = { d ->
                        scope.launch { container.userPreferences.setEyeComfortFilters(settings.blueLightWarmth, d) }
                    },
                    valueRange = 0f..0.48f
                )

                if (!useVisualPdfMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cỡ chữ: ${settings.readerFontSizeSp.toInt()}", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = settings.readerFontSizeSp,
                                onValueChange = { size ->
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            size, settings.readerLineHeightMultiplier, settings.readerUseSerif, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                valueRange = 15f..26f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Giãn dòng: ${String.format("%.2f", settings.readerLineHeightMultiplier)}", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = settings.readerLineHeightMultiplier,
                                onValueChange = { lh ->
                                    scope.launch {
                                        container.userPreferences.setReaderTypography(
                                            settings.readerFontSizeSp, lh, settings.readerUseSerif, settings.readerHorizontalMarginDp
                                        )
                                    }
                                },
                                valueRange = 1.45f..2.15f
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Serif văn học", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.readerUseSerif,
                            onCheckedChange = { serif ->
                                scope.launch {
                                    container.userPreferences.setReaderTypography(
                                        settings.readerFontSizeSp, settings.readerLineHeightMultiplier, serif, settings.readerHorizontalMarginDp
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun TextAndIllustrationReader(
    chapter: ChapterEntity,
    chapterIndex: Int,
    totalChapters: Int,
    settings: AppSettings,
    palette: ReaderPalette,
    barsVisible: Boolean,
    contentPadding: PaddingValues,
    onToggleBars: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val fontFamily = if (settings.readerUseSerif) FontFamily.Serif else FontFamily.SansSerif

    // Parse & paginate on background Default dispatcher -> 0ms main-thread freeze!
    var blocks by remember(chapter.id) { mutableStateOf<List<ReaderBlock>>(emptyList()) }
    var pages by remember(chapter.id, settings.readerFontSizeSp, settings.readerLineHeightMultiplier, settings.readerPaginatedMode) {
        mutableStateOf<List<List<ReaderBlock>>>(emptyList())
    }

    LaunchedEffect(chapter.id, settings.readerFontSizeSp, settings.readerLineHeightMultiplier, settings.readerPaginatedMode) {
        val parsedBlocks = withContext(Dispatchers.Default) { parseChapterBlocks(chapter) }
        blocks = parsedBlocks
        if (settings.readerPaginatedMode) {
            pages = withContext(Dispatchers.Default) {
                paginateBlocks(
                    blocks = parsedBlocks,
                    fontSizeSp = settings.readerFontSizeSp,
                    lineHeightMultiplier = settings.readerLineHeightMultiplier,
                    screenHeightDp = config.screenHeightDp,
                    screenWidthDp = config.screenWidthDp,
                    horizontalMarginDp = settings.readerHorizontalMarginDp
                )
            }
        }
    }

    if (blocks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = palette.accent)
        }
        return
    }

    if (settings.readerPaginatedMode && pages.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
        LaunchedEffect(chapter.id) {
            pagerState.scrollToPage(0)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(
                    top = if (barsVisible) contentPadding.calculateTopPadding() else 26.dp,
                    bottom = if (barsVisible) contentPadding.calculateBottomPadding() else 14.dp
                )
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageBlocks = pages[pageIndex]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageIndex, pages.size, chapterIndex) {
                            detectTapGestures { offset ->
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                when {
                                    offset.x < width * 0.24f -> {
                                        if (pageIndex > 0) {
                                            scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                                        } else {
                                            onPrevChapter()
                                        }
                                    }

                                    offset.x > width * 0.76f -> {
                                        if (pageIndex < pages.lastIndex) {
                                            scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                        } else {
                                            onNextChapter()
                                        }
                                    }

                                    else -> onToggleBars()
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

            // Crisp Kindle-style page footer
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = settings.readerHorizontalMarginDp.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Chương ${chapterIndex + 1}/$totalChapters",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText.copy(alpha = 0.75f)
                )
                Text(
                    text = "Trang ${pagerState.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText.copy(alpha = 0.75f)
                )
            }
        }
    } else {
        // Virtualized LazyColumn scroll mode — butter smooth 60-120 FPS even on giant chapters!
        val listState = rememberLazyListState()
        LaunchedEffect(chapter.id) { listState.scrollToItem(0) }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .pointerInput(Unit) { detectTapGestures { onToggleBars() } },
            contentPadding = PaddingValues(
                start = settings.readerHorizontalMarginDp.dp,
                end = settings.readerHorizontalMarginDp.dp,
                top = (if (barsVisible) contentPadding.calculateTopPadding() else 28.dp) + 14.dp,
                bottom = (if (barsVisible) contentPadding.calculateBottomPadding() else 24.dp) + 32.dp
            )
        ) {
            itemsIndexed(
                items = blocks,
                key = { idx, _ -> "${chapter.id}_$idx" },
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
