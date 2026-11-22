package com.nutra.app.core.designsystem

import android.view.RoundedCorner
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** AOSP BACK_GESTURE decelerating curve for responsive pre-commit predictive back progress. */
internal val PredictiveBackDecelerateEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** Material 3 Emphasized Decelerate curve for smooth forward entry transitions. */
private val OverlayEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** Smooth spring used to snap the shrinking screen back to full size when a gesture is cancelled. */
internal val PredictiveBackCancelSpring = spring<Float>(dampingRatio = 0.80f, stiffness = 420f)

internal const val PREDICTIVE_BACK_MIN_SCALE = 0.88f
internal const val PREDICTIVE_BACK_COMMIT_SCALE_MULTIPLIER = 0.90f
internal const val PREDICTIVE_BACK_TARGET_CORNER_DP = 32f
internal const val PREDICTIVE_BACK_EDGE_MARGIN_DP = 8f
internal const val PREDICTIVE_BACK_COMMIT_EXTRA_SHIFT_DP = 40f
internal const val PREDICTIVE_BACK_BG_MIN_SCALE = 0.96f
internal const val PREDICTIVE_BACK_BG_PARALLAX_DP = 18f
internal const val PREDICTIVE_BACK_MAX_SCRIM_ALPHA = 0.24f

@Immutable
data class PredictiveBackTransform(
    val foregroundScale: Float = 1f,
    val foregroundTranslationX: Float = 0f,
    val foregroundAlpha: Float = 1f,
    val foregroundCornerRadiusPx: Float = 0f,
    val foregroundClip: Boolean = false,
    val foregroundShadowElevationPx: Float = 0f,
    val foregroundBorderAlpha: Float = 0f,
    val backgroundScale: Float = 1f,
    val backgroundTranslationX: Float = 0f,
    val backgroundScrimAlpha: Float = 0f,
) {
    companion object {
        val Identity = PredictiveBackTransform()
    }
}

/**
 * Pure Cross-Activity Predictive Back transform math modeled after Android's platform
 * CrossActivityBackAnimation and Material 3 Predictive Back guidelines.
 */
