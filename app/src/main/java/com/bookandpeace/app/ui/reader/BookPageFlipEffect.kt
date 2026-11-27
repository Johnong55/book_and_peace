package com.bookandpeace.app.ui.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bookandpeace.app.data.local.PageTurnStyle
import com.bookandpeace.app.ui.theme.ReaderPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val PaperTurnEasing = CubicBezierEasing(0.24f, 0.08f, 0.22f, 1.0f)

/**
 * GPU-accelerated physical book page-turn modifier for Compose HorizontalPager.
 *
 * When [style] == [PageTurnStyle.REALISTIC_BOOK]:
 * - Keeps the book spine anchored on the left edge and stacks the next page directly underneath.
 * - Rotates the turning top page in 3D perspective around the left spine with natural paper arch,
 *   curved specular highlight, outer paper-edge rim, and opaque backside veil near 90°.
 * - Casts a real-time directional shadow across the revealed page underneath in lockstep with
 *   the lifted page's moving edge.
 * - Adds an unobtrusive left-spine binding gutter and subtle right stacked-paper edge lines
 *   so the screen feels like an open physical book even while reading still.
 */
internal fun Modifier.bookPageFlipEffect(
    pagerState: PagerState,
    pageIndex: Int,
    style: PageTurnStyle,
    palette: ReaderPalette,
    isDark: Boolean
): Modifier = composed {
    if (style != PageTurnStyle.REALISTIC_BOOK) {
        return@composed this
    }

    val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
    val pageZIndex = when {
        pageOffset >= 0f -> 2f - pageOffset.coerceIn(0f, 1f)
        else -> 0f
    }

    this
        .zIndex(pageZIndex)
        .graphicsLayer {
            val offset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            cameraDistance = 20f * density
            transformOrigin = TransformOrigin(0f, 0.5f)

            when {
                offset >= 1f || offset <= -1f -> {
                    alpha = 0f
                    translationX = offset * size.width
                }

                offset >= 0f -> {
                    // Top turning page pinned at left spine, rotating in 3D space
                    val p = offset.coerceIn(0f, 1f)
                    translationX = offset * size.width
                    rotationY = -p * 108f
                    // Gentle organic paper flex/arch during mid-turn
                    val arch = sin(p * PI).toFloat()
                    scaleY = 1f + arch * 0.012f
                    // Smoothly disappear right after passing 90 degrees so mirrored text never shows
                    alpha = if (p < 0.78f) 1f else ((1f - p) / 0.22f).coerceIn(0f, 1f)
                }

                else -> {
                    // Underlying page anchored directly underneath, lifting gently into focus
                    val reveal = (1f + offset).coerceIn(0f, 1f)
                    translationX = offset * size.width
                    rotationY = 0f
                    scaleX = 0.988f + 0.012f * reveal
                    scaleY = 0.992f + 0.008f * reveal
                    alpha = 1f
                }
            }
        }
        .drawWithContent {
            drawContent()

            val offset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            if (offset >= 1f || offset <= -1f) return@drawWithContent

            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@drawWithContent

            val shadowTint = if (isDark) Color.Black else Color(0xFF1F160D)
            val sheenTint = if (isDark) Color(0xFFD6CEC2) else Color(0xFFFFFBF2)

            // 1. Subtle permanent open-book left spine gutter & right stacked-paper rim
            val spineWidth = 18.dp.toPx().coerceAtMost(w * 0.08f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        shadowTint.copy(alpha = if (isDark) 0.26f else 0.11f),
                        shadowTint.copy(alpha = if (isDark) 0.09f else 0.035f),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = spineWidth
                ),
                size = Size(spineWidth, h)
            )

            // Delicate stacked paper lines along right book edge
            val stackWidth = 4.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        shadowTint.copy(alpha = if (isDark) 0.20f else 0.09f)
                    ),
                    startX = w - stackWidth,
                    endX = w
                ),
                topLeft = Offset(w - stackWidth, 0f),
                size = Size(stackWidth, h)
            )
            drawLine(
                color = shadowTint.copy(alpha = if (isDark) 0.28f else 0.14f),
                start = Offset(w - 1.5.dp.toPx(), 0f),
                end = Offset(w - 1.5.dp.toPx(), h),
                strokeWidth = 0.8.dp.toPx()
            )

            // 2. Dynamic lighting on the TOP turning page (offset in 0..1)
            if (offset > 0.004f) {
                val p = offset.coerceIn(0f, 1f)
                val arch = sin(p * PI).toFloat()

                // Curved paper illumination: darker near spine crease, warm specular highlight across bend
                val highlightCenter = (0.72f - p * 0.38f).coerceIn(0.18f, 0.85f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to shadowTint.copy(alpha = (0.22f * p).coerceIn(0f, 0.28f)),
                        (highlightCenter - 0.22f).coerceIn(0.05f, 0.80f) to Color.Transparent,
                        highlightCenter to sheenTint.copy(alpha = (if (isDark) 0.09f else 0.22f) * arch),
                        (highlightCenter + 0.20f).coerceIn(0.20f, 0.96f) to shadowTint.copy(alpha = (0.14f * p).coerceIn(0f, 0.18f)),
                        1f to shadowTint.copy(alpha = (0.28f * p).coerceIn(0f, 0.34f))
                    ),
                    size = Size(w, h)
                )

                // Crisp physical paper-thickness highlight along the curling outer right edge
                val lipWidth = 10.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            sheenTint.copy(alpha = (if (isDark) 0.18f else 0.42f) * arch),
                            shadowTint.copy(alpha = 0.30f * arch)
                        ),
                        startX = w - lipWidth,
                        endX = w
                    ),
                    topLeft = Offset(w - lipWidth, 0f),
                    size = Size(lipWidth, h)
                )

                // Opaque paper backside veil as page approaches 90 degrees
                if (p > 0.66f) {
                    val veilAlpha = ((p - 0.66f) / 0.22f).coerceIn(0f, 0.92f)
                    drawRect(
                        color = palette.barSurface.copy(alpha = veilAlpha),
                        size = Size(w, h)
                    )
                }
            }

            // 3. Dynamic cast shadow on the UNDERLYING page being revealed (offset in -1..0)
            if (offset < -0.004f) {
                val topProgress = (1f + offset).coerceIn(0f, 1f)
                val angleRad = (topProgress * 108f).coerceAtMost(90f) * (PI / 180.0)
                val edgeX = (cos(angleRad).toFloat() * w).coerceIn(0f, w)
                val fadeOut = (1f - topProgress).coerceIn(0f, 1f)

                // Ambient covered-page shade on the left of the lifted edge
                if (edgeX > 1f) {
                    drawRect(
                        color = shadowTint.copy(alpha = (if (isDark) 0.42f else 0.20f) * fadeOut),
                        topLeft = Offset.Zero,
                        size = Size(edgeX, h)
                    )
                }

                // Soft sweeping shadow band trailing just outside the peeling edge
                val shadowSpread = (w * 0.26f * (0.45f + 0.55f * fadeOut)).coerceAtLeast(24.dp.toPx())
                val bandEnd = (edgeX + shadowSpread).coerceAtMost(w)
                if (bandEnd > edgeX + 1f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                shadowTint.copy(alpha = (if (isDark) 0.48f else 0.26f) * fadeOut),
                                shadowTint.copy(alpha = (if (isDark) 0.18f else 0.09f) * fadeOut),
                                Color.Transparent
                            ),
                            startX = edgeX,
                            endX = bandEnd
                        ),
                        topLeft = Offset(edgeX, 0f),
                        size = Size(bandEnd - edgeX, h)
                    )
                }
            }
        }
}

