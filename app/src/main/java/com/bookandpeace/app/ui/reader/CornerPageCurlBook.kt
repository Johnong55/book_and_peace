package com.bookandpeace.app.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bookandpeace.app.ui.theme.ReaderPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private val NaturalCurlEasing = CubicBezierEasing(0.22f, 0.08f, 0.18f, 1.0f)
private val SoftSettleEasing = CubicBezierEasing(0.18f, 0.85f, 0.28f, 1.0f)

private enum class CurlDirection {
    NONE,
    NEXT,
    PREV
}

@Immutable
private data class CurlGeometry(
    val flatPath: Path,
    val peeledPath: Path,
    val backPath: Path,
    val midPoint: Offset,
    val unitNormal: Offset, // points from curled tip -> original corner (into peeled side)
    val curlDistance: Float,
    val tip: Offset,
    val anchorCorner: Offset,
    val reflectionMatrix: Matrix
)

/**
 * Clips a convex polygon against the half-plane: dot(P - mid, normal) <= 0 (when keepNegative == true)
 * or >= 0 (when keepNegative == false). Uses Sutherland-Hodgman algorithm.
 */
private fun clipPolygonHalfPlane(
    polygon: List<Offset>,
    mid: Offset,
    normal: Offset,
    keepNegative: Boolean
): List<Offset> {
    if (polygon.isEmpty()) return emptyList()
    val output = ArrayList<Offset>(polygon.size + 2)
    fun signedDist(p: Offset): Float = (p.x - mid.x) * normal.x + (p.y - mid.y) * normal.y
    fun isInside(d: Float): Boolean = if (keepNegative) d <= 0f else d >= 0f

    var prev = polygon.last()
    var prevDist = signedDist(prev)
    var prevInside = isInside(prevDist)

    for (curr in polygon) {
        val currDist = signedDist(curr)
        val currInside = isInside(currDist)

        if (currInside != prevInside) {
            val denom = (prevDist - currDist)
            if (abs(denom) > 1e-5f) {
                val t = (prevDist / denom).coerceIn(0f, 1f)
                output.add(
                    Offset(
                        x = prev.x + (curr.x - prev.x) * t,
                        y = prev.y + (curr.y - prev.y) * t
                    )
                )
            }
        }
        if (currInside) {
            output.add(curr)
        }
        prev = curr
        prevDist = currDist
        prevInside = currInside
    }
    return output
}

private fun List<Offset>.toClosedPath(): Path = Path().apply {
    if (isEmpty()) return@apply
    moveTo(first().x, first().y)
    for (i in 1 until size) {
        lineTo(this@toClosedPath[i].x, this@toClosedPath[i].y)
    }
    close()
}

/**
 * Enforces physical left-spine binding constraints so the curling corner never tears away
 * from the book spine x = 0.
 */
private fun constrainCurlTip(raw: Offset, width: Float, height: Float, anchorY: Float): Offset {
    var tx = raw.x.coerceIn(-width * 1.02f, width - 0.5f)
    var ty = raw.y

    // Keep slight vertical margin while near rest so fold angle stays organic
    val spineNear = Offset(0f, anchorY)
    val dx1 = tx - spineNear.x
    val dy1 = ty - spineNear.y
    val dist1 = hypot(dx1, dy1)
    if (dist1 > width && dist1 > 1e-4f) {
        val scale = width / dist1
        tx = spineNear.x + dx1 * scale
        ty = spineNear.y + dy1 * scale
    }

    // Opposite spine corner constraint: distance <= diagonal of the page
    val spineFar = Offset(0f, height - anchorY)
    val maxDiag = hypot(width, height)
    val dx2 = tx - spineFar.x
    val dy2 = ty - spineFar.y
    val dist2 = hypot(dx2, dy2)
    if (dist2 > maxDiag && dist2 > 1e-4f) {
        val scale = maxDiag / dist2
        tx = spineFar.x + dx2 * scale
        ty = spineFar.y + dy2 * scale
    }

    return Offset(tx, ty)
}

