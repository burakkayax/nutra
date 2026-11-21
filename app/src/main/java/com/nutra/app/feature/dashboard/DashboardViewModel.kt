package com.nutra.app.feature.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.DailyNutritionSummary
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.roundToInt

@Immutable
data class CustomFoodDraft(
    val name: String = "",
    val unitLabel: String = "",
    val unitGrams: String = "100",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
)

@Immutable
data class DashboardUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isWeekCalendarVisible: Boolean = false,
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
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
    val bannerEventId: Long = 0L,
)

@Immutable
data class LoggingSheetUiState(
    val activeLoggingMealType: MealType = MealType.BREAKFAST,
    val isCreatingCustomFood: Boolean = false,
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val favoritesOnly: Boolean = false,
    val isSearchActive: Boolean = false,
    val isCatalogLoading: Boolean = true,
    val rankedFoods: List<FoodItem> = emptyList(),
    val smartCombos: List<SmartComboSuggestion> = emptyList(),
    val selectedFoodForPortion: FoodItem? = null,
    val customFoodDraft: CustomFoodDraft = CustomFoodDraft(),
    val sheetFeedbackMessage: String? = null,
    val sheetFeedbackEventId: Long = 0L,
) {
    val shouldShowSmartCombos: Boolean
        get() = !isSearchActive && !favoritesOnly && selectedCategory == FoodCategory.ALL && smartCombos.isNotEmpty()
}

private data class CatalogAndCombosState(
    val isSearchActive: Boolean = false,
    val isCatalogLoading: Boolean = true,
    val rankedFoods: List<FoodItem> = emptyList(),
    val smartCombos: List<SmartComboSuggestion> = emptyList(),
)

private data class DashboardBannerState(
    val editingLogItem: MealLogItem? = null,
    val lastDeletedItem: MealLogItem? = null,
    val statusBannerMessage: String? = null,
    val bannerEventId: Long = 0L,
)

