package com.nutra.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nutra.app.core.datastore.MAX_EXCLUDED_KEYWORDS
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSelectableCard
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryPreferencesScreen(
    profile: UserProfile,
    dislikedFoods: ImmutableList<FoodFeedbackRecord>,
    onNavigateBack: () -> Unit,
    onDietaryProfileChange: (DietaryProfile) -> Unit,
    onAdaptiveMetabolismEnabledChange: (Boolean) -> Unit,
    onRestoreDislikedFood: (FoodFeedbackRecord) -> Unit,
    feedback: ProfileFeedback? = null,
    onClearFeedback: () -> Unit = {},
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val dietary = profile.dietaryProfile
    var keywordInput by remember { mutableStateOf("") }

    val parsedCandidates = remember(keywordInput) {
        keywordInput.split(',', ';')
            .map { it.trim() }
            .filter { TurkishTextNormalizer.normalize(it).length >= 2 }
    }
    val canAddKeyword = dietary.excludedKeywords.size < MAX_EXCLUDED_KEYWORDS && parsedCandidates.isNotEmpty()

    fun addExcludedKeyword() {
        if (parsedCandidates.isEmpty() || dietary.excludedKeywords.size >= MAX_EXCLUDED_KEYWORDS) return
        val existingNormalized = dietary.excludedKeywords.mapTo(HashSet()) { TurkishTextNormalizer.normalize(it) }
        val additions = mutableListOf<String>()
        for (item in parsedCandidates) {
            if (dietary.excludedKeywords.size + additions.size >= MAX_EXCLUDED_KEYWORDS) break
            val norm = TurkishTextNormalizer.normalize(item)
            if (existingNormalized.add(norm)) {
                additions.add(item)
            }
        }
        if (additions.isNotEmpty()) {
            onDietaryProfileChange(dietary.copy(excludedKeywords = dietary.excludedKeywords + additions))
        }
        keywordInput = ""
    }

    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(baseTimeoutMillis = 2800L, hasAction = false)
    LaunchedEffect(feedback?.eventId, feedback?.message) {
        if (feedback != null) {
            delay(snackbarTimeoutMs)
            onClearFeedback()
        }
    }

    val scrollState = rememberScrollState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(scrollState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }

    val isGuardrailActive = dietary.specialHealthGuardrail || profile.age < AdaptiveMetabolismEngine.MIN_ADULT_AGE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
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
                NutraSectionHeader(title = "Alerjenler ve kaçınılan gruplar")
                NutraCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Seçilen alerjenleri içeren besinler Nutra Intelligence önerilerinden ve sık birlikte kombinlerinden hariç tutulur.",
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
                NutraSectionHeader(
                    title = "Sevmediğin besin veya malzemeler",
                    supportingText = "${dietary.excludedKeywords.size}/$MAX_EXCLUDED_KEYWORDS",
                )
                NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NutraTextField(
                            value = keywordInput,
                            onValueChange = { keywordInput = it },
                            placeholder = "Örn. mantar, brokoli...",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addExcludedKeyword() }),
                            modifier = Modifier.weight(1f),
                        )
                        NutraIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "Hariç tutulan ekle",
                            onClick = ::addExcludedKeyword,
                            enabled = canAddKeyword,
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
                            .graphicsLayer { alpha = if (isGuardrailActive) 0.5f else 1f }
                            .clip(NutraTheme.shapes.sm)
                            .toggleable(
                                value = profile.adaptiveMetabolismEnabled && !isGuardrailActive,
                                enabled = !isGuardrailActive,
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
                                text = if (isGuardrailActive) {
                                    "Koruyucu gözetim modu açıkken otomatik kalori önerileri devre dışıdır"
                                } else {
                                    "Kilo trendine göre kalori hedefi önerisi göster"
                                },
                                style = NutraTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        NutraSwitch(
                            checked = profile.adaptiveMetabolismEnabled && !isGuardrailActive,
                            onCheckedChange = null,
                            enabled = !isGuardrailActive,
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
        }

        NutraStickyScreenHeader(
            title = "Beslenme tercihleri",
            eyebrow = "Diyet ve alerjen tercihlerini ayarla",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NutraSnackbarHost(
            visible = feedback != null,
            message = feedback?.message.orEmpty(),
            tone = feedback?.tone ?: NutraSnackbarTone.Success,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
        )
    }
}