private fun computeCurlGeometry(
    width: Float,
    height: Float,
    rawTip: Offset,
    anchorY: Float
): CurlGeometry? {
    if (width <= 2f || height <= 2f) return null
    val corner = Offset(width, anchorY)
    val tip = constrainCurlTip(rawTip, width, height, anchorY)
    val dx = corner.x - tip.x
    val dy = corner.y - tip.y
    val dist = hypot(dx, dy)
    if (dist < 2.5f) return null

    val nx = dx / dist
    val ny = dy / dist
    val normal = Offset(nx, ny)
    val mid = Offset((corner.x + tip.x) * 0.5f, (corner.y + tip.y) * 0.5f)

    val rect = listOf(
        Offset(0f, 0f),
        Offset(width, 0f),
        Offset(width, height),
        Offset(0f, height)
    )

    val flatPoly = clipPolygonHalfPlane(rect, mid, normal, keepNegative = true)
    val peeledPoly = clipPolygonHalfPlane(rect, mid, normal, keepNegative = false)

    // Reflect every vertex of the peeled polygon across the fold line to form the exact curled backside flap
    val backPoly = peeledPoly.map { p ->
        val d = (p.x - mid.x) * nx + (p.y - mid.y) * ny
        Offset(p.x - 2f * d * nx, p.y - 2f * d * ny)
    }

    // 4x4 column-major reflection matrix for Compose Canvas withTransform
    val dConst = 2f * (mid.x * nx + mid.y * ny)
    val matrix = Matrix().apply {
        values[0] = 1f - 2f * nx * nx
        values[1] = -2f * nx * ny
        values[4] = -2f * nx * ny
        values[5] = 1f - 2f * ny * ny
        values[10] = 1f
        values[12] = dConst * nx
        values[13] = dConst * ny
        values[15] = 1f
    }

    return CurlGeometry(
        flatPath = flatPoly.toClosedPath(),
        peeledPath = peeledPoly.toClosedPath(),
        backPath = backPoly.toClosedPath(),
        midPoint = mid,
        unitNormal = normal,
        curlDistance = dist,
        tip = tip,
        anchorCorner = corner,
        reflectionMatrix = matrix
    )
}

/**
 * Interactive physical corner page-curl book container.
 *
 * Features:
 * - Peel directly from bottom-right or top-right corner with your finger; the curled corner,
 *   crease angle, shadows, and backside follow your fingertip live at 60–120 FPS.
 * - Drag from the left edge to unfold the previous page smoothly back onto the stack.
 * - Tap right 35% / left 35% for a natural curved Bezier corner-curl page turn; tap center 30% for menu.
 * - Displays subtle realistic ink bleed-through + 3D cylindrical paper specular shading on the curled back,
 *   plus dynamic cast shadows on both the flat page and the revealed page underneath.
 * - Synchronized 3-stage tactile haptics (paper lift, midpoint rustle, paper settle).
 */
