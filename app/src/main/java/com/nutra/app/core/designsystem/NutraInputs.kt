package com.nutra.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** iOS-style segmented control with sliding thumb. */
@Composable
fun <T> NutraSegmentedControl(
    items: ImmutableList<T>,
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
            .heightIn(min = 48.dp)
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
            .heightIn(min = 42.dp)
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
        targetValue = if (selected) colors.ink else colors.controlBorder,
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
            uncheckedBorderColor = colors.controlBorder,
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
    size: Dp = 44.dp,
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
            .focusable(enabled = enabled, interactionSource = interactionSource)
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
                        val slop = viewConfiguration.touchSlop
                        val boxSize = this.size
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                releasedNormally = change != null &&
                                    !change.isConsumed &&
                                    change.position.x in -slop..(boxSize.width + slop) &&
                                    change.position.y in -slop..(boxSize.height + slop)
                                break
                            }
                            if (change.isConsumed ||
                                change.position.x !in -slop..(boxSize.width + slop) ||
                                change.position.y !in -slop..(boxSize.height + slop)
                            ) {
                                releasedNormally = false
                                break
                            }
                            if (event.type == PointerEventType.Release) {
                                releasedNormally = true
                                break
                            }
                        }
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
            isFocused -> colors.controlBorderFocused
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
