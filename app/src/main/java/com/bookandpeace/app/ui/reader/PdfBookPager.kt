package com.bookandpeace.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.data.local.ReaderThemeMode
import com.bookandpeace.app.ui.theme.ReaderPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private class PdfRenderEngine(pdfFile: File) {
    private val mutex = Mutex()
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)
    private val cache = object : LruCache<Int, Bitmap>(6) {}

    val pageCount: Int get() = renderer.pageCount

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index !in 0 until pageCount) return@withContext null
        cache.get(index)?.let { return@withContext it }

        mutex.withLock {
            cache.get(index)?.let { return@withLock it }
            runCatching {
                renderer.openPage(index).use { page ->
                    val safeWidth = targetWidthPx.coerceIn(720, 1800)
                    val ratio = safeWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val height = (page.height * ratio).toInt().coerceIn(720, 3000)
                    val bitmap = Bitmap.createBitmap(safeWidth, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bitmap)
                    bitmap
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { cache.evictAll() }
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
fun PdfBookPager(
    pdfFilePath: String,
    initialPage: Int,
    themeMode: ReaderThemeMode,
    palette: ReaderPalette,
    barsVisible: Boolean,
    onToggleBars: () -> Unit,
    onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val renderWidthPx = remember(screenWidthPx) { (screenWidthPx * 1.35f).toInt().coerceIn(900, 1600) }

    val engineState = remember(pdfFilePath) {
        runCatching { PdfRenderEngine(File(pdfFilePath)) }.getOrNull()
    }

    DisposableEffect(engineState) {
        onDispose { engineState?.close() }
    }

    if (engineState == null || engineState.pageCount <= 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Không mở được file PDF gốc. Hãy chuyển sang chế độ Văn bản ở góc trên.",
                color = palette.secondaryText,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val totalPages = engineState.pageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, totalPages - 1),
        pageCount = { totalPages }
    )

    // Pre-fetch neighbor pages in background so swiping is instantaneous
    LaunchedEffect(pagerState.currentPage) {
        val current = pagerState.currentPage
        onPageChanged(current, totalPages)
        launch { engineState.renderPage(current + 1, renderWidthPx) }
        launch { engineState.renderPage(current - 1, renderWidthPx) }
    }

    // Eye-comfort filter: turns stark white PDF pages into warm Sepia/Green paper, or soft dark mode at night
    val pageColorFilter = remember(themeMode, palette.background) {
        if (themeMode.isDark) {
            // Soft low-glare inversion (avoids pure black/white halation)
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.78f, 0f, 0f, 0f, 222f,
                        0f, -0.78f, 0f, 0f, 218f,
                        0f, 0f, -0.78f, 0f, 210f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        } else {
            ColorFilter.tint(palette.background, BlendMode.Multiply)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { pageIdx ->
            var bitmap by remember(pageIdx, pdfFilePath) { mutableStateOf<Bitmap?>(null) }
            var zoomScale by remember(pageIdx) { mutableFloatStateOf(1f) }

            LaunchedEffect(pageIdx) {
                bitmap = engineState.renderPage(pageIdx, renderWidthPx)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .pointerInput(pageIdx, totalPages) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoomScale = if (zoomScale > 1.1f) 1f else 1.85f
                            },
                            onTap = { offset ->
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                when {
                                    offset.x < width * 0.22f && pageIdx > 0 -> {
                                        scope.launch { pagerState.animateScrollToPage(pageIdx - 1) }
                                    }

                                    offset.x > width * 0.78f && pageIdx < totalPages - 1 -> {
                                        scope.launch { pagerState.animateScrollToPage(pageIdx + 1) }
                                    }

                                    else -> onToggleBars()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Trang ${pageIdx + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = palette.accent
                    )
                }
            }
        }

        // Subtle e-reader page footer when bars hidden, or interactive scrubber when bars visible
        if (barsVisible) {
            Surface(
                color = palette.barSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                var sliderPage by remember(pagerState.currentPage) { mutableIntStateOf(pagerState.currentPage) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (totalPages > 1) {
                        Slider(
                            value = sliderPage.toFloat(),
                            onValueChange = { sliderPage = it.toInt().coerceIn(0, totalPages - 1) },
                            onValueChangeFinished = {
                                scope.launch { pagerState.scrollToPage(sliderPage) }
                            },
                            valueRange = 0f..(totalPages - 1).toFloat()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Trang ${pagerState.currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.text
                        )
                        Text(
                            "${((pagerState.currentPage + 1) * 100 / totalPages)}% • Chạm đúp để phóng to",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryText
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Trang ${pagerState.currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }
    }
}
