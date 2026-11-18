package com.nutra.app.core.network

import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.ServingUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private fun JsonElement?.asNonBlankStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonElement?.asFiniteFloatOrNull(): Float? =
    (this as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() }

@Singleton
class OpenFoodFactsService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupBarcode(barcode: String): AppResult<FoodItem> = withContext(Dispatchers.IO) {
        val cleaned = barcode.trim()
        if (cleaned.length !in 8..14 || !cleaned.all { it.isDigit() }) {
            return@withContext AppResult.Error("Geçersiz barkod numarası (8–14 haneli EAN/UPC giriniz).")
        }

        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$cleaned.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NutraAndroid/1.0 (Android 17)")
                .get()
                .build()

            okHttpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error("Barkod sunucusuna ulaşılamadı (Kod: ${response.code}).")
                }
                val bodyStr = response.body.string()
                parseProductResponse(cleaned, bodyStr)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppResult.Error("Barkod sorgulanırken internet bağlantısı kurulamadı. Yerel barkodları (örn. 8690526011012) çevrimdışı kullanabilirsiniz.", e)
        } catch (e: Exception) {
            AppResult.Error("Barkod verisi işlenirken hata oluştu. Lütfen tekrar deneyin.", e)
        }
    }

    internal fun parseProductResponse(cleanedBarcode: String, bodyStr: String): AppResult<FoodItem> {
        val root = json.parseToJsonElement(bodyStr) as? JsonObject
            ?: return AppResult.Error("Ürün verisi okunamadı.")
        val status = (root["status"] as? JsonPrimitive)?.intOrNull ?: 0
        if (status != 1) {
            return AppResult.Error("Bu barkoda ($cleanedBarcode) ait ürün OpenFoodFacts veritabanında bulunamadı.")
        }

        val product = root["product"] as? JsonObject
            ?: return AppResult.Error("Ürün detayı okunamadı.")

        val name = product["product_name_tr"].asNonBlankStringOrNull()
            ?: product["product_name"].asNonBlankStringOrNull()
            ?: "Barkodlu Ürün ($cleanedBarcode)"

        val brands = product["brands"].asNonBlankStringOrNull()
            ?: "Paketli Ürün"

        val nutriments = product["nutriments"] as? JsonObject
        val energyKcal = nutriments?.get("energy-kcal_100g").asFiniteFloatOrNull()
            ?: nutriments?.get("energy-kcal").asFiniteFloatOrNull()
            ?: nutriments?.get("energy_100g").asFiniteFloatOrNull()?.div(4.184f)
        val kcal100 = energyKcal?.roundToInt() ?: 150

        val protein100 = nutriments?.get("proteins_100g").asFiniteFloatOrNull() ?: 5f
        val carbs100 = nutriments?.get("carbohydrates_100g").asFiniteFloatOrNull() ?: 20f
        val fat100 = nutriments?.get("fat_100g").asFiniteFloatOrNull() ?: 5f

        val servingGrams = product["serving_quantity"].asFiniteFloatOrNull()
            ?.coerceIn(10f, 500f)
            ?: 100f

        val item = FoodItem(
            name = name,
            subtitle = brands,
            category = FoodCategory.CUSTOM,
            caloriesPer100g = kcal100.coerceAtLeast(0),
            proteinPer100g = protein100.coerceAtLeast(0f),
            carbsPer100g = carbs100.coerceAtLeast(0f),
            fatPer100g = fat100.coerceAtLeast(0f),
            defaultUnitLabel = if (servingGrams == 100f) "100 Gram" else "1 Paket/Porsiyon (${servingGrams.roundToInt()}g)",
            defaultUnitGrams = servingGrams,
            alternateUnits = listOf(ServingUnit("100 Gram", 100f)),
            barcode = cleanedBarcode,
            isCustom = true,
        )
        return AppResult.Success(item)
    }
}
