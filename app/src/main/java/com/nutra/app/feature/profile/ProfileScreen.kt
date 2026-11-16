package com.nutra.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraListRow
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraModalSheet
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

private fun formatInt(value: Int): String =
    NumberFormat.getIntegerInstance(TR_LOCALE).format(value)

private fun formatKg(value: Float): String =
    String.format(TR_LOCALE, "%.1f", value)

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

private enum class BodyMetricField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
    val allowDecimal: Boolean,
) {
    CURRENT_WEIGHT("Mevcut kilo", "kg", "35,0 – 250,0 kg", 35.0, 250.0, true),
    TARGET_WEIGHT("Hedef kilo", "kg", "35,0 – 250,0 kg", 35.0, 250.0, true),
    HEIGHT("Boy", "cm", "130 – 225 cm", 130.0, 225.0, false),
    AGE("Yaş", "yaş", "14 – 95 yaş", 14.0, 95.0, false),
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val goals = profile.goals
    val colors = NutraTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showEditBodySheet by remember { mutableStateOf(false) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var activeGoalField by remember { mutableStateOf<ProfileGoalField?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val jsonContent = pendingExportJson
        if (uri != null && jsonContent != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
            }
            pendingExportJson = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!raw.isNullOrBlank()) {
                viewModel.importBackupJson(raw)
            }
        }
    }

    LaunchedEffect(state.feedbackMessage) {
        if (state.feedbackMessage != null) {
            delay(3200)
            viewModel.clearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                top = contentPadding.calculateTopPadding() + 28.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") {
                NutraScreenHeader(
                    title = "Profil",
                    eyebrow = "Vücut profili ve hedefler",
                )
            }

            // 1. Personal body & goal overview card
            item(key = "body_profile_card") {
                val weightDiff = abs(profile.currentWeightKg - profile.targetWeightKg)
                val targetSubtitle = when {
                    weightDiff < 0.2f -> "Hedef kilonla eşitsin"
                    profile.currentWeightKg > profile.targetWeightKg ->
                        "Hedefe ${formatKg(weightDiff)} kg kaldı"
                    else ->
                        "Hedef için +${formatKg(weightDiff)} kg"
                }

                NutraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                            onClick = { showEditBodySheet = true },
                            style = NutraButtonStyle.Secondary,
                            size = NutraButtonSize.Small,
                            leadingIcon = Icons.Rounded.Edit,
                        )
                    }

                    Text(
                        text = targetSubtitle,
                        style = NutraTheme.typography.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.padding(top = 14.dp),
                    )

                    NutraDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BodyStatColumn(
                            label = "Mevcut",
                            value = "${formatKg(profile.currentWeightKg)} kg",
                            modifier = Modifier.weight(1f),
                        )
                        BodyStatColumn(
                            label = "Hedef",
                            value = "${formatKg(profile.targetWeightKg)} kg",
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
                            modifier = Modifier.weight(0.75f),
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
                        onAction = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.recalculateWithMifflinStJeor()
                        },
                    )

                    NutraCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutraMetricStepperRow(
                                label = "Kalori",
                                valueText = "${formatInt(goals.calories)} kcal",
                                accentColor = colors.accent,
                                onDecrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustCalorieGoal(-50)
                                },
                                onIncrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustCalorieGoal(50)
                                },
                                onValueClick = { activeGoalField = ProfileGoalField.CALORIES },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Protein",
                                valueText = "${goals.proteinGrams} g",
                                accentColor = colors.protein,
                                onDecrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustProteinGoal(-5)
                                },
                                onIncrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustProteinGoal(5)
                                },
                                onValueClick = { activeGoalField = ProfileGoalField.PROTEIN },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Karbonhidrat",
                                valueText = "${goals.carbsGrams} g",
                                accentColor = colors.carbs,
                                onDecrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustCarbsGoal(-5)
                                },
                                onIncrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustCarbsGoal(5)
                                },
                                onValueClick = { activeGoalField = ProfileGoalField.CARBS },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Yağ",
                                valueText = "${goals.fatGrams} g",
                                accentColor = colors.fat,
                                onDecrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustFatGoal(-5)
                                },
                                onIncrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustFatGoal(5)
                                },
                                onValueClick = { activeGoalField = ProfileGoalField.FAT },
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Su",
                                valueText = "${formatInt(goals.waterMl)} ml",
                                accentColor = colors.water,
                                onDecrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustWaterGoal(-250)
                                },
                                onIncrement = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.adjustWaterGoal(250)
                                },
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
                            modifier = Modifier.fillMaxWidth(),
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
                                onCheckedChange = { enabled ->
                                    viewModel.updateAppearance(profile.themePreference, enabled)
                                },
                            )
                        }
                    }
                }
            }

            // 4. AI & Backup management
            item(key = "ai_and_data") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSectionHeader(title = "Yapay zeka ve veri")

                    NutraCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        NutraListRow(
                            title = "Gemini AI bağlantısı",
                            subtitle = if (profile.geminiApiKey.isNotBlank()) {
                                "Özel anahtar aktif · ${profile.geminiModel}"
                            } else {
                                "Cihaz içi akıllı ayrıştırıcı aktif"
                            },
                            leading = {
                                NutraIconBadge(
                                    icon = Icons.Rounded.AutoAwesome,
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

                        NutraDivider(startIndent = 52.dp)

                        NutraListRow(
                            title = "Verileri dışa aktar",
                            subtitle = "Kayıtları JSON yedeği olarak indir",
                            leading = {
                                NutraIconBadge(
                                    icon = Icons.Rounded.FileDownload,
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
                            onClick = {
                                viewModel.exportBackupToCallback { json ->
                                    pendingExportJson = json
                                    exportLauncher.launch("nutra_yedek_${LocalDate.now()}.json")
                                }
                            },
                        )

                        NutraDivider(startIndent = 52.dp)

                        NutraListRow(
                            title = "Yedekten geri yükle",
                            subtitle = "JSON dosyasından içe aktar",
                            leading = {
                                NutraIconBadge(
                                    icon = Icons.Rounded.FileUpload,
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
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                        )
                    }
                }
            }
        }

        NutraSnackbarHost(
            visible = state.feedbackMessage != null,
            message = state.feedbackMessage.orEmpty(),
            icon = Icons.Rounded.Check,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
        )
    }

    if (showEditBodySheet) {
        EditBodyProfileSheet(
            profile = profile,
            onDismiss = { showEditBodySheet = false },
            onSave = { sex, age, heightCm, currentWeight, targetWeight, activity, goalType, recalculate ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.updateBodyProfile(
                    sex = sex,
                    age = age,
                    heightCm = heightCm,
                    currentWeightKg = currentWeight,
                    targetWeightKg = targetWeight,
                    activityLevel = activity,
                    goalType = goalType,
                    recalculateGoals = recalculate,
                )
                showEditBodySheet = false
            },
        )
    }

    if (showGeminiDialog) {
        GeminiConfigDialog(
            initialApiKey = profile.geminiApiKey,
            initialModel = profile.geminiModel,
            onDismiss = { showGeminiDialog = false },
            onSave = { apiKey, model ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
    var apiKeyInput by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var modelInput by remember(initialModel) { mutableStateOf(initialModel) }

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
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            NutraTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = "Model",
                placeholder = "gemini-2.5-flash",
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBodyProfileSheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        currentWeightKg: Float,
        targetWeightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
        recalculateGoals: Boolean,
    ) -> Unit,
) {
    val colors = NutraTheme.colors
    var sex by remember(profile) { mutableStateOf(profile.sex) }
    var age by remember(profile) { mutableIntStateOf(profile.age) }
    var heightCm by remember(profile) { mutableIntStateOf(profile.heightCm) }
    var currentWeight by remember(profile) { mutableFloatStateOf(profile.currentWeightKg) }
    var targetWeight by remember(profile) { mutableFloatStateOf(profile.targetWeightKg) }
    var activity by remember(profile) { mutableStateOf(profile.activityLevel) }
    var goalType by remember(profile) { mutableStateOf(profile.goalType) }
    var recalculateGoals by remember { mutableStateOf(true) }
    var activeBodyField by remember { mutableStateOf<BodyMetricField?>(null) }

    NutraModalSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Vücut profilini düzenle",
                        style = NutraTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Ölçülerini ve aktivite düzeyini güncel tut",
                        style = NutraTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cinsiyet",
                        style = NutraTheme.typography.label,
                        color = colors.textSecondary,
                    )
                    NutraSegmentedControl(
                        items = BiologicalSex.entries,
                        selectedItem = sex,
                        onItemSelected = { sex = it },
                        labelProvider = { it.title },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hedefin",
                        style = NutraTheme.typography.label,
                        color = colors.textSecondary,
                    )
                    GoalType.entries.forEach { option ->
                        SheetOptionRow(
                            title = option.title,
                            subtitle = option.subtitle,
                            selected = option == goalType,
                            onClick = { goalType = option },
                        )
                    }
                }

                NutraCard(
                    color = colors.background,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NutraMetricStepperRow(
                            label = "Mevcut kilo",
                            valueText = "${formatKg(currentWeight)} kg",
                            valueContainerColor = colors.surface,
                            onDecrement = {
                                currentWeight = ((currentWeight - 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onIncrement = {
                                currentWeight = ((currentWeight + 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onValueClick = { activeBodyField = BodyMetricField.CURRENT_WEIGHT },
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Hedef kilo",
                            valueText = "${formatKg(targetWeight)} kg",
                            valueContainerColor = colors.surface,
                            onDecrement = {
                                targetWeight = ((targetWeight - 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onIncrement = {
                                targetWeight = ((targetWeight + 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onValueClick = { activeBodyField = BodyMetricField.TARGET_WEIGHT },
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Boy",
                            valueText = "$heightCm cm",
                            valueContainerColor = colors.surface,
                            onDecrement = { heightCm = (heightCm - 1).coerceIn(130, 225) },
                            onIncrement = { heightCm = (heightCm + 1).coerceIn(130, 225) },
                            onValueClick = { activeBodyField = BodyMetricField.HEIGHT },
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Yaş",
                            valueText = "$age yaş",
                            valueContainerColor = colors.surface,
                            onDecrement = { age = (age - 1).coerceIn(14, 95) },
                            onIncrement = { age = (age + 1).coerceIn(14, 95) },
                            onValueClick = { activeBodyField = BodyMetricField.AGE },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hareket düzeyi",
                        style = NutraTheme.typography.label,
                        color = colors.textSecondary,
                    )
                    ActivityLevel.entries.forEach { level ->
                        SheetOptionRow(
                            title = level.title,
                            subtitle = level.subtitle,
                            selected = level == activity,
                            onClick = { activity = level },
                        )
                    }
                }

                NutraCard(
                    color = colors.background,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = "Hedefleri yeniden hesapla",
                                style = NutraTheme.typography.label,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Mifflin-St Jeor formülüne göre kalori ve makroları günceller",
                                style = NutraTheme.typography.caption,
                                color = colors.textSecondary,
                            )
                        }
                        NutraSwitch(
                            checked = recalculateGoals,
                            onCheckedChange = { recalculateGoals = it },
                        )
                    }
                }
            }

            NutraBottomActionBar {
                NutraButton(
                    text = "Vazgeç",
                    onClick = onDismiss,
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Medium,
                    modifier = Modifier.weight(0.35f),
                )
                NutraButton(
                    text = "Kaydet",
                    onClick = {
                        onSave(
                            sex,
                            age,
                            heightCm,
                            currentWeight,
                            targetWeight,
                            activity,
                            goalType,
                            recalculateGoals,
                        )
                    },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    modifier = Modifier.weight(0.65f),
                )
            }
        }
    }

    val editingBodyField = activeBodyField
    if (editingBodyField != null) {
        val currentValue = when (editingBodyField) {
            BodyMetricField.CURRENT_WEIGHT -> formatKg(currentWeight)
            BodyMetricField.TARGET_WEIGHT -> formatKg(targetWeight)
            BodyMetricField.HEIGHT -> heightCm.toString()
            BodyMetricField.AGE -> age.toString()
        }
        NutraNumericInputDialog(
            title = editingBodyField.dialogTitle,
            initialValue = currentValue,
            unit = editingBodyField.unit,
            rangeHint = editingBodyField.rangeHint,
            minValue = editingBodyField.minValue,
            maxValue = editingBodyField.maxValue,
            allowDecimal = editingBodyField.allowDecimal,
            onDismiss = { activeBodyField = null },
            onConfirm = { entered ->
                when (editingBodyField) {
                    BodyMetricField.CURRENT_WEIGHT -> {
                        currentWeight = ((entered * 10.0).roundToInt().coerceIn(350, 2500)) / 10f
                    }
                    BodyMetricField.TARGET_WEIGHT -> {
                        targetWeight = ((entered * 10.0).roundToInt().coerceIn(350, 2500)) / 10f
                    }
                    BodyMetricField.HEIGHT -> {
                        heightCm = entered.roundToInt().coerceIn(130, 225)
                    }
                    BodyMetricField.AGE -> {
                        age = entered.roundToInt().coerceIn(14, 95)
                    }
                }
                activeBodyField = null
            },
        )
    }
}

@Composable
private fun SheetOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NutraTheme.colors
    val container by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.background,
        animationSpec = tween(160),
        label = "sheetOptionContainer",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) colors.onInk else colors.textPrimary,
        animationSpec = tween(160),
        label = "sheetOptionTitle",
    )
    val subColor by animateColorAsState(
        targetValue = if (selected) colors.onInk.copy(alpha = 0.72f) else colors.textSecondary,
        animationSpec = tween(160),
        label = "sheetOptionSub",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NutraTheme.shapes.md)
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                text = title,
                style = NutraTheme.typography.titleSmall,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = NutraTheme.typography.caption,
                color = subColor,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onInk,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