internal fun computePredictiveBackTransform(
    enterProgress: Float,
    backProgress: Float,
    commitExitProgress: Float,
    swipeEdge: Int,
    containerWidthPx: Float,
    density: Float,
    deviceCornerRadiusPx: Float,
    isDarkTheme: Boolean,
    reducedMotion: Boolean,
    isRtl: Boolean = false,
): PredictiveBackTransform {
    val safeEnter = enterProgress.coerceIn(0f, 1f)
    val safeBack = backProgress.coerceIn(0f, 1f)
    val safeExit = commitExitProgress.coerceIn(0f, 1f)
    val safeDensity = density.coerceAtLeast(1f)
    val safeDeviceCorner = deviceCornerRadiusPx.coerceAtLeast(0f)

    // Settled at full size — zero clipping, shadow, or scrim overhead.
    if (safeEnter >= 0.999f && safeBack <= 0.001f && safeExit <= 0.001f) {
        return PredictiveBackTransform.Identity
    }

    if (reducedMotion) {
        val alpha = if (safeBack > 0.001f || safeExit > 0.001f) {
            (1f - maxOf(safeBack * 0.35f, safeExit)).coerceIn(0f, 1f)
        } else {
            safeEnter
        }
        return PredictiveBackTransform(
            foregroundAlpha = alpha,
            backgroundScrimAlpha = PREDICTIVE_BACK_MAX_SCRIM_ALPHA * 0.5f * alpha,
        )
    }

    // Forward entry transition (new screen opening over the previous screen).
    if (safeEnter < 0.999f && safeBack <= 0.001f && safeExit <= 0.001f) {
        val enterDir = if (isRtl) -1f else 1f
        val startOffsetPx = if (containerWidthPx > 0f) {
            (containerWidthPx * 0.22f).coerceIn(48f * safeDensity, 140f * safeDensity)
        } else {
            72f * safeDensity
        }
        val fgAlpha = (safeEnter / 0.6f).coerceIn(0f, 1f)
        return PredictiveBackTransform(
            foregroundScale = lerp(0.97f, 1f, safeEnter),
            foregroundTranslationX = enterDir * startOffsetPx * (1f - safeEnter),
            foregroundAlpha = fgAlpha,
            backgroundScale = lerp(1f, PREDICTIVE_BACK_BG_MIN_SCALE, safeEnter),
            backgroundTranslationX = -enterDir * (PREDICTIVE_BACK_BG_PARALLAX_DP * safeDensity) * safeEnter,
            backgroundScrimAlpha = PREDICTIVE_BACK_MAX_SCRIM_ALPHA * safeEnter,
        )
    }

    // Interactive Cross-Activity Predictive Back gesture + seamless post-commit exit.
    val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    val easedBack = PredictiveBackDecelerateEasing.transform(safeBack)

    val preCommitScale = lerp(1f, PREDICTIVE_BACK_MIN_SCALE, easedBack)
    val maxPreShiftPx = if (containerWidthPx > 0f) {
        (containerWidthPx * 0.06f - PREDICTIVE_BACK_EDGE_MARGIN_DP * safeDensity)
            .coerceAtLeast(12f * safeDensity)
    } else {
        16f * safeDensity
    }
    val preCommitShiftX = direction * maxPreShiftPx * easedBack

    val targetCornerPx = maxOf(
        safeDeviceCorner * 1.12f,
        PREDICTIVE_BACK_TARGET_CORNER_DP * safeDensity,
    )
    val preCommitCornerPx = lerp(safeDeviceCorner, targetCornerPx, easedBack)

    // Seamlessly continue from the committed pose during post-commit exit (zero jump at safeExit == 0f).
    val finalScale = preCommitScale * lerp(1f, PREDICTIVE_BACK_COMMIT_SCALE_MULTIPLIER, safeExit)
    val finalShiftX = preCommitShiftX + direction * (PREDICTIVE_BACK_COMMIT_EXTRA_SHIFT_DP * safeDensity) * safeExit
    val finalCornerPx = lerp(preCommitCornerPx, targetCornerPx, safeExit)
    val finalAlpha = (1f - safeExit).coerceIn(0f, 1f)

    val visualActive = maxOf(easedBack, safeExit).coerceIn(0f, 1f)
    val shouldClip = visualActive > 0.001f && finalCornerPx > 0.5f
    val shadowPx = (12f * safeDensity) * visualActive * finalAlpha
    val borderAlpha = (if (isDarkTheme) 0.14f else 0.06f) * visualActive * finalAlpha

    // Previous screen smoothly emerges from recessed/dimmed state as foreground shrinks & exits.
    val backgroundReveal = (easedBack * 0.65f + safeExit * (1f - easedBack * 0.65f)).coerceIn(0f, 1f)
    val bgScale = lerp(PREDICTIVE_BACK_BG_MIN_SCALE, 1f, backgroundReveal)
    val bgShiftX = -direction * (PREDICTIVE_BACK_BG_PARALLAX_DP * safeDensity) * (1f - backgroundReveal)
    val bgScrim = PREDICTIVE_BACK_MAX_SCRIM_ALPHA * (1f - backgroundReveal)

    return PredictiveBackTransform(
        foregroundScale = finalScale,
        foregroundTranslationX = finalShiftX,
        foregroundAlpha = finalAlpha,
        foregroundCornerRadiusPx = finalCornerPx,
        foregroundClip = shouldClip,
        foregroundShadowElevationPx = shadowPx,
        foregroundBorderAlpha = borderAlpha,
        backgroundScale = bgScale,
        backgroundTranslationX = bgShiftX,
        backgroundScrimAlpha = bgScrim,
    )
}

/**
 * Reads the hardware display's rounded corner radius via Android's native [RoundedCorner] API
 * without attaching or overriding any WindowInsets listener.
 */
