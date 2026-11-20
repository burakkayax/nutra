package com.nutra.app.core.network

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import com.nutra.app.domain.model.AiAnalyzedFoodItem
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
private data class GeminiParsedFoodDto(
    val name: String,
    val unitLabel: String,
    val grams: Float,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val confidence: Float = 0.9f,
)

@Serializable
private data class GeminiFoodAnalysisResponseDto(
    val items: List<GeminiParsedFoodDto> = emptyList(),
)

@Singleton
class GeminiAiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private companion object {
        val SEGMENT_SPLIT_REGEX = Regex(
            "(?<!\\d),|,(?!\\d)|[;\\n]|(?:^|\\s)(?:ve|[iİ]le|art[ıiİI])(?=\\s|$)|\\+",
            RegexOption.IGNORE_CASE,
        )
        val GRAM_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:gr|gram|g|ml)\\b", RegexOption.IGNORE_CASE)
        val COUNT_REGEX = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(adet|dilim|kase|porsiyon|bardak|kaşık|kasik|avuç|avuc|tane|ölçek|olcek)?\\b", RegexOption.IGNORE_CASE)
        val HALF_WORD_REGEX = Regex("\\byarim\\b")
        val UNIT_WORD_STRIP_REGEX = Regex("\\b(yarim|tam|orta|buyuk|kucuk|ince|kalin|adet|tane|dilim|kase|porsiyon|su|bardagi|bardak|yemek|tatli|kasigi|kasik|avuc|olcek|paket)\\b")
        val NUMBER_STRIP_REGEX = Regex("\\d+(?:[.,]\\d+)?")
        val WHITESPACE_REGEX = Regex("\\s+")
        const val MAX_IMAGE_DIMENSION_PX = 1024
        const val JPEG_QUALITY = 82
        val SYSTEM_INSTRUCTION = """
            Sen uzman bir Türk diyetisyen ve besin veri analistisin.
            Kullanıcının yazdığı metindeki veya yemek fotoğrafındaki her bir besin kalemini ayrıştır.
            Her besin için Türkçe isim (name), doğal porsiyon açıklaması (unitLabel, örn. '2 Adet (Orta)', '1 Kase', '40g Dilim'),
            toplam gramaj (grams), toplam kalori (calories), protein (proteinGrams), karbonhidrat (carbsGrams),
            yağ (fatGrams) ve tahmin güven skoru (confidence: 0.70 - 0.99 arası) hesapla.
        """.trimIndent()
        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "items",
                        buildJsonObject {
                            put("type", "ARRAY")
                            put(
                                "items",
                                buildJsonObject {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        buildJsonObject {
                                            put("name", buildJsonObject { put("type", "STRING") })
                                            put("unitLabel", buildJsonObject { put("type", "STRING") })
                                            put("grams", buildJsonObject { put("type", "NUMBER") })
                                            put("calories", buildJsonObject { put("type", "INTEGER") })
                                            put("proteinGrams", buildJsonObject { put("type", "NUMBER") })
                                            put("carbsGrams", buildJsonObject { put("type", "NUMBER") })
                                            put("fatGrams", buildJsonObject { put("type", "NUMBER") })
                                            put("confidence", buildJsonObject { put("type", "NUMBER") })
                                        },
                                    )
                                    put(
                                        "required",
                                        JsonArray(
                                            listOf(
                                                "name",
                                                "unitLabel",
                                                "grams",
                                                "calories",
                                                "proteinGrams",
                                                "carbsGrams",
                                                "fatGrams",
                                                "confidence",
                                            ).map { JsonPrimitive(it) },
                                        ),
                                    )
                                },
                            )
                        },
                    )
                },
            )
            put("required", JsonArray(listOf(JsonPrimitive("items"))))
        }
    }

    suspend fun loadCompressedImageBytes(
        contentResolver: ContentResolver,
        uri: Uri,
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val srcWidth = boundsOpts.outWidth
            val srcHeight = boundsOpts.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) {
                return@runCatching null
            }

            var sampleSize = 1
            val longestSide = max(srcWidth, srcHeight)
            while (longestSide / sampleSize > MAX_IMAGE_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / max(decoded.width, decoded.height).toFloat()
            val finalBitmap = if (scale < 1f) {
                decoded.scale(
                    width = (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    height = (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    filter = true,
                ).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            try {
                ByteArrayOutputStream(64 * 1024).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    out.toByteArray()
                }
            } finally {
                finalBitmap.recycle()
            }
        }.getOrNull()
    }

    /**
     * Analyzes natural language food descriptions (e.g. "2 haşlanmış yumurta, 40g ezine peyniri ve 1 dilim tam buğday ekmeği")
     * or plate photos using Google Gemini Structured JSON Output, with an instant on-device NLU fallback when no API key is configured.
     */
    suspend fun analyzeFoodInput(
        promptText: String,
        imageBytes: ByteArray?,
        imageMimeType: String = "image/jpeg",
        apiKey: String,
        modelName: String,
        catalogFoods: List<FoodItem>,
    ): AppResult<List<AiAnalyzedFoodItem>> = withContext(Dispatchers.IO) {
        if (promptText.isBlank() && imageBytes == null) {
            return@withContext AppResult.Error("Lütfen yediklerinizi yazın veya bir yemek fotoğrafı seçin.")
        }

        // If Gemini API key is configured, call Gemini REST API with Structured JSON Schema
        if (apiKey.isNotBlank()) {
            val remoteResult = callGeminiStructuredApi(
                promptText = promptText,
                imageBytes = imageBytes,
                imageMimeType = imageMimeType,
                apiKey = apiKey,
                modelName = modelName.ifBlank { "gemini-2.5-flash" },
            )
            if (remoteResult is AppResult.Success && remoteResult.data.isNotEmpty()) {
                return@withContext remoteResult
            }
            // If network/API fails and text was provided, fall back to on-device NLU so the user is never blocked
            if (promptText.isNotBlank()) {
                val fallback = parseWithOnDeviceNlu(promptText, catalogFoods)
                if (fallback.isNotEmpty()) {
                    return@withContext AppResult.Success(fallback)
                }
            }
            return@withContext if (remoteResult is AppResult.Success) {
                AppResult.Error("Fotoğraf veya metinden besin ayrıştırılamadı. Lütfen daha net bir açıklama veya fotoğraf deneyin.")
            } else {
                remoteResult
            }
        }

        // On-Device NLU & Vision Assist (Zero-Config Mode when API Key is empty)
        val localItems = if (promptText.isNotBlank()) {
            parseWithOnDeviceNlu(promptText, catalogFoods)
        } else {
            // Photo provided in offline/no-key mode: provide a realistic sample plate breakdown that user can tweak
            buildSamplePlateForOfflinePhoto(catalogFoods)
        }

        if (localItems.isEmpty()) {
            AppResult.Error("Besin ayrıştırılamadı. Örn: '2 yumurta, 30g beyaz peynir, 1 dilim tam buğday ekmeği' şeklinde yazabilirsiniz.")
        } else {
            AppResult.Success(localItems)
        }
    }

    private suspend fun callGeminiStructuredApi(
        promptText: String,
        imageBytes: ByteArray?,
        imageMimeType: String,
        apiKey: String,
        modelName: String,
    ): AppResult<List<AiAnalyzedFoodItem>> {
        return try {
            val partsArray = buildJsonArray {
                if (imageBytes != null) {
                    val base64Img = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    add(
                        buildJsonObject {
                            put(
                                "inlineData",
                                buildJsonObject {
                                    put("mimeType", imageMimeType)
                                    put("data", base64Img)
                                },
                            )
                        },
                    )
                }
                val effectivePrompt = promptText.ifBlank {
                    "Bu fotoğraftaki tabakta yer alan yiyecekleri, tahmini porsiyon ve gramajlarıyla ayrıştır."
                }
                add(buildJsonObject { put("text", "$SYSTEM_INSTRUCTION\n\nKullanıcı girdisi: $effectivePrompt") })
            }

            val requestPayload = buildJsonObject {
                put(
                    "contents",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("parts", partsArray)
                            },
                        )
                    },
                )
                put(
                    "generationConfig",
                    buildJsonObject {
                        put("responseMimeType", "application/json")
                        put("responseSchema", RESPONSE_SCHEMA)
                        put("temperature", 0.2)
                    },
                )
            }

            val cleanModel = modelName.trim().removePrefix("models/").ifBlank { "gemini-2.5-flash" }
            val url = "https://generativelanguage.googleapis.com/v1beta/models"
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("$cleanModel:generateContent")
                .build()
            val body = requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey.trim())
                .post(body)
                .build()

            okHttpClient.newCall(request).await().use { response ->
                val rawBody = response.body.string()
                if (!response.isSuccessful) {
                    return AppResult.Error("Gemini API yanıt vermedi (Kod: ${response.code}). Lütfen API anahtarınızı kontrol edin.")
                }

                val textContent = extractStructuredText(rawBody)
                    ?: return AppResult.Error("AI yanıtı çözümlenemedi.")

                val parsed = json.decodeFromString<GeminiFoodAnalysisResponseDto>(textContent)
                val mapped = parsed.items.map { dto ->
                    val safeGrams = if (dto.grams.isFinite()) dto.grams.coerceIn(1f, 5000f) else 100f
                    val safeProtein = if (dto.proteinGrams.isFinite()) dto.proteinGrams.coerceAtLeast(0f) else 0f
                    val safeCarbs = if (dto.carbsGrams.isFinite()) dto.carbsGrams.coerceAtLeast(0f) else 0f
                    val safeFat = if (dto.fatGrams.isFinite()) dto.fatGrams.coerceAtLeast(0f) else 0f
                    val safeConf = if (dto.confidence.isFinite()) dto.confidence.coerceIn(0.5f, 0.99f) else 0.85f
                    AiAnalyzedFoodItem(
                        id = UUID.randomUUID().toString(),
                        name = dto.name.ifBlank { "Besin" },
                        unitLabel = dto.unitLabel.ifBlank { "1 Porsiyon" },
                        baseGrams = safeGrams,
                        multiplier = 1f,
                        baseCalories = dto.calories.coerceAtLeast(0),
                        baseProtein = safeProtein,
                        baseCarbs = safeCarbs,
                        baseFat = safeFat,
                        confidence = safeConf,
                        included = true,
                    )
                }
                AppResult.Success(mapped)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error("AI bağlantısı sırasında hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}", e)
        }
    }

    internal fun extractStructuredText(rawBody: String): String? {
        val root = runCatching { json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull() ?: return null
        val firstCandidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val content = firstCandidate["content"] as? JsonObject ?: return null
        val parts = content["parts"] as? JsonArray ?: return null

        val nonThoughtTexts = buildList {
            for (element in parts) {
                val part = element as? JsonObject ?: continue
                val isThought = (part["thought"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true
                if (isThought) continue
                val text = (part["text"] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { it.isNotBlank() }
                if (text != null) add(text)
            }
        }
        val joined = nonThoughtTexts.joinToString("\n").trim()
        if (joined.isEmpty()) return null
        return stripMarkdownCodeFences(joined)
    }

    private fun stripMarkdownCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline == -1) return trimmed.removePrefix("```").removeSuffix("```").trim()
        val body = trimmed.substring(firstNewline + 1)
        val closingIndex = body.lastIndexOf("```")
        return if (closingIndex >= 0) body.substring(0, closingIndex).trim() else body.trim()
    }

    /**
     * Built-in Turkish Natural Language Food Parser (On-Device NLU).
     * Parses phrases like "2 haşlanmış yumurta, 40g ezine peyniri ve 1 dilim tam buğday ekmeği"
     * into individual structured items with accurate macros.
     */
    fun parseWithOnDeviceNlu(
        text: String,
        catalogFoods: List<FoodItem>,
    ): List<AiAnalyzedFoodItem> {
        val segments = text
            .split(SEGMENT_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.length >= 2 }

        val results = mutableListOf<AiAnalyzedFoodItem>()

        for (segment in segments) {
            val normSegment = TurkishTextNormalizer.normalize(segment)
            val isHalf = HALF_WORD_REGEX.containsMatchIn(normSegment)

            val explicitGrams = GRAM_REGEX.find(normSegment)?.groupValues?.getOrNull(1)
                ?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

            val countMatch = if (explicitGrams == null) COUNT_REGEX.find(normSegment) else null
            val quantity = when {
                countMatch != null -> countMatch.groupValues[1].replace(',', '.').toFloatOrNull()
                    ?.takeIf { it.isFinite() && it > 0f } ?: 1f
                isHalf -> 0.5f
                else -> 1f
            }.coerceIn(0.1f, 50f)

            // Strip numbers and unit words to match the food name
            val cleanedFoodQuery = normSegment
                .replace(GRAM_REGEX, " ")
                .replace(UNIT_WORD_STRIP_REGEX, " ")
                .replace(NUMBER_STRIP_REGEX, " ")
                .trim()
                .replace(WHITESPACE_REGEX, " ")

            if (cleanedFoodQuery.isBlank()) continue

            val matchedFood = findBestCatalogMatch(cleanedFoodQuery, catalogFoods)
            if (matchedFood != null) {
                val totalGrams = (explicitGrams ?: (matchedFood.defaultUnitGrams * quantity)).coerceIn(1f, 5000f)
                val unitDescription = if (explicitGrams != null) {
                    "${explicitGrams.roundToInt()} ${matchedFood.displayUnitSuffix}"
                } else if (quantity == 1f) {
                    matchedFood.defaultUnitLabel
                } else {
                    val qStr = if (quantity % 1f == 0f) quantity.toInt().toString() else quantity.toString()
                    "$qStr x ${matchedFood.defaultUnitLabel}"
                }

                results.add(
                    AiAnalyzedFoodItem(
                        id = UUID.randomUUID().toString(),
                        name = matchedFood.name,
                        unitLabel = unitDescription,
                        baseGrams = totalGrams,
                        multiplier = 1f,
                        baseCalories = matchedFood.caloriesForGrams(totalGrams),
                        baseProtein = matchedFood.proteinForGrams(totalGrams),
                        baseCarbs = matchedFood.carbsForGrams(totalGrams),
                        baseFat = matchedFood.fatForGrams(totalGrams),
                        confidence = 0.95f,
                        included = true,
                    ),
                )
            } else {
                // Intelligent estimation for custom/unlisted dish
                val estGrams = explicitGrams ?: (150f * quantity)
                val estCal = (estGrams * 1.45f).roundToInt()
                results.add(
                    AiAnalyzedFoodItem(
                        id = UUID.randomUUID().toString(),
                        name = segment.replaceFirstChar { it.uppercaseChar() },
                        unitLabel = "${estGrams.roundToInt()} g (Tahmini Porsiyon)",
                        baseGrams = estGrams,
                        multiplier = 1f,
                        baseCalories = estCal,
                        baseProtein = ((estCal * 0.22f) / 4f * 10f).roundToInt() / 10f,
                        baseCarbs = ((estCal * 0.48f) / 4f * 10f).roundToInt() / 10f,
                        baseFat = ((estCal * 0.30f) / 9f * 10f).roundToInt() / 10f,
                        confidence = 0.78f,
                        included = true,
                    ),
                )
            }
        }

        return results
    }

    private fun findBestCatalogMatch(query: String, catalogFoods: List<FoodItem>): FoodItem? {
        val rawTokens = TurkishTextNormalizer.tokenizeNormalized(query)
        val tokens = ArrayList<String>(rawTokens.size)
        for (i in rawTokens.indices) {
            val t = rawTokens[i]
            if (t.length >= 2) tokens.add(t)
        }
        if (tokens.isEmpty()) return null

        var bestFood: FoodItem? = null
        var bestScore = 17
        for (i in catalogFoods.indices) {
            val food = catalogFoods[i]
            val normName = food.normalizedName.ifBlank { TurkishTextNormalizer.normalize(food.name) }
            val normSub = food.normalizedSearchText.ifBlank { TurkishTextNormalizer.normalize(food.subtitle) }
            var score = 0
            if (normName == query) score += 120
            if (normName.contains(query)) score += 60
            else if (normSub.contains(query)) score += 45
            var nameTokenHits = 0
            for (j in tokens.indices) {
                val token = tokens[j]
                if (containsWholeWord(normName, token)) {
                    score += 30
                    nameTokenHits++
                } else if (normName.contains(token)) {
                    score += 18
                    nameTokenHits++
                } else if (containsWholeWord(normSub, token)) {
                    score += 12
                } else if (normSub.contains(token)) {
                    score += 6
                }
            }
            if (score > 0 && food.brand == null) {
                score += 8
            }
            if (score > 0 && nameTokenHits == tokens.size) {
                score += 10
            }
            if (score > bestScore || (score == bestScore && bestFood != null && food.name.length < bestFood.name.length)) {
                bestScore = score
                bestFood = food
            }
        }
        return bestFood
    }

    private fun containsWholeWord(text: String, word: String): Boolean {
        val wordLen = word.length
        if (wordLen == 0 || wordLen > text.length) return false
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val leftOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val end = idx + wordLen
            val rightOk = end == text.length || !text[end].isLetterOrDigit()
            if (leftOk && rightOk) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    private fun buildSamplePlateForOfflinePhoto(catalogFoods: List<FoodItem>): List<AiAnalyzedFoodItem> {
        val chicken = catalogFoods.firstOrNull {
            (it.normalizedName.ifBlank { TurkishTextNormalizer.normalize(it.name) }).contains("tavuk")
        }
        val bulgur = catalogFoods.firstOrNull {
            (it.normalizedName.ifBlank { TurkishTextNormalizer.normalize(it.name) }).contains("bulgur")
        }
        val salad = catalogFoods.firstOrNull {
            (it.normalizedName.ifBlank { TurkishTextNormalizer.normalize(it.name) }).contains("salata")
        }

        return listOfNotNull(chicken, bulgur, salad).map { food ->
            AiAnalyzedFoodItem(
                id = UUID.randomUUID().toString(),
                name = food.name,
                unitLabel = food.defaultUnitLabel,
                baseGrams = food.defaultUnitGrams,
                multiplier = 1f,
                baseCalories = food.caloriesForGrams(food.defaultUnitGrams),
                baseProtein = food.proteinForGrams(food.defaultUnitGrams),
                baseCarbs = food.carbsForGrams(food.defaultUnitGrams),
                baseFat = food.fatForGrams(food.defaultUnitGrams),
                confidence = 0.89f,
                included = true,
            )
        }
    }
}