/**
 * Observes interactive drag & page-settle transitions on a [PagerState]
 * to fire crisp tactile paper-lift and paper-settle haptics automatically.
 */
@Composable
internal fun ObserveBookPagerHaptics(
    pagerState: PagerState,
    haptics: BookHaptics
) {
    var lastSettledPage by remember(pagerState) { mutableIntStateOf(pagerState.settledPage) }
    var liftTriggeredForDrag by remember(pagerState) { mutableStateOf(false) }

    // Paper-settle tactile click when a page finishes turning onto a new page
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled != lastSettledPage) {
                    lastSettledPage = settled
                    haptics.onPageSettle()
                }
                liftTriggeredForDrag = false
            }
    }

    // Subtle paper-lift impulse when user starts dragging a page by finger
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to abs(pagerState.currentPageOffsetFraction) }
            .collect { (scrolling, fraction) ->
                if (!scrolling) {
                    liftTriggeredForDrag = false
                } else if (!liftTriggeredForDrag && fraction > 0.06f && fraction < 0.45f) {
                    liftTriggeredForDrag = true
                    haptics.onPageLift()
                }
            }
    }
}

internal suspend fun PagerState.flipToPageRealistic(
    targetPage: Int,
    style: PageTurnStyle
) {
    if (pageCount <= 0) return
    val clamped = targetPage.coerceIn(0, pageCount - 1)
    if (clamped != currentPage) {
        val duration = if (style == PageTurnStyle.REALISTIC_BOOK) 410 else 220
        animateScrollToPage(
            page = clamped,
            animationSpec = tween(durationMillis = duration, easing = PaperTurnEasing)
        )
    }
}
