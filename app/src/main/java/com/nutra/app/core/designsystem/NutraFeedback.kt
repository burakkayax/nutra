package com.nutra.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class NutraSnackbarTone { Neutral, Success, Error }

/** Shared pulse animation alpha for loading skeleton placeholders (respects system reduced motion). */
@Composable
fun rememberSkeletonPulseAlpha(label: String = "nutraSkeletonPulse"): State<Float> {
    if (LocalReducedMotion.current) {
        return remember { mutableFloatStateOf(0.72f) }
    }
    val infiniteTransition = rememberInfiniteTransition(label = label)
    return infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
}

/** Pulsing skeleton placeholder block for loading states. */
@Composable
fun NutraSkeletonBar(
    pulseAlphaState: State<Float>,
    modifier: Modifier = Modifier,
    shape: Shape = NutraTheme.shapes.pill,
    color: Color = NutraTheme.colors.surfaceMuted,
) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = pulseAlphaState.value }
            .clip(shape)
            .background(color),
    )
}

/** Computes an accessibility-aware auto-dismiss duration for transient snackbars. */
@Composable
fun rememberAccessibleSnackbarTimeout(
    baseTimeoutMillis: Long = 3200L,
    hasAction: Boolean = false,
): Long {
    val accessibilityManager = LocalAccessibilityManager.current
    return remember(accessibilityManager, baseTimeoutMillis, hasAction) {
        accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = baseTimeoutMillis,
            containsIcons = true,
            containsText = true,
            containsControls = hasAction,
        ) ?: baseTimeoutMillis
    }
}

/** Ink snackbar. Animate it in with [NutraSnackbarHost]. */
@Composable
private fun NutraSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    tone: NutraSnackbarTone = NutraSnackbarTone.Neutral,
) {
    val colors = NutraTheme.colors
    val container = if (colors.isDark) colors.surfaceMuted else colors.ink
    val contentColor = if (colors.isDark) colors.textPrimary else colors.onInk
    val actionColor = if (colors.isDark) colors.accent else colors.accentOnInk
    val resolvedIcon = icon ?: when (tone) {
        NutraSnackbarTone.Success -> Icons.Rounded.Check
        NutraSnackbarTone.Error -> Icons.Rounded.Warning
        NutraSnackbarTone.Neutral -> null
    }
    val iconTint = when (tone) {
        NutraSnackbarTone.Error -> if (colors.isDark) colors.dangerText else colors.dangerOnInk
        NutraSnackbarTone.Success, NutraSnackbarTone.Neutral -> actionColor
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = NutraTheme.shapes.lg,
        color = container,
        border = if (colors.isDark) BorderStroke(1.dp, colors.controlBorder.copy(alpha = 0.6f)) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (resolvedIcon != null) {
                Icon(resolvedIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = message,
                style = NutraTheme.typography.body,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            if (actionLabel != null && onAction != null) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 44.dp)
                        .clip(NutraTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        style = NutraTheme.typography.label,
                        color = actionColor,
                    )
                }
            }
        }
    }
}

private data class SnackbarSnapshot(
    val message: String,
    val actionLabel: String?,
    val hasAction: Boolean,
    val icon: ImageVector?,
    val tone: NutraSnackbarTone,
)

@Composable
fun NutraSnackbarHost(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    tone: NutraSnackbarTone = NutraSnackbarTone.Neutral,
) {
    val latestOnAction by rememberUpdatedState(onAction)
    val liveSnapshot = if (visible && message.isNotBlank()) {
        SnackbarSnapshot(
            message = message,
            actionLabel = actionLabel,
            hasAction = onAction != null,
            icon = icon,
            tone = tone,
        )
    } else {
        null
    }
    var cached by remember { mutableStateOf<SnackbarSnapshot?>(null) }
    LaunchedEffect(liveSnapshot) {
        if (liveSnapshot != null) {
            cached = liveSnapshot
        }
    }
    val displayed = liveSnapshot ?: cached
    AnimatedVisibility(
        visible = visible && displayed != null,
        modifier = modifier.widthIn(max = NutraContentMaxWidth),
        enter = fadeIn(tween(180)) + slideInVertically(spring(stiffness = 500f)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        val snapshot = displayed ?: return@AnimatedVisibility
        NutraSnackbar(
            message = snapshot.message,
            actionLabel = snapshot.actionLabel,
            onAction = if (snapshot.hasAction) {
                { latestOnAction?.invoke() }
            } else {
                null
            },
            icon = snapshot.icon,
            tone = snapshot.tone,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlays
// ─────────────────────────────────────────────────────────────────────────────