@Composable
internal fun CornerPageCurlBook(
    pageCount: Int,
    currentPage: Int,
    palette: ReaderPalette,
    isDark: Boolean,
    haptics: BookHaptics,
    gestureEnabled: Boolean = true,
    onInteractionStart: () -> Unit = {},
    onPageCommitted: (Int) -> Unit,
    onBoundaryPrev: () -> Unit,
    onBoundaryNext: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentPageState by rememberUpdatedState(currentPage)
    val pageCountState by rememberUpdatedState(pageCount)
    val onInteractionStartState by rememberUpdatedState(onInteractionStart)
    val onPageCommittedState by rememberUpdatedState(onPageCommitted)
    val onBoundaryPrevState by rememberUpdatedState(onBoundaryPrev)
    val onBoundaryNextState by rememberUpdatedState(onBoundaryNext)
    val onTapCenterState by rememberUpdatedState(onTapCenter)

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var curlDirection by remember { mutableStateOf(CurlDirection.NONE) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var tipX by remember { mutableFloatStateOf(0f) }
    var tipY by remember { mutableFloatStateOf(0f) }

    var activeAnimJob by remember { mutableStateOf<Job?>(null) }
    var pendingCommitPage by remember { mutableStateOf<Int?>(null) }

    // Reset curl cleanly if pageCount changes abruptly (e.g. chapter switch)
    LaunchedEffect(pageCount) {
        activeAnimJob?.cancel()
        activeAnimJob = null
        pendingCommitPage = null
        curlDirection = CurlDirection.NONE
    }

    fun finishPendingCommitImmediately() {
        activeAnimJob?.cancel()
        activeAnimJob = null
        val pending = pendingCommitPage
        pendingCommitPage = null
        curlDirection = CurlDirection.NONE
        if (pending != null && pending in 0 until pageCountState) {
            onPageCommittedState(pending)
        }
    }

    /**
     * Animates the corner along a natural curved Bezier arc (lifting slightly as it crosses the book,
     * then settling softly flat onto the opposite side — just like a human hand turning a page).
     */
    fun animateCurlArc(
        direction: CurlDirection,
        startPos: Offset,
        endPos: Offset,
        arcLiftY: Float,
        durationMs: Int,
        commitTargetPage: Int?,
        playSettleHaptic: Boolean
    ) {
        activeAnimJob?.cancel()
        pendingCommitPage = commitTargetPage
        val anim = Animatable(0f)
        activeAnimJob = scope.launch {
            try {
                val ctrl = Offset(
                    x = (startPos.x + endPos.x) * 0.5f,
                    y = (startPos.y + endPos.y) * 0.5f + arcLiftY
                )
                val easing = if (commitTargetPage != null) NaturalCurlEasing else SoftSettleEasing
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = durationMs, easing = easing)
                ) {
                    val t = value
                    val inv = 1f - t
                    tipX = inv * inv * startPos.x + 2f * inv * t * ctrl.x + t * t * endPos.x
                    tipY = inv * inv * startPos.y + 2f * inv * t * ctrl.y + t * t * endPos.y
                }
                val committed = pendingCommitPage
                pendingCommitPage = null
                curlDirection = CurlDirection.NONE
                if (committed != null && committed in 0 until pageCountState) {
                    onPageCommittedState(committed)
                    if (playSettleHaptic) haptics.onPageSettle()
                }
            } catch (_: CancellationException) {
                // Handled by caller
            } finally {
                if (pendingCommitPage == null && curlDirection == direction && anim.value >= 0.99f) {
                    curlDirection = CurlDirection.NONE
                }
            }
        }
    }

    fun triggerTapNext(tapY: Float) {
        finishPendingCommitImmediately()
        val cur = currentPageState
        val total = pageCountState
        if (cur >= total - 1) {
            onBoundaryNextState()
            return
        }
        val w = viewportSize.width.toFloat().coerceAtLeast(1f)
        val h = viewportSize.height.toFloat().coerceAtLeast(1f)
        val fromTop = tapY < h * 0.35f
        val ancY = if (fromTop) 0f else h
        val start = Offset(w - 6f, if (fromTop) h * 0.08f else h * 0.92f)
        val end = Offset(-w * 1.02f, ancY)
        val lift = if (fromTop) h * 0.24f else -h * 0.24f

        anchorY = ancY
        tipX = start.x
        tipY = start.y
        curlDirection = CurlDirection.NEXT
        haptics.onPageLift()

        animateCurlArc(
            direction = CurlDirection.NEXT,
            startPos = start,
            endPos = end,
            arcLiftY = lift,
            durationMs = 450,
            commitTargetPage = cur + 1,
            playSettleHaptic = true
        )
    }

    fun triggerTapPrev(tapY: Float) {
        finishPendingCommitImmediately()
        val cur = currentPageState
        if (cur <= 0) {
            onBoundaryPrevState()
            return
        }
        val w = viewportSize.width.toFloat().coerceAtLeast(1f)
        val h = viewportSize.height.toFloat().coerceAtLeast(1f)
        val fromTop = tapY < h * 0.35f
        val ancY = if (fromTop) 0f else h
        val start = Offset(-w * 0.98f, ancY)
        val end = Offset(w, ancY)
        val lift = if (fromTop) h * 0.22f else -h * 0.22f

        anchorY = ancY
        tipX = start.x
        tipY = start.y
        curlDirection = CurlDirection.PREV
        haptics.onPageLift()

        animateCurlArc(
            direction = CurlDirection.PREV,
            startPos = start,
            endPos = end,
            arcLiftY = lift,
            durationMs = 430,
            commitTargetPage = cur - 1,
            playSettleHaptic = true
        )
    }

    val safeCurrent = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val topPageIndex = when (curlDirection) {
        CurlDirection.NONE -> safeCurrent
        CurlDirection.NEXT -> safeCurrent
        CurlDirection.PREV -> (safeCurrent - 1).coerceAtLeast(0)
    }
    val bottomPageIndex: Int? = when (curlDirection) {
        CurlDirection.NONE -> null
        CurlDirection.NEXT -> (safeCurrent + 1).takeIf { it < pageCount }
        CurlDirection.PREV -> safeCurrent
    }

    val topPageLayer = rememberGraphicsLayer()

    val curlGeometry = if (curlDirection != CurlDirection.NONE && viewportSize.width > 0 && viewportSize.height > 0) {
        computeCurlGeometry(
            width = viewportSize.width.toFloat(),
            height = viewportSize.height.toFloat(),
            rawTip = Offset(tipX, tipY),
            anchorY = anchorY
        )
    } else null

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemGestures = WindowInsets.systemGestures
    val leftEdgeGuardPx by rememberUpdatedState(
        maxOf(
            systemGestures.getLeft(density, layoutDirection).toFloat(),
            with(density) { 30.dp.toPx() }
        )
    )
    val rightEdgeGuardPx by rememberUpdatedState(
        maxOf(
            systemGestures.getRight(density, layoutDirection).toFloat(),
            with(density) { 30.dp.toPx() }
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .onSizeChanged { viewportSize = it }
            .then(
                if (gestureEnabled) {
                    Modifier.pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val downTime = down.uptimeMillis
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)

                            // Reserve outer left & right bezels exclusively for Android System Back gesture
                            if (downPos.x < leftEdgeGuardPx || downPos.x > (w - rightEdgeGuardPx)) {
                                return@awaitEachGesture
                            }

                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, downPos)

                            var isDragging = false
                            var dragDir = CurlDirection.NONE
                            var boundaryDragDir = 0 // -1 = prev boundary, +1 = next boundary
                            var totalDx = 0f
                            var totalDy = 0f
                            var rustleFired = false
                            var currentPointerPos = downPos
                            var lastUptimeMillis = downTime
                            var wasCancelled = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: run {
                                        wasCancelled = true
                                        break
                                    }

                                lastUptimeMillis = change.uptimeMillis
                                if (change.isConsumed) {
                                    wasCancelled = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (!change.changedToUp()) {
                                        wasCancelled = true
                                    } else {
                                        currentPointerPos = change.position
                                        velocityTracker.addPosition(change.uptimeMillis, currentPointerPos)
                                    }
                                    break
                                }

                                val delta = change.positionChange()
                                currentPointerPos = change.position
                                totalDx += delta.x
                                totalDy += delta.y
                                velocityTracker.addPosition(change.uptimeMillis, currentPointerPos)

                                if (!isDragging && hypot(totalDx, totalDy) > touchSlop) {
                                    isDragging = true
                                    onInteractionStartState()
                                    finishPendingCommitImmediately()
                                    val cur = currentPageState
                                    val total = pageCountState
                                    val fromTop = downPos.y < h * 0.36f
                                    anchorY = if (fromTop) 0f else h

                                    if (totalDx < 0f) {
                                        // Dragging left => peel corner forward to NEXT page
                                        if (cur < total - 1) {
                                            dragDir = CurlDirection.NEXT
                                            curlDirection = CurlDirection.NEXT
                                            haptics.onPageLift()
                                        } else {
                                            boundaryDragDir = +1
                                        }
                                    } else {
                                        // Dragging right => peel PREVIOUS page back from left
                                        if (cur > 0) {
                                            dragDir = CurlDirection.PREV
                                            curlDirection = CurlDirection.PREV
                                            haptics.onPageLift()
                                        } else {
                                            boundaryDragDir = -1
                                        }
                                    }
                                }

                                if (isDragging && dragDir != CurlDirection.NONE) {
                                    change.consume()
                                    val ancY = anchorY
                                    val fromTop = ancY < h * 0.5f

                                    // Smooth zero-jump corner peeling directly proportional to finger drag
                                    val mappedX = when (dragDir) {
                                        CurlDirection.NEXT -> {
                                            val pull = (downPos.x - currentPointerPos.x).coerceAtLeast(0f)
                                            (w - pull * 1.85f).coerceIn(-w * 0.98f, w - 1f)
                                        }

                                        CurlDirection.PREV -> {
                                            val push = (currentPointerPos.x - downPos.x).coerceAtLeast(0f)
                                            (-w * 0.96f + push * 1.85f).coerceIn(-w * 0.98f, w - 1f)
                                        }

                                        CurlDirection.NONE -> w
                                    }

                                    val progress = ((w - mappedX) / (2f * w)).coerceIn(0f, 1f)
                                    val organicLift = sin(progress * PI).toFloat() * h * 0.09f * if (fromTop) 1f else -1f
                                    val targetY = (currentPointerPos.y + organicLift).coerceIn(h * 0.06f, h * 0.94f)

                                    tipX = mappedX
                                    tipY = targetY

                                    // Subtle midpoint paper-rustle tactile tick during slow manual drag
                                    if (!rustleFired && abs(mappedX - w * 0.10f) < w * 0.14f) {
                                        rustleFired = true
                                        haptics.onPaperRustle()
                                    }
                                }
                            }

                            if (wasCancelled) {
                                // Android system gesture (e.g. Back swipe) took over — abort silently!
                                if (dragDir != CurlDirection.NONE) {
                                    curlDirection = CurlDirection.NONE
                                }
                                return@awaitEachGesture
                            }

                            if (!isDragging && (lastUptimeMillis - downTime) < 460L) {
                                // Instant crisp single-tap zones: 35% Left / 30% Center / 35% Right
                                when {
                                    downPos.x < w * 0.35f -> {
                                        onInteractionStartState()
                                        triggerTapPrev(downPos.y)
                                    }
                                    downPos.x > w * 0.65f -> {
                                        onInteractionStartState()
                                        triggerTapNext(downPos.y)
                                    }
                                    else -> {
                                        finishPendingCommitImmediately()
                                        onTapCenterState()
                                    }
                                }
                            } else if (isDragging) {
                                val vx = velocityTracker.calculateVelocity().x
                                val cur = currentPageState
                                val ancY = anchorY
                                val fromTop = ancY < h * 0.5f

                                when {
                                    dragDir == CurlDirection.NEXT -> {
                                        val shouldComplete = vx < -340f || tipX < w * 0.42f
                                        if (shouldComplete) {
                                            animateCurlArc(
                                                direction = CurlDirection.NEXT,
                                                startPos = Offset(tipX, tipY),
                                                endPos = Offset(-w * 1.02f, ancY),
                                                arcLiftY = if (fromTop) h * 0.08f else -h * 0.08f,
                                                durationMs = 310,
                                                commitTargetPage = cur + 1,
                                                playSettleHaptic = true
                                            )
                                        } else {
                                            animateCurlArc(
                                                direction = CurlDirection.NEXT,
                                                startPos = Offset(tipX, tipY),
                                                endPos = Offset(w, ancY),
                                                arcLiftY = 0f,
                                                durationMs = 240,
                                                commitTargetPage = null,
                                                playSettleHaptic = false
                                            )
                                        }
                                    }

                                    dragDir == CurlDirection.PREV -> {
                                        val shouldComplete = vx > 340f || tipX > -w * 0.10f
                                        if (shouldComplete) {
                                            animateCurlArc(
                                                direction = CurlDirection.PREV,
                                                startPos = Offset(tipX, tipY),
                                                endPos = Offset(w, ancY),
                                                arcLiftY = if (fromTop) h * 0.06f else -h * 0.06f,
                                                durationMs = 300,
                                                commitTargetPage = cur - 1,
                                                playSettleHaptic = true
                                            )
                                        } else {
                                            animateCurlArc(
                                                direction = CurlDirection.PREV,
                                                startPos = Offset(tipX, tipY),
                                                endPos = Offset(-w * 1.02f, ancY),
                                                arcLiftY = 0f,
                                                durationMs = 220,
                                                commitTargetPage = null,
                                                playSettleHaptic = false
                                            )
                                        }
                                    }

                                    boundaryDragDir > 0 && (totalDx < -w * 0.14f || vx < -450f) -> {
                                        onBoundaryNextState()
                                    }

                                    boundaryDragDir < 0 && (totalDx > w * 0.14f || vx > 450f) -> {
                                        onBoundaryPrevState()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // =========================================================================
        // LAYER 1: UNDERLYING PAGE (Only composed while actively curling)
        // =========================================================================
        if (bottomPageIndex != null) {
            key("bottom_$bottomPageIndex") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .drawWithContent {
                            drawContent()
                            drawStaticBookFrame(isDark = isDark)
                            curlGeometry?.let { geom ->
                                drawRevealedPageCastShadow(geom = geom, isDark = isDark)
                            }
                        }
                ) {
                    pageContent(bottomPageIndex)
                }
            }
        }

        // =========================================================================
        // LAYER 2: TOP PAGE (Clippable flat surface + Curled Backside Flap)
        // =========================================================================
        key("top_$topPageIndex") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .drawWithContent {
                        val geom = curlGeometry
                        if (geom == null) {
                            // Resting state: zero extra layer cost!
                            drawContent()
                            drawStaticBookFrame(isDark = isDark)
                        } else {
                            // Record crisp top page into hardware GraphicsLayer
                            topPageLayer.record {
                                this@drawWithContent.drawContent()
                            }

                            // 2A. Draw the still-flat portion of the current page
                            clipPath(geom.flatPath) {
                                drawLayer(topPageLayer)
                                drawStaticBookFrame(isDark = isDark)
                                drawFlatPageFoldCreaseShadow(geom = geom, isDark = isDark)
                            }

                            // 2B. Soft drop shadow cast BY the curled flap onto the flat left page
                            drawCurledFlapDropShadow(geom = geom, isDark = isDark)

                            // 2C. Curled Backside Paper Flap with faint ink bleed-through + 3D cylindrical lighting
                            val backPaperColor = if (isDark) {
                                palette.barSurface
                            } else {
                                Color(
                                    red = (palette.background.red * 0.97f).coerceIn(0f, 1f),
                                    green = (palette.background.green * 0.96f).coerceIn(0f, 1f),
                                    blue = (palette.background.blue * 0.94f).coerceIn(0f, 1f),
                                    alpha = 1f
                                )
                            }

                            clipPath(geom.backPath) {
                                // Opaque paper base
                                drawPath(path = geom.backPath, color = backPaperColor)

                                // Real faint ink bleed-through reflected across fold line
                                withTransform({ transform(geom.reflectionMatrix) }) {
                                    drawLayer(topPageLayer)
                                }

                                // Translucent paper fiber veil (leaves ~13% realistic ink bleed-through)
                                drawPath(
                                    path = geom.backPath,
                                    color = backPaperColor.copy(alpha = if (isDark) 0.90f else 0.87f)
                                )

                                // 3D cylindrical paper curl specular highlight & crease shadow
                                drawCylindricalBackLighting(geom = geom, isDark = isDark)
                            }

                            // Crisp physical paper rim along the curled corner border
                            val rimColor = if (isDark) {
                                Color.White.copy(alpha = 0.12f)
                            } else {
                                Color(0xFF4A3B2C).copy(alpha = 0.22f)
                            }
                            drawPath(
                                path = geom.backPath,
                                color = rimColor,
                                style = Stroke(width = 0.9.dp.toPx())
                            )
                        }
                    }
            ) {
                pageContent(topPageIndex)
            }
        }
    }
}

