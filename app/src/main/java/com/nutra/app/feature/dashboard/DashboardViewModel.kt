package com.nutra.app.feature.dashboard

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.network.GeminiAiService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

@Immutable
data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val isWeekCalendarVisible: Boolean = false,
    val weekDays: List<LocalDate> = emptyList(),
    val weekStatusMap: Map<String, DayStatusIndicator> = emptyMap(),
    val dailySummary: DailyNutritionSummary = DailyNutritionSummary(
        dateIso = LocalDate.now().toString(),
        goals = MacroGoals(),
        meals = MealType.entries.map { MealSummary(it, emptyList()) },
        waterMl = 0,
    ),
    val expandedMeals: Set<MealType> = emptySet(),
    val activeLoggingMealType: MealType? = null,
    val editingLogItem: MealLogItem? = null,
    val lastDeletedItem: MealLogItem? = null,
    val statusBannerMessage: String? = null,
)

@Immutable
data class LoggingSheetUiState(
    val activeLoggingMealType: MealType = MealType.BREAKFAST,
    val loggingTabMode: LoggingTabMode = LoggingTabMode.CATALOG,
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val isSearchActive: Boolean = false,
    val isCatalogLoading: Boolean = true,
    val rankedFoods: List<FoodItem> = emptyList(),
    val smartCombos: List<SmartComboSuggestion> = emptyList(),
    val selectedFoodForPortion: FoodItem? = null,
    val aiPromptText: String = "",
    val aiAnalyzing: Boolean = false,
    val aiAnalyzedItems: List<AiAnalyzedFoodItem> = emptyList(),
    val aiErrorMessage: String? = null,
    val isGeminiKeyConfigured: Boolean = false,
    val barcodeInput: String = "",
    val barcodeLoading: Boolean = false,
    val barcodeErrorMessage: String? = null,
)

private data class DashboardBannerState(
    val editingLogItem: MealLogItem? = null,
    val lastDeletedItem: MealLogItem? = null,
    val statusBannerMessage: String? = null,
)

