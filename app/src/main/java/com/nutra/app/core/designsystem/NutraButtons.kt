package com.nutra.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class NutraButtonStyle { Primary, Secondary, Destructive }

enum class NutraButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    Large(56.dp, 24.dp, 20.dp),
    Medium(48.dp, 20.dp, 18.dp),
    Small(42.dp, 14.dp, 16.dp),
}

@Composable
fun NutraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NutraButtonStyle = NutraButtonStyle.Primary,
    size: NutraButtonSize = NutraButtonSize.Large,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    hapticFeedback: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val (enabledContainer, enabledContent) = when (style) {
        NutraButtonStyle.Primary -> colors.ink to colors.onInk
        NutraButtonStyle.Secondary -> colors.surfaceMuted to colors.textPrimary
        NutraButtonStyle.Destructive -> colors.dangerSoft to colors.dangerText
    }
    val container = if (enabled || loading) enabledContainer else colors.surfaceMuted
    val content = if (enabled || loading) enabledContent else colors.textTertiary
    val isActive = enabled && !loading
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed && isActive) 0.97f else 1f,
        animationSpec = PressScaleSpring,
        label = "btnPressScale",
    )
    Surface(
        onClick = {
            if (hapticFeedback != null) haptic.performHapticFeedback(hapticFeedback)
            onClick()
        },
        enabled = isActive,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = size.height)
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            },
        shape = NutraTheme.shapes.pill,
        color = container,
        contentColor = content,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = size.height)
                .padding(horizontal = size.horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.graphicsLayer { alpha = if (loading) 0f else 1f },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(size.iconSize))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = if (size == NutraButtonSize.Small) NutraTheme.typography.labelSmall else NutraTheme.typography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(size.iconSize))
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.iconSize),
                    color = content,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/** Circular icon button. Defaults to a muted filled circle. */
@Composable
fun NutraIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = NutraTheme.colors.surfaceMuted,
    contentColor: Color = NutraTheme.colors.textPrimary,
    enabled: Boolean = true,
    hapticFeedback: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = PressScaleSpring,
        label = "iconBtnPressScale",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.35f
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (hapticFeedback != null) haptic.performHapticFeedback(hapticFeedback)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress
// ─────────────────────────────────────────────────────────────────────────────

/** Pill chip. Selected = ink filled, unselected = muted. */
@Composable
fun NutraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    role: Role = Role.Checkbox,
) {
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val bgState = animateColorAsState(
        targetValue = if (selected) colors.ink else colors.surfaceMuted,
        animationSpec = tween(150),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) colors.onInk else colors.textSecondary,
        animationSpec = tween(150),
        label = "chipFg",
    )
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 44.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind { drawRect(bgState.value) }
            .selectable(selected = selected, role = role) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = NutraTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Returns an animated highlight alpha (0f..1f) that only activates when the user holds down
 * beyond [holdDelayMs], preventing a flash on quick taps where a sliding pill already animates.
 */
@Composable
fun rememberHoldHighlightAlpha(
    interactionSource: InteractionSource,
    holdDelayMs: Long = 130L,
    label: String = "holdHighlightAlpha",
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    var isHolding by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(holdDelayMs)
            isHolding = true
        } else {
            isHolding = false
        }
    }
    return animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(durationMillis = if (isHolding) 140 else 110),
        label = label,
    )
}

/** Sticky bottom action area for sheets/screens (respects nav bar insets). */
@Composable
fun NutraBottomActionBar(
    modifier: Modifier = Modifier,
    includeNavigationInsets: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NutraTheme.colors.surfaceElevated)
            .then(if (includeNavigationInsets) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
