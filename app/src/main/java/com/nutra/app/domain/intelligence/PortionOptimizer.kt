package com.nutra.app.domain.intelligence

import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.ServingUnitLabels
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Solves box-constrained convex quadratic portion optimization to match a target meal's
 * calorie and macronutrient budget using realistic household-friendly portions.
 *
 * Used both internally by [MealRecommendationEngine] and directly for user-selected
 * "Kendi Öğününü Dengele" combinations (e.g., Chicken + Bulgur + Yogurt).
 */
class PortionOptimizer @Inject constructor() {

    private companion object {
        const val COORDINATE_DESCENT_ITERS = 24
        val CLEAN_UNIT_MULTIPLIERS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 4f)
        val INDIVISIBLE_PACKAGED_MULTIPLIERS = floatArrayOf(0.5f, 1f, 2f)
        val INDIVISIBLE_UNIT_KEYWORDS = arrayOf("paket", "şişe", "sise", "kutu", "ambalaj", "bar")
    }

    fun optimizePortions(
        foods: List<ClassifiedFood>,
        budget: MealBudget,
        usualGramsByFoodId: Map<Long, Float> = emptyMap(),
    ): CustomPortionOptimizationResult {
        if (foods.isEmpty()) {
            return CustomPortionOptimizationResult(
                items = emptyList(),
                totalCalories = 0,
                totalProtein = 0f,
                totalCarbs = 0f,
                totalFat = 0f,
                fitScore = 0,
                note = "Dengelemek için en az bir besin seç.",
            )
        }

        val k = foods.size
        val ePerG = DoubleArray(k) { foods[it].food.caloriesPer100g.coerceAtLeast(0) / 100.0 }
        val pPerG = DoubleArray(k) { finiteNonNeg(foods[it].food.proteinPer100g) / 100.0 }
        val cPerG = DoubleArray(k) { finiteNonNeg(foods[it].food.carbsPer100g) / 100.0 }
        val fPerG = DoubleArray(k) { finiteNonNeg(foods[it].food.fatPer100g) / 100.0 }

        val minG = DoubleArray(k) { finitePositive(foods[it].portionConstraint.minGrams, 10f).toDouble() }
        val maxG = DoubleArray(k) { idx ->
            max(minG[idx], finitePositive(foods[idx].portionConstraint.maxGrams, 200f).toDouble())
        }
        val anchorG = DoubleArray(k) { idx ->
            val cf = foods[idx]
            val usual = usualGramsByFoodId[cf.food.id]?.takeIf { it.isFinite() && it > 0f }
            val typical = finitePositive(cf.portionConstraint.typicalGrams, 100f)
            val target = usual?.coerceIn(minG[idx].toFloat(), maxG[idx].toFloat())
                ?: typical.coerceIn(minG[idx].toFloat(), maxG[idx].toFloat())
            target.toDouble()
        }

        val targetE = budget.targetCalories.coerceAtLeast(100).toDouble()
        val targetP = finiteNonNeg(budget.targetProteinGrams).coerceAtLeast(5f).toDouble()
        val targetC = finiteNonNeg(budget.targetCarbsGrams).coerceAtLeast(5f).toDouble()
        val targetF = finiteNonNeg(budget.targetFatGrams).coerceAtLeast(3f).toDouble()

        val normE = max(targetE, 150.0)
        val normP = max(targetP, 15.0)
        val normC = max(targetC, 20.0)
        val normF = max(targetF, 10.0)

        // Weight calories & protein strongly; keep habitual-portion regularization gentle so targets are hit cleanly
        val proteinEnergyShare = (targetP * 4.0) / targetE
        val wE = 2.60
        val wP = if (proteinEnergyShare >= 0.25) 2.20 else 1.50
        val wC = 0.65
        val wF = 0.65
        val lambdaReg = 0.015

        val g = DoubleArray(k) { anchorG[it] }

        // Initial uniform scaling toward calorie target before coordinate refinement
        var initialE = 0.0
        for (i in 0 until k) initialE += ePerG[i] * g[i]
        if (initialE > 20.0) {
            val scale = (targetE / initialE).coerceIn(0.55, 1.65)
            for (i in 0 until k) {
                g[i] = (g[i] * scale).coerceIn(minG[i], maxG[i])
            }
        }

        // Exact 1-D coordinate descent on strictly convex quadratic objective
        repeat(COORDINATE_DESCENT_ITERS) {
            for (i in 0 until k) {
                var sumE = 0.0
                var sumP = 0.0
                var sumC = 0.0
                var sumF = 0.0
                for (j in 0 until k) {
                    if (j == i) continue
                    sumE += ePerG[j] * g[j]
                    sumP += pPerG[j] * g[j]
                    sumC += cPerG[j] * g[j]
                    sumF += fPerG[j] * g[j]
                }

                val ae = ePerG[i] / normE
                val ap = pPerG[i] / normP
                val ac = cPerG[i] / normC
                val af = fPerG[i] / normF
                val ar = 1.0 / max(anchorG[i], 20.0)

                val re = (sumE - targetE) / normE
                val rp = (sumP - targetP) / normP
                val rc = (sumC - targetC) / normC
                val rf = (sumF - targetF) / normF
                val rr = -anchorG[i] / max(anchorG[i], 20.0)

                val quadA = wE * ae * ae + wP * ap * ap + wC * ac * ac + wF * af * af + lambdaReg * ar * ar
                val linB = wE * ae * re + wP * ap * rp + wC * ac * rc + wF * af * rf + lambdaReg * ar * rr

                if (quadA > 1e-12) {
                    val opt = (-linB / quadA).coerceIn(minG[i], maxG[i])
                    g[i] = opt
                }
            }
        }

        // Snap continuous gram amounts to human-friendly household measures or clean gram increments
        val snapped = snapPortionsHumanFriendly(
            foods = foods,
            continuousGrams = g,
            targetE = targetE,
            targetP = targetP,
            targetC = targetC,
            targetF = targetF,
            normE = normE,
            normP = normP,
            normC = normC,
            normF = normF,
            wE = wE,
            wP = wP,
            wC = wC,
            wF = wF,
        )

        var totalCal = 0
        var totalP = 0.0
        var totalC = 0.0
        var totalF = 0.0
        for (item in snapped) {
            totalCal += item.calories
            totalP += item.proteinGrams
            totalC += item.carbsGrams
            totalF += item.fatGrams
        }

        val fitScore = computeFitScore(
            actualCal = totalCal,
            actualP = totalP.toFloat(),
            actualC = totalC.toFloat(),
            actualF = totalF.toFloat(),
            budget = budget,
        )

        val note = when {
            fitScore >= 88 -> "Porsiyonlar kalan kalori ve makro hedefine çok yakın dengelendi."
            fitScore >= 72 -> "Gerçekçi porsiyon sınırları içinde hedefe en yakın denge bulundu."
            totalCal > budget.targetCalories * 1.2 ->
                "Seçili besinlerin minimum gerçekçi porsiyonları kalan kalorinin biraz üzerinde kalıyor."
            else ->
                "Seçili besinler gerçekçi porsiyon aralığında dengelendi; ekstra bir protein veya karbonhidrat ekleyerek uyumu artırabilirsin."
        }

        return CustomPortionOptimizationResult(
            items = snapped,
            totalCalories = totalCal,
            totalProtein = round1(totalP.toFloat()),
            totalCarbs = round1(totalC.toFloat()),
            totalFat = round1(totalF.toFloat()),
            fitScore = fitScore,
            note = note,
        )
    }

    fun computeFitScore(
        actualCal: Int,
        actualP: Float,
        actualC: Float,
        actualF: Float,
        budget: MealBudget,
    ): Int {
        val safeCal = actualCal.coerceAtLeast(0).toFloat()
        val safeP = finiteNonNeg(actualP)
        val safeC = finiteNonNeg(actualC)
        val safeF = finiteNonNeg(actualF)
        val targetE = budget.targetCalories.coerceAtLeast(100).toFloat()
        val targetP = finiteNonNeg(budget.targetProteinGrams).coerceAtLeast(10f)
        val targetC = finiteNonNeg(budget.targetCarbsGrams).coerceAtLeast(15f)
        val targetF = finiteNonNeg(budget.targetFatGrams).coerceAtLeast(8f)

        val errE = abs(safeCal - targetE) / max(targetE, 150f)
        val errP = abs(safeP - targetP) / max(targetP, 18f)
        val errC = abs(safeC - targetC) / max(targetC, 25f)
        val errF = abs(safeF - targetF) / max(targetF, 12f)

        val rawErr = 0.42f * errE + 0.34f * errP + 0.12f * errC + 0.12f * errF
        val weightedErr = if (rawErr.isFinite()) rawErr.coerceIn(0f, 1f) else 1f
        return ((1f - weightedErr) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun buildPortionWithGrams(
        classified: ClassifiedFood,
        grams: Float,
        preferHouseholdSnap: Boolean = true,
    ): RecommendedFoodPortion {
        val food = classified.food
        val minBound = finitePositive(classified.portionConstraint.minGrams, 10f)
        val maxBound = max(minBound, finitePositive(classified.portionConstraint.maxGrams, 200f))
        val finiteGrams = if (grams.isFinite()) grams else classified.portionConstraint.typicalGrams
        val clamped = finiteGrams.coerceIn(minBound, maxBound)
        val presentation = resolveCleanUnitPresentation(
            food = food,
            targetGrams = clamped,
            minGrams = minBound,
            maxGrams = maxBound,
            gramStep = classified.portionConstraint.gramStep,
            preferHouseholdSnap = preferHouseholdSnap,
        )
        val finalGrams = presentation.totalGrams.coerceIn(minBound, maxBound)
        return RecommendedFoodPortion(
            food = food,
            feedbackKey = classified.feedbackKey,
            role = classified.role,
            unitLabel = presentation.unitLabel,
            unitGrams = presentation.unitGrams,
            multiplier = presentation.multiplier,
            totalGrams = finalGrams,
            calories = food.caloriesForGrams(finalGrams),
            proteinGrams = food.proteinForGrams(finalGrams),
            carbsGrams = food.carbsForGrams(finalGrams),
            fatGrams = food.fatForGrams(finalGrams),
            minGrams = minBound,
            maxGrams = maxBound,
        )
    }

    private data class UnitPresentation(
        val unitLabel: String,
        val unitGrams: Float,
        val multiplier: Float,
        val totalGrams: Float,
    )

    private fun resolveCleanUnitPresentation(
        food: FoodItem,
        targetGrams: Float,
        minGrams: Float,
        maxGrams: Float,
        gramStep: Float,
        preferHouseholdSnap: Boolean,
    ): UnitPresentation {
        val defGrams = food.defaultUnitGrams
        val isRawDefault = ServingUnitLabels.isRawUnitLabel(food.defaultUnitLabel) ||
            food.defaultUnitLabel == ServingUnitLabels.BASE_100G ||
            food.defaultUnitLabel == ServingUnitLabels.BASE_100ML

        // Prefer natural household unit if a clean multiplier lands within tolerance AND strictly inside [minGrams, maxGrams].
        // When user explicitly adjusts grams manually (preferHouseholdSnap == false), only use a household unit if it matches nearly exactly (<= 1.5%)
        // so manual +/- clicks never snap back to the previous weight.
        val snapTolerance = if (preferHouseholdSnap) 0.08f else 0.015f
        val lowerUnitLabel = food.defaultUnitLabel.lowercase()
        val isIndivisiblePackaged = INDIVISIBLE_UNIT_KEYWORDS.any { lowerUnitLabel.contains(it) }
        val allowedMultipliers = if (isIndivisiblePackaged) INDIVISIBLE_PACKAGED_MULTIPLIERS else CLEAN_UNIT_MULTIPLIERS
        if (!isRawDefault && defGrams.isFinite() && defGrams >= 10f && defGrams <= 350f) {
            var bestMult: Float? = null
            var bestRelErr = Float.MAX_VALUE
            for (m in allowedMultipliers) {
                val candG = defGrams * m
                if (candG < minGrams - 0.01f || candG > maxGrams + 0.01f) continue
                val relErr = abs(candG - targetGrams) / targetGrams.coerceAtLeast(10f)
                if (relErr <= snapTolerance && relErr < bestRelErr) {
                    bestRelErr = relErr
                    bestMult = m
                }
            }
            if (bestMult != null) {
                val total = (defGrams * bestMult).coerceIn(minGrams, maxGrams)
                return UnitPresentation(
                    unitLabel = food.defaultUnitLabel,
                    unitGrams = defGrams,
                    multiplier = bestMult,
                    totalGrams = total,
                )
            }
        }

        // Clean raw gram / ml representation (e.g., 150 g, 140 g, 200 ml) strictly inside [minGrams, maxGrams]
        val finalRawGrams = if (preferHouseholdSnap) {
            val step = gramStep.coerceAtLeast(5f)
            ((targetGrams / step).roundToInt() * step).coerceIn(minGrams, maxGrams)
        } else {
            targetGrams.roundToInt().toFloat().coerceIn(minGrams, maxGrams)
        }
        return UnitPresentation(
            unitLabel = ServingUnitLabels.rawUnitFor(food.baseUnit),
            unitGrams = 1f,
            multiplier = finalRawGrams,
            totalGrams = finalRawGrams,
        )
    }

    private fun snapPortionsHumanFriendly(
        foods: List<ClassifiedFood>,
        continuousGrams: DoubleArray,
        targetE: Double,
        targetP: Double,
        targetC: Double,
        targetF: Double,
        normE: Double,
        normP: Double,
        normC: Double,
        normF: Double,
        wE: Double,
        wP: Double,
        wC: Double,
        wF: Double,
    ): List<RecommendedFoodPortion> {
        val k = foods.size
        // Build 2-3 clean discrete candidates per food around the continuous optimum
        val candidateLists = Array(k) { i ->
            val cf = foods[i]
            val c = cf.portionConstraint
            val step = c.gramStep.coerceAtLeast(5f)
            val baseRounded = ((continuousGrams[i] / step).roundToInt() * step)
                .coerceIn(c.minGrams, c.maxGrams)
            val options = LinkedHashSet<Float>(4)
            options.add(baseRounded)
            val lower = (baseRounded - step).coerceIn(c.minGrams, c.maxGrams)
            val upper = (baseRounded + step).coerceIn(c.minGrams, c.maxGrams)
            options.add(lower)
            options.add(upper)
            options.map { buildPortionWithGrams(cf, it) }.distinctBy { it.totalGrams.roundToInt() }
        }

        fun lossOf(eAcc: Double, pAcc: Double, cAcc: Double, fAcc: Double): Double {
            val de = (eAcc - targetE) / normE
            val dp = (pAcc - targetP) / normP
            val dc = (cAcc - targetC) / normC
            val df = (fAcc - targetF) / normF
            return wE * de * de + wP * dp * dp + wC * dc * dc + wF * df * df
        }

        // Exact search for k <= 4 (<= 3^4 = 81 combos); coordinate descent over discrete candidates for k > 4
        var bestSelection = Array(k) { candidateLists[it][0] }
        if (k <= 4) {
            var bestLoss = Double.MAX_VALUE
            val current = arrayOfNulls<RecommendedFoodPortion>(k)
            fun dfs(idx: Int, eAcc: Double, pAcc: Double, cAcc: Double, fAcc: Double) {
                if (idx == k) {
                    val loss = lossOf(eAcc, pAcc, cAcc, fAcc)
                    if (loss < bestLoss) {
                        bestLoss = loss
                        bestSelection = Array(k) { current[it]!! }
                    }
                    return
                }
                for (cand in candidateLists[idx]) {
                    current[idx] = cand
                    dfs(
                        idx + 1,
                        eAcc + cand.calories,
                        pAcc + cand.proteinGrams,
                        cAcc + cand.carbsGrams,
                        fAcc + cand.fatGrams,
                    )
                }
            }
            dfs(0, 0.0, 0.0, 0.0, 0.0)
        } else {
            var eSum = 0.0
            var pSum = 0.0
            var cSum = 0.0
            var fSum = 0.0
            for (i in 0 until k) {
                eSum += bestSelection[i].calories
                pSum += bestSelection[i].proteinGrams
                cSum += bestSelection[i].carbsGrams
                fSum += bestSelection[i].fatGrams
            }
            repeat(3) {
                var improved = false
                for (i in 0 until k) {
                    val old = bestSelection[i]
                    val baseE = eSum - old.calories
                    val baseP = pSum - old.proteinGrams
                    val baseC = cSum - old.carbsGrams
                    val baseF = fSum - old.fatGrams
                    var bestCand = old
                    var bestLoss = lossOf(eSum, pSum, cSum, fSum)
                    for (cand in candidateLists[i]) {
                        val loss = lossOf(
                            baseE + cand.calories,
                            baseP + cand.proteinGrams,
                            baseC + cand.carbsGrams,
                            baseF + cand.fatGrams,
                        )
                        if (loss < bestLoss - 1e-9) {
                            bestLoss = loss
                            bestCand = cand
                        }
                    }
                    if (bestCand !== old) {
                        bestSelection[i] = bestCand
                        eSum = baseE + bestCand.calories
                        pSum = baseP + bestCand.proteinGrams
                        cSum = baseC + bestCand.carbsGrams
                        fSum = baseF + bestCand.fatGrams
                        improved = true
                    }
                }
                if (!improved) return@repeat
            }
        }

        return bestSelection.toList()
    }

    private fun finiteNonNeg(v: Float): Float = if (v.isFinite() && v > 0f) v else 0f
    private fun finitePositive(v: Float, fallback: Float): Float = if (v.isFinite() && v > 0f) v else fallback
    private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
}
