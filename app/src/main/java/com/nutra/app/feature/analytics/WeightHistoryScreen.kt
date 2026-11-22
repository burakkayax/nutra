package com.nutra.app.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.BodyMetricBounds
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

@Composable
fun WeightHistoryScreen(
    entries: ImmutableList<WeightEntry>,
    todayIso: String,
    targetWeightKg: Float,
    latestWeightKg: Float,
    goalType: GoalType,
    onNavigateBack: () -> Unit,
    onAddWeight: (Float) -> Unit,
    onEditWeightEntry: (WeightEntry, Float) -> Unit,
    onDeleteWeightEntry: (WeightEntry) -> Unit,
    feedbackMessage: String? = null,
    feedbackEventId: Long = 0L,
    onClearFeedback: () -> Unit = {},
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }

    var showAddWeightDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WeightEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<WeightEntry?>(null) }

    val referenceToday = remember(todayIso) {
        runCatching { LocalDate.parse(todayIso) }.getOrElse { LocalDate.now() }
    }

    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(baseTimeoutMillis = 2800L, hasAction = false)
    LaunchedEffect(feedbackEventId, feedbackMessage) {
        if (feedbackMessage != null) {
            delay(snackbarTimeoutMs)
            onClearFeedback()
        }
    }

    val eyebrowText = remember(entries.size, targetWeightKg) {
        if (entries.isEmpty()) {
            "Henüz tartı kaydı yok"
        } else {
            "${NutraFormatters.formatInt(entries.size)} kayıt · Hedef ${NutraFormatters.formatKg(targetWeightKg)} kg"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = NutraTheme.spacing.screen,
                end = NutraTheme.spacing.screen,
                top = maxOf(defaultTopPadding, measuredHeaderHeight + 6.dp),
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.Top,
        ) {
            if (entries.isEmpty()) {
                item(key = "empty_weight_history") {
                    NutraCard {
                        NutraEmptyState(
                            icon = NutraIcons.MonitorWeight,
                            title = "Henüz tartı kaydı yok",
                            message = "Günlük tartılarını eklediğinde tüm ölçümlerini buradan görüntüleyebilir, düzenleyebilir veya silebilirsin.",
                            action = {
                                NutraButton(
                                    text = "Kilo ekle",
                                    onClick = { showAddWeightDialog = true },
                                    style = NutraButtonStyle.Primary,
                                    size = NutraButtonSize.Medium,
                                    leadingIcon = Icons.Rounded.Add,
                                )
                            },
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.id },
                    contentType = { _, _ -> "weight_history_row" },
                ) { index, entry ->
                    val isFirst = index == 0
                    val isLast = index == entries.lastIndex
                    // entries is newest-first, so index + 1 is the chronologically previous weigh-in.
                    val previousEntry = entries.getOrNull(index + 1)
                    val deltaFromPrevious = previousEntry?.let { entry.weightKg - it.weightKg }

                    val rowShape = when {
                        isFirst && isLast -> NutraTheme.shapes.lg
                        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        isLast -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RectangleShape
                    }

                    val dateText = remember(entry.dateIso, referenceToday) {
                        NutraFormatters.formatShortDateWithYearIfNeeded(entry.dateIso, referenceToday)
                    }
                    val weekdayText = remember(entry.dateIso) {
                        NutraFormatters.formatShortWeekday(entry.dateIso)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(colors.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(horizontal = NutraTheme.spacing.card, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(NutraTheme.shapes.xs)
                                    .clickable(role = Role.Button, onClickLabel = "Tartıyı düzenle") {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        editingEntry = entry
                                    }
                                    .padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = dateText,
                                        style = NutraTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (weekdayText.isNotBlank() || deltaFromPrevious != null) {
                                        val deltaLabel = deltaFromPrevious?.let { delta ->
                                            when {
                                                abs(delta) < 0.05f -> "Değişim yok"
                                                delta > 0f -> "+${NutraFormatters.formatKg(delta)} kg"
                                                else -> "${NutraFormatters.formatKg(delta)} kg"
                                            }
                                        }
                                        val deltaColor = when {
                                            deltaFromPrevious == null || abs(deltaFromPrevious) < 0.05f -> colors.textTertiary
                                            goalType == GoalType.LOSE_WEIGHT -> if (deltaFromPrevious < 0f) colors.onAccentSoft else colors.warningText
                                            goalType == GoalType.GAIN_MUSCLE -> if (deltaFromPrevious > 0f) colors.onAccentSoft else colors.warningText
                                            else -> if (abs(deltaFromPrevious) <= 0.4f) colors.onAccentSoft else colors.warningText
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (weekdayText.isNotBlank()) {
                                                Text(
                                                    text = weekdayText,
                                                    style = NutraTheme.typography.caption,
                                                    color = colors.textTertiary,
                                                    maxLines = 1,
                                                )
                                            }
                                            if (weekdayText.isNotBlank() && deltaLabel != null) {
                                                Text(
                                                    text = "·",
                                                    style = NutraTheme.typography.caption,
                                                    color = colors.textTertiary,
                                                )
                                            }
                                            if (deltaLabel != null) {
                                                Text(
                                                    text = deltaLabel,
                                                    style = NutraTheme.typography.caption,
                                                    color = deltaColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "${NutraFormatters.formatKg(entry.weightKg)} kg",
                                    style = NutraTheme.typography.numberMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                )
                            }

                            NutraIconButton(
                                icon = Icons.Rounded.Edit,
                                contentDescription = "$dateText tartısını düzenle",
                                onClick = { editingEntry = entry },
                                size = 40.dp,
                                iconSize = 17.dp,
                                containerColor = colors.surfaceMuted,
                                contentColor = colors.textSecondary,
                            )

                            NutraIconButton(
                                icon = NutraIcons.DeleteOutline,
                                contentDescription = "$dateText tartısını sil",
                                onClick = { pendingDeleteEntry = entry },
                                size = 40.dp,
                                iconSize = 17.dp,
                                containerColor = colors.dangerSoft,
                                contentColor = colors.dangerText,
                            )
                        }

                        if (!isLast) {
                            NutraDivider(startIndent = NutraTheme.spacing.card, endIndent = NutraTheme.spacing.card)
                        }
                    }
                }
            }

            item(key = "weight_history_bottom_spacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        NutraStickyScreenHeader(
            title = "Son ölçümler",
            eyebrow = eyebrowText,
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            actions = {
                NutraIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Kilo ekle",
                    onClick = { showAddWeightDialog = true },
                    size = 44.dp,
                    iconSize = 20.dp,
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NutraSnackbarHost(
            visible = feedbackMessage != null,
            message = feedbackMessage.orEmpty(),
            tone = NutraSnackbarTone.Success,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 14.dp),
        )
    }

    if (showAddWeightDialog) {
        LogWeightDialog(
            initialWeight = latestWeightKg,
            onDismiss = { showAddWeightDialog = false },
            onSave = { weight ->
                onAddWeight(weight)
                showAddWeightDialog = false
            },
        )
    }

    editingEntry?.let { entry ->
        val dateLabel = NutraFormatters.formatShortDateWithYearIfNeeded(entry.dateIso, referenceToday)
        NutraNumericInputDialog(
            title = "$dateLabel tartısı",
            initialValue = NutraFormatters.formatKg(entry.weightKg),
            unit = "kg",
            rangeHint = "30,0 – 300,0 kg",
            minValue = BodyMetricBounds.MIN_WEIGHT_KG.toDouble(),
            maxValue = BodyMetricBounds.MAX_WEIGHT_KG.toDouble(),
            allowDecimal = true,
            onDismiss = { editingEntry = null },
            onConfirm = { entered ->
                val safe = ((entered * 10.0).roundToInt()
                    .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS)) / 10f
                onEditWeightEntry(entry, safe)
                editingEntry = null
            },
        )
    }

    pendingDeleteEntry?.let { entry ->
        val dateLabel = NutraFormatters.formatShortDateWithYearIfNeeded(entry.dateIso, referenceToday)
        NutraDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = "Tartı kaydını sil?",
            message = "$dateLabel tarihindeki ${NutraFormatters.formatKg(entry.weightKg)} kg tartı kaydı kaldırılacak.",
            confirmText = "Sil",
            onConfirm = {
                onDeleteWeightEntry(entry)
                pendingDeleteEntry = null
            },
            dismissText = "Vazgeç",
            onDismiss = { pendingDeleteEntry = null },
            destructive = true,
        )
    }
}
