package com.nutra.app.feature.profile

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.core.notification.WaterReminderBounds
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.core.notification.WaterReminderScheduler
import com.nutra.app.domain.model.WaterReminderSettings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

private val FIRST_ROW_INTERVAL_PRESETS = listOf(30, 60, 90)
private val SECOND_ROW_INTERVAL_PRESETS = listOf(120, 180)

private enum class ReminderTimeField(val title: String) {
    START("Başlangıç saati"),
    END("Bitiş saati"),
}

private enum class NotificationAccessState {
    ALLOWED,
    PERMISSION_MISSING,
    CHANNEL_MUTED,
}

private fun inspectNotificationAccess(context: Context): NotificationAccessState {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return NotificationAccessState.PERMISSION_MISSING
    }
    val channel = context.getSystemService(NotificationManager::class.java)
        ?.getNotificationChannel(WaterReminderScheduler.CHANNEL_ID)
    return if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
        NotificationAccessState.CHANNEL_MUTED
    } else {
        NotificationAccessState.ALLOWED
    }
}

private fun openWaterNotificationSettings(context: Context, accessState: NotificationAccessState) {
    val intent = if (accessState == NotificationAccessState.CHANNEL_MUTED) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, WaterReminderScheduler.CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(intent) }
}

@Composable
fun WaterReminderScreen(
    initialSettings: WaterReminderSettings,
    waterGoalMl: Int,
    onNavigateBack: () -> Unit,
    onSettingsChange: (WaterReminderSettings) -> Unit,
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

    var accessState by remember { mutableStateOf(inspectNotificationAccess(context)) }
    val hasNotificationPermission = accessState == NotificationAccessState.ALLOWED
    var permissionDeniedNotice by rememberSaveable { mutableStateOf(false) }
    var pendingEnableAfterPermission by rememberSaveable { mutableStateOf(false) }

    var showCustomIntervalDialog by remember { mutableStateOf(false) }
    var activeTimeField by remember { mutableStateOf<ReminderTimeField?>(null) }
    var liveNow by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        liveNow = LocalDateTime.now()
        while (true) {
            delay(30_000L)
            liveNow = LocalDateTime.now()
        }
    }

    fun buildSanitizedSettings(
        nextEnabled: Boolean = enabled,
        nextIntervalMinutes: Int = intervalMinutes,
        nextStartTotalMinutes: Int = startTotalMinutes,
        nextEndTotalMinutes: Int = endTotalMinutes,
        nextStopWhenGoalReached: Boolean = stopWhenGoalReached,
    ): WaterReminderSettings = WaterReminderScheduleCalculator.sanitize(
        WaterReminderSettings(
            enabled = nextEnabled,
            intervalMinutes = nextIntervalMinutes,
            startHour = nextStartTotalMinutes / 60,
            startMinute = nextStartTotalMinutes % 60,
            endHour = nextEndTotalMinutes / 60,
            endMinute = nextEndTotalMinutes % 60,
            stopWhenGoalReached = nextStopWhenGoalReached,
        ),
    )

    fun persistSettings(
        nextEnabled: Boolean = enabled,
        nextIntervalMinutes: Int = intervalMinutes,
        nextStartTotalMinutes: Int = startTotalMinutes,
        nextEndTotalMinutes: Int = endTotalMinutes,
        nextStopWhenGoalReached: Boolean = stopWhenGoalReached,
    ) {
        onSettingsChange(
            buildSanitizedSettings(
                nextEnabled = nextEnabled,
                nextIntervalMinutes = nextIntervalMinutes,
                nextStartTotalMinutes = nextStartTotalMinutes,
                nextEndTotalMinutes = nextEndTotalMinutes,
                nextStopWhenGoalReached = nextStopWhenGoalReached,
            ),
        )
    }

    val draftSettings = remember(
        enabled,
        intervalMinutes,
        startTotalMinutes,
        endTotalMinutes,
        stopWhenGoalReached,
    ) {
        buildSanitizedSettings()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val nextAccess = if (granted) inspectNotificationAccess(context) else NotificationAccessState.PERMISSION_MISSING
        accessState = nextAccess
        if (nextAccess == NotificationAccessState.ALLOWED) {
            permissionDeniedNotice = false
            if (pendingEnableAfterPermission) {
                enabled = true
                persistSettings(nextEnabled = true)
            }
            pendingEnableAfterPermission = false
        } else {
            permissionDeniedNotice = true
            if (enabled) {
                enabled = false
                persistSettings(nextEnabled = false)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        liveNow = LocalDateTime.now()
        val prevAccess = accessState
        val nextAccess = inspectNotificationAccess(context)
        accessState = nextAccess
        if (nextAccess == NotificationAccessState.ALLOWED) {
            permissionDeniedNotice = false
            if (pendingEnableAfterPermission) {
                pendingEnableAfterPermission = false
                enabled = true
                persistSettings(nextEnabled = true)
            } else if (prevAccess != NotificationAccessState.ALLOWED && enabled) {
                persistSettings(nextEnabled = true)
            }
        }
    }

    fun handleToggleEnabled(nextEnabled: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (!nextEnabled) {
            pendingEnableAfterPermission = false
            if (enabled) {
                enabled = false
                persistSettings(nextEnabled = false)
            }
            return
        }
        val currentAccess = inspectNotificationAccess(context)
        accessState = currentAccess
        when (currentAccess) {
            NotificationAccessState.ALLOWED -> {
                permissionDeniedNotice = false
                pendingEnableAfterPermission = false
                if (!enabled) {
                    enabled = true
                    persistSettings(nextEnabled = true)
                }
            }
            NotificationAccessState.CHANNEL_MUTED -> {
                pendingEnableAfterPermission = true
                permissionDeniedNotice = true
            }
            NotificationAccessState.PERMISSION_MISSING -> {
                pendingEnableAfterPermission = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val scrollState = rememberScrollState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(scrollState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }
    val secondaryAlpha = if (enabled) 1f else 0.55f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen)
                .padding(
                    top = maxOf(defaultTopPadding, measuredHeaderHeight + 6.dp),
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xl),
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
                                    "~${WaterReminderScheduleCalculator.formatInterval(draftSettings.intervalMinutes)} aralıkla · ${WaterReminderScheduleCalculator.formatClock(draftSettings.startHour, draftSettings.startMinute)}–${WaterReminderScheduleCalculator.formatClock(draftSettings.endHour, draftSettings.endMinute)}"
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
                    val isChannelMuted = accessState == NotificationAccessState.CHANNEL_MUTED
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
                                    text = if (isChannelMuted) "Bildirim kanalı kapalı" else "Bildirim izni gerekli",
                                    style = NutraTheme.typography.label,
                                    color = colors.warningText,
                                )
                                Text(
                                    text = if (isChannelMuted) {
                                        "Su hatırlatıcı kanalı sistem ayarlarında sessize alınmış. Bildirim almak için kanalı aç."
                                    } else {
                                        "Hatırlatma bildirimleri için sistem ayarlarından izin ver."
                                    },
                                    style = NutraTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            NutraButton(
                                text = "Ayarları aç",
                                onClick = { openWaterNotificationSettings(context, accessState) },
                                style = NutraButtonStyle.Secondary,
                                size = NutraButtonSize.Small,
                            )
                        }
                    }
                }
            }

            // 2. Period selection & custom interval
            Column(
                modifier = Modifier.graphicsLayer { alpha = secondaryAlpha },
                verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
            ) {
                NutraSectionHeader(title = "Hatırlatma periyodu")

                NutraCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FIRST_ROW_INTERVAL_PRESETS.forEach { preset ->
                            NutraChip(
                                text = WaterReminderScheduleCalculator.formatInterval(preset),
                                selected = !isCustomMode && intervalMinutes == preset,
                                onClick = {
                                    isCustomMode = false
                                    if (intervalMinutes != preset) {
                                        intervalMinutes = preset
                                        persistSettings(nextIntervalMinutes = preset)
                                    }
                                },
                                role = Role.RadioButton,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SECOND_ROW_INTERVAL_PRESETS.forEach { preset ->
                            NutraChip(
                                text = WaterReminderScheduleCalculator.formatInterval(preset),
                                selected = !isCustomMode && intervalMinutes == preset,
                                onClick = {
                                    isCustomMode = false
                                    if (intervalMinutes != preset) {
                                        intervalMinutes = preset
                                        persistSettings(nextIntervalMinutes = preset)
                                    }
                                },
                                role = Role.RadioButton,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        NutraChip(
                            text = "Özel",
                            selected = isCustomMode,
                            onClick = { isCustomMode = true },
                            role = Role.RadioButton,
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
                                    val next = (intervalMinutes - WaterReminderBounds.CUSTOM_STEP_MINUTES)
                                        .coerceIn(
                                            WaterReminderBounds.MIN_INTERVAL_MINUTES,
                                            WaterReminderBounds.MAX_INTERVAL_MINUTES,
                                        )
                                    if (next != intervalMinutes) {
                                        intervalMinutes = next
                                        persistSettings(nextIntervalMinutes = next)
                                    }
                                },
                                onIncrement = {
                                    val next = (intervalMinutes + WaterReminderBounds.CUSTOM_STEP_MINUTES)
                                        .coerceIn(
                                            WaterReminderBounds.MIN_INTERVAL_MINUTES,
                                            WaterReminderBounds.MAX_INTERVAL_MINUTES,
                                        )
                                    if (next != intervalMinutes) {
                                        intervalMinutes = next
                                        persistSettings(nextIntervalMinutes = next)
                                    }
                                },
                                onValueClick = { showCustomIntervalDialog = true },
                                valueMinWidth = 86.dp,
                            )
                        }
                    }
                }
            }

            // 3. Active hours & smart stop toggle
            Column(
                modifier = Modifier.graphicsLayer { alpha = secondaryAlpha },
                verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
            ) {
                NutraSectionHeader(title = "Aktif saatler")

                NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NutraMetricStepperRow(
                            label = "Başlangıç",
                            valueText = WaterReminderScheduleCalculator.formatClockFromMinutes(startTotalMinutes),
                            onDecrement = {
                                val next = (startTotalMinutes - WaterReminderBounds.TIME_STEP_MINUTES)
                                    .coerceAtLeast(WaterReminderBounds.MIN_START_TOTAL_MINUTES)
                                if (next != startTotalMinutes) {
                                    startTotalMinutes = next
                                    persistSettings(nextStartTotalMinutes = next)
                                }
                            },
                            onIncrement = {
                                val maxStart = (endTotalMinutes - WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES)
                                    .coerceAtMost(WaterReminderBounds.MAX_START_TOTAL_MINUTES)
                                val next = (startTotalMinutes + WaterReminderBounds.TIME_STEP_MINUTES)
                                    .coerceAtMost(maxStart)
                                if (next != startTotalMinutes) {
                                    startTotalMinutes = next
                                    persistSettings(nextStartTotalMinutes = next)
                                }
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
                                val next = (endTotalMinutes - WaterReminderBounds.TIME_STEP_MINUTES)
                                    .coerceAtLeast(minEnd)
                                if (next != endTotalMinutes) {
                                    endTotalMinutes = next
                                    persistSettings(nextEndTotalMinutes = next)
                                }
                            },
                            onIncrement = {
                                val next = (endTotalMinutes + WaterReminderBounds.TIME_STEP_MINUTES)
                                    .coerceAtMost(WaterReminderBounds.MAX_END_TOTAL_MINUTES)
                                if (next != endTotalMinutes) {
                                    endTotalMinutes = next
                                    persistSettings(nextEndTotalMinutes = next)
                                }
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
                                    onValueChange = { next ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (next != stopWhenGoalReached) {
                                            stopWhenGoalReached = next
                                            persistSettings(nextStopWhenGoalReached = next)
                                        }
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

                // Subtle live schedule preview when enabled (ticks every 30s)
                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    val dailyCount = remember(draftSettings) {
                        WaterReminderScheduleCalculator.estimateDailyReminderCount(draftSettings)
                    }
                    val nextLabel = remember(draftSettings, liveNow) {
                        WaterReminderScheduleCalculator.computeNextTriggerDateTime(liveNow, draftSettings)
                            ?.let { WaterReminderScheduleCalculator.formatNextTriggerLabel(liveNow, it) }
                    }
                    NutraCard(
                        contentPadding = PaddingValues(
                            horizontal = NutraTheme.spacing.card,
                            vertical = 14.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    text = "Günde ~$dailyCount hatırlatma",
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
                        Text(
                            text = "Pil tasarrufu nedeniyle bildirim zamanı birkaç dakika esneyebilir.",
                            style = NutraTheme.typography.caption,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }

        NutraStickyScreenHeader(
            title = "Su hatırlatıcı",
            eyebrow = "Gün içinde düzenli su içmeni hatırlatır",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
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
                val next = entered.roundToInt().coerceIn(
                    WaterReminderBounds.MIN_INTERVAL_MINUTES,
                    WaterReminderBounds.MAX_INTERVAL_MINUTES,
                )
                isCustomMode = true
                if (next != intervalMinutes) {
                    intervalMinutes = next
                    persistSettings(nextIntervalMinutes = next)
                }
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
            ReminderTimeField.START -> WaterReminderBounds.MIN_START_TOTAL_MINUTES
            ReminderTimeField.END -> maxOf(
                WaterReminderBounds.MIN_END_TOTAL_MINUTES,
                startTotalMinutes + WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES,
            ).coerceAtMost(WaterReminderBounds.MAX_END_TOTAL_MINUTES)
        }
        val maxMinutes = when (editingTime) {
            ReminderTimeField.START -> minOf(
                WaterReminderBounds.MAX_START_TOTAL_MINUTES,
                endTotalMinutes - WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES,
            ).coerceAtLeast(WaterReminderBounds.MIN_START_TOTAL_MINUTES)
            ReminderTimeField.END -> WaterReminderBounds.MAX_END_TOTAL_MINUTES
        }
        ReminderTimePickerDialog(
            title = editingTime.title,
            initialTotalMinutes = initialMinutes,
            minTotalMinutes = minMinutes,
            maxTotalMinutes = maxMinutes,
            onDismiss = { activeTimeField = null },
            onConfirm = { selectedMinutes ->
                when (editingTime) {
                    ReminderTimeField.START -> {
                        if (selectedMinutes != startTotalMinutes) {
                            startTotalMinutes = selectedMinutes
                            persistSettings(nextStartTotalMinutes = selectedMinutes)
                        }
                    }
                    ReminderTimeField.END -> {
                        if (selectedMinutes != endTotalMinutes) {
                            endTotalMinutes = selectedMinutes
                            persistSettings(nextEndTotalMinutes = selectedMinutes)
                        }
                    }
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
                onDecrement = {
                    val candidate = totalMinutes - 60
                    if (candidate >= minTotalMinutes) {
                        totalMinutes = candidate
                    }
                },
                onIncrement = {
                    val candidate = totalMinutes + 60
                    if (candidate <= maxTotalMinutes) {
                        totalMinutes = candidate
                    }
                },
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