@Composable
fun rememberDisplayCornerRadiusPx(): Float {
    val view = LocalView.current
    fun readCornerPx(): Float {
        if (view.isInEditMode) return 0f
        val insets = view.rootWindowInsets ?: return 0f
        var maxRadius = 0f
        for (position in intArrayOf(
            RoundedCorner.POSITION_TOP_LEFT,
            RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT,
            RoundedCorner.POSITION_BOTTOM_RIGHT,
        )) {
            val radius = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
            if (radius > maxRadius) maxRadius = radius
        }
        return maxRadius
    }

    var cornerRadiusPx by remember(view) { mutableFloatStateOf(readCornerPx()) }
    LaunchedEffect(view) {
        if (cornerRadiusPx <= 0f) {
            withFrameNanos { }
            val updated = readCornerPx()
            if (updated > 0f) {
                cornerRadiusPx = updated
            }
        }
    }
    return cornerRadiusPx
}

/**
 * Applies draw-phase Cross-Activity Predictive Back transforms (scale, rounded clipping,
 * horizontal shift, shadow, and subtle rim border) with zero recompositions per gesture frame.
 */
fun Modifier.predictiveBackForeground(
    transformProvider: () -> PredictiveBackTransform,
    borderColor: Color,
    ambientShadowColor: Color = Color.Black,
): Modifier = this
    .graphicsLayer {
        val t = transformProvider()
        scaleX = t.foregroundScale
        scaleY = t.foregroundScale
        translationX = t.foregroundTranslationX
        alpha = t.foregroundAlpha
        if (t.foregroundClip && t.foregroundCornerRadiusPx > 0.5f) {
            // Quantize to 0.5px steps so Compose outline caching stays efficient during drags.
            val snappedCorner = (t.foregroundCornerRadiusPx * 2f).roundToInt() / 2f
            shape = RoundedCornerShape(snappedCorner)
            clip = true
            shadowElevation = t.foregroundShadowElevationPx
            this.ambientShadowColor = ambientShadowColor
            this.spotShadowColor = ambientShadowColor
        } else {
            shape = RectangleShape
            clip = false
            shadowElevation = 0f
        }
    }
    .drawWithContent {
        drawContent()
        val t = transformProvider()
        if (t.foregroundBorderAlpha > 0.005f && t.foregroundCornerRadiusPx > 0.5f) {
            val strokePx = 1.dp.toPx()
            val half = strokePx / 2f
            val r = (t.foregroundCornerRadiusPx - half).coerceAtLeast(0f)
            drawRoundRect(
                color = borderColor.copy(alpha = t.foregroundBorderAlpha),
                topLeft = Offset(half, half),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = strokePx),
            )
        }
    }

/**
 * Applies draw-phase Cross-Activity background reveal transforms (subtle scale-up, parallax,
 * and clearing dim scrim) to the previous screen behind a shrinking foreground surface.
 */
fun Modifier.predictiveBackBackground(
    transformProvider: () -> PredictiveBackTransform,
    scrimColor: Color = Color.Black,
): Modifier = this
    .graphicsLayer {
        val t = transformProvider()
        scaleX = t.backgroundScale
        scaleY = t.backgroundScale
        translationX = t.backgroundTranslationX
    }
    .drawWithContent {
        drawContent()
        val scrimAlpha = transformProvider().backgroundScrimAlpha
        if (scrimAlpha > 0.002f) {
            drawRect(color = scrimColor.copy(alpha = scrimAlpha))
        }
    }

/**
 * Controller for a single-layer Cross-Activity Predictive Back overlay rendered above a base screen.
 * Keeps the outgoing destination mounted until its post-commit exit animation completes and exposes
 * draw-phase transform values so gesture tracking causes zero recompositions.
 */