private data class LoggingModalInternalState(
    val activeLoggingMealType: MealType? = null,
    val loggingTabMode: LoggingTabMode = LoggingTabMode.CATALOG,
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val selectedFoodForPortion: FoodItem? = null,
    val aiPromptText: String = "",
    val aiAnalyzing: Boolean = false,
    val aiAnalyzedItems: List<AiAnalyzedFoodItem> = emptyList(),
    val aiErrorMessage: String? = null,
    val barcodeInput: String = "",
    val barcodeLoading: Boolean = false,
    val barcodeErrorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val rankerAndComboEngine: ContextualMealRankerAndComboEngine,
    private val geminiAiService: dagger.Lazy<GeminiAiService>,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val isWeekCalendarVisibleFlow = MutableStateFlow(false)
    private val expandedMealsFlow = MutableStateFlow<Set<MealType>>(emptySet())
    private val bannerStateFlow = MutableStateFlow(DashboardBannerState())
    private val modalStateFlow = MutableStateFlow(LoggingModalInternalState())
    private val searchQueryFlow = MutableStateFlow("")
    val searchQueryState: StateFlow<String> = searchQueryFlow

    private val dateDependentSummaryFlow = selectedDateFlow.flatMapLatest { date ->
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val weekDays = (0L..6L).map { monday.plusDays(it) }
        combine(
            nutritionRepository.observeDailySummary(date.toString()),
            nutritionRepository.observeWeekStatusIndicators(monday.toString(), sunday.toString()),
        ) { summary, weekStatus ->
            Triple(date to weekDays, summary, weekStatus)
        }
    }

    private val activeLoggingMealTypeFlow = modalStateFlow
        .map { it.activeLoggingMealType }
        .distinctUntilChanged()

    val uiState: StateFlow<DashboardUiState> = combine(
        dateDependentSummaryFlow,
        combine(expandedMealsFlow, isWeekCalendarVisibleFlow) { e, w -> e to w },
        activeLoggingMealTypeFlow,
        bannerStateFlow,
    ) { (dateAndDays, summary, weekStatus), (expandedMeals, isWeekCalendarVisible), activeLoggingMeal, banner ->
        DashboardUiState(
            isLoading = false,
            selectedDate = dateAndDays.first,
            isWeekCalendarVisible = isWeekCalendarVisible,
            weekDays = dateAndDays.second,
            weekStatusMap = weekStatus,
            dailySummary = summary,
            expandedMeals = expandedMeals,
            activeLoggingMealType = activeLoggingMeal,
            editingLogItem = banner.editingLogItem,
            lastDeletedItem = banner.lastDeletedItem,
            statusBannerMessage = banner.statusBannerMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = DashboardUiState(),
    )

    val isReady: StateFlow<Boolean> = uiState
        .map { !it.isLoading }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val isModalOpenFlow = activeLoggingMealTypeFlow
        .map { it != null }
        .distinctUntilChanged()

    private val catalogPrewarmTrigger = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            isReady.first { it }
            delay(350)
            catalogPrewarmTrigger.value = true
        }
    }

    private val shouldKeepCatalogWarmFlow = combine(
        isModalOpenFlow,
        catalogPrewarmTrigger,
    ) { isOpen, prewarm -> isOpen || prewarm }.distinctUntilChanged()

    private val cachedFoodsFlow: StateFlow<List<FoodItem>?> = shouldKeepCatalogWarmFlow
        .flatMapLatest { shouldLoad ->
            if (shouldLoad) {
                nutritionRepository.observeAllFoods().map<List<FoodItem>, List<FoodItem>?> { it }
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val cachedRecentLogsFlow: StateFlow<List<MealLogItem>> = shouldKeepCatalogWarmFlow
        .flatMapLatest { shouldLoad ->
            if (shouldLoad) nutritionRepository.observeRecentHistoricalMealLogs() else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val debouncedSearchQueryFlow = searchQueryFlow
        .debounce { query -> if (query.isBlank()) 0L else 45L }
        .distinctUntilChanged()

    private val selectedCategoryFlow = modalStateFlow
        .map { it.selectedCategory }
        .distinctUntilChanged()

    private val rankedFoodsWithQueryFlow = isModalOpenFlow.flatMapLatest { isOpen ->
        if (!isOpen) {
            flowOf(Triple(false, true, emptyList<FoodItem>()))
        } else {
            combine(
                cachedFoodsFlow,
                cachedRecentLogsFlow,
                activeLoggingMealTypeFlow.map { it ?: MealType.BREAKFAST }.distinctUntilChanged(),
                debouncedSearchQueryFlow,
                selectedCategoryFlow,
            ) { foods, logs, mealType, query, category ->
                val isSearchActive = query.isNotBlank()
                if (foods == null) {
                    Triple(isSearchActive, true, emptyList())
                } else if (foods.isEmpty()) {
                    Triple(isSearchActive, false, emptyList())
                } else {
                    val ranked = rankerAndComboEngine.rankFoodsForMeal(
                        allFoods = foods,
                        historicalLogs = logs,
                        mealType = mealType,
                        query = query,
                        selectedCategory = category,
                    )
                    Triple(isSearchActive, false, ranked)
                }
            }.flowOn(Dispatchers.Default)
        }
    }

    private val currentMealItemsFlow = combine(
        isModalOpenFlow,
        activeLoggingMealTypeFlow,
        uiState.map { it.dailySummary.meals }.distinctUntilChanged(),
    ) { isOpen, mealType, meals ->
        if (!isOpen || mealType == null) {
            emptyList()
        } else {
            meals.firstOrNull { it.mealType == mealType }?.items.orEmpty()
        }
    }.distinctUntilChanged()

    private val smartCombosFlow = isModalOpenFlow.flatMapLatest { isOpen ->
        if (!isOpen) {
            flowOf(emptyList())
        } else {
            combine(
                activeLoggingMealTypeFlow.map { it ?: MealType.BREAKFAST }.distinctUntilChanged(),
                currentMealItemsFlow,
                cachedRecentLogsFlow,
                cachedFoodsFlow,
            ) { mealType, currentMealItems, logs, foods ->
                if (foods.isNullOrEmpty()) {
                    emptyList()
                } else {
                    rankerAndComboEngine.buildSmartComboSuggestions(
                        mealType = mealType,
                        currentDateMealItems = currentMealItems,
                        historicalLogs = logs,
                        catalogFoods = foods,
                    )
                }
            }.flowOn(Dispatchers.Default)
        }
    }

    private val isGeminiKeyConfiguredFlow = preferencesDataStore.userProfileFlow
        .map { it.geminiApiKey.isNotBlank() }
        .distinctUntilChanged()

    val loggingUiState: StateFlow<LoggingSheetUiState> = combine(
        modalStateFlow,
        rankedFoodsWithQueryFlow,
        smartCombosFlow,
        isGeminiKeyConfiguredFlow,
    ) { modal, (isSearchActive, isCatalogLoading, rankedFoods), smartCombos, isGeminiConfigured ->
        LoggingSheetUiState(
            activeLoggingMealType = modal.activeLoggingMealType ?: MealType.BREAKFAST,
            loggingTabMode = modal.loggingTabMode,
            selectedCategory = modal.selectedCategory,
            isSearchActive = isSearchActive,
            isCatalogLoading = isCatalogLoading,
            rankedFoods = rankedFoods,
            smartCombos = smartCombos,
            selectedFoodForPortion = modal.selectedFoodForPortion,
            aiPromptText = modal.aiPromptText,
            aiAnalyzing = modal.aiAnalyzing,
            aiAnalyzedItems = modal.aiAnalyzedItems,
            aiErrorMessage = modal.aiErrorMessage,
            isGeminiKeyConfigured = isGeminiConfigured,
            barcodeInput = modal.barcodeInput,
            barcodeLoading = modal.barcodeLoading,
            barcodeErrorMessage = modal.barcodeErrorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(10_000),
        initialValue = LoggingSheetUiState(),
    )

    fun selectDate(date: LocalDate) {
        selectedDateFlow.value = date
    }

    fun toggleWeekCalendar() {
        isWeekCalendarVisibleFlow.update { !it }
    }

    fun shiftWeek(weeksDelta: Long) {
        selectedDateFlow.update { it.plusWeeks(weeksDelta) }
    }

    fun jumpToToday() {
        selectedDateFlow.value = LocalDate.now()
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
        searchQueryFlow.value = ""
        modalStateFlow.update {
            it.copy(
                activeLoggingMealType = mealType,
                loggingTabMode = initialTab,
                selectedCategory = FoodCategory.ALL,
                selectedFoodForPortion = null,
                aiErrorMessage = null,
                barcodeErrorMessage = null,
            )
        }
    }

    fun closeAddFoodModal() {
        searchQueryFlow.value = ""
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

    fun selectLoggingMealType(mealType: MealType) {
        modalStateFlow.update { it.copy(activeLoggingMealType = mealType) }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
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
            modalStateFlow.update { state ->
                val currentPortion = state.selectedFoodForPortion
                if (currentPortion != null && currentPortion.id == food.id) {
                    state.copy(selectedFoodForPortion = currentPortion.copy(isFavorite = !food.isFavorite))
                } else {
                    state
                }
            }
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
            modalStateFlow.update { it.copy(selectedFoodForPortion = null) }
            bannerStateFlow.update {
                it.copy(statusBannerMessage = "${food.name} (${mealType.title}) eklendi")
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
            modalStateFlow.update { it.copy(activeLoggingMealType = null) }
            bannerStateFlow.update {
                it.copy(statusBannerMessage = "${combo.title} (${combo.totalCalories} kcal) eklendi")
            }
        }
    }

    fun openEditLoggedItem(item: MealLogItem) {
        bannerStateFlow.update { it.copy(editingLogItem = item) }
    }

    fun closeEditLoggedItem() {
        bannerStateFlow.update { it.copy(editingLogItem = null) }
    }

    fun updateLoggedItemMultiplier(item: MealLogItem, newMultiplier: Float) {
        viewModelScope.launch {
            nutritionRepository.updateMealLogMultiplier(item, newMultiplier)
            bannerStateFlow.update { it.copy(editingLogItem = null) }
        }
    }

    fun deleteLoggedItem(item: MealLogItem) {
        viewModelScope.launch {
            nutritionRepository.deleteMealLog(item.id)
            bannerStateFlow.update {
                it.copy(
                    editingLogItem = null,
                    lastDeletedItem = item,
                )
            }
        }
    }

    fun undoDeleteLoggedItem() {
        val deleted = bannerStateFlow.value.lastDeletedItem ?: return
        viewModelScope.launch {
            nutritionRepository.restoreMealLogItem(deleted)
            bannerStateFlow.update { it.copy(lastDeletedItem = null) }
        }
    }

    fun dismissUndoBanner() {
        bannerStateFlow.update { it.copy(lastDeletedItem = null, statusBannerMessage = null) }
    }

    // --- AI Food Analysis ---
    fun updateAiPromptText(text: String) {
        modalStateFlow.update { it.copy(aiPromptText = text, aiErrorMessage = null) }
    }

    fun analyzePhotoWithAi(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            modalStateFlow.update { it.copy(aiAnalyzing = true, aiErrorMessage = null) }
            val aiService = geminiAiService.get()
            val bytes = aiService.loadCompressedImageBytes(contentResolver, uri)
            if (bytes == null) {
                modalStateFlow.update {
                    it.copy(aiAnalyzing = false, aiErrorMessage = "Fotoğraf okunamadı.")
                }
                return@launch
            }
            runAiAnalysisInternal(bytes)
        }
    }

    fun analyzeWithAi(imageBytes: ByteArray? = null) {
        viewModelScope.launch {
            modalStateFlow.update { it.copy(aiAnalyzing = true, aiErrorMessage = null) }
            runAiAnalysisInternal(imageBytes)
        }
    }

    private suspend fun runAiAnalysisInternal(imageBytes: ByteArray?) {
        val prompt = modalStateFlow.value.aiPromptText
        val profile = preferencesDataStore.getSnapshot()
        val catalog = loggingUiState.value.rankedFoods
        val result = geminiAiService.get().analyzeFoodInput(
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
                )
            }
            bannerStateFlow.update {
                it.copy(statusBannerMessage = "${entries.size} AI besin kalemi eklendi")
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
            modalStateFlow.update { it.copy(activeLoggingMealType = null) }
            bannerStateFlow.update {
                it.copy(statusBannerMessage = "${savedFood.name} oluşturuldu ve öğüne eklendi")
            }
        }
    }
}
