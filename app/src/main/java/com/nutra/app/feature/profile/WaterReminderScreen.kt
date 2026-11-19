package com.nutra.app.feature.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.notification.WaterReminderBounds
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.model.WaterReminderSettings
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

private enum class ReminderTimeField(val title: String) {
    START("Başlangıç saati"),
    END("Bitiş saati"),
}

private enum class PendingPermissionAction {
    NONE,
    ENABLE_TOGGLE,
    SAVE_ENABLED,
}

private fun hasSystemNotificationPermission(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
fun WaterReminderScreen(
    initialSettings: WaterReminderSettings,
    waterGoalMl: Int,
    onNavigateBack: () -> Unit,
    onSave: (WaterReminderSettings) -> Unit,
) {
    val colors = NutraTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val sanitizedInitial = remember(initialSettings) {
        WaterReminderScheduleCalculator.sanitize(initialSettings)
    }

    var enabled by rememberSaveable { mutableStateOf(sanitizedInitial.enabled) }
    var intervalMinutes by rememberSaveable { mutableIntStateOf(sanitizedInitial.intervalMinutes) }
    var isCustomMode by rememberSaveable {
        mutableStateOf(sanitizedInitial.intervalMinutes !in WaterReminderBounds.PRESET_INTERVALS_MINUTES)
    }
    var startTotalMinutes by rememberSaveable {
        mutableIntStateOf(sanitizedInitial.startHour * 60 + sanitizedInitial.startMinute)
    }
    var endTotalMinutes by rememberSaveable {
        mutableIntStateOf(sanitizedInitial.endHour * 60 + sanitizedInitial.endMinute)
    }
    var stopWhenGoalReached by rememberSaveable { mutableStateOf(sanitizedInitial.stopWhenGoalReached) }

    var hasNotificationPermission by remember { mutableStateOf(hasSystemNotificationPermission(context)) }
    var permissionDeniedNotice by rememberSaveable { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf(PendingPermissionAction.NONE) }

    var showCustomIntervalDialog by remember { mutableStateOf(false) }
    var activeTimeField by remember { mutableStateOf<ReminderTimeField?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val draftSettings = remember(
        enabled,
        intervalMinutes,
        startTotalMinutes,
        endTotalMinutes,
        stopWhenGoalReached,
    ) {
        WaterReminderScheduleCalculator.sanitize(
            WaterReminderSettings(
                enabled = enabled,
                intervalMinutes = intervalMinutes,
                startHour = startTotalMinutes / 60,
                startMinute = startTotalMinutes % 60,
                endHour = endTotalMinutes / 60,
                endMinute = endTotalMinutes % 60,
                stopWhenGoalReached = stopWhenGoalReached,
            ),
        )
    }

    val hasUnsavedChanges = draftSettings != sanitizedInitial

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val effectiveGranted = granted && hasSystemNotificationPermission(context)
        hasNotificationPermission = effectiveGranted
        if (effectiveGranted) {
            permissionDeniedNotice = false
            when (pendingPermissionAction) {
                PendingPermissionAction.ENABLE_TOGGLE -> {
                    enabled = true
                }
                PendingPermissionAction.SAVE_ENABLED -> {
                    enabled = true
                    onSave(draftSettings.copy(enabled = true))
                }
                PendingPermissionAction.NONE -> Unit
            }
        } else {
            permissionDeniedNotice = true
            enabled = false
        }
        pendingPermissionAction = PendingPermissionAction.NONE
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val nowGranted = hasSystemNotificationPermission(context)
        hasNotificationPermission = nowGranted
        if (nowGranted) {
            permissionDeniedNotice = false
        }
    }

    fun requestExit() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    fun handleToggleEnabled(nextEnabled: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (!nextEnabled) {
            enabled = false
            return
        }
        val currentlyGranted = hasSystemNotificationPermission(context)
        hasNotificationPermission = currentlyGranted
        if (currentlyGranted) {
            permissionDeniedNotice = false
            enabled = true
        } else {
            pendingPermissionAction = PendingPermissionAction.ENABLE_TOGGLE
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleSave() {
        if (draftSettings.enabled && !hasSystemNotificationPermission(context)) {
            hasNotificationPermission = false
            pendingPermissionAction = PendingPermissionAction.SAVE_ENABLED
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        onSave(draftSettings)
    }

    BackHandler(onBack = ::requestExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
        ) {
            // Top bar (identical layout to EditBodyProfileScreen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.section, bottom = NutraTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
            ) {
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    onClick = ::requestExit,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Su hatırlatıcı",
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Gün içinde düzenli su içmeni hatırlatır",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.md, bottom = NutraTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xxl),
            ) {
                // 1. Master toggle card
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraCard(contentPadding = PaddingValues(0.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NutraTheme.shapes.lg)
                                .toggleable(
                                    value = enabled,
                                    role = Role.Switch,
                                    onValueChange = ::handleToggleEnabled,
                                )
                                .padding(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Su hatırlatıcı",
                                    style = NutraTheme.typography.label,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = if (enabled) {
                                        "${WaterReminderScheduleCalculator.formatInterval(draftSettings.intervalMinutes)} aralıkla · ${WaterReminderScheduleCalculator.formatClock(draftSettings.startHour, draftSettings.startMinute)}–${WaterReminderScheduleCalculator.formatClock(draftSettings.endHour, draftSettings.endMinute)}"
                                    } else {
                                        "Düzenli bildirim almak için aç"
                                    },
                                    style = NutraTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            NutraSwitch(
                                checked = enabled,
                                onCheckedChange = null,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !hasNotificationPermission && (permissionDeniedNotice || enabled),
                        enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                        exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                    ) {
                        NutraCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "Bildirim izni gerekli",
                                        style = NutraTheme.typography.label,
                                        color = colors.warningText,
                                    )
                                    Text(
                                        text = "Hatırlatma bildirimleri için sistem ayarlarından izin ver.",
                                        style = NutraTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                NutraButton(
                                    text = "Ayarları aç",
                                    onClick = { openAppNotificationSettings(context) },
                                    style = NutraButtonStyle.Secondary,
                                    size = NutraButtonSize.Small,
                                )
                            }
                        }
                    }
                }

                // 2. Period selection & custom interval
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Hatırlatma periyodu")

                    NutraCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val firstRowPresets = listOf(30, 60, 90)
                        val secondRowPresets = listOf(120, 180)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            firstRowPresets.forEach { preset ->
                                NutraChip(
                                    text = WaterReminderScheduleCalculator.formatInterval(preset),
                                    selected = !isCustomMode && intervalMinutes == preset,
                                    onClick = {
                                        isCustomMode = false
                                        intervalMinutes = preset
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            secondRowPresets.forEach { preset ->
                                NutraChip(
                                    text = WaterReminderScheduleCalculator.formatInterval(preset),
                                    selected = !isCustomMode && intervalMinutes == preset,
                                    onClick = {
                                        isCustomMode = false
                                        intervalMinutes = preset
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            NutraChip(
                                text = "Özel",
                                selected = isCustomMode,
                                onClick = { isCustomMode = true },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        AnimatedVisibility(
                            visible = isCustomMode,
                            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NutraDivider()
                                NutraMetricStepperRow(
                                    label = "Özel periyot",
                                    supportingText = "10 – 360 dk arası",
                                    valueText = WaterReminderScheduleCalculator.formatInterval(intervalMinutes),
                                    onDecrement = {
                                        intervalMinutes = (intervalMinutes - WaterReminderBounds.CUSTOM_STEP_MINUTES)
                                            .coerceIn(
                                                WaterReminderBounds.MIN_INTERVAL_MINUTES,
                                                WaterReminderBounds.MAX_INTERVAL_MINUTES,
                                            )
                                    },
                                    onIncrement = {
                                        intervalMinutes = (intervalMinutes + WaterReminderBounds.CUSTOM_STEP_MINUTES)
                                            .coerceIn(
                                                WaterReminderBounds.MIN_INTERVAL_MINUTES,
                                                WaterReminderBounds.MAX_INTERVAL_MINUTES,
                                            )
                                    },
                                    onValueClick = { showCustomIntervalDialog = true },
                                    valueMinWidth = 86.dp,
                                )
                            }
                        }
                    }
                }

                // 3. Active hours & smart stop toggle
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Aktif saatler")

                    NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutraMetricStepperRow(
                                label = "Başlangıç",
                                valueText = WaterReminderScheduleCalculator.formatClockFromMinutes(startTotalMinutes),
                                onDecrement = {
                                    startTotalMinutes = (startTotalMinutes - WaterReminderBounds.TIME_STEP_MINUTES)
                                        .coerceAtLeast(WaterReminderBounds.MIN_START_TOTAL_MINUTES)
                                },
                                onIncrement = {
                                    val maxStart = (endTotalMinutes - WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES)
                                        .coerceAtMost(WaterReminderBounds.MAX_START_TOTAL_MINUTES)
                                    startTotalMinutes = (startTotalMinutes + WaterReminderBounds.TIME_STEP_MINUTES)
                                        .coerceAtMost(maxStart)
                                },
                                onValueClick = { activeTimeField = ReminderTimeField.START },
                                valueMinWidth = 76.dp,
                            )

                            NutraDivider()

                            NutraMetricStepperRow(
                                label = "Bitiş",
                                valueText = WaterReminderScheduleCalculator.formatClockFromMinutes(endTotalMinutes),
                                onDecrement = {
                                    val minEnd = (startTotalMinutes + WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES)
                                        .coerceAtLeast(WaterReminderBounds.MIN_END_TOTAL_MINUTES)
                                    endTotalMinutes = (endTotalMinutes - WaterReminderBounds.TIME_STEP_MINUTES)
                                        .coerceAtLeast(minEnd)
                                },
                                onIncrement = {
                                    endTotalMinutes = (endTotalMinutes + WaterReminderBounds.TIME_STEP_MINUTES)
                                        .coerceAtMost(WaterReminderBounds.MAX_END_TOTAL_MINUTES)
                                },
                                onValueClick = { activeTimeField = ReminderTimeField.END },
                                valueMinWidth = 76.dp,
                            )

                            NutraDivider()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NutraTheme.shapes.sm)
                                    .toggleable(
                                        value = stopWhenGoalReached,
                                        role = Role.Switch,
                                        onValueChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            stopWhenGoalReached = it
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "Hedefe ulaşınca durdur",
                                        style = NutraTheme.typography.label,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = "Günlük ${NutraFormatters.formatInt(waterGoalMl)} ml tamamlandığında bildirim gönderme",
                                        style = NutraTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                NutraSwitch(
                                    checked = stopWhenGoalReached,
                                    onCheckedChange = null,
                                )
                            }
                        }
                    }

                    // Subtle live schedule preview when enabled
                    AnimatedVisibility(
                        visible = enabled,
                        enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                        exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                    ) {
                        val now = remember(draftSettings) { LocalDateTime.now() }
                        val dailyCount = remember(draftSettings) {
                            WaterReminderScheduleCalculator.estimateDailyReminderCount(draftSettings)
                        }
                        val nextLabel = remember(draftSettings, now) {
                            WaterReminderScheduleCalculator.computeNextTriggerDateTime(now, draftSettings)
                                ?.let { WaterReminderScheduleCalculator.formatNextTriggerLabel(now, it) }
                        }
                        NutraCard(
                            contentPadding = PaddingValues(
                                horizontal = NutraTheme.spacing.card,
                                vertical = 14.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    NutraDot(color = colors.water)
                                    Text(
                                        text = "Günde $dailyCount hatırlatma",
                                        style = NutraTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (nextLabel != null) {
                                    Text(
                                        text = "Sonraki: $nextLabel",
                                        style = NutraTheme.typography.labelSmall,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            NutraDivider()
            NutraBottomActionBar {
                NutraButton(
                    text = "Vazgeç",
                    onClick = ::requestExit,
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Medium,
                    modifier = Modifier.weight(0.38f),
                )
                NutraButton(
                    text = "Kaydet",
                    onClick = ::handleSave,
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    hapticFeedback = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(0.62f),
                )
            }
        }
    }

    if (showDiscardDialog) {
        NutraDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "Değişikliklerden vazgeç?",
            message = "Su hatırlatıcı ayarların kaydedilmedi. Çıkmak istediğine emin misin?",
            confirmText = "Vazgeç ve çık",
            onConfirm = {
                showDiscardDialog = false
                onNavigateBack()
            },
            dismissText = "Düzenlemeye dön",
            onDismiss = { showDiscardDialog = false },
            destructive = true,
        )
    }

    if (showCustomIntervalDialog) {
        NutraNumericInputDialog(
            title = "Özel periyot",
            initialValue = intervalMinutes.toString(),
            unit = "dk",
            rangeHint = "${WaterReminderBounds.MIN_INTERVAL_MINUTES} – ${WaterReminderBounds.MAX_INTERVAL_MINUTES} dk",
            minValue = WaterReminderBounds.MIN_INTERVAL_MINUTES.toDouble(),
            maxValue = WaterReminderBounds.MAX_INTERVAL_MINUTES.toDouble(),
            allowDecimal = false,
            onDismiss = { showCustomIntervalDialog = false },
            onConfirm = { entered ->
                intervalMinutes = entered.roundToInt().coerceIn(
                    WaterReminderBounds.MIN_INTERVAL_MINUTES,
                    WaterReminderBounds.MAX_INTERVAL_MINUTES,
                )
                isCustomMode = true
                showCustomIntervalDialog = false
            },
        )
    }

    val editingTime = activeTimeField
    if (editingTime != null) {
        val initialMinutes = when (editingTime) {
            ReminderTimeField.START -> startTotalMinutes
            ReminderTimeField.END -> endTotalMinutes
        }
        val minMinutes = when (editingTime) {
            ReminderTimeField.START -> 0
            ReminderTimeField.END -> (startTotalMinutes + WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES).coerceAtMost(23 * 60 + 55)
        }
        val maxMinutes = when (editingTime) {
            ReminderTimeField.START -> (endTotalMinutes - WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES).coerceAtLeast(0)
            ReminderTimeField.END -> 23 * 60 + 55
        }
        ReminderTimePickerDialog(
            title = editingTime.title,
            initialTotalMinutes = initialMinutes,
            minTotalMinutes = minMinutes,
            maxTotalMinutes = maxMinutes,
            onDismiss = { activeTimeField = null },
            onConfirm = { selectedMinutes ->
                when (editingTime) {
                    ReminderTimeField.START -> startTotalMinutes = selectedMinutes
                    ReminderTimeField.END -> endTotalMinutes = selectedMinutes
                }
                activeTimeField = null
            },
        )
    }
}

@Composable
private fun ReminderTimePickerDialog(
    title: String,
    initialTotalMinutes: Int,
    minTotalMinutes: Int,
    maxTotalMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var totalMinutes by rememberSaveable(initialTotalMinutes) {
        mutableIntStateOf(initialTotalMinutes.coerceIn(minTotalMinutes, maxTotalMinutes))
    }
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60

    NutraDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = "Uygula",
        onConfirm = { onConfirm(totalMinutes.coerceIn(minTotalMinutes, maxTotalMinutes)) },
        dismissText = "Vazgeç",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NutraMetricStepperRow(
                label = "Saat",
                valueText = "%02d".format(Locale.US, hour),
                onDecrement = { totalMinutes = (totalMinutes - 60).coerceIn(minTotalMinutes, maxTotalMinutes) },
                onIncrement = { totalMinutes = (totalMinutes + 60).coerceIn(minTotalMinutes, maxTotalMinutes) },
                valueMinWidth = 64.dp,
            )
            NutraDivider()
            NutraMetricStepperRow(
                label = "Dakika",
                valueText = "%02d".format(Locale.US, minute),
                onDecrement = { totalMinutes = (totalMinutes - 5).coerceIn(minTotalMinutes, maxTotalMinutes) },
                onIncrement = { totalMinutes = (totalMinutes + 5).coerceIn(minTotalMinutes, maxTotalMinutes) },
                valueMinWidth = 64.dp,
            )
        }
    }
}