@Stable
class PredictiveBackOverlayState<T : Any>(
    initialDestination: T?,
    private val animationScope: CoroutineScope,
) {
    var renderedDestination: T? by mutableStateOf(initialDestination)
        internal set

    internal val enterProgress = Animatable(if (initialDestination != null) 1f else 0f)
    internal val backProgress = Animatable(0f)
    internal val commitExitProgress = Animatable(0f)

    internal var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    internal var isGesturing by mutableStateOf(false)
    internal var isCommittingExit by mutableStateOf(false)
    internal var containerWidthPx by mutableFloatStateOf(0f)

    internal var enterJob: Job? = null
    internal var cancelSpringJob: Job? = null
    internal var exitJob: Job? = null

    /** True whenever an overlay is currently rendered on screen (including during exit tail). */
    val hasRenderedOverlay: Boolean by derivedStateOf { renderedDestination != null }

    /**
     * True whenever the underlying previous screen should be visibly rendered behind the overlay
     * (forward enter, interactive gesture, cancel spring return, or post-commit exit).
     */
    val isBackgroundRevealed: Boolean by derivedStateOf {
        renderedDestination != null && (
            isGesturing ||
                isCommittingExit ||
                enterProgress.value < 0.999f ||
                backProgress.value > 0.001f ||
                commitExitProgress.value > 0.001f
            )
    }

    /** True only when an overlay completely covers the base screen at rest (safe to pause base tab). */
    val overlayFullyCoversBase: Boolean by derivedStateOf {
        renderedDestination != null && !isBackgroundRevealed
    }

    fun currentTransform(
        density: Float,
        deviceCornerRadiusPx: Float,
        isDarkTheme: Boolean,
        reducedMotion: Boolean,
        isRtl: Boolean,
    ): PredictiveBackTransform {
        if (renderedDestination == null) return PredictiveBackTransform.Identity
        return computePredictiveBackTransform(
            enterProgress = enterProgress.value,
            backProgress = backProgress.value,
            commitExitProgress = commitExitProgress.value,
            swipeEdge = swipeEdge,
            containerWidthPx = containerWidthPx,
            density = density,
            deviceCornerRadiusPx = deviceCornerRadiusPx,
            isDarkTheme = isDarkTheme,
            reducedMotion = reducedMotion,
            isRtl = isRtl,
        )
    }

    internal fun startEnter(destination: T, reducedMotion: Boolean) {
        exitJob?.cancel()
        cancelSpringJob?.cancel()
        enterJob?.cancel()
        isGesturing = false
        isCommittingExit = false
        renderedDestination = destination
        enterJob = animationScope.launch {
            backProgress.snapTo(0f)
            commitExitProgress.snapTo(0f)
            if (reducedMotion) {
                enterProgress.snapTo(0f)
                enterProgress.animateTo(1f, tween(120))
            } else {
                enterProgress.snapTo(0f)
                enterProgress.animateTo(1f, tween(260, easing = OverlayEnterEasing))
            }
        }
    }

    internal fun startProgrammaticExit(
        isRtl: Boolean,
        reducedMotion: Boolean,
        latestCurrentDestination: () -> T?,
    ) {
        if (renderedDestination == null || isCommittingExit) return
        enterJob?.cancel()
        cancelSpringJob?.cancel()
        exitJob?.cancel()
        isGesturing = false
        isCommittingExit = true
        swipeEdge = if (isRtl) BackEventCompat.EDGE_RIGHT else BackEventCompat.EDGE_LEFT
        exitJob = animationScope.launch {
            try {
                enterProgress.snapTo(1f)
                commitExitProgress.snapTo(0f)
                commitExitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (reducedMotion) 100 else 200,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } finally {
                withContext(NonCancellable) {
                    if (latestCurrentDestination() == null) {
                        renderedDestination = null
                        isCommittingExit = false
                        backProgress.snapTo(0f)
                        commitExitProgress.snapTo(0f)
                    }
                }
            }
        }
    }

    internal fun startCommittedExit(
        reducedMotion: Boolean,
        onCommitDismiss: () -> Unit,
        latestCurrentDestination: () -> T?,
    ) {
        enterJob?.cancel()
        cancelSpringJob?.cancel()
        exitJob?.cancel()
        isGesturing = false
        isCommittingExit = true
        onCommitDismiss()
        exitJob = animationScope.launch {
            try {
                enterProgress.snapTo(1f)
                commitExitProgress.snapTo(0f)
                commitExitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (reducedMotion) 100 else 190,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } finally {
                withContext(NonCancellable) {
                    if (latestCurrentDestination() == null) {
                        renderedDestination = null
                        isCommittingExit = false
                        backProgress.snapTo(0f)
                        commitExitProgress.snapTo(0f)
                    }
                }
            }
        }
    }

    internal fun cancelGestureWithSpring(reducedMotion: Boolean) {
        isGesturing = false
        cancelSpringJob?.cancel()
        cancelSpringJob = animationScope.launch {
            if (reducedMotion) {
                backProgress.snapTo(0f)
            } else {
                backProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = PredictiveBackCancelSpring,
                )
            }
        }
    }
}

