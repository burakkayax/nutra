package com.nutra.app.domain.metabolism

import androidx.compose.runtime.Immutable
import com.nutra.app.domain.model.MacroGoals

enum class DayIntakeValidity {
    COMPLETE,
    UNLOGGED,
    SUSPECTED_INCOMPLETE,
    EXTREME_OUTLIER,
}

@Immutable
data class DailyIntakeObservation(
    val dateIso: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val mealTypeCount: Int,
    val itemCount: Int,
)

enum class DataQualityLevel(val title: String) {
    INSUFFICIENT("Veri yetersiz"),
    LOW("Düşük güven"),
    MODERATE("Orta güven"),
    HIGH("Yüksek güven"),
}

@Immutable
data class DataQualityReport(
    val level: DataQualityLevel,
    val spanDays: Int,
    val loggedIntakeDaysLast28: Int,
    val completeIntakeDaysLast28: Int,
    val suspectedIncompleteDaysLast28: Int,
    val weighInCountLast28: Int,
    val totalValidWeighIns: Int,
    val intakeCompletenessRatio: Float,
    val weighInsPerWeek: Float,
    val ignoredOutlierWeighIns: Int,
    val summaryMessage: String,
    val improvementTip: String?,
)

@Immutable
data class AdaptiveWeightPoint(
    val dateIso: String,
    val rawWeightKg: Float?,
    val trendWeightKg: Float,
    val isOutlierRaw: Boolean = false,
)

@Immutable
data class TdeeHistoryPoint(
    val dateIso: String,
    val estimatedTdeeKcal: Int,
    val uncertaintyKcal: Int,
    val priorTdeeKcal: Int,
    val rollingIntakeAvgKcal: Int?,
)

@Immutable
data class WeightProjectionPoint(
    val dateIso: String,
    val weekOffset: Int,
    val expectedKg: Float,
    val optimisticKg: Float,
    val conservativeKg: Float,
)

@Immutable
data class TargetEtaRange(
    val reachesTarget: Boolean,
    val alreadyAtTarget: Boolean,
    val minWeeks: Int?,
    val maxWeeks: Int?,
    val expectedWeeks: Int?,
    val earliestDateIso: String?,
    val latestDateIso: String?,
    val equilibriumWeightKg: Float,
    val summaryText: String,
)

enum class PlateauFactorType(val title: String) {
    WATER_RETENTION_NOISE("Geçici su ve glikojen dalgalanması"),
    INCOMPLETE_LOGGING("Eksik veya düzensiz beslenme kaydı"),
    SPARSE_WEIGH_INS("Seyrek tartım sıklığı"),
    INTAKE_GOAL_MISMATCH("Kalori alımının hedeften sapması"),
    METABOLIC_OR_ACTIVITY_SHIFT("Enerji harcaması veya aktivite değişimi"),
}

@Immutable
data class PlateauFactor(
    val type: PlateauFactorType,
    val likelihoodScore: Float,
    val explanation: String,
)

@Immutable
data class PlateauAssessment(
    val isStalled: Boolean,
    val stallDurationDays: Int,
    val headline: String,
    val explanation: String,
    val factors: List<PlateauFactor>,
    val recommendsWaiting: Boolean,
)

enum class MetabolismGuardrailState {
    NORMAL_ADULT,
    ADOLESCENT_PROTECTED,
    SPECIAL_HEALTH_PROTECTED,
}

@Immutable
data class CalorieTargetProposal(
    val currentGoals: MacroGoals,
    val proposedGoals: MacroGoals,
    val deltaKcal: Int,
    val title: String,
    val rationale: String,
    val uncertaintyNote: String,
    val isSafetyClamped: Boolean,
    val safetyNote: String?,
)

@Immutable
data class MetabolismAnalysisResult(
    val analysisDateIso: String,
    val guardrailState: MetabolismGuardrailState,
    val guardrailNotice: String?,
    val priorBmrKcal: Int,
    val priorTdeeKcal: Int,
    val estimatedTdeeKcal: Int,
    val uncertaintyKcal: Int,
    val tdeeDeltaFromPriorKcal: Int,
    val confidenceScore: Int,
    val isAdaptiveCalibrated: Boolean,
    val currentRawWeightKg: Float?,
    val currentTrendWeightKg: Float,
    val weeklyWeightChangeKg: Float,
    val scaleVolatilityKg: Float,
    val averageDailyIntakeLast28Kcal: Int?,
    val averageDailyIntakeLast14Kcal: Int?,
    val activeDailyCalorieGoal: Int,
    val targetWeightKg: Float,
    val dataQuality: DataQualityReport,
    val weightSeries: List<AdaptiveWeightPoint>,
    val tdeeHistory: List<TdeeHistoryPoint>,
    val projectionCurve: List<WeightProjectionPoint>,
    val targetEta: TargetEtaRange?,
    val plateauAssessment: PlateauAssessment,
    val calorieProposal: CalorieTargetProposal?,
    val plainSummary: String,
)
