package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.repository.validateBackupPayload
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import com.nutra.app.domain.usecase.WeightTrendCalculator
import com.nutra.app.feature.analytics.resolveNearestXIndex
import com.nutra.app.feature.profile.BoundedReadResult
import com.nutra.app.feature.profile.readBoundedUtf8Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs

class NutraPerformanceAndStabilityTest {

    @Test
    fun nutraFormatters_fastPathsMatchJavaFormattersAcrossBoundaryValues() {
        val trNumber = NumberFormat.getIntegerInstance(NutraFormatters.TR_LOCALE)
        for (v in listOf(-1_234_567, -1000, -999, -1, 0, 1, 999, 1000, 2250, 1_000_000)) {
            assertEquals("formatInt($v)", trNumber.format(v), NutraFormatters.formatInt(v))
        }

        for (kg in listOf(-12.4f, -0.5f, -0.04f, 0f, 0.04f, 0.05f, 72.5f, 75.4f, 120.0f, 249.9f)) {
            val normalized = if (abs(kg) < 0.05f) 0f else kg
            val expected = String.format(NutraFormatters.TR_LOCALE, "%.1f", normalized)
            assertEquals("formatKg($kg)", expected, NutraFormatters.formatKg(kg))
        }
        assertEquals("0,0", NutraFormatters.formatKg(Float.NaN))
        assertEquals("0,0", NutraFormatters.formatKg(Float.POSITIVE_INFINITY))

        assertEquals("0", NutraFormatters.formatMacroGrams(-5f))
        assertEquals("0,4", NutraFormatters.formatMacroGrams(0.4f))
        assertEquals("12", NutraFormatters.formatMacroGrams(12.0f))
        assertEquals("12,4", NutraFormatters.formatMacroGrams(12.4f))
        assertEquals("1.234", NutraFormatters.formatMacroGrams(1234.0f))
        assertEquals("1234,5", NutraFormatters.formatMacroGrams(1234.5f))

        assertEquals("0,25", NutraFormatters.formatMultiplier(0.25f))
        assertEquals("0,5", NutraFormatters.formatMultiplier(0.5f))
        assertEquals("1", NutraFormatters.formatMultiplier(1.0f))
        assertEquals("1,25", NutraFormatters.formatMultiplier(1.25f))
        assertEquals("2,05", NutraFormatters.formatMultiplier(2.05f))
        assertEquals("1", NutraFormatters.formatMultiplier(Float.NaN))

        assertEquals("0 ml", NutraFormatters.formatWaterLitres(0))
        assertEquals("999 ml", NutraFormatters.formatWaterLitres(999))
        assertEquals("1,0 L", NutraFormatters.formatWaterLitres(1000))
        assertEquals("2,1 L", NutraFormatters.formatWaterLitres(2100))
        assertEquals("2,5 L", NutraFormatters.formatWaterLitres(2500))
    }

    @Test
    fun turkishTextNormalizer_tokenizesSingleAndMultiWordQueriesCleanly() {
        assertEquals(emptyList<String>(), TurkishTextNormalizer.tokenizeNormalized(""))
        assertEquals(listOf("yumurta"), TurkishTextNormalizer.tokenizeNormalized("yumurta"))
        assertEquals(
            listOf("tam", "bugday", "ekmegi"),
            TurkishTextNormalizer.tokenizeNormalized(TurkishTextNormalizer.normalize("  Tam   Buğday\tEkmeği  ")),
        )
    }

    @Test
    fun weightTrendCalculator_handlesUnsortedDuplicatesAndTieBreakersAccurately() {
        val calc = WeightTrendCalculator()
        val entries = listOf(
            WeightEntry(id = 3, dateIso = "2026-11-12", weightKg = 75.8f, timestampMillis = 3000L),
            WeightEntry(id = 1, dateIso = "2026-11-10", weightKg = 76.4f, timestampMillis = 1000L),
            WeightEntry(id = 2, dateIso = "2026-11-10", weightKg = 76.0f, timestampMillis = 1500L),
            WeightEntry(id = 4, dateIso = "2026-11-11", weightKg = 77.0f, timestampMillis = 2000L),
            WeightEntry(id = 5, dateIso = "2026-11-11", weightKg = 77.2f, timestampMillis = 2000L), // Equal timestamp, higher id wins
        )

        val trend = calc.calculateTrend(entries, smoothingFactor = 0.25f)
        assertEquals(3, trend.size)
        assertEquals("2026-11-10", trend[0].dateIso)
        assertEquals(76.0f, trend[0].rawWeightKg, 0.001f)
        assertEquals(76.0f, trend[0].emaTrendKg, 0.01f)

        assertEquals("2026-11-11", trend[1].dateIso)
        assertEquals(77.2f, trend[1].rawWeightKg, 0.001f)
        assertEquals(76.3f, trend[1].emaTrendKg, 0.05f)

        assertEquals("2026-11-12", trend[2].dateIso)
        assertEquals(75.8f, trend[2].rawWeightKg, 0.001f)
    }

