package com.nutra.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
                    val isCompactOrLarge = isCompactWidthOrLargeFont()
                    val hasBoth = confirmText != null && onConfirm != null && dismissText != null && onDismiss != null
                    val stackButtons = hasBoth && (
                        isCompactOrLarge || confirmText.length + dismissText.length > 22
                        )
                    if (stackButtons) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NutraButton(
                                text = confirmText,
                                onClick = onConfirm,
                                style = if (destructive) NutraButtonStyle.Destructive else NutraButtonStyle.Primary,
                                size = NutraButtonSize.Medium,
                                enabled = confirmEnabled,
                                hapticFeedback = confirmHaptic,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NutraButton(
                                text = dismissText,
                                onClick = onDismiss,
                                style = NutraButtonStyle.Secondary,
                                size = NutraButtonSize.Medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
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
        targetValue = if (showError) colors.danger else colors.controlBorderFocused,
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
