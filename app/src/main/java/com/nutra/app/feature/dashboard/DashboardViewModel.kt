package com.nutra.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.CalorieDisplayMode
import com.nutra.app. core.network.GeminiAiService
import com.nutra.app.domain.model.AiAnalyzedFoodItem
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DayStatusIndicator
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class LoggingTabMode(val title: String) {
    CATALOG("Katalog"),
    AI_ANALYZE("AI"),
    BARCODE("Barkod"),
    CUSTOM("Özel"),
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val weekDays: List<LocalDate> = emptyList(),
    val weekStatusMap: Map<String, DayStatusIndicator> = emptyMap(),
    val dailySummary: DailyNutritionSummary = DailyNutritionSummary(
        dateIso = LocalDate.now().toString(),
        goals = MacroGoals(),
        meals = MealType.entries.map { MealSummary(it, emptyList()) },
        waterMl = 0,
    ),
    val calorieDisplayMode: CalorieDisplayMode = CalorieDisplayMode.CONSUMED,
    val expandedMeals: Set<MealType> = emptySet(),
    val activeLoggingMealType: MealType? = null,
    val loggingTabMode: LoggingTabMode = LoggingTabMode.CATALOG,
    val searchQuery: String = "",
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val rankedFoods: List<FoodItem> = emptyList(),
    val smartCombos: List<SmartComboSuggestion> = emptyList(),
    val selectedFoodForPortion: FoodItem? = null,
    val editingLogItem: MealLogItem? = null,
    val lastDeletedItem: MealLogItem? = null,
    val aiPromptText: String = "",
    val aiAnalyzing: Boolean = false,
    val aiAnalyzedItems: List<AiAnalyzedFoodItem> = emptyList(),
    val aiErrorMessage: String? = null,
    val isGeminiKeyConfigured: Boolean = false,
    val barcodeInput: String = "",
    val barcodeLoading: Boolean = false,
    val barcodeErrorMessage: String? = null,
    val statusBannerMessage: String? = null,
)