@Composable
fun <T : Any> rememberPredictiveBackOverlayState(
    currentDestination: T?,
): PredictiveBackOverlayState<T> {
    val animationScope = rememberCoroutineScope()
    return remember {
        PredictiveBackOverlayState(
            initialDestination = currentDestination,
            animationScope = animationScope,
        )
    }
}

/**
 * Hosts a full-size overlay screen over a base layer with native Android [PredictiveBackHandler]
 * Cross-Activity animations (real-time shrinking, dynamic corner rounding, edge-aware horizontal
 * movement, spring cancel recovery, and continuous post-commit exit).
 */
@Composable
fun <T : Any> NutraPredictiveBackOverlayHost(
    currentDestination: T?,
    state: PredictiveBackOverlayState<T>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    deviceCornerRadiusPx: Float = rememberDisplayCornerRadiusPx(),
    content: @Composable BoxScope.(T) -> Unit,
) {
    val colors = NutraTheme.colors
    val reducedMotion = NutraTheme.reducedMotion
    val density = LocalDensity.current.density
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestCurrent by rememberUpdatedState(currentDestination)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestReducedMotion by rememberUpdatedState(reducedMotion)

    LaunchedEffect(currentDestination) {
        val target = currentDestination
        if (target != null) {
            if (target != state.renderedDestination || state.isCommittingExit || state.enterProgress.value < 0.999f) {
                state.startEnter(destination = target, reducedMotion = latestReducedMotion)
            }
        } else if (state.renderedDestination != null && !state.isCommittingExit) {
            state.startProgrammaticExit(
                isRtl = isRtl,
                reducedMotion = latestReducedMotion,
                latestCurrentDestination = { latestCurrent },
            )
        }
    }

    PredictiveBackHandler(enabled = currentDestination != null && !state.isCommittingExit) { backEvents ->
        try {
            state.enterJob?.cancel()
            state.cancelSpringJob?.cancel()
            state.enterProgress.snapTo(1f)
            backEvents.collect { event ->
                state.isGesturing = true
                state.swipeEdge = event.swipeEdge
                state.backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            state.startCommittedExit(
                reducedMotion = latestReducedMotion,
                onCommitDismiss = latestOnDismiss,
                latestCurrentDestination = { latestCurrent },
            )
        } catch (e: CancellationException) {
            state.cancelGestureWithSpring(reducedMotion = latestReducedMotion)
            throw e
        }
    }

    val destinationToRender = state.renderedDestination ?: return
    val isClosing by rememberUpdatedState(state.isCommittingExit)
    val borderColor = colors.textPrimary
    val shadowColor = colors.ink

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0) {
                    state.containerWidthPx = size.width.toFloat()
                }
            }
            .predictiveBackForeground(
                transformProvider = {
                    state.currentTransform(
                        density = density,
                        deviceCornerRadiusPx = deviceCornerRadiusPx,
                        isDarkTheme = colors.isDark,
                        reducedMotion = reducedMotion,
                        isRtl = isRtl,
                    )
                },
                borderColor = borderColor,
                ambientShadowColor = shadowColor,
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (isClosing) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        content(destinationToRender)
    }
}

