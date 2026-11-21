package com.nutra.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSelectableCard
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryPreferencesScreen(
    profile: UserProfile,
    dislikedFoods: List<FoodFeedbackRecord>,
    onNavigateBack: () -> Unit,
    onDietaryProfileChange: (DietaryProfile) -> Unit,
    onAdaptiveMetabolismEnabledChange: (Boolean) -> Unit,
    onRestoreDislikedFood: (FoodFeedbackRecord) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val dietary = profile.dietaryProfile
    var keywordInput by remember { mutableStateOf("") }

    fun addExcludedKeyword() {
        val cleaned = keywordInput.trim()
        if (cleaned.length < 2) return
        if (dietary.excludedKeywords.none { it.equals(cleaned, ignoreCase = true) }) {
            onDietaryProfileChange(dietary.copy(excludedKeywords = dietary.excludedKeywords + cleaned))
        }
        keywordInput = ""
    }

    BackHandler(onBack = onNavigateBack)

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
            // Top bar matching DailyGoalsScreen & EditBodyProfileScreen
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
                    onClick = onNavigateBack,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Beslenme tercihleri",
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Alerjenler, beslenme düzeni ve güvenlik ayarları",
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
                // 1. Diet style
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Beslenme düzeni")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DietStyle.entries.forEach { style ->
                            NutraSelectableCard(
                                title = style.title,
                                subtitle = style.subtitle,
                                selected = dietary.dietStyle == style,
                                onClick = {
                                    if (dietary.dietStyle != style) {
                                        onDietaryProfileChange(dietary.copy(dietStyle = style))
                                    }
                                },
                            )
                        }
                    }
                }

                // 2. Allergens
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(
                        title = "Alerjenler ve kaçınılan gruplar",
                        supportingText = if (dietary.excludedAllergens.isEmpty()) "Yok" else "${dietary.excludedAllergens.size} seçili",
                    )
                    NutraCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Seçilen alerjenleri içeren besinler Nutra Intelligence önerilerinden hariç tutulur.",
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AllergenTag.entries.forEach { tag ->
                                val isExcluded = tag in dietary.excludedAllergens
                                NutraChip(
                                    text = tag.title,
                                    selected = isExcluded,
                                    onClick = {
                                        val next = if (isExcluded) {
                                            dietary.excludedAllergens - tag
                                        } else {
                                            dietary.excludedAllergens + tag
                                        }
                                        onDietaryProfileChange(dietary.copy(excludedAllergens = next))
                                    },
                                )
                            }
                        }
                    }
                }

                // 3. Excluded custom keywords
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Sevmediğin besin veya malzemeler")
                    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NutraTextField(
                                value = keywordInput,
                                onValueChange = { keywordInput = it },
                                placeholder = "Örn. mantar, brokoli, sakatat...",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addExcludedKeyword() }),
                                modifier = Modifier.weight(1f),
                            )
                            NutraIconButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Hariç tutulan ekle",
                                onClick = ::addExcludedKeyword,
                                enabled = keywordInput.trim().length >= 2,
                                size = 48.dp,
                            )
                        }
                        if (dietary.excludedKeywords.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                dietary.excludedKeywords.forEach { kw ->
                                    NutraChip(
                                        text = kw,
                                        selected = true,
                                        leadingIcon = Icons.Rounded.Close,
                                        onClick = {
                                            onDietaryProfileChange(
                                                dietary.copy(
                                                    excludedKeywords = dietary.excludedKeywords.filterNot { it == kw },
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Hidden/disliked foods from Intelligence
                if (dislikedFoods.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                        NutraSectionHeader(
                            title = "Önerilerden gizlenen besinler",
                            supportingText = "${dislikedFoods.size} besin",
                        )
                        NutraCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                dislikedFoods.forEach { fb ->
                                    NutraChip(
                                        text = fb.foodName.ifBlank { fb.foodKey },
                                        selected = false,
                                        leadingIcon = Icons.Rounded.Refresh,
                                        onClick = { onRestoreDislikedFood(fb) },
                                    )
                                }
                            }
                            Text(
                                text = "Bir besine dokunarak yeniden öğün önerilerine dahil edebilirsin.",
                                style = NutraTheme.typography.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }

                // 5. Adaptive Metabolism & Clinical Safety Guardrail Controls
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Adaptif metabolizma ve güvenlik")
                    NutraCard(contentPadding = PaddingValues(NutraTheme.spacing.card)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NutraTheme.shapes.sm)
                                .toggleable(
                                    value = profile.adaptiveMetabolismEnabled,
                                    role = Role.Switch,
                                    onValueChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAdaptiveMetabolismEnabledChange(enabled)
                                    },
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Adaptif kalori önerileri",
                                    style = NutraTheme.typography.label,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Kilo trendine göre kalori hedefi önerisi göster (onayın olmadan değişmez)",
                                    style = NutraTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            NutraSwitch(
                                checked = profile.adaptiveMetabolismEnabled,
                                onCheckedChange = null,
                            )
                        }

                        NutraDivider(modifier = Modifier.padding(vertical = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NutraTheme.shapes.sm)
                                .toggleable(
                                    value = dietary.specialHealthGuardrail,
                                    role = Role.Switch,
                                    onValueChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDietaryProfileChange(dietary.copy(specialHealthGuardrail = enabled))
                                    },
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Özel sağlık / Gözetim modu",
                                    style = NutraTheme.typography.label,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Gebelik, emzirme veya tıbbi beslenme sürecinde otomatik kalori azaltma önerilerini kapatır",
                                    style = NutraTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            NutraSwitch(
                                checked = dietary.specialHealthGuardrail,
                                onCheckedChange = null,
                            )
                        }

                        if (profile.age < AdaptiveMetabolismEngine.MIN_ADULT_AGE) {
                            NutraDivider(modifier = Modifier.padding(vertical = 14.dp))
                            Text(
                                text = "Profil yaşın ${profile.age} olduğu için gelişim dönemi koruma kalkanı otomatik olarak aktiftir.",
                                style = NutraTheme.typography.caption,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