private data class LoggingModalInternalState(
    val activeLoggingMealType: MealType? = null,
    val loggingTabMode: LoggingTabMode = LoggingTabMode.CATALOG,
    val searchQuery: String = "",
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val selectedFoodForPortion: FoodItem? = null,
    val editingLogItem: MealLogItem? = null,
    val lastDeletedItem: MealLogItem? = null,
    val aiPromptText: String = "",
    val aiAnalyzing: Boolean = false,
    val aiAnalyzedItems: List<AiAnalyzedFoodItem> = emptyList(),
    val aiErrorMessage: String? = null,
    val barcodeInput: String = "",
    val barcodeLoading: Boolean = false,
    val barcodeErrorMessage: String? = null,
    val statusBannerMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val rankerAndComboEngine: ContextualMealRankerAndComboEngine,
    private val geminiAiService: GeminiAiService,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val calorieDisplayModeFlow = MutableStateFlow(CalorieDisplayMode.CONSUMED)
    private val expandedMealsFlow = MutableStateFlow<Set<MealType>>(emptySet())
    private val modalStateFlow = MutableStateFlow(LoggingModalInternalState())

    private val allFoodsFlow = nutritionRepository.observeAllFoods()
    private val recentLogsFlow = nutritionRepository.observeRecentHistoricalMealLogs()

    private val dateDependentSummaryFlow = selectedDateFlow.flatMapLatest { date ->
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        combine(
            nutritionRepository.observeDailySummary(date.toString()),
            nutritionRepository.observeWeekStatusIndicators(monday.toString(), sunday.toString()),
        ) { summary, weekStatus ->
            Triple(date, summary, weekStatus)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        dateDependentSummaryFlow,
        calorieDisplayModeFlow,
        expandedMealsFlow,
        modalStateFlow,
        combine(allFoodsFlow, recentLogsFlow, preferencesDataStore.userProfileFlow) { f, l, p -> Triple(f, l, p) },
    ) { (date, summary, weekStatus), displayMode, expandedMeals, modal, (allFoods, recentLogs, profile) ->
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekDays = (0L..6L).map { monday.plusDays(it) }

        val activeMeal = modal.activeLoggingMealType ?: MealType.BREAKFAST
        val rankedFoods = rankerAndComboEngine.rankFoodsForMeal(
            allFoods = allFoods,
            historicalLogs = recentLogs,
            mealType = activeMeal,
            query = modal.searchQuery,
            selectedCategory = modal.selectedCategory,
        )

        val currentMealItems = summary.meals.flatMap { it.items }
        val smartCombos = rankerAndComboEngine.buildSmartComboSuggestions(
            mealType = activeMeal,
            currentDateMealItems = currentMealItems,
            historicalLogs = recentLogs,
            catalogFoods = allFoods,
        )

        DashboardUiState(
            isLoading = false,
            selectedDate = date,
            weekDays = weekDays,
            weekStatusMap = weekStatus,
            dailySummary = summary,
            calorieDisplayMode = displayMode,
            expandedMeals = expandedMeals,
            activeLoggingMealType = modal.activeLoggingMealType,
            loggingTabMode = modal.loggingTabMode,
            searchQuery = modal.searchQuery,
            selectedCategory = modal.selectedCategory,
            rankedFoods = rankedFoods,
            smartCombos = smartCombos,
            selectedFoodForPortion = modal.selectedFoodForPortion,
            editingLogItem = modal.editingLogItem,
            lastDeletedItem = modal.lastDeletedItem,
            aiPromptText = modal.aiPromptText,
            aiAnalyzing = modal.aiAnalyzing,
            aiAnalyzedItems = modal.aiAnalyzedItems,
            aiErrorMessage = modal.aiErrorMessage,
            isGeminiKeyConfigured = profile.geminiApiKey.isNotBlank(),
            barcodeInput = modal.barcodeInput,
            barcodeLoading = modal.barcodeLoading,
            barcodeErrorMessage = modal.barcodeErrorMessage,
            statusBannerMessage = modal.statusBannerMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )

    fun selectDate(date: LocalDate) {
        selectedDateFlow.value = date
    }

    fun shiftWeek(weeksDelta: Long) {
        selectedDateFlow.update { it.plusWeeks(weeksDelta) }
    }

    fun jumpToToday() {
        selectedDateFlow.value = LocalDate.now()
    }

    fun toggleCalorieDisplayMode() {
        calorieDisplayModeFlow.update { it.next() }
    }

    fun toggleMealExpanded(mealType: MealType) {
        expandedMealsFlow.update { current ->
            if (current.contains(mealType)) current - mealType else current + mealType
        }
    }

    fun addWater(deltaMl: Int) {
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            nutritionRepository.addWaterMl(dateIso, deltaMl)
        }
    }

    fun openAddFoodModal(mealType: MealType, initialTab: LoggingTabMode = LoggingTabMode.CATALOG) {
        modalStateFlow.update {
            it.copy(
                activeLoggingMealType = mealType,
                loggingTabMode = initialTab,
                searchQuery = "",
                selectedCategory = FoodCategory.ALL,
                selectedFoodForPortion = null,
                aiErrorMessage = null,
                barcodeErrorMessage = null,
            )
        }
    }

    fun closeAddFoodModal() {
        modalStateFlow.update {
            it.copy(
                activeLoggingMealType = null,
                selectedFoodForPortion = null,
                aiErrorMessage = null,
                barcodeErrorMessage = null,
            )
        }
    }

    fun selectLoggingTab(tab: LoggingTabMode) {
        modalStateFlow.update {
            it.copy(
                loggingTabMode = tab,
                selectedFoodForPortion = null,
                aiErrorMessage = null,
                barcodeErrorMessage = null,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        modalStateFlow.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: FoodCategory) {
        modalStateFlow.update { it.copy(selectedCategory = category) }
    }

    fun openPortionPicker(food: FoodItem) {
        modalStateFlow.update { it.copy(selectedFoodForPortion = food) }
    }

    fun closePortionPicker() {
        modalStateFlow.update { it.copy(selectedFoodForPortion = null) }
    }

    fun toggleFavorite(food: FoodItem) {
        viewModelScope.launch {
            nutritionRepository.toggleFavoriteFood(food.id, !food.isFavorite)
        }
    }

    fun logFoodWithPortion(
        food: FoodItem,
        unitLabel: String,
        unitGrams: Float,
        multiplier: Float,
    ) {
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            nutritionRepository.logFoodToMeal(
                dateIso = dateIso,
                mealType = mealType,
                food = food,
                unitLabel = unitLabel,
                unitGrams = unitGrams,
                multiplier = multiplier,
            )
            modalStateFlow.update {
                it.copy(
                    selectedFoodForPortion = null,
                    statusBannerMessage = "${food.name} (${mealType.title}) eklendi",
                )
            }
        }
    }

    fun logSmartCombo(combo: SmartComboSuggestion) {
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            nutritionRepository.logComboItemsToMeal(
                dateIso = dateIso,
                mealType = mealType,
                entries = combo.items,
            )
            modalStateFlow.update {
                it.copy(
                    activeLoggingMealType = null,
                    statusBannerMessage = "${combo.title} (${combo.totalCalories} kcal) eklendi",
                )
            }
        }
    }

    fun openEditLoggedItem(item: MealLogItem) {
        modalStateFlow.update { it.copy(editingLogItem = item) }
    }

    fun closeEditLoggedItem() {
        modalStateFlow.update { it.copy(editingLogItem = null) }
    }

    fun updateLoggedItemMultiplier(item: MealLogItem, newMultiplier: Float) {
        viewModelScope.launch {
            nutritionRepository.updateMealLogMultiplier(item, newMultiplier)
            modalStateFlow.update { it.copy(editingLogItem = null) }
        }
    }

    fun deleteLoggedItem(item: MealLogItem) {
        viewModelScope.launch {
            nutritionRepository.deleteMealLog(item.id)
            modalStateFlow.update {
                it.copy(
                    editingLogItem = null,
                    lastDeletedItem = item,
                )
            }
        }
    }

    fun undoDeleteLoggedItem() {
        val deleted = modalStateFlow.value.lastDeletedItem ?: return
        viewModelScope.launch {
            nutritionRepository.restoreMealLogItem(deleted)
            modalStateFlow.update { it.copy(lastDeletedItem = null) }
        }
    }

    fun dismissUndoBanner() {
        modalStateFlow.update { it.copy(lastDeletedItem = null, statusBannerMessage = null) }
    }

    // --- AI Food Analysis ---
    fun updateAiPromptText(text: String) {
        modalStateFlow.update { it.copy(aiPromptText = text, aiErrorMessage = null) }
    }

    fun analyzeWithAi(imageBytes: ByteArray? = null) {
        val prompt = modalStateFlow.value.aiPromptText
        viewModelScope.launch {
            modalStateFlow.update { it.copy(aiAnalyzing = true, aiErrorMessage = null) }
            val profile = preferencesDataStore.getSnapshot()
            val catalog = uiState.value.rankedFoods
            val result = geminiAiService.analyzeFoodInput(
                promptText = prompt,
                imageBytes = imageBytes,
                apiKey = profile.geminiApiKey,
                modelName = profile.geminiModel,
                catalogFoods = catalog,
            )
            when (result) {
                is AppResult.Success -> {
                    modalStateFlow.update {
                        it.copy(
                            aiAnalyzing = false,
                            aiAnalyzedItems = result.data,
                            aiErrorMessage = null,
                        )
                    }
                }
                is AppResult.Error -> {
                    modalStateFlow.update {
                        it.copy(
                            aiAnalyzing = false,
                            aiErrorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun updateAiItemMultiplier(itemId: String, multiplier: Float) {
        modalStateFlow.update { state ->
            state.copy(
                aiAnalyzedItems = state.aiAnalyzedItems.map { item ->
                    if (item.id == itemId) item.copy(multiplier = multiplier.coerceIn(0.25f, 10f)) else item
                },
            )
        }
    }

    fun toggleAiItemIncluded(itemId: String) {
        modalStateFlow.update { state ->
            state.copy(
                aiAnalyzedItems = state.aiAnalyzedItems.map { item ->
                    if (item.id == itemId) item.copy(included = !item.included) else item
                },
            )
        }
    }

    fun commitAiAnalyzedItems() {
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()
        val selectedItems = modalStateFlow.value.aiAnalyzedItems.filter { it.included }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            val entries = selectedItems.map { ai ->
                ComboFoodEntry(
                    foodId = null,
                    foodName = ai.name,
                    unitLabel = ai.unitLabel,
                    multiplier = ai.multiplier,
                    totalGrams = ai.effectiveGrams,
                    calories = ai.effectiveCalories,
                    proteinGrams = ai.effectiveProtein,
                    carbsGrams = ai.effectiveCarbs,
                    fatGrams = ai.effectiveFat,
                )
            }
            nutritionRepository.logComboItemsToMeal(dateIso, mealType, entries)
            modalStateFlow.update {
                it.copy(
                    activeLoggingMealType = null,
                    aiPromptText = "",
                    aiAnalyzedItems = emptyList(),
                    statusBannerMessage = "${entries.size} AI besin kalemi eklendi",
                )
            }
        }
    }

    // --- Barcode ---
    fun updateBarcodeInput(code: String) {
        modalStateFlow.update { it.copy(barcodeInput = code, barcodeErrorMessage = null) }
    }

    fun lookupBarcode(codeOverride: String? = null) {
        val code = (codeOverride ?: modalStateFlow.value.barcodeInput).trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            modalStateFlow.update { it.copy(barcodeInput = code, barcodeLoading = true, barcodeErrorMessage = null) }
            when (val res = nutritionRepository.findOrLookupBarcode(code)) {
                is AppResult.Success -> {
                    modalStateFlow.update {
                        it.copy(
                            barcodeLoading = false,
                            selectedFoodForPortion = res.data,
                            barcodeErrorMessage = null,
                        )
                    }
                }
                is AppResult.Error -> {
                    modalStateFlow.update {
                        it.copy(
                            barcodeLoading = false,
                            barcodeErrorMessage = res.message,
                        )
                    }
                }
            }
        }
    }

    // --- Custom Food Creation ---
    fun createAndLogCustomFood(
        name: String,
        unitLabel: String,
        unitGrams: Float,
        caloriesPerServing: Int,
        proteinPerServing: Float,
        carbsPerServing: Float,
        fatPerServing: Float,
    ) {
        val safeGrams = unitGrams.coerceAtLeast(1f)
        val factor = 100f / safeGrams
        val newFood = FoodItem(
            name = name.trim(),
            subtitle = "Özel Besin · $unitLabel",
            category = FoodCategory.CUSTOM,
            caloriesPer100g = (caloriesPerServing * factor).toInt().coerceAtLeast(0),
            proteinPer100g = (proteinPerServing * factor).coerceAtLeast(0f),
            carbsPer100g = (carbsPerServing * factor).coerceAtLeast(0f),
            fatPer100g = (fatPerServing * factor).coerceAtLeast(0f),
            defaultUnitLabel = unitLabel.ifBlank { "1 Porsiyon" },
            defaultUnitGrams = safeGrams,
            isCustom = true,
            isFavorite = true,
        )
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()

        viewModelScope.launch {
            val savedFood = nutritionRepository.createCustomFood(newFood)
            nutritionRepository.logFoodToMeal(
                dateIso = dateIso,
                mealType = mealType,
                food = savedFood,
                unitLabel = savedFood.defaultUnitLabel,
                unitGrams = savedFood.defaultUnitGrams,
                multiplier = 1f,
            )
            modalStateFlow.update {
                it.copy(
                    activeLoggingMealType = null,
                    statusBannerMessage = "${savedFood.name} oluşturuldu ve öğüne eklendi",
                )
            }
        }
    }
}
