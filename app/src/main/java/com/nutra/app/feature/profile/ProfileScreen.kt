package com.nutra.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.isCompactWidthOrLargeFont
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraListRow
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStickyHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.model.ThemePreference
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    onEditBodyProfile: () -> Unit = {},
    onOpenDailyGoals: () -> Unit = {},
    onOpenWaterReminder: () -> Unit = {},
    onOpenDietaryPreferences: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = NutraTheme.colors
    if (state.isLoading) {
        ProfileLoadingSkeleton(contentPadding = contentPadding)
        return
    }
    val profile = state.profile
    val goals = profile.goals
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val useTwoByTwoStats = isCompactWidthOrLargeFont()
    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val defaultTopClearance = contentPadding.calculateTopPadding() + NutraStickyHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopClearance) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context.contentResolver, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.prepareImportFromUri(context.contentResolver, uri)
        }
    }

    val feedback = state.feedback
    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(
        baseTimeoutMillis = if (feedback?.canUndoGoals == true) 4500L else 3200L,
        hasAction = feedback?.canUndoGoals == true,
    )
    LaunchedEffect(feedback?.eventId, feedback?.message) {
        if (feedback != null) {
            delay(snackbarTimeoutMs)
            viewModel.clearFeedback()
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
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                top = maxOf(defaultTopClearance, measuredHeaderHeight + 4.dp),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xl),
        ) {
            // 1. Personal body & goal overview card
            item(key = "body_profile_card") {
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
                                text = "Vücut profili",
                                style = NutraTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${profile.sex.title} · ${profile.activityLevel.title}",
                                style = NutraTheme.typography.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        NutraButton(
                            text = "Düzenle",
                            onClick = onEditBodyProfile,
                            style = NutraButtonStyle.Secondary,
                            size = NutraButtonSize.Small,
                            leadingIcon = Icons.Rounded.Edit,
                        )
                    }

                    NutraDivider(modifier = Modifier.padding(vertical = 16.dp))

                    if (useTwoByTwoStats) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                BodyStatColumn(
                                    label = "Mevcut",
                                    value = "${NutraFormatters.formatKg(profile.currentWeightKg)} kg",
                                    modifier = Modifier.weight(1f),
                                )
                                BodyStatColumn(
                                    label = "Hedef",
                                    value = "${NutraFormatters.formatKg(profile.targetWeightKg)} kg",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                BodyStatColumn(
                                    label = "Boy",
                                    value = "${profile.heightCm} cm",
                                    modifier = Modifier.weight(1f),
                                )
                                BodyStatColumn(
                                    label = "Yaş",
                                    value = "${profile.age}",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BodyStatColumn(
                                label = "Mevcut",
                                value = "${NutraFormatters.formatKg(profile.currentWeightKg)} kg",
                                modifier = Modifier.weight(1f),
                            )
                            BodyStatColumn(
                                label = "Hedef",
                                value = "${NutraFormatters.formatKg(profile.targetWeightKg)} kg",
                                modifier = Modifier.weight(1f),
                            )
                            BodyStatColumn(
                                label = "Boy",
                                value = "${profile.heightCm} cm",
                                modifier = Modifier.weight(1f),
                            )
                            BodyStatColumn(
                                label = "Yaş",
                                value = "${profile.age}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 2. Daily goals & water reminder entries
            item(key = "goals_and_reminders") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(title = "Hedefler ve hatırlatıcılar")

                    NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.sm)) {
                        NutraListRow(
                            title = "Günlük hedefler",
                            subtitle = "${NutraFormatters.formatInt(goals.calories)} kcal · ${NutraFormatters.formatWaterLitres(goals.waterMl)} su",
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.InsightsRounded,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                )
                            },
                            onClick = onOpenDailyGoals,
                        )

                        NutraDivider(startIndent = 54.dp)

                        NutraListRow(
                            title = "Su hatırlatıcı",
                            subtitle = WaterReminderScheduleCalculator.formatProfileSummary(profile.waterReminder),
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.WaterDrop,
                                    tint = if (profile.waterReminder.enabled) colors.water else colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                )
                            },
                            onClick = onOpenWaterReminder,
                        )

                        NutraDivider(startIndent = 54.dp)

                        val allergenCount = profile.dietaryProfile.excludedAllergens.size
                        val dietSubtitle = if (allergenCount > 0) {
                            "${profile.dietaryProfile.dietStyle.title} · $allergenCount alerjen filtresi"
                        } else {
                            profile.dietaryProfile.dietStyle.title
                        }
                        NutraListRow(
                            title = "Beslenme tercihleri",
                            subtitle = dietSubtitle,
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.Restaurant,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                )
                            },
                            onClick = onOpenDietaryPreferences,
                        )
                    }
                }
            }

            // 3. Appearance preferences
            item(key = "appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(title = "Görünüm")

                    NutraCard {
                        Text(
                            text = "Tema",
                            style = NutraTheme.typography.label,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )

                        NutraSegmentedControl(
                            items = remember { ThemePreference.entries.toImmutableList() },
                            selectedItem = profile.themePreference,
                            onItemSelected = { theme ->
                                viewModel.updateAppearance(theme, profile.useDynamicColor)
                            },
                            labelProvider = { it.title },
                        )

                        NutraDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NutraTheme.shapes.sm)
                                .toggleable(
                                    value = profile.useDynamicColor,
                                    role = Role.Switch,
                                    onValueChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateAppearance(profile.themePreference, enabled)
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
                                    text = "Dinamik renk",
                                    style = NutraTheme.typography.label,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Duvar kağıdı vurgu rengini uygula",
                                    style = NutraTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            NutraSwitch(
                                checked = profile.useDynamicColor,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }

            // 4. Backup & data management
            item(key = "data_backup") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(title = "Veri ve yedekleme")

                    NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.sm)) {
                        NutraListRow(
                            title = "Verileri dışa aktar",
                            subtitle = if (state.isExportingBackup) "Dışa aktarılıyor..." else "Kayıtları JSON yedeği olarak indir",
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.FileDownload,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                if (state.isExportingBackup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.textTertiary,
                                    )
                                }
                            },
                            onClick = if (state.isBackupBusy) {
                                null
                            } else {
                                { exportLauncher.launch("nutra_yedek_${LocalDate.now()}.json") }
                            },
                        )

                        NutraDivider(startIndent = 54.dp)

                        NutraListRow(
                            title = "Yedekten geri yükle",
                            subtitle = if (state.isImportingBackup) "Yedek doğrulanıyor..." else "JSON dosyasından içe aktar",
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.FileUpload,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                if (state.isImportingBackup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.textTertiary,
                                    )
                                }
                            },
                            onClick = if (state.isBackupBusy) {
                                null
                            } else {
                                { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                            },
                        )
                    }
                }
            }
        }

        NutraStickyScreenHeader(
            title = "Profil",
            eyebrow = "Hedefler ve ayarlar",
            collapseFractionState = headerCollapseFraction,
            horizontalPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
            ),
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NutraSnackbarHost(
            visible = feedback != null,
            message = feedback?.message.orEmpty(),
            tone = feedback?.tone ?: NutraSnackbarTone.Neutral,
            actionLabel = if (feedback?.canUndoGoals == true) "Geri al" else null,
            onAction = if (feedback?.canUndoGoals == true) {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.undoRecalculateGoals()
                }
            } else {
                null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
        )
    }

    val report = state.pendingImportReport
    if (report != null) {
        val summaryLines = buildList {
            add("${NutraFormatters.formatInt(report.validMealLogs)} öğün kaydı")
            add("${NutraFormatters.formatInt(report.validWaterDays)} gün su kaydı")
            add("${NutraFormatters.formatInt(report.validWeightEntries)} tartı kaydı")
            if (report.validCustomFoods > 0) {
                add("${NutraFormatters.formatInt(report.validCustomFoods)} özel besin")
            }
        }.joinToString(" · ")

        NutraDialog(
            onDismissRequest = viewModel::cancelPendingImport,
            title = "Yedekten geri yükle?",
            message = "Mevcut tüm kayıtların ve profil ayarların seçilen yedekle değiştirilecek. Bu işlem geri alınamaz.",
            confirmText = "Geri yükle",
            onConfirm = viewModel::confirmPendingImport,
            dismissText = "Vazgeç",
            onDismiss = viewModel::cancelPendingImport,
            destructive = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.surfaceMuted)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Yedek içeriği (v${report.payload.version})",
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = summaryLines,
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                if (report.totalSkippedCount > 0) {
                    Text(
                        text = "${report.totalSkippedCount} geçersiz veya bozuk kayıt atlanacak.",
                        style = NutraTheme.typography.caption,
                        color = colors.warningText,
                    )
                }
                report.warnings.take(2).forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileLoadingSkeleton(
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val pulseAlpha = rememberSkeletonPulseAlpha("profileSkeletonPulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                    end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                    top = contentPadding.calculateTopPadding() + 28.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.38f).height(14.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.28f).height(28.dp), shape = NutraTheme.shapes.sm)
            }
            NutraCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.55f).height(20.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(42.dp), shape = NutraTheme.shapes.sm)
            }
            NutraCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) {
                    NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(34.dp), shape = NutraTheme.shapes.sm)
                }
            }
        }
    }
}

@Composable
private fun BodyStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = NutraTheme.typography.numberSmall,
            color = NutraTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
