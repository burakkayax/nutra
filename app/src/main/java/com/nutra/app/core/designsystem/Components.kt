package com.nutra.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Shared enums
// ─────────────────────────────────────────────────────────────────────────────

enum class NutraButtonStyle { Primary, Secondary, Accent, Ghost, Destructive }

enum class NutraButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    Large(56.dp, 24.dp, 20.dp),
    Medium(48.dp, 20.dp, 18.dp),
    Small(40.dp, 14.dp, 16.dp),
}

/** Animation spec used for all progress indicators. */
private val ProgressSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)

private fun Modifier.nutraRoundedStroke(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
): Modifier = drawWithContent {
    drawContent()
    if (color.alpha > 0f) {
        val strokePx = strokeWidth.toPx()
        val half = strokePx / 2f
        val radius = cornerRadius.toPx()
        drawRoundRect(
            color = color,
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val (container, content) = when (style) {
        NutraButtonStyle.Primary -> if (colors.isDark) {
            colors.accent to colors.onAccent
        } else {
            colors.ink to colors.onInk
        }
        NutraButtonStyle.Secondary -> colors.surfaceMuted to colors.textPrimary
        NutraButtonStyle.Accent -> colors.accent to colors.onAccent
        NutraButtonStyle.Ghost -> Color.Transparent to colors.textPrimary
        NutraButtonStyle.Destructive -> colors.dangerSoft to colors.danger
    }
    val horizontalPad = if (style == NutraButtonStyle.Ghost) 8.dp else size.horizontalPadding
    val isActive = enabled && !loading
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = isActive,
        modifier = modifier
            .heightIn(min = size.height)
            .alpha(if (enabled) 1f else 0.4f),
        shape = NutraTheme.shapes.pill,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = size.height)
                .padding(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.iconSize),
                    color = content,
                    strokeWidth = 2.dp,
                )
            } else {
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
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
) {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val safeOverflow = if (overflowProgress.isFinite()) overflowProgress.coerceIn(0f, 1f) else 0f
    val animatedState = animateFloatAsState(safeProgress, ProgressSpring, label = "bar")
    val animatedOverflowState = animateFloatAsState(safeOverflow, ProgressSpring, label = "barOverflow")
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
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
    val ratio = consumed / safeTarget.toFloat()
    val isOver = consumed.roundToInt() > safeTarget
    val valueColor by animateColorAsState(
        targetValue = if (isOver) NutraTheme.colors.warning else NutraTheme.colors.textPrimary,
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
                text = "${consumed.roundToInt()}",
                style = NutraTheme.typography.numberSmall,
                color = valueColor,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = " / $safeTarget $unit",
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
        )
    }
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
            .height(40.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind { drawRect(bgState.value) }
            .clickable(role = Role.Tab) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = NutraTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
    val segmentThumb = NutraTheme.colors.segmentThumb
    val textPrimary = NutraTheme.colors.textPrimary
    val textSecondary = NutraTheme.colors.textSecondary
    val layoutDirection = LocalLayoutDirection.current

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val animatedIndexState = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "segThumbIndex",
    )
    val gap = 2.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(NutraTheme.shapes.pill)
            .background(NutraTheme.colors.surfaceMuted)
            .padding(3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                val fg by animateColorAsState(
                    targetValue = if (isSelected) textPrimary else textSecondary,
                    animationSpec = tween(180),
                    label = "segFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(NutraTheme.shapes.pill)
                        .clickable(role = Role.Tab) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(item)
                            }
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelProvider(item),
                        style = NutraTheme.typography.labelSmall,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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
    val bg by animateColorAsState(
        targetValue = if (selected) colors.ink else Color.Transparent,
        animationSpec = tween(160),
        label = "selCircleBg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.divider,
        animationSpec = tween(160),
        label = "selCircleBorder",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape),
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
            .nutraRoundedStroke(borderColorState.value, cornerRadius = 24.dp, strokeWidth = 1.5.dp)
            .clickable(role = Role.RadioButton) {
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
    onCheckedChange: (Boolean) -> Unit,
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
    NutraIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = 18.dp,
        containerColor = containerColor,
        enabled = enabled,
    )
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
                Text(label, style = NutraTheme.typography.titleSmall, color = NutraTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supportingText != null) {
                    Text(supportingText, style = NutraTheme.typography.caption, color = NutraTheme.colors.textTertiary, maxLines = 1)
                }
            }
        }
        NutraStepperButton(NutraIcons.Remove, "$label azalt", onDecrement, size = 40.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .widthIn(min = valueMinWidth)
                .heightIn(min = 40.dp)
                .clip(NutraTheme.shapes.pill)
                .background(valueContainerColor)
                .then(
                    if (onValueClick != null) {
                        Modifier
                            .border(1.dp, NutraTheme.colors.divider, NutraTheme.shapes.pill)
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onValueClick()
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = valueText,
                style = NutraTheme.typography.numberSmall,
                color = NutraTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        NutraStepperButton(Icons.Rounded.Add, "$label artır", onIncrement, size = 40.dp)
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
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
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
                            color = if (isError) colors.danger else Color.Transparent,
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
fun NutraSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = NutraTheme.colors
    val container = if (colors.isDark) colors.surfaceMuted else colors.ink
    val contentColor = if (colors.isDark) colors.textPrimary else colors.onInk

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
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
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
                    color = colors.accent,
                    modifier = Modifier
                        .clip(NutraTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun NutraSnackbarHost(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(spring(stiffness = 500f)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        NutraSnackbar(message = message, actionLabel = actionLabel, onAction = onAction, icon = icon)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlays
// ─────────────────────────────────────────────────────────────────────────────

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
                    .background(NutraTheme.colors.divider),
            )
        },
        content = content,
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
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = NutraTheme.shapes.xl,
            color = NutraTheme.colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = NutraTheme.typography.titleMedium, color = NutraTheme.colors.textPrimary)
                    if (message != null) {
                        Text(message, style = NutraTheme.typography.body, color = NutraTheme.colors.textSecondary)
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

    fun submitIfValid() {
        if (parsed != null && isValid) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onConfirm(parsed)
        }
    }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = "Uygula",
        onConfirm = ::submitIfValid,
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
                    onDone = { submitIfValid() },
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
                                color = if (showError) colors.danger else Color.Transparent,
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
                color = if (showError) colors.danger else colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