private data class LoggingModalInternalState(
    val activeLoggingMealType: MealType? = null,
    val isCreatingCustomFood: Boolean = false,
    val selectedCategory: FoodCategory = FoodCategory.ALL,
    val favoritesOnly: Boolean = false,
    val selectedFoodForPortion: FoodItem? = null,
    val customFoodDraft: CustomFoodDraft = CustomFoodDraft(),
    val sheetFeedbackMessage: String? = null,
    val sheetFeedbackEventId: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nutritionRepository: NutritionRepository,
    private val rankerAndComboEngine: ContextualMealRankerAndComboEngine,
) : ViewModel() {

    private companion object {
        const val KEY_SELECTED_DATE_ISO = "dashboard_selected_date_iso"
        const val KEY_WEEK_CALENDAR_VISIBLE = "dashboard_week_calendar_visible"
        const val KEY_EXPANDED_MEALS = "dashboard_expanded_meals"
        val MEAL_TYPE_BY_NAME: Map<String, MealType> = MealType.entries.associateBy { it.name }
    }

    private val todayFlow = MutableStateFlow(LocalDate.now())
    private val selectedDateFlow = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SELECTED_DATE_ISO)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: todayFlow.value,
    )
    private val isWeekCalendarVisibleFlow = MutableStateFlow(
        savedStateHandle.get<Boolean>(KEY_WEEK_CALENDAR_VISIBLE) ?: false,
    )
    private val expandedMealsFlow = MutableStateFlow<Set<MealType>>(
        savedStateHandle.get<List<String>>(KEY_EXPANDED_MEALS)
            ?.mapNotNull { MEAL_TYPE_BY_NAME[it] }
            ?.toSet()
            ?: emptySet(),
    )
    private val bannerStateFlow = MutableStateFlow(DashboardBannerState())
    private val modalStateFlow = MutableStateFlow(LoggingModalInternalState())
    private val searchQueryFlow = MutableStateFlow("")
    private val bannerSeq = AtomicLong(0L)
    private val sheetFeedbackSeq = AtomicLong(0L)
    private val isSubmittingModalAction = AtomicBoolean(false)
    private val isUpdatingLoggedItem = AtomicBoolean(false)
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    @Volatile
    private var lastCatalogAndCombos = CatalogAndCombosState()
    val searchQueryState: StateFlow<String> = searchQueryFlow

    private val dateDependentSummaryFlow = selectedDateFlow.flatMapLatest { date ->
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        nutritionRepository.observeDailySummary(date.toString()).map { summary ->
            Triple(date, weekStart, summary)
        }
    }

    private val activeLoggingMealTypeFlow = modalStateFlow
        .map { it.activeLoggingMealType }
        .distinctUntilChanged()

    private val isCatalogTabActiveFlow = modalStateFlow
        .map { it.activeLoggingMealType != null && !it.isCreatingCustomFood }
        .distinctUntilChanged()

    val uiState: StateFlow<DashboardUiState> = combine(
        todayFlow,
        dateDependentSummaryFlow,
        combine(expandedMealsFlow, isWeekCalendarVisibleFlow) { e, w -> e to w },
        activeLoggingMealTypeFlow,
        bannerStateFlow,
    ) { today, (selectedDate, weekStart, summary), (expandedMeals, isWeekCalendarVisible), activeLoggingMeal, banner ->
        DashboardUiState(
            isLoading = false,
            today = today,
            selectedDate = selectedDate,
            isWeekCalendarVisible = isWeekCalendarVisible,
            weekStart = weekStart,
            dailySummary = summary,
            expandedMeals = expandedMeals,
            activeLoggingMealType = activeLoggingMeal,
            editingLogItem = banner.editingLogItem,
            lastDeletedItem = banner.lastDeletedItem,
            statusBannerMessage = banner.statusBannerMessage,
            bannerEventId = banner.bannerEventId,
        )
    }.onEach {
        if (!_isReady.value) _isReady.value = true
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = DashboardUiState(),
    )

    private val cachedFoodsFlow: StateFlow<List<FoodItem>?> = nutritionRepository
        .observeAllFoods()
        .map<List<FoodItem>, List<FoodItem>?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            initialValue = null,
        )

    private val cachedRecentLogsFlow: StateFlow<List<MealLogItem>> = nutritionRepository
        .observeRecentHistoricalMealLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            // Trigger initial Dashboard load and gently warm catalog cache after first frame settles.
            uiState.first { !it.isLoading }
            delay(500L)
            nutritionRepository.ensureCatalogSeeded()
            cachedFoodsFlow.first { it != null }
        }
    }

    private val debouncedSearchQueryFlow = searchQueryFlow
        .debounce { query -> if (query.isBlank()) 0L else 45L }
        .distinctUntilChanged()

    private val selectedCategoryFilterFlow = modalStateFlow
        .map { it.selectedCategory to it.favoritesOnly }
        .distinctUntilChanged()

    private val activeModalMealFlow = activeLoggingMealTypeFlow
        .map { it ?: MealType.BREAKFAST }
        .distinctUntilChanged()

    private val mealUsageStatsFlow = combine(
        cachedRecentLogsFlow,
        activeModalMealFlow,
    ) { logs, mealType ->
        rankerAndComboEngine.prepareMealUsageStats(logs, mealType)
    }.flowOn(Dispatchers.Default)

    private val catalogAndCombosFlow = combine(
        activeLoggingMealTypeFlow,
        isCatalogTabActiveFlow,
    ) { activeMeal, isCatalogActive ->
        activeMeal to isCatalogActive
    }.distinctUntilChanged().flatMapLatest { (activeMeal, isCatalogActive) ->
        when {
            activeMeal == null -> flow {
                val empty = CatalogAndCombosState()
                lastCatalogAndCombos = empty
                emit(empty)
            }
            !isCatalogActive -> flow { emit(lastCatalogAndCombos) }
            else -> {
                val rankedFlow = combine(
                    cachedFoodsFlow,
                    mealUsageStatsFlow,
                    debouncedSearchQueryFlow,
                    selectedCategoryFilterFlow,
                ) { foods, stats, query, (category, favoritesOnly) ->
                    val isSearchActive = query.isNotBlank()
                    when {
                        foods == null -> Triple(isSearchActive, true, emptyList())
                        foods.isEmpty() -> Triple(isSearchActive, false, emptyList())
                        else -> {
                            val sourceFoods = if (favoritesOnly) {
                                foods.filter { it.isFavorite }
                            } else {
                                foods
                            }
                            val ranked = rankerAndComboEngine.rankFoodsWithStats(
                                allFoods = sourceFoods,
                                stats = stats,
                                mealType = activeMeal,
                                query = query,
                                selectedCategory = if (favoritesOnly) FoodCategory.ALL else category,
                            )
                            Triple(isSearchActive, false, ranked)
                        }
                    }
                }.flowOn(Dispatchers.Default)

                val currentMealItemsForMealFlow = uiState
                    .map { summary ->
                        summary.dailySummary.meals.firstOrNull { it.mealType == activeMeal }?.items.orEmpty()
                    }
                    .distinctUntilChanged()

                val showCombosEligibilityFlow = combine(
                    debouncedSearchQueryFlow,
                    selectedCategoryFilterFlow,
                ) { query, (category, favoritesOnly) ->
                    query.isBlank() && !favoritesOnly && category == FoodCategory.ALL
                }.distinctUntilChanged()

                val combosFlow = showCombosEligibilityFlow.flatMapLatest { eligible ->
                    if (!eligible) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            currentMealItemsForMealFlow,
                            cachedRecentLogsFlow,
                            cachedFoodsFlow,
                        ) { currentMealItems, logs, foods ->
                            if (foods.isNullOrEmpty()) {
                                emptyList()
                            } else {
                                rankerAndComboEngine.buildSmartComboSuggestions(
                                    mealType = activeMeal,
                                    currentDateMealItems = currentMealItems,
                                    historicalLogs = logs,
                                    catalogFoods = foods,
                                )
                            }
                        }.flowOn(Dispatchers.Default)
                    }
                }

                combine(rankedFlow, combosFlow) { (isSearchActive, isCatalogLoading, rankedFoods), smartCombos ->
                    CatalogAndCombosState(
                        isSearchActive = isSearchActive,
                        isCatalogLoading = isCatalogLoading,
                        rankedFoods = rankedFoods,
                        smartCombos = if (isCatalogLoading) emptyList() else smartCombos,
                    )
                }.onEach { snapshot ->
                    lastCatalogAndCombos = snapshot
                }
            }
        }
    }

    val loggingUiState: StateFlow<LoggingSheetUiState> = combine(
        modalStateFlow,
        catalogAndCombosFlow,
    ) { modal, catalogAndCombos ->
        LoggingSheetUiState(
            activeLoggingMealType = modal.activeLoggingMealType ?: MealType.BREAKFAST,
            isCreatingCustomFood = modal.isCreatingCustomFood,
            selectedCategory = modal.selectedCategory,
            favoritesOnly = modal.favoritesOnly,
            isSearchActive = catalogAndCombos.isSearchActive,
            isCatalogLoading = catalogAndCombos.isCatalogLoading,
            rankedFoods = catalogAndCombos.rankedFoods,
            smartCombos = catalogAndCombos.smartCombos,
            selectedFoodForPortion = modal.selectedFoodForPortion,
            customFoodDraft = modal.customFoodDraft,
            sheetFeedbackMessage = modal.sheetFeedbackMessage,
            sheetFeedbackEventId = modal.sheetFeedbackEventId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = LoggingSheetUiState(),
    )

    private fun updateSelectedDate(date: LocalDate) {
        selectedDateFlow.value = date
        savedStateHandle[KEY_SELECTED_DATE_ISO] = date.toString()
    }

    private fun updateExpandedMeals(transform: (Set<MealType>) -> Set<MealType>) {
        expandedMealsFlow.update { current ->
            val next = transform(current)
            savedStateHandle[KEY_EXPANDED_MEALS] = ArrayList(next.map { it.name })
            next
        }
    }

    fun refreshToday() {
        val now = LocalDate.now()
        val prevToday = todayFlow.value
        if (now != prevToday) {
            todayFlow.value = now
            if (selectedDateFlow.value == prevToday) {
                updateSelectedDate(now)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        updateSelectedDate(date)
    }

    fun toggleWeekCalendar() {
        isWeekCalendarVisibleFlow.update { current ->
            val next = !current
            savedStateHandle[KEY_WEEK_CALENDAR_VISIBLE] = next
            next
        }
    }

    fun shiftWeek(weeksDelta: Long) {
        updateSelectedDate(selectedDateFlow.value.plusWeeks(weeksDelta))
    }

    fun jumpToToday() {
        val now = LocalDate.now()
        todayFlow.value = now
        updateSelectedDate(now)
    }

    fun toggleMealExpanded(mealType: MealType) {
        updateExpandedMeals { current ->
            if (current.contains(mealType)) current - mealType else current + mealType
        }
    }

    fun addWater(deltaMl: Int) {
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            nutritionRepository.addWaterMl(dateIso, deltaMl)
        }
    }

    fun openAddFoodModal(mealType: MealType) {
        searchQueryFlow.value = ""
        modalStateFlow.update {
            it.copy(
                activeLoggingMealType = mealType,
                isCreatingCustomFood = false,
                selectedCategory = FoodCategory.ALL,
                favoritesOnly = false,
                selectedFoodForPortion = null,
                customFoodDraft = CustomFoodDraft(),
                sheetFeedbackMessage = null,
            )
        }
    }

    fun closeAddFoodModal() {
        searchQueryFlow.value = ""
        modalStateFlow.update {
            it.copy(
                activeLoggingMealType = null,
                isCreatingCustomFood = false,
                selectedFoodForPortion = null,
                sheetFeedbackMessage = null,
            )
        }
    }

    fun dismissSheetFeedback() {
        modalStateFlow.update { it.copy(sheetFeedbackMessage = null) }
    }

    fun openCustomFoodCreator() {
        modalStateFlow.update {
            it.copy(
                isCreatingCustomFood = true,
                selectedFoodForPortion = null,
            )
        }
    }

    fun closeCustomFoodCreator() {
        modalStateFlow.update {
            it.copy(isCreatingCustomFood = false)
        }
    }

    fun selectLoggingMealType(mealType: MealType) {
        modalStateFlow.update { it.copy(activeLoggingMealType = mealType) }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun selectCategory(category: FoodCategory) {
        modalStateFlow.update { it.copy(selectedCategory = category, favoritesOnly = false) }
    }

    fun selectFavoritesOnly() {
        modalStateFlow.update { it.copy(favoritesOnly = true, selectedCategory = FoodCategory.ALL) }
    }

    fun openPortionPicker(food: FoodItem) {
        modalStateFlow.update { it.copy(selectedFoodForPortion = food) }
    }

    fun closePortionPicker() {
        modalStateFlow.update { it.copy(selectedFoodForPortion = null) }
    }

    fun toggleFavorite(food: FoodItem) {
        viewModelScope.launch {
            val nextFav = nutritionRepository.flipFavoriteFood(food.id) ?: return@launch
            modalStateFlow.update { state ->
                val currentPortion = state.selectedFoodForPortion
                if (currentPortion != null && currentPortion.id == food.id) {
                    state.copy(selectedFoodForPortion = currentPortion.copy(isFavorite = nextFav))
                } else {
                    state
                }
            }
        }
    }

    fun quickLogDefaultFood(food: FoodItem) {
        logFoodWithPortion(
            food = food,
            unitLabel = food.defaultUnitLabel,
            unitGrams = food.defaultUnitGrams,
            multiplier = 1f,
        )
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
            updateExpandedMeals { it + mealType }
            val feedbackId = sheetFeedbackSeq.incrementAndGet()
            modalStateFlow.update {
                it.copy(
                    selectedFoodForPortion = null,
                    sheetFeedbackMessage = "${food.name} (${mealType.title}) eklendi",
                    sheetFeedbackEventId = feedbackId,
                )
            }
        }
    }

    fun logSmartCombo(combo: SmartComboSuggestion) {
        if (!isSubmittingModalAction.compareAndSet(false, true)) return
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            try {
                nutritionRepository.logComboItemsToMeal(
                    dateIso = dateIso,
                    mealType = mealType,
                    entries = combo.items,
                )
                updateExpandedMeals { it + mealType }
                modalStateFlow.update { it.copy(activeLoggingMealType = null, isCreatingCustomFood = false) }
                showStatusBanner("${combo.title} (${NutraFormatters.formatInt(combo.totalCalories)} kcal) eklendi")
            } finally {
                isSubmittingModalAction.set(false)
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
        if (!isUpdatingLoggedItem.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                nutritionRepository.updateMealLogMultiplier(item, newMultiplier)
                bannerStateFlow.update { it.copy(editingLogItem = null) }
            } finally {
                isUpdatingLoggedItem.set(false)
            }
        }
    }

    fun deleteLoggedItem(item: MealLogItem) {
        viewModelScope.launch {
            nutritionRepository.deleteMealLog(item.id)
            val eventId = bannerSeq.incrementAndGet()
            bannerStateFlow.update {
                it.copy(
                    editingLogItem = null,
                    lastDeletedItem = item,
                    statusBannerMessage = null,
                    bannerEventId = eventId,
                )
            }
        }
    }

    private fun showStatusBanner(message: String) {
        val eventId = bannerSeq.incrementAndGet()
        bannerStateFlow.update {
            it.copy(
                lastDeletedItem = null,
                statusBannerMessage = message,
                bannerEventId = eventId,
            )
        }
    }

    fun showIntelligenceBanner(message: String) {
        showStatusBanner(message)
    }

    fun onIntelligenceMealLogged(mealType: MealType, message: String) {
        updateExpandedMeals { it + mealType }
        showStatusBanner(message)
    }

    fun undoDeleteLoggedItem() {
        val deleted = bannerStateFlow.getAndUpdate { it.copy(lastDeletedItem = null) }.lastDeletedItem ?: return
        viewModelScope.launch {
            nutritionRepository.restoreMealLogItem(deleted)
        }
    }

    fun dismissUndoBanner() {
        bannerStateFlow.update { it.copy(lastDeletedItem = null, statusBannerMessage = null) }
    }

    // --- Custom Food Creation ---
    fun updateCustomFoodDraft(draft: CustomFoodDraft) {
        modalStateFlow.update { it.copy(customFoodDraft = draft) }
    }

    fun createAndLogCustomFood(
        name: String,
        unitLabel: String,
        unitGrams: Float,
        caloriesPerServing: Int,
        proteinPerServing: Float,
        carbsPerServing: Float,
        fatPerServing: Float,
    ) {
        if (!isSubmittingModalAction.compareAndSet(false, true)) return
        val safeGrams = unitGrams.coerceIn(1f, 5000f)
        val factor = 100f / safeGrams
        val newFood = FoodItem(
            name = name.trim(),
            subtitle = "Özel Besin · ${unitLabel.trim().ifBlank { "1 Porsiyon" }}",
            category = FoodCategory.CUSTOM,
            caloriesPer100g = (caloriesPerServing * factor).roundToInt().coerceAtLeast(0),
            proteinPer100g = (proteinPerServing * factor).coerceAtLeast(0f),
            carbsPer100g = (carbsPerServing * factor).coerceAtLeast(0f),
            fatPer100g = (fatPerServing * factor).coerceAtLeast(0f),
            defaultUnitLabel = unitLabel.trim().ifBlank { "1 Porsiyon" },
            defaultUnitGrams = safeGrams,
            isCustom = true,
            isFavorite = true,
        )
        val mealType = modalStateFlow.value.activeLoggingMealType ?: MealType.BREAKFAST
        val dateIso = selectedDateFlow.value.toString()

        viewModelScope.launch {
            try {
                val savedFood = nutritionRepository.createCustomFoodAndLog(
                    dateIso = dateIso,
                    mealType = mealType,
                    food = newFood,
                )
                updateExpandedMeals { it + mealType }
                modalStateFlow.update {
                    it.copy(
                        activeLoggingMealType = null,
                        isCreatingCustomFood = false,
                        customFoodDraft = CustomFoodDraft(),
                    )
                }
                showStatusBanner("${savedFood.name} oluşturuldu ve öğüne eklendi")
            } finally {
                isSubmittingModalAction.set(false)
            }
        }
    }
}
