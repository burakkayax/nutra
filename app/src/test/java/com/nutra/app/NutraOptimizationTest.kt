package com.nutra.app

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutraOptimizationTest {

    @Test
    fun turkishTextNormalizer_collapsesWhitespaceAndNormalizesTurkishChars() {
        assertEquals(
            "suzme yogurt 100g",
            TurkishTextNormalizer.normalize("  Süzme   Yoğurt\t\n 100g  "),
        )
        assertEquals("", TurkishTextNormalizer.normalize("   \n\t  "))
        assertEquals("igdir sicagi", TurkishTextNormalizer.normalize("Iğdır Sıcağı"))

        val tokens = TurkishTextNormalizer.tokenizeNormalized("tam bugday")
        assertTrue(TurkishTextNormalizer.matchesNormalizedTokens("tam bugday ekmegi lifli", tokens))
        assertFalse(TurkishTextNormalizer.matchesNormalizedTokens("beyaz ekmek", tokens))
    }

    @Test
    fun foodItemEntity_fastPathAndJsonRoundtripProduceEquivalentDomainModels() {
        val noAlternates = FoodItem(
            id = 10L,
            name = "Sade Kahve",
            subtitle = "Şekersiz filtre kahve",
            category = FoodCategory.DRINKS,
            caloriesPer100g = 2,
            proteinPer100g = 0.2f,
            carbsPer100g = 0f,
            fatPer100g = 0f,
            defaultUnitLabel = "1 Fincan",
            defaultUnitGrams = 200f,
            alternateUnits = emptyList(),
            preferredMealTypes = emptySet(),
        )
        val emptyEntity = FoodItemEntity.fromDomain(noAlternates, "sade kahve sekersiz filtre kahve")
        val decodedEmpty = emptyEntity.toDomain()
        assertTrue(decodedEmpty.alternateUnits.isEmpty())
        assertTrue(decodedEmpty.preferredMealTypes.isEmpty())
        assertEquals("sade kahve", decodedEmpty.normalizedName)
        assertEquals("sade kahve sekersiz filtre kahve", decodedEmpty.normalizedSearchText)

        val withAlternates = noAlternates.copy(
            alternateUnits = listOf(ServingUnit("Büyük Kupa", 350f)),
            preferredMealTypes = setOf(MealType.BREAKFAST, MealType.SNACK),
        )
        val fullDecoded = FoodItemEntity.fromDomain(withAlternates, "sade kahve").toDomain()
        assertEquals(1, fullDecoded.alternateUnits.size)
        assertEquals("Büyük Kupa", fullDecoded.alternateUnits.first().label)
        assertEquals(setOf(MealType.BREAKFAST, MealType.SNACK), fullDecoded.preferredMealTypes)
    }

    @Test
    fun nutraFormatters_formatsTurkishNumbersDatesAndWaterConsistently() {
        assertEquals("2.250", NutraFormatters.formatInt(2250))
        assertEquals("75,4", NutraFormatters.formatKg(75.4f))
        assertEquals("750 ml", NutraFormatters.formatWaterLitres(750))
        assertEquals("2,1 L", NutraFormatters.formatWaterLitres(2100))

        val today = LocalDate.now().toString()
        assertEquals("Bugün", NutraFormatters.formatShortDate(today, todayIso = today))
        assertEquals("10 Kas", NutraFormatters.formatShortDate("2026-11-10", todayIso = "2026-11-16"))
        assertFalse(NutraFormatters.formatShortWeekday("2026-11-16").isBlank())
    }
}
