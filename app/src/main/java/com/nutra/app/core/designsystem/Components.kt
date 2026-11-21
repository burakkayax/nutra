package com.nutra.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Shared enums
// ─────────────────────────────────────────────────────────────────────────────

enum class NutraButtonStyle { Primary, Secondary, Destructive }

enum class NutraSnackbarTone { Neutral, Success, Error }

/** Maximum content width for adaptive centering on landscape phones and tablets. */
val NutraContentMaxWidth: Dp = 640.dp

enum class NutraButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    Large(56.dp, 24.dp, 20.dp),
    Medium(48.dp, 20.dp, 18.dp),
    Small(42.dp, 14.dp, 16.dp),
}

/** Animation spec used for all progress indicators. */
private val ProgressSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
private val PressScaleSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)
private val RatioSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 360f)
private val SegmentThumbSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

private fun Modifier.nutraRoundedStroke(
    color: () -> Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokeColor = color()
    if (strokeColor.alpha > 0f) {
        val strokePx = strokeWidth.toPx()
        val half = strokePx / 2f
        val radius = cornerRadius.toPx()
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(half, half),
            size = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokePx),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Containers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Borderless surface card. Use sparingly: group related content, don't box every row.
 */
@Composable
fun NutraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = NutraTheme.colors.surface,
    shape: Shape = NutraTheme.shapes.lg,
    contentPadding: PaddingValues = PaddingValues(NutraTheme.spacing.card),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    } else {
        Column(
            modifier = modifier
                .clip(shape)
                .background(color)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Large screen title with optional eyebrow (small line above) and trailing actions.
 */
@Composable
fun NutraScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color = NutraTheme.colors.textSecondary,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = NutraTheme.typography.bodySmall,
                    color = eyebrowColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = NutraTheme.typography.titleLarge,
                color = NutraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Section title with optional pill action button on the right. */
@Composable
fun NutraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionContainerColor: Color = NutraTheme.colors.surface,
    actionContentColor: Color = NutraTheme.colors.textPrimary,
    actionHaptic: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = NutraTheme.typography.titleMedium,
                color = NutraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(actionContainerColor)
                    .clickable(role = Role.Button) {
                        if (actionHaptic != null) haptic.performHapticFeedback(actionHaptic)
                        onAction()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = actionContentColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = actionLabel,
                    style = NutraTheme.typography.labelSmall,
                    color = actionContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun NutraDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp,
    color: Color = NutraTheme.colors.divider,
) {
    Box(
        modifier = modifier
            .padding(start = startIndent, end = endIndent)
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

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

@Composable
fun NutraLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = NutraTheme.colors.accent,
    trackColor: Color = NutraTheme.colors.surfaceMuted,
    height: Dp = 6.dp,
    overflowProgress: Float = 0f,
    overflowColor: Color = NutraTheme.colors.warning,
    contentDescription: String? = null,
) {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val safeOverflow = if (overflowProgress.isFinite()) overflowProgress.coerceIn(0f, 1f) else 0f
    val animatedState = animateFloatAsState(safeProgress, ProgressSpring, label = "bar")
    val animatedOverflowState = animateFloatAsState(safeOverflow, ProgressSpring, label = "barOverflow")
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .drawBehind {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                drawRoundRect(color = trackColor, cornerRadius = corner)
                val fraction = animatedState.value
                if (fraction > 0.005f && size.width > 0f) {
                    val fillWidth = (size.width * fraction).coerceAtLeast(size.height.coerceAtMost(size.width))
                    drawRoundRect(
                        color = color,
                        size = Size(fillWidth, size.height),
                        cornerRadius = corner,
                    )
                }
                val overflowFraction = animatedOverflowState.value
                if (overflowFraction > 0.005f && size.width > 0f) {
                    val overflowWidth = (size.width * (0.14f + overflowFraction * 0.22f))
                        .coerceIn(size.height.coerceAtMost(size.width), size.width * 0.36f)
                    drawRoundRect(
                        color = overflowColor,
                        topLeft = Offset(size.width - overflowWidth, 0f),
                        size = Size(overflowWidth, size.height),
                        cornerRadius = corner,
                    )
                }
            },
    )
}

/** Compact macro statistic: label, "consumed / target", thin bar. */
@Composable
fun NutraMacroStat(
    label: String,
    consumed: Float,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "g",
) {
    val safeTarget = target.coerceAtLeast(1)
    val roundedConsumed = consumed.roundToInt()
    val ratio = consumed / safeTarget.toFloat()
    val isOver = roundedConsumed > safeTarget
    val valueColor by animateColorAsState(
        targetValue = if (isOver) NutraTheme.colors.warningText else NutraTheme.colors.textPrimary,
        animationSpec = tween(180),
        label = "macroValueColor",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textSecondary,
            maxLines = 1,
        )
        Row {
            Text(
                text = NutraFormatters.formatInt(roundedConsumed),
                style = NutraTheme.typography.numberSmall,
                color = valueColor,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " / ${NutraFormatters.formatInt(safeTarget)} $unit",
                style = NutraTheme.typography.caption,
                color = NutraTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
        }
        NutraLinearProgress(
            progress = ratio,
            color = color,
            height = 4.dp,
            overflowProgress = if (isOver) (ratio - 1f).coerceAtLeast(0.05f) else 0f,
            overflowColor = NutraTheme.colors.warning,
            contentDescription = "$label $roundedConsumed / $safeTarget $unit",
        )
    }
}

/** Spring-animated 3-segment macro energy share bar shared across Analytics and Onboarding. */
@Composable
fun NutraStackedRatioBar(
    proteinRatio: Float,
    carbsRatio: Float,
    fatRatio: Float,
    hasData: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    contentDescription: String? = null,
) {
    val colors = NutraTheme.colors
    val pColor = colors.protein.copy(alpha = alpha)
    val cColor = colors.carbs.copy(alpha = alpha)
    val fColor = colors.fat.copy(alpha = alpha)
    val trackColor = colors.surfaceMuted

    val animPState = animateFloatAsState(if (hasData) proteinRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioP")
    val animCState = animateFloatAsState(if (hasData) carbsRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioC")
    val animFState = animateFloatAsState(if (hasData) fatRatio.coerceAtLeast(0f) else 0f, RatioSpring, label = "macroRatioF")

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(NutraTheme.shapes.pill)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val pRaw = animPState.value
                val cRaw = animCState.value
                val fRaw = animFState.value
                if (!hasData && pRaw + cRaw + fRaw < 0.01f) {
                    drawRect(color = trackColor)
                    return@drawBehind
                }
                val pVal = if (pRaw > 0.005f) pRaw.coerceAtLeast(0.01f) else 0f
                val cVal = if (cRaw > 0.005f) cRaw.coerceAtLeast(0.01f) else 0f
                val fVal = if (fRaw > 0.005f) fRaw.coerceAtLeast(0.01f) else 0f
                val total = pVal + cVal + fVal
                if (total <= 0.001f) {
                    drawRect(color = trackColor)
                    return@drawBehind
                }

                val gapPx = 2.dp.toPx()
                val activeCount = (if (pVal > 0f) 1 else 0) + (if (cVal > 0f) 1 else 0) + (if (fVal > 0f) 1 else 0)
                val usableWidth = (size.width - gapPx * (activeCount - 1).coerceAtLeast(0)).coerceAtLeast(0f)
                var x = 0f

                if (pVal > 0f) {
                    val w = usableWidth * (pVal / total)
                    drawRect(color = pColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (cVal > 0f) {
                    val w = usableWidth * (cVal / total)
                    drawRect(color = cColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (fVal > 0f) {
                    val w = (size.width - x).coerceAtLeast(0f)
                    drawRect(color = fColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                }
            },
    )
}

/** Shared pulse animation alpha for loading skeleton placeholders. */
@Composable
fun rememberSkeletonPulseAlpha(label: String = "nutraSkeletonPulse"): State<Float> {
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

/** Small colored dot used as a legend marker. */
@Composable
fun NutraDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

// ─────────────────────────────────────────────────────────────────────────────
// Selection controls
// ─────────────────────────────────────────────────────────────────────────────

/** Pill chip. Selected = ink filled, unselected = muted. */
@Composable
fun NutraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
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
            .heightIn(min = 40.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind { drawRect(bgState.value) }
            .selectable(selected = selected, role = Role.Tab) {
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

/** iOS-style segmented control with sliding thumb. */
@Composable
fun <T> NutraSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val segmentThumb = colors.segmentThumb
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val holdHighlightBase = colors.ink.copy(alpha = if (colors.isDark) 0.12f else 0.07f)
    val layoutDirection = LocalLayoutDirection.current

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = SegmentThumbSpring,
        label = "segThumbIndex",
    )
    val gap = 2.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(NutraTheme.shapes.pill)
            .background(colors.surfaceMuted)
            .padding(3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBehind {
                    val count = items.size
                    val gapPx = gap.toPx()
                    val slotWidth = ((size.width - gapPx * (count - 1)) / count).coerceAtLeast(0f)
                    val visualIndex = if (layoutDirection == LayoutDirection.Rtl) {
                        (count - 1) - animatedIndexState.value
                    } else {
                        animatedIndexState.value
                    }
                    val x = visualIndex * (slotWidth + gapPx)
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(
                        color = segmentThumb,
                        topLeft = Offset(x, 0f),
                        size = Size(slotWidth, size.height),
                        cornerRadius = corner,
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                NutraSegmentedControlItem(
                    label = labelProvider(item),
                    isSelected = isSelected,
                    selectedColor = textPrimary,
                    unselectedColor = textSecondary,
                    holdHighlightBase = holdHighlightBase,
                    onClick = {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemSelected(item)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NutraSegmentedControlItem(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    holdHighlightBase: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdAlphaState = rememberHoldHighlightAlpha(
        interactionSource = interactionSource,
        label = "segHoldAlpha",
    )
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed && !isSelected) 0.97f else 1f,
        animationSpec = PressScaleSpring,
        label = "segPressScale",
    )
    val fg by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(180),
        label = "segFg",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 38.dp)
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(NutraTheme.shapes.pill)
            .drawBehind {
                val a = holdAlphaState.value
                if (a > 0.005f) {
                    drawRect(holdHighlightBase.copy(alpha = holdHighlightBase.alpha * a))
                }
            }
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NutraTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Animated circular selection indicator used in selectable cards, radios and checkboxes. */
@Composable
fun NutraSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    iconSize: Dp = 14.dp,
) {
    val colors = NutraTheme.colors
    val bgState = animateColorAsState(
        targetValue = if (selected) colors.ink else Color.Transparent,
        animationSpec = tween(160),
        label = "selCircleBg",
    )
    val borderState = animateColorAsState(
        targetValue = if (selected) colors.ink else colors.divider,
        animationSpec = tween(160),
        label = "selCircleBorder",
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val radius = this.size.minDimension / 2f
                val strokePx = 1.5.dp.toPx()
                val fill = bgState.value
                if (fill.alpha > 0f) {
                    drawCircle(color = fill, radius = radius)
                }
                drawCircle(
                    color = borderState.value,
                    radius = (radius - strokePx / 2f).coerceAtLeast(0f),
                    style = Stroke(width = strokePx),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(tween(150), initialScale = 0.6f) + fadeIn(tween(140)),
            exit = scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onInk,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Large selectable option card (e.g. goal or activity level).
 * Selected state uses an ink outline + check mark; no color flooding.
 */
@Composable
fun NutraSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = NutraTheme.colors.textPrimary,
    iconContainer: Color = NutraTheme.colors.surfaceMuted,
) {
    val haptic = LocalHapticFeedback.current
    val borderColorState = animateColorAsState(
        if (selected) NutraTheme.colors.ink else Color.Transparent,
        tween(160),
        label = "selBorder",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NutraTheme.shapes.lg)
            .background(NutraTheme.colors.surface)
            .nutraRoundedStroke(color = { borderColorState.value }, cornerRadius = 24.dp, strokeWidth = 1.5.dp)
            .selectable(selected = selected, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            NutraIconBadge(
                icon = icon,
                tint = iconTint,
                container = iconContainer,
                shape = NutraTheme.shapes.xs,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NutraTheme.typography.titleSmall, color = NutraTheme.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = NutraTheme.typography.bodySmall, color = NutraTheme.colors.textSecondary)
            }
        }
        NutraSelectionIndicator(selected = selected)
    }
}

@Composable
fun NutraSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NutraTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = if (colors.isDark) colors.textSecondary else colors.surface,
            uncheckedTrackColor = colors.surfaceMuted,
            uncheckedBorderColor = if (colors.isDark) colors.segmentThumb else colors.divider,
        ),
        thumbContent = null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Steppers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NutraStepperButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = NutraTheme.colors.surfaceMuted,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = PressScaleSpring,
        label = "stepperPressScale",
    )
    val latestOnClick by rememberUpdatedState(onClick)
    val latestEnabled by rememberUpdatedState(enabled)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.35f
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(containerColor)
            .indication(interactionSource, LocalIndication.current)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                if (enabled) {
                    onClick {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnClick()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    val pressEmitJob = scope.launch { interactionSource.emit(press) }
                    var didRepeat = false
                    val repeatJob = scope.launch {
                        delay(360L)
                        var ticks = 0
                        while (isActive && latestEnabled) {
                            didRepeat = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnClick()
                            ticks++
                            delay(if (ticks >= 8) 55L else 95L)
                        }
                    }
                    var releasedNormally = false
                    try {
                        val up = waitForUpOrCancellation()
                        releasedNormally = up != null
                    } finally {
                        repeatJob.cancel()
                        scope.launch {
                            pressEmitJob.join()
                            interactionSource.emit(
                                if (releasedNormally) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                            )
                        }
                    }
                    if (releasedNormally && !didRepeat && latestEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NutraTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Label on the left, − [pillbox value] + on the right. */
@Composable
fun NutraMetricStepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    accentColor: Color? = null,
    onValueClick: (() -> Unit)? = null,
    valueContainerColor: Color = NutraTheme.colors.surfaceMuted,
    valueMinWidth: Dp = 68.dp,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (accentColor != null) NutraDot(accentColor)
            Column {
                Text(label, style = NutraTheme.typography.titleSmall, color = NutraTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (supportingText != null) {
                    Text(supportingText, style = NutraTheme.typography.caption, color = NutraTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        NutraStepperButton(NutraIcons.Remove, "$label azalt", onDecrement, size = NutraButtonSize.Small.height)
        if (onValueClick != null) {
            val valueInteractionSource = remember { MutableInteractionSource() }
            val isValuePressed by valueInteractionSource.collectIsPressedAsState()
            val valuePressScaleState = animateFloatAsState(
                targetValue = if (isValuePressed) 0.97f else 1f,
                animationSpec = PressScaleSpring,
                label = "metricValuePressScale",
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueClick()
                },
                interactionSource = valueInteractionSource,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .widthIn(min = valueMinWidth)
                    .heightIn(min = NutraButtonSize.Small.height)
                    .graphicsLayer {
                        val s = valuePressScaleState.value
                        scaleX = s
                        scaleY = s
                    }
                    .semantics {
                        onClick(label = "$label değerini yaz") {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueClick()
                            true
                        }
                    },
                shape = NutraTheme.shapes.pill,
                color = valueContainerColor,
                contentColor = NutraTheme.colors.textPrimary,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = NutraButtonSize.Small.height)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = valueText,
                        style = NutraTheme.typography.numberSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .widthIn(min = valueMinWidth)
                    .heightIn(min = NutraButtonSize.Small.height),
                shape = NutraTheme.shapes.pill,
                color = valueContainerColor,
                contentColor = NutraTheme.colors.textPrimary,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = NutraButtonSize.Small.height)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = valueText,
                        style = NutraTheme.typography.numberSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        NutraStepperButton(Icons.Rounded.Add, "$label artır", onIncrement, size = NutraButtonSize.Small.height)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inputs
// ─────────────────────────────────────────────────────────────────────────────

/** Filled, borderless text field. */
@Composable
fun NutraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    compact: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    textStyle: TextStyle = NutraTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = NutraTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val strokeColorState = animateColorAsState(
        targetValue = when {
            isError -> colors.danger
            isFocused -> colors.ink.copy(alpha = 0.32f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "textFieldStroke",
    )
    val resolvedTextStyle = remember(textStyle, colors.textPrimary) {
        textStyle.copy(color = colors.textPrimary)
    }
    val cursorBrush = remember(colors.accent) { SolidColor(colors.accent) }
    val startPad = if (compact) 12.dp else 16.dp
    val endPad = when {
        trailing != null && singleLine -> 6.dp
        compact -> 12.dp
        else -> 16.dp
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = NutraTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            interactionSource = interactionSource,
            textStyle = resolvedTextStyle,
            cursorBrush = cursorBrush,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(NutraTheme.shapes.md)
                        .background(colors.surfaceMuted)
                        .nutraRoundedStroke(
                            color = { strokeColorState.value },
                            cornerRadius = 16.dp,
                            strokeWidth = 1.5.dp,
                        )
                        .padding(
                            start = startPad,
                            end = endPad,
                            top = if (singleLine) 6.dp else 14.dp,
                            bottom = if (singleLine) 6.dp else 14.dp,
                        ),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = textStyle, color = colors.textTertiary, maxLines = if (singleLine) 1 else maxLines, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                    if (suffix != null) {
                        Text(suffix, style = NutraTheme.typography.body, color = colors.textTertiary)
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lists
// ─────────────────────────────────────────────────────────────────────────────

/** Rounded square with a tinted background holding an icon. */
@Composable
fun NutraIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = NutraTheme.colors.textPrimary,
    container: Color = NutraTheme.colors.surfaceMuted,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = NutraTheme.shapes.sm,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Generic list row: [leading] · title/subtitle · [trailing]. */
@Composable
fun NutraListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = NutraTheme.colors.textPrimary,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NutraTheme.typography.titleSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = NutraTheme.typography.bodySmall, color = NutraTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Small pill label. */
@Composable
fun NutraBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NutraTheme.colors.textSecondary,
    container: Color = NutraTheme.colors.surfaceMuted,
) {
    Text(
        text = text,
        style = NutraTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(NutraTheme.shapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun NutraEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NutraIconBadge(icon = icon, size = 56.dp, iconSize = 26.dp, shape = CircleShape, tint = NutraTheme.colors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(title, style = NutraTheme.typography.titleSmall, color = NutraTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = NutraTheme.typography.bodySmall, color = NutraTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feedback
// ─────────────────────────────────────────────────────────────────────────────

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
    val resolvedIcon = icon ?: when (tone) {
        NutraSnackbarTone.Success -> Icons.Rounded.Check
        NutraSnackbarTone.Error -> Icons.Rounded.Warning
        NutraSnackbarTone.Neutral -> null
    }
    val iconTint = when (tone) {
        NutraSnackbarTone.Error -> if (colors.isDark) colors.dangerText else DarkNutraColors.danger
        NutraSnackbarTone.Success, NutraSnackbarTone.Neutral -> colors.accentOnInk
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NutraTheme.shapes.lg,
        color = container,
        border = if (colors.isDark) BorderStroke(1.dp, colors.segmentThumb) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 40.dp),
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
                Text(
                    text = actionLabel,
                    style = NutraTheme.typography.label,
                    color = colors.accentOnInk,
                    modifier = Modifier
                        .clip(NutraTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
    var cached by remember { mutableStateOf<SnackbarSnapshot?>(null) }
    if (visible && message.isNotBlank()) {
        val next = SnackbarSnapshot(
            message = message,
            actionLabel = actionLabel,
            hasAction = onAction != null,
            icon = icon,
            tone = tone,
        )
        if (cached != next) {
            cached = next
        }
    }
    AnimatedVisibility(
        visible = visible && cached != null,
        modifier = modifier.widthIn(max = NutraContentMaxWidth),
        enter = fadeIn(tween(180)) + slideInVertically(spring(stiffness = 500f)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        val snapshot = cached ?: return@AnimatedVisibility
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

internal fun createModalSheetNestedScrollGuard(
    isExpanded: () -> Boolean,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y < 0f && isExpanded()) {
            return Offset(0f, available.y)
        }
        if (available.y > 0f && source == NestedScrollSource.SideEffect) {
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        if (available.y < 0f) {
            return Velocity(0f, available.y)
        }
        if (available.y > 0f && consumed.y > 0f) {
            return Velocity(0f, available.y)
        }
        return Velocity.Zero
    }
}

/** Styled modal bottom sheet with a slim drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutraModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val nestedScrollGuard = remember(sheetState) {
        createModalSheetNestedScrollGuard(
            isExpanded = { sheetState.currentValue == SheetValue.Expanded },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = NutraTheme.shapes.sheet,
        containerColor = NutraTheme.colors.surfaceElevated,
        contentColor = NutraTheme.colors.textPrimary,
        scrimColor = NutraTheme.colors.scrim,
        tonalElevation = 0.dp,
        contentWindowInsets = contentWindowInsets,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(NutraTheme.colors.textTertiary.copy(alpha = 0.4f)),
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(nestedScrollGuard),
                content = content,
            )
        },
    )
}

/**
 * Minimal dialog: title, optional message, custom content and up to two actions.
 * The confirm action is the primary (ink) button; [destructive] turns it red.
 */
@Composable
fun NutraDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = "Vazgeç",
    onDismiss: (() -> Unit)? = onDismissRequest,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmHaptic: HapticFeedbackType? = if (destructive) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = NutraTheme.colors
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = NutraTheme.shapes.xl,
            color = colors.surfaceElevated,
            border = if (colors.isDark) BorderStroke(1.dp, colors.divider) else null,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = NutraTheme.typography.titleMedium, color = colors.textPrimary)
                    if (message != null) {
                        Text(message, style = NutraTheme.typography.body, color = colors.textSecondary)
                    }
                }
                content?.invoke(this)
                if (confirmText != null || dismissText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (dismissText != null && onDismiss != null) {
                            NutraButton(
                                text = dismissText,
                                onClick = onDismiss,
                                style = NutraButtonStyle.Secondary,
                                size = NutraButtonSize.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (confirmText != null && onConfirm != null) {
                            NutraButton(
                                text = confirmText,
                                onClick = onConfirm,
                                style = if (destructive) NutraButtonStyle.Destructive else NutraButtonStyle.Primary,
                                size = NutraButtonSize.Medium,
                                enabled = confirmEnabled,
                                hapticFeedback = confirmHaptic,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
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

/**
 * Focused dialog for manual numeric entry from metric pillboxes (supports Turkish comma & dot decimals).
 */
@Composable
fun NutraNumericInputDialog(
    title: String,
    initialValue: String,
    unit: String,
    rangeHint: String,
    minValue: Double,
    maxValue: Double,
    allowDecimal: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember(initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(0, initialValue.length),
            )
        )
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val parsed = NutraFormatters.parseLocalizedDouble(fieldValue.text)
    val isValid = parsed != null && parsed in minValue..maxValue
    val showError = fieldValue.text.isNotEmpty() && !isValid
    val strokeColorState = animateColorAsState(
        targetValue = if (showError) colors.danger else colors.ink.copy(alpha = 0.25f),
        animationSpec = tween(150),
        label = "numericStroke",
    )

    fun submitIfValid(triggerHaptic: Boolean = false) {
        if (parsed != null && isValid) {
            if (triggerHaptic) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onConfirm(parsed)
        }
    }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = "Uygula",
        onConfirm = { submitIfValid(triggerHaptic = false) },
        confirmEnabled = isValid,
        dismissText = "Vazgeç",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    val filtered = NutraFormatters.sanitizeNumericInput(next.text, allowDecimal = allowDecimal)
                    val prefixStart = NutraFormatters.sanitizeNumericInput(
                        next.text.take(next.selection.start.coerceIn(0, next.text.length)),
                        allowDecimal = allowDecimal,
                    ).length
                    val prefixEnd = NutraFormatters.sanitizeNumericInput(
                        next.text.take(next.selection.end.coerceIn(0, next.text.length)),
                        allowDecimal = allowDecimal,
                    ).length
                    fieldValue = next.copy(
                        text = filtered,
                        selection = TextRange(
                            prefixStart.coerceIn(0, filtered.length),
                            prefixEnd.coerceIn(0, filtered.length),
                        ),
                    )
                },
                singleLine = true,
                textStyle = NutraTheme.typography.numberLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitIfValid(triggerHaptic = true) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clip(NutraTheme.shapes.md)
                            .background(colors.surfaceMuted)
                            .nutraRoundedStroke(
                                color = { strokeColorState.value },
                                cornerRadius = 16.dp,
                                strokeWidth = 1.5.dp,
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = NutraTheme.typography.numberLarge,
                                    color = colors.textTertiary,
                                )
                            }
                            innerTextField()
                        }
                        if (unit.isNotEmpty()) {
                            Text(
                                text = unit,
                                style = NutraTheme.typography.titleSmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                },
            )

            Text(
                text = if (showError) "Geçerli aralık: $rangeHint" else "Aralık: $rangeHint",
                style = NutraTheme.typography.caption,
                color = if (showError) colors.dangerText else colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
