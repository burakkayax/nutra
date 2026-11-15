package com.nutra.app.core.network

import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.ServingUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class OpenFoodFactsService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupBarcode(barcode: String): AppResult<FoodItem> = withContext(Dispatchers.IO) {
        val cleaned = barcode.trim()
        if (cleaned.length < 8) {
            return@withContext AppResult.Error("Geçersiz barkod numarası (En az 8 haneli EAN/UPC giriniz).")
        }

        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$cleaned.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NutraAndroid/1.0 (Android 17)")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error("Barkod sunucusuna ulaşılamadı (Kod: ${response.code}).")
                }
                val bodyStr = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val status = root["status"]?.jsonPrimitive?.intOrNull ?: 0
                if (status != 1) {
                    return@withContext AppResult.Error("Bu barkoda ($cleaned) ait ürün OpenFoodFacts veritabanında bulunamadı.")
                }

                val product = root["product"]?.jsonObject
                    ?: return@withContext AppResult.Error("Ürün detayı okunamadı.")

                val name = product["product_name_tr"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: product["product_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: "Barkodlu Ürün ($cleaned)"

                val brands = product["brands"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: "Paketli Ürün"

                val nutriments = product["nutriments"]?.jsonObject
                val kcal100 = nutriments?.get("energy-kcal_100g")?.jsonPrimitive?.floatOrNull?.roundToInt()
                    ?: nutriments?.get("energy-kcal")?.jsonPrimitive?.floatOrNull?.roundToInt()
                    ?: 150

                val protein100 = nutriments?.get("proteins_100g")?.jsonPrimitive?.floatOrNull ?: 5f
                val carbs100 = nutriments?.get("carbohydrates_100g")?.jsonPrimitive?.floatOrNull ?: 20f
                val fat100 = nutriments?.get("fat_100g")?.jsonPrimitive?.floatOrNull ?: 5f

                val servingGrams = product["serving_quantity"]?.jsonPrimitive?.floatOrNull?.coerceIn(10f, 500f) ?: 100f

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
                    barcode = cleaned,
                    isCustom = true,
                )
                AppResult.Success(item)
            }
        } catch (e: Exception) {
            AppResult.Error("Barkod sorgulanırken internet bağlantısı kurulamadı. Yerel barkodları (örn. 8690526011012) çevrimdışı kullanabilirsiniz.", e)
        }
    }
}