    @Test
    fun resolveNearestXIndex_binarySearchMatchesLinearScanExactly() {
        fun linearScan(touchX: Float, widthPx: Int, padXPx: Float, fractions: FloatArray): Int {
            if (fractions.isEmpty()) return 0
            val usableWidth = (widthPx - padXPx * 2f).coerceAtLeast(1f)
            var bestIdx = 0
            var bestDist = Float.MAX_VALUE
            for (i in fractions.indices) {
                val x = padXPx + usableWidth * fractions[i]
                val d = abs(touchX - x)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            return bestIdx
        }

        val testArrays = listOf(
            floatArrayOf(),
            floatArrayOf(0f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, 0.1f, 0.1f, 0.45f, 0.8f, 1f),
            FloatArray(120) { i -> (i.toFloat() / 119f) * (i.toFloat() / 119f) },
        )

        for (arr in testArrays) {
            for (x in -20..1020 step 7) {
                val expected = linearScan(x.toFloat(), widthPx = 1000, padXPx = 24f, fractions = arr)
                val actual = resolveNearestXIndex(x.toFloat(), widthPx = 1000, padXPx = 24f, xFractions = arr)
                assertEquals("Mismatch at touchX=$x for size=${arr.size}", expected, actual)
            }
        }
    }

    @Test
    fun contextualRanker_worksAccuratelyOnLargeSyntheticCatalog() {
        val baseCatalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, f ->
            f.copy(
                id = idx + 1L,
                normalizedName = f.normalizedName.ifBlank { TurkishTextNormalizer.normalize(f.name) },
                normalizedSearchText = f.normalizedSearchText.ifBlank {
                    TurkishTextNormalizer.normalize("${f.name} ${f.subtitle} ${f.category.title}")
                },
            )
        }
        val largeCatalog = List(1500) { i ->
            val src = baseCatalog[i % baseCatalog.size]
            src.copy(
                id = i + 1L,
                name = if (i < baseCatalog.size) src.name else "${src.name} #$i",
                normalizedName = if (i < baseCatalog.size) src.normalizedName else "${src.normalizedName} #$i",
                normalizedSearchText = if (i < baseCatalog.size) src.normalizedSearchText else "${src.normalizedSearchText} #$i",
            )
        }

        val engine = ContextualMealRankerAndComboEngine()
        val egg = baseCatalog.first { it.name.contains("Haşlanmış Yumurta") }
        val cheese = baseCatalog.first { it.name.contains("Ezine") }
        val history = listOf(
            MealLogItem(1L, "2026-11-15", MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 1000L),
            MealLogItem(2L, "2026-11-15", MealType.BREAKFAST, cheese.id, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 1001L),
            MealLogItem(3L, "2026-11-16", MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 2000L),
            MealLogItem(4L, "2026-11-16", MealType.BREAKFAST, cheese.id, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 2001L),
        )
        val stats = engine.prepareMealUsageStats(history, MealType.BREAKFAST)
        val ranked = engine.rankFoodsWithStats(largeCatalog, stats, MealType.BREAKFAST, "haslanmis yumurta", FoodCategory.ALL)
        assertTrue(ranked.isNotEmpty())
        assertEquals(egg.id, ranked.first().id)

        val combos = engine.buildSmartComboSuggestions(MealType.BREAKFAST, emptyList(), history, largeCatalog)
        assertTrue(combos.isNotEmpty())
        assertEquals(2, combos.first().coOccurrenceCount)
    }

    @Test
    fun backupValidationAndBoundedReader_rejectInvalidVersionCorruptDataAndOversizedFiles() {
        val validEmpty = NutraBackupPayload(
            version = 1,
            exportedAtDateIso = LocalDate.now().toString(),
            userProfile = UserProfile(),
            customFoods = emptyList(),
            mealLogs = emptyList(),
            waterLogs = emptyMap(),
            weightEntries = emptyList(),
        )
        assertNull(validateBackupPayload(validEmpty))

        val unsupportedVersion = validEmpty.copy(version = 99)
        assertNotNull(validateBackupPayload(unsupportedVersion))

        val corruptMeals = validEmpty.copy(
            mealLogs = listOf(
                MealLogItem(1L, "not-a-date", MealType.LUNCH, null, "Test", "1 Porsiyon", 1f, 100f, 100, 10f, 10f, 2f, 1L),
            ),
        )
        assertNotNull(validateBackupPayload(corruptMeals))

        val corruptWater = validEmpty.copy(waterLogs = mapOf("bad-date" to 500))
        assertNotNull(validateBackupPayload(corruptWater))

        val small = readBoundedUtf8Stream(ByteArrayInputStream("""{"version":1}""".toByteArray()), maxBytes = 1024)
        assertTrue(small is BoundedReadResult.Success)
        assertEquals("""{"version":1}""", (small as BoundedReadResult.Success).text)

        val oversized = readBoundedUtf8Stream(ByteArrayInputStream(ByteArray(2048) { 'a'.code.toByte() }), maxBytes = 1024)
        assertEquals(BoundedReadResult.TooLarge, oversized)

        val blank = readBoundedUtf8Stream(ByteArrayInputStream("   \n\t ".toByteArray()), maxBytes = 1024)
        assertEquals(BoundedReadResult.EmptyOrFailed, blank)
    }
}