/** Subtle open-book left spine gutter and right stacked-pages rim */
private fun DrawScope.drawStaticBookFrame(isDark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return

    val shadowTint = if (isDark) Color.Black else Color(0xFF1F160D)
    val spineWidth = 16.dp.toPx().coerceAtMost(w * 0.07f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                shadowTint.copy(alpha = if (isDark) 0.24f else 0.10f),
                shadowTint.copy(alpha = if (isDark) 0.08f else 0.03f),
                Color.Transparent
            ),
            startX = 0f,
            endX = spineWidth
        ),
        size = Size(spineWidth, h)
    )

    // Delicate stacked paper lines on outer right edge
    val stackWidth = 4.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                shadowTint.copy(alpha = if (isDark) 0.18f else 0.08f)
            ),
            startX = w - stackWidth,
            endX = w
        ),
        topLeft = Offset(w - stackWidth, 0f),
        size = Size(stackWidth, h)
    )
    drawLine(
        color = shadowTint.copy(alpha = if (isDark) 0.25f else 0.13f),
        start = Offset(w - 1.4.dp.toPx(), 0f),
        end = Offset(w - 1.4.dp.toPx(), h),
        strokeWidth = 0.75.dp.toPx()
    )
}

/** Cast shadow falling from the curled fold onto the newly revealed NEXT page underneath */
private fun DrawScope.drawRevealedPageCastShadow(geom: CurlGeometry, isDark: Boolean) {
    val shadowTint = if (isDark) Color.Black else Color(0xFF1B1209)
    val maxReach = (geom.curlDistance * 0.34f).coerceIn(18.dp.toPx(), size.width * 0.28f)
    val fadeStrength = (geom.curlDistance / (size.width * 0.35f)).coerceIn(0.25f, 1f) *
        ((size.width * 2.05f - geom.curlDistance) / (size.width * 0.55f)).coerceIn(0f, 1f)
    if (fadeStrength <= 0.01f) return

    val start = geom.midPoint
    val end = Offset(
        x = start.x + geom.unitNormal.x * maxReach,
        y = start.y + geom.unitNormal.y * maxReach
    )
    clipPath(geom.peeledPath) {
        drawRect(
            brush = Brush.linearGradient(
                0f to shadowTint.copy(alpha = (if (isDark) 0.58f else 0.34f) * fadeStrength),
                0.28f to shadowTint.copy(alpha = (if (isDark) 0.28f else 0.15f) * fadeStrength),
                0.70f to shadowTint.copy(alpha = (if (isDark) 0.08f else 0.04f) * fadeStrength),
                1f to Color.Transparent,
                start = start,
                end = end
            )
        )
    }
}

