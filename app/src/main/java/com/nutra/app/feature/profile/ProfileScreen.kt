package com.nutra.app.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraListRow
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.ThemePreference
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

private val GEMINI_MODEL_PRESETS = listOf(
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-pro",
)

private enum class ProfileGoalField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
) {
    CALORIES("Kalori hedefi", "kcal", "1.000 – 6.000 kcal", 1000.0, 6000.0),
    PROTEIN("Protein hedefi", "g", "30 – 400 g", 30.0, 400.0),
    CARBS("Karbonhidrat hedefi", "g", "20 – 600 g", 20.0, 600.0),
    FAT("Yağ hedefi", "g", "20 – 250 g", 20.0, 250.0),
    WATER("Su hedefi", "ml", "1.000 – 7.000 ml", 1000.0, 7000.0),
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    onEditBodyProfile: () -> Unit = {},
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
    val listState = rememberLazyListState()

    var showGeminiDialog by remember { mutableStateOf(false) }
    var activeGoalField by remember { mutableStateOf<ProfileGoalField?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

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
            pendingImportUri = uri
        }
    }

    val feedback = state.feedback
    LaunchedEffect(feedback?.eventId, feedback?.message) {
        if (feedback != null) {
            delay(if (feedback.canUndoGoals) 4500L else 3200L)
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
                top = contentPadding.calculateTopPadding() + 28.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xl),
        ) {
            item(key = "header") {
                NutraScreenHeader(
                    title = "Profil",
                    eyebrow = "Hedefler ve ayarlar",
                )
            }

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
                                text = profile.goalType.title,
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

            // 2. Daily macro & water goals
            item(key = "daily_goals") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(
                        title = "Günlük hedefler",
                        actionLabel = "Oto hesapla",
                        actionIcon = Icons.Rounded.Refresh,
                        actionContainerColor = colors.surfaceElevated,
                        actionHaptic = HapticFeedbackType.Confirm,
                        onAction = viewModel::recalculateWithMifflinStJeor,
                    )

                    NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutraMetricStepperRow(
                                label = "Kalori",
                                valueText = "${NutraFormatters.formatInt(goals.calories)} kcal",
                                accentColor = colors.accent,
                                onDecrement = { viewModel.adjustCalorieGoal(-50) },
                                onIncrement = { viewModel.adjustCalorieGoal(50) },
                                onValueClick = { activeGoalField = ProfileGoalField.CALORIES },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Protein",
                                valueText = "${goals.proteinGrams} g",
                                accentColor = colors.protein,
                                onDecrement = { viewModel.adjustProteinGoal(-5) },
                                onIncrement = { viewModel.adjustProteinGoal(5) },
                                onValueClick = { activeGoalField = ProfileGoalField.PROTEIN },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Karbonhidrat",
                                valueText = "${goals.carbsGrams} g",
                                accentColor = colors.carbs,
                                onDecrement = { viewModel.adjustCarbsGoal(-5) },
                                onIncrement = { viewModel.adjustCarbsGoal(5) },
                                onValueClick = { activeGoalField = ProfileGoalField.CARBS },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Yağ",
                                valueText = "${goals.fatGrams} g",
                                accentColor = colors.fat,
                                onDecrement = { viewModel.adjustFatGoal(-5) },
                                onIncrement = { viewModel.adjustFatGoal(5) },
                                onValueClick = { activeGoalField = ProfileGoalField.FAT },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Su",
                                valueText = "${NutraFormatters.formatInt(goals.waterMl)} ml",
                                accentColor = colors.water,
                                onDecrement = { viewModel.adjustWaterGoal(-250) },
                                onIncrement = { viewModel.adjustWaterGoal(250) },
                                onValueClick = { activeGoalField = ProfileGoalField.WATER },
                            )
                        }
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
                            items = ThemePreference.entries,
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

            // 4. AI & Backup management
            item(key = "ai_and_data") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(title = "Yapay zeka ve veri")

                    NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.sm)) {
                        NutraListRow(
                            title = "Gemini AI bağlantısı",
                            subtitle = if (profile.geminiApiKey.isNotBlank()) {
                                "Özel anahtar aktif · ${profile.geminiModel}"
                            } else {
                                "Cihaz içi akıllı ayrıştırıcı aktif"
                            },
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.AutoAwesome,
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
                            onClick = { showGeminiDialog = true },
                        )

                        NutraDivider(startIndent = 54.dp)

                        NutraListRow(
                            title = "Verileri dışa aktar",
                            subtitle = if (state.isBackupBusy) "İşleniyor..." else "Kayıtları JSON yedeği olarak indir",
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.FileDownload,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                if (state.isBackupBusy) {
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
                            subtitle = if (state.isBackupBusy) "İşleniyor..." else "JSON dosyasından içe aktar",
                            leading = {
                                NutraIconBadge(
                                    icon = NutraIcons.FileUpload,
                                    tint = colors.textPrimary,
                                    container = colors.surfaceMuted,
                                )
                            },
                            trailing = {
                                if (state.isBackupBusy) {
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

    val uriToImport = pendingImportUri
    if (uriToImport != null) {
        NutraDialog(
            onDismissRequest = { pendingImportUri = null },
            title = "Yedekten geri yükle?",
            message = "Mevcut tüm öğün, su, kilo kayıtların ve profil ayarların seçilen yedek dosyasıyla değiştirilecek. Bu işlem geri alınamaz.",
            confirmText = "Geri yükle",
            onConfirm = {
                pendingImportUri = null
                viewModel.importBackupFromUri(context.contentResolver, uriToImport)
            },
            dismissText = "Vazgeç",
            onDismiss = { pendingImportUri = null },
            destructive = true,
        )
    }

    if (showGeminiDialog) {
        GeminiConfigDialog(
            initialApiKey = profile.geminiApiKey,
            initialModel = profile.geminiModel,
            onDismiss = { showGeminiDialog = false },
            onSave = { apiKey, model ->
                viewModel.saveGeminiConfig(apiKey, model)
                showGeminiDialog = false
            },
        )
    }

    val editingGoalField = activeGoalField
    if (editingGoalField != null) {
        val currentValue = when (editingGoalField) {
            ProfileGoalField.CALORIES -> goals.calories.toString()
            ProfileGoalField.PROTEIN -> goals.proteinGrams.toString()
            ProfileGoalField.CARBS -> goals.carbsGrams.toString()
            ProfileGoalField.FAT -> goals.fatGrams.toString()
            ProfileGoalField.WATER -> goals.waterMl.toString()
        }
        NutraNumericInputDialog(
            title = editingGoalField.dialogTitle,
            initialValue = currentValue,
            unit = editingGoalField.unit,
            rangeHint = editingGoalField.rangeHint,
            minValue = editingGoalField.minValue,
            maxValue = editingGoalField.maxValue,
            allowDecimal = false,
            onDismiss = { activeGoalField = null },
            onConfirm = { entered ->
                val rounded = entered.roundToInt()
                when (editingGoalField) {
                    ProfileGoalField.CALORIES -> viewModel.setCalorieGoal(rounded)
                    ProfileGoalField.PROTEIN -> viewModel.setProteinGoal(rounded)
                    ProfileGoalField.CARBS -> viewModel.setCarbsGoal(rounded)
                    ProfileGoalField.FAT -> viewModel.setFatGoal(rounded)
                    ProfileGoalField.WATER -> viewModel.setWaterGoal(rounded)
                }
                activeGoalField = null
            },
        )
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

@Composable
private fun GeminiConfigDialog(
    initialApiKey: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, model: String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var apiKeyInput by rememberSaveable(initialApiKey) { mutableStateOf(initialApiKey) }
    var modelInput by rememberSaveable(initialModel) { mutableStateOf(initialModel) }
    var showApiKey by remember { mutableStateOf(false) }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = "Gemini AI bağlantısı",
        message = "İsteğe bağlı Gemini API anahtarınla fotoğraf ve serbest metin analizi yapabilirsin. Boş bırakılırsa cihaz içi ayrıştırıcı çalışır.",
        confirmText = "Kaydet",
        onConfirm = { onSave(apiKeyInput, modelInput) },
        dismissText = "Vazgeç",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NutraTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = "Gemini API anahtarı",
                placeholder = "AIza...",
                trailing = if (apiKeyInput.isNotEmpty()) {
                    {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NutraIconButton(
                                icon = if (showApiKey) NutraIcons.VisibilityOff else NutraIcons.Visibility,
                                contentDescription = if (showApiKey) "Anahtarı gizle" else "Anahtarı göster",
                                onClick = { showApiKey = !showApiKey },
                                size = 40.dp,
                                iconSize = 18.dp,
                                containerColor = NutraTheme.colors.surfaceElevated,
                            )
                            NutraIconButton(
                                icon = Icons.Rounded.Close,
                                contentDescription = "Temizle",
                                onClick = { apiKeyInput = "" },
                                size = 40.dp,
                                iconSize = 16.dp,
                                containerColor = NutraTheme.colors.surfaceElevated,
                            )
                        }
                    }
                } else {
                    null
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                singleLine = true,
            )
            NutraTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = "Model",
                placeholder = "gemini-2.5-flash",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onSave(apiKeyInput, modelInput)
                    },
                ),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GEMINI_MODEL_PRESETS.forEach { preset ->
                    NutraChip(
                        text = preset,
                        selected = modelInput.trim().equals(preset, ignoreCase = true),
                        onClick = { modelInput = preset },
                    )
                }
            }
        }
    }
}
