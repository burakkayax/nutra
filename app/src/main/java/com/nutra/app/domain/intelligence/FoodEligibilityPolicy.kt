package com.nutra.app.domain.intelligence

import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single centralized eligibility policy enforcing allergen exclusions, diet styles,
 * user-excluded keywords, disliked feedback, and unverified custom-food guardrails
 * across both Intelligence Studio and Dashboard combo/recommendation surfaces.
 */
@Singleton
class FoodEligibilityPolicy @Inject constructor() {

    fun isEligible(
        classified: ClassifiedFood,
        dietaryProfile: DietaryProfile,
        feedbackByKey: Map<String, FoodFeedbackRecord>,
        requireReadyToEat: Boolean = true,
        forAutomaticRecommendation: Boolean = true,
    ): Boolean {
        if (requireReadyToEat && classified.edibility != EdibilityStatus.READY_TO_EAT) return false

        val fb = feedbackByKey[classified.feedbackKey]
        if (fb?.status == FoodFeedbackStatus.DISLIKED) return false

        // Safety gate for unverified custom foods when the user has strict allergen/diet rules
        val hasStrictDietaryGuardrail = dietaryProfile.excludedAllergens.isNotEmpty() ||
            dietaryProfile.dietStyle == DietStyle.VEGAN ||
            dietaryProfile.dietStyle == DietStyle.VEGETARIAN ||
            dietaryProfile.dietStyle == DietStyle.PESCATARIAN
        if (forAutomaticRecommendation &&
            classified.food.isCustom &&
            !classified.food.allergenVerified &&
            hasStrictDietaryGuardrail
        ) {
            return false
        }

        // Hard allergen exclusions
        for (excluded in dietaryProfile.excludedAllergens) {
            if (excluded in classified.allergens) return false
        }

        // Diet style hard rules
        when (dietaryProfile.dietStyle) {
            DietStyle.STANDARD, DietStyle.MEDITERRANEAN, DietStyle.LOW_CARB -> Unit
            DietStyle.VEGETARIAN -> {
                if (classified.containsRedMeat || classified.containsPoultry || classified.containsFish) return false
            }
            DietStyle.VEGAN -> {
                if (classified.containsAnimalProduct) return false
            }
            DietStyle.PESCATARIAN -> {
                if (classified.containsRedMeat || classified.containsPoultry) return false
            }
        }

        // User-excluded custom keywords (also tolerate comma/semicolon-separated entries)
        if (dietaryProfile.excludedKeywords.isNotEmpty()) {
            val searchTarget = buildString(96) {
                append(classified.food.normalizedName.ifBlank { TurkishTextNormalizer.normalize(classified.food.name) })
                if (classified.food.normalizedSearchText.isNotBlank()) {
                    append(' ')
                    append(classified.food.normalizedSearchText)
                }
            }
            for (rawKw in dietaryProfile.excludedKeywords) {
                for (part in rawKw.split(',', ';')) {
                    val normKw = TurkishTextNormalizer.normalize(part)
                    if (normKw.length >= 2 && searchTarget.contains(normKw)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    fun hasStrictAllergenOrDietRules(dietaryProfile: DietaryProfile): Boolean =
        dietaryProfile.excludedAllergens.isNotEmpty() ||
            dietaryProfile.dietStyle == DietStyle.VEGAN ||
            dietaryProfile.dietStyle == DietStyle.VEGETARIAN ||
            dietaryProfile.dietStyle == DietStyle.PESCATARIAN
}