/** Subtle upward-bend shading right along the fold line on the flat side of the top page */
private fun DrawScope.drawFlatPageFoldCreaseShadow(geom: CurlGeometry, isDark: Boolean) {
    val shadowTint = if (isDark) Color.Black else Color(0xFF23180E)
    val creaseReach = (geom.curlDistance * 0.10f).coerceIn(8.dp.toPx(), 26.dp.toPx())
    val start = geom.midPoint
    val end = Offset(
        x = start.x - geom.unitNormal.x * creaseReach,
        y = start.y - geom.unitNormal.y * creaseReach
    )
    drawRect(
        brush = Brush.linearGradient(
            0f to shadowTint.copy(alpha = if (isDark) 0.30f else 0.14f),
            0.45f to shadowTint.copy(alpha = if (isDark) 0.10f else 0.04f),
            1f to Color.Transparent,
            start = start,
            end = end
        )
    )
}

/** Soft drop shadow cast by the curled flap onto the flat left portion of the current page */
private fun DrawScope.drawCurledFlapDropShadow(geom: CurlGeometry, isDark: Boolean) {
    val shadowTint = if (isDark) Color.Black else Color(0xFF1A1108)
    val flapDepth = (geom.curlDistance * 0.5f).coerceAtLeast(12.dp.toPx())
    val shadowTail = (flapDepth * 0.45f).coerceIn(12.dp.toPx(), 42.dp.toPx())
    val start = geom.midPoint
    val end = Offset(
        x = start.x - geom.unitNormal.x * (flapDepth + shadowTail),
        y = start.y - geom.unitNormal.y * (flapDepth + shadowTail)
    )
    clipPath(geom.flatPath) {
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                (flapDepth / (flapDepth + shadowTail)).coerceIn(0.35f, 0.88f) to
                    shadowTint.copy(alpha = if (isDark) 0.36f else 0.18f),
                1f to Color.Transparent,
                start = start,
                end = end
            )
        )
    }
}

