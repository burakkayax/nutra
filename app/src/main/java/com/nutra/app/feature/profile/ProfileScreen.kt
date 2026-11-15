package com.nutra.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.ThemePreference
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val goals = profile.goals
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var apiKeyInput by remember(profile.geminiApiKey) { mutableStateOf(profile.geminiApiKey) }
    var modelInput by remember(profile.geminiModel) { mutableStateOf(profile.geminiModel) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

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
            delay(3500)
            viewModel.clearFeedback()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background)
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = NutraTheme.spacing.screenHorizontal,
            end = NutraTheme.spacing.screenHorizontal,
            top = 14.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.medium),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Profil & Hedefler",
                    style = NutraTheme.typography.screenHeader,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = "${profile.goalType.title} · ${profile.currentWeightKg} kg → Hedef ${profile.targetWeightKg} kg",
                    style = NutraTheme.typography.bodyRegular,
                    color = NutraTheme.colors.textSecondary,
                )
            }
        }

        if (state.feedbackMessage != null) {
            item(key = "feedback_banner") {
                NutraCard(
                    backgroundColor = NutraTheme.colors.calorieAccent.copy(alpha = 0.14f),
                    borderColor = NutraTheme.colors.calorieAccent,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.feedbackMessage.orEmpty(),
                        style = NutraTheme.typography.macroValue,
                        color = NutraTheme.colors.calorieAccent,
                    )
                }
            }
        }

        // 1. Daily Goals Editor Card
        item(key = "goals_editor") {
            NutraCard(
                shape = NutraTheme.shapes.heroCard,
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Günlük Beslenme Hedefleri",
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.recalculateWithMifflinStJeor()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = NutraTheme.colors.calorieAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "Formülle Hesapla",
                                style = NutraTheme.typography.macroLabel,
                                color = NutraTheme.colors.calorieAccent,
                            )
                        }
                    }

                    GoalStepperRow(
                        label = "Kalori",
                        valueText = "${goals.calories} kcal",
                        color = NutraTheme.colors.calorieAccent,
                        onDecrement = {
                            viewModel.updateGoals(goals.copy(calories = (goals.calories - 50).coerceIn(1100, 5000)))
                        },
                        onIncrement = {
                            viewModel.updateGoals(goals.copy(calories = (goals.calories + 50).coerceIn(1100, 5000)))
                        },
                    )
                    GoalStepperRow(
                        label = "Protein",
                        valueText = "${goals.proteinGrams} g",
                        color = NutraTheme.colors.proteinAccent,
                        onDecrement = {
                            viewModel.updateGoals(goals.copy(proteinGrams = (goals.proteinGrams - 5).coerceIn(40, 350)))
                        },
                        onIncrement = {
                            viewModel.updateGoals(goals.copy(proteinGrams = (goals.proteinGrams + 5).coerceIn(40, 350)))
                        },
                    )
                    GoalStepperRow(
                        label = "Karbonhidrat",
                        valueText = "${goals.carbsGrams} g",
                        color = NutraTheme.colors.carbsAccent,
                        onDecrement = {
                            viewModel.updateGoals(goals.copy(carbsGrams = (goals.carbsGrams - 5).coerceIn(30, 550)))
                        },
                        onIncrement = {
                            viewModel.updateGoals(goals.copy(carbsGrams = (goals.carbsGrams + 5).coerceIn(30, 550)))
                        },
                    )
                    GoalStepperRow(
                        label = "Yağ",
                        valueText = "${goals.fatGrams} g",
                        color = NutraTheme.colors.fatAccent,
                        onDecrement = {
                            viewModel.updateGoals(goals.copy(fatGrams = (goals.fatGrams - 5).coerceIn(25, 220)))
                        },
                        onIncrement = {
                            viewModel.updateGoals(goals.copy(fatGrams = (goals.fatGrams + 5).coerceIn(25, 220)))
                        },
                    )
                    GoalStepperRow(
                        label = "Günlük Su",
                        valueText = "${goals.waterMl} ml",
                        color = NutraTheme.colors.waterAccent,
                        onDecrement = {
                            viewModel.updateGoals(goals.copy(waterMl = (goals.waterMl - 100).coerceIn(1200, 6000)))
                        },
                        onIncrement = {
                            viewModel.updateGoals(goals.copy(waterMl = (goals.waterMl + 100).coerceIn(1200, 6000)))
                        },
                    )
                }
            }
        }

        // 2. Theme & Appearance Card
        item(key = "appearance_card") {
            NutraCard(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Görünüm & Tasarım Sistemi",
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )

                    NutraSegmentedControl(
                        items = ThemePreference.entries,
                        selectedItem = profile.themePreference,
                        onItemSelected = { newTheme ->
                            viewModel.updateAppearance(newTheme, profile.useDynamicColor)
                        },
                        labelProvider = { it.title },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Material You Dinamik Renkler",
                                style = NutraTheme.typography.foodItemTitle,
                                color = NutraTheme.colors.textPrimary,
                            )
                            Text(
                                text = "Kapalıyken Nutra küratörlü semantik renk paleti kullanılır",
                                style = NutraTheme.typography.bodySmall,
                                color = NutraTheme.colors.textSecondary,
                            )
                        }
                        Switch(
                            checked = profile.useDynamicColor,
                            onCheckedChange = { enabled ->
                                viewModel.updateAppearance(profile.themePreference, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NutraTheme.colors.calorieAccent,
                            ),
                        )
                    }
                }
            }
        }

        // 3. Gemini AI Configuration Card
        item(key = "gemini_config") {
            NutraCard(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = NutraTheme.colors.calorieAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Gemini AI Besin Analizi Yapılandırması",
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                    }
                    Text(
                        text = "Fotoğraf ve doğal dil analizi için Google Gemini API anahtarınızı girebilirsiniz. Anahtar girilmediğinde cihaz içi akıllı Türkçe ayrıştırıcı (On-Device NLU) otomatik devreye girer.",
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.textSecondary,
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Anahtarı (İsteğe Bağlı)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = NutraTheme.shapes.innerSubCard,
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model (Varsayılan: gemini-2.5-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = NutraTheme.shapes.innerSubCard,
                    )

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.saveGeminiConfig(apiKeyInput, modelInput)
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = NutraTheme.shapes.chip,
                        colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
                    ) {
                        Text("AI Ayarlarını Kaydet", color = Color.White, style = NutraTheme.typography.macroValue)
                    }
                }
            }
        }

        // 4. Offline-First Backup & Restore Card
        item(key = "backup_card") {
            NutraCard(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Veri Güvenliği & Çevrimdışı Yedekleme",
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Tüm besin kayıtlarınız, özel yemekleriniz ve kilo geçmişiniz cihazınızda saklanır. İstediğiniz zaman JSON olarak dışa/içe aktarabilirsiniz.",
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.textSecondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackupToCallback { json ->
                                    pendingExportJson = json
                                    exportLauncher.launch("nutra_yedek_${LocalDate.now()}.json")
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = NutraTheme.shapes.chip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NutraTheme.colors.elevatedSurface,
                                contentColor = NutraTheme.colors.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Dışa Aktar", style = NutraTheme.typography.macroValue)
                        }

                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = NutraTheme.shapes.chip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NutraTheme.colors.elevatedSurface,
                                contentColor = NutraTheme.colors.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Geri Yükle", style = NutraTheme.typography.macroValue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalStepperRow(
    label: String,
    valueText: String,
    color: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(color),
            )
            Text(
                text = label,
                style = NutraTheme.typography.foodItemTitle,
                color = NutraTheme.colors.textPrimary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(NutraTheme.shapes.chip)
                    .background(NutraTheme.colors.elevatedSurface)
                    .border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.chip)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDecrement()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = null, tint = NutraTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
            }
            Text(
                text = valueText,
                style = NutraTheme.typography.cardTitle,
                color = NutraTheme.colors.textPrimary,
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(NutraTheme.shapes.chip)
                    .background(NutraTheme.colors.elevatedSurface)
                    .border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.chip)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onIncrement()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = NutraTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
