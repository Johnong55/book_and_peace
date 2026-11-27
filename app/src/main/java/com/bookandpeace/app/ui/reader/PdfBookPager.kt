package com.bookandpeace.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.bookandpeace.app.data.local.PageTurnStyle
import com.bookandpeace.app.data.local.ReaderThemeMode
import com.bookandpeace.app.ui.theme.ReaderPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal class PdfRenderEngine(pdfFile: File) {
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
internal fun rememberPdfRenderEngine(pdfFilePath: String): PdfRenderEngine? {
    val engine = remember(pdfFilePath) {
        runCatching { PdfRenderEngine(File(pdfFilePath)) }.getOrNull()
    }
    DisposableEffect(engine) {
        onDispose { engine?.close() }
    }
    return engine
}

/**
 * Full-screen native PDF book pager with:
 * - Instant zero-delay Left/Right tap-to-turn zones (35% left / 30% center / 35% right)
 * - Realistic 3D physical book page-turning animation + dynamic shadow & spine lighting
 * - Interactive tactile paper-lift & paper-settle haptics
 */
@Composable
internal fun PdfBookPagerCanvas(
    engine: PdfRenderEngine?,
    pdfFilePath: String,
    pagerState: PagerState,
    themeMode: ReaderThemeMode,
    pageTurnStyle: PageTurnStyle,
    palette: ReaderPalette,
    haptics: BookHaptics,
    zoomScale: Float,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
    onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val renderWidthPx = remember(screenWidthPx, zoomScale) {
        val factor = if (zoomScale > 1.2f) 1.75f else 1.35f
        (screenWidthPx * factor).toInt().coerceIn(900, 1800)
    }

    if (engine == null || engine.pageCount <= 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Không mở được file PDF gốc. Hãy chuyển sang chế độ Bản chữ ở thanh trên.",
                color = palette.secondaryText,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val totalPages = engine.pageCount

    // Interactive drag lift & settle haptics
    ObserveBookPagerHaptics(pagerState = pagerState, haptics = haptics)

    // Pre-fetch neighbor pages in background so swiping & 3D page turns are instantaneous
    LaunchedEffect(pagerState.currentPage, totalPages) {
        val current = pagerState.currentPage
        onPageChanged(current, totalPages)
        launch { engine.renderPage(current + 1, renderWidthPx) }
        launch { engine.renderPage(current - 1, renderWidthPx) }
    }

    // Eye-comfort filter: turns stark white PDF pages into warm Sepia/Green paper, or soft dark mode at night
    val pageColorFilter = remember(themeMode, palette.background) {
        if (themeMode.isDark) {
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

    val isZoomed = zoomScale > 1.05f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 26.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { pageIdx ->
            var bitmap by remember(pageIdx, pdfFilePath) { mutableStateOf<Bitmap?>(null) }
            var panX by remember(pageIdx, zoomScale) { mutableFloatStateOf(0f) }
            var panY by remember(pageIdx, zoomScale) { mutableFloatStateOf(0f) }

            LaunchedEffect(pageIdx, renderWidthPx) {
                bitmap = engine.renderPage(pageIdx, renderWidthPx)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bookPageFlipEffect(
                        pagerState = pagerState,
                        pageIndex = pageIdx,
                        style = if (isZoomed) PageTurnStyle.SMOOTH_SLIDE else pageTurnStyle,
                        palette = palette,
                        isDark = themeMode.isDark
                    )
                    .background(palette.background)
                    .pointerInput(pageIdx, totalPages, isZoomed) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < width * 0.35f -> onTapLeft()
                                offset.x > width * 0.65f -> onTapRight()
                                else -> onTapCenter()
                            }
                        }
                    }
                    .then(
                        if (isZoomed) {
                            Modifier.pointerInput(pageIdx, zoomScale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val maxX = size.width * (zoomScale - 1f) * 0.55f
                                    val maxY = size.height * (zoomScale - 1f) * 0.55f
                                    panX = (panX + dragAmount.x).coerceIn(-maxX, maxX)
                                    panY = (panY + dragAmount.y).coerceIn(-maxY, maxY)
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
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
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panX,
                                translationY = panY
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
    }
}