/** 3D cylindrical illumination across the curled paper backside (crease shadow -> warm sheen -> soft tip) */
private fun DrawScope.drawCylindricalBackLighting(geom: CurlGeometry, isDark: Boolean) {
    val shadowTint = if (isDark) Color.Black else Color(0xFF281B0F)
    val highlightTint = if (isDark) Color(0xFFDAD3C8) else Color(0xFFFFFDF7)
    val bandWidth = (geom.curlDistance * 0.52f).coerceIn(28.dp.toPx(), size.width * 0.65f)
    val start = geom.midPoint
    val end = Offset(
        x = start.x - geom.unitNormal.x * bandWidth,
        y = start.y - geom.unitNormal.y * bandWidth
    )
    drawRect(
        brush = Brush.linearGradient(
            0.00f to shadowTint.copy(alpha = if (isDark) 0.42f else 0.24f),
            0.14f to shadowTint.copy(alpha = if (isDark) 0.12f else 0.06f),
            0.34f to highlightTint.copy(alpha = if (isDark) 0.13f else 0.42f),
            0.58f to Color.Transparent,
            0.85f to shadowTint.copy(alpha = if (isDark) 0.18f else 0.10f),
            1.00f to shadowTint.copy(alpha = if (isDark) 0.32f else 0.18f),
            start = start,
            end = end
        )
    )
}