/**
 * Reusable interactive Cross-Activity Predictive Back state for in-place step/tab back navigation
 * (e.g., secondary primary tab -> Dashboard, or Onboarding step N -> step N-1) where the previous
 * destination is already known and rendered behind the shrinking current surface during the gesture.
 */
@Stable
class InlinePredictiveBackState(
    private val animationScope: CoroutineScope,
) {
    internal val backProgress = Animatable(0f)
    internal val commitExitProgress = Animatable(0f)
    internal var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    internal var isGesturing by mutableStateOf(false)
    internal var isCommitting by mutableStateOf(false)
    internal var containerWidthPx by mutableFloatStateOf(0f)

    internal var cancelSpringJob: Job? = null
    internal var exitJob: Job? = null

    val isActive: Boolean by derivedStateOf {
        isGesturing || isCommitting || backProgress.value > 0.001f || commitExitProgress.value > 0.001f
    }

    fun currentTransform(
        density: Float,
        deviceCornerRadiusPx: Float,
        isDarkTheme: Boolean,
        reducedMotion: Boolean,
        isRtl: Boolean,
    ): PredictiveBackTransform {
        if (!isActive) return PredictiveBackTransform.Identity
        return computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = backProgress.value,
            commitExitProgress = commitExitProgress.value,
            swipeEdge = swipeEdge,
            containerWidthPx = containerWidthPx,
            density = density,
            deviceCornerRadiusPx = deviceCornerRadiusPx,
            isDarkTheme = isDarkTheme,
            reducedMotion = reducedMotion,
            isRtl = isRtl,
        )
    }

    internal fun cancelGestureWithSpring(reducedMotion: Boolean) {
        isGesturing = false
        cancelSpringJob?.cancel()
        cancelSpringJob = animationScope.launch {
            if (reducedMotion) {
                backProgress.snapTo(0f)
            } else {
                backProgress.animateTo(0f, PredictiveBackCancelSpring)
            }
        }
    }

    internal fun commitWithExit(
        reducedMotion: Boolean,
        onComplete: () -> Unit,
    ) {
        cancelSpringJob?.cancel()
        exitJob?.cancel()
        isGesturing = false
        isCommitting = true
        exitJob = animationScope.launch {
            var completedNormally = false
            try {
                commitExitProgress.snapTo(0f)
                commitExitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (reducedMotion) 90 else 180,
                        easing = FastOutSlowInEasing,
                    ),
                )
                completedNormally = true
            } finally {
                withContext(NonCancellable) {
                    if (completedNormally) {
                        onComplete()
                    }
                    backProgress.snapTo(0f)
                    commitExitProgress.snapTo(0f)
                    isCommitting = false
                }
            }
        }
    }

    fun resetImmediately() {
        cancelSpringJob?.cancel()
        exitJob?.cancel()
        isGesturing = false
        isCommitting = false
        animationScope.launch {
            backProgress.snapTo(0f)
            commitExitProgress.snapTo(0f)
        }
    }
}

@Composable
fun rememberInlinePredictiveBackState(): InlinePredictiveBackState {
    val scope = rememberCoroutineScope()
    return remember { InlinePredictiveBackState(scope) }
}

@Composable
fun InlinePredictiveBackHandler(
    enabled: Boolean,
    state: InlinePredictiveBackState,
    onCommit: () -> Unit,
) {
    val reducedMotion = NutraTheme.reducedMotion
    val latestReducedMotion by rememberUpdatedState(reducedMotion)
    val latestOnCommit by rememberUpdatedState(onCommit)

    PredictiveBackHandler(enabled = enabled && !state.isCommitting) { backEvents ->
        try {
            state.cancelSpringJob?.cancel()
            backEvents.collect { event ->
                state.isGesturing = true
                state.swipeEdge = event.swipeEdge
                state.backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            state.commitWithExit(
                reducedMotion = latestReducedMotion,
                onComplete = latestOnCommit,
            )
        } catch (e: CancellationException) {
            state.cancelGestureWithSpring(reducedMotion = latestReducedMotion)
            throw e
        }
    }
}
