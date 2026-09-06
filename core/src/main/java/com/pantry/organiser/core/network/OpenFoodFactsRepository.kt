package com.pantry.organiser.core.network

import android.util.Log
import com.pantry.organiser.core.model.PantryConstants
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OffResponse(
    @SerialName("product") val product: OffProduct? = null,
    @SerialName("status") val status: Int? = null
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    @SerialName("generic_name") val genericName: String? = null,
    @SerialName("brands") val brands: String? = null,
    @SerialName("brand_owner") val brandOwner: String? = null,
    @SerialName("quantity") val weight: String? = null,
    @SerialName("image_front_small_url") val imageUrl: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null
) {
    val displayProductName: String? get() = productName ?: productNameEn ?: genericName
    val displayBrands: String? get() = brands ?: brandOwner

    /**
     * Heuristic to extract bundle size (e.g. "4 x 100g", "3x80g", "4x 200ml", "4 Pack", "Pack of 6")
     */
    fun inferUnitsPerPack(): Int = PantryConstants.inferUnitsPerPack(displayProductName, weight)
}

class OpenFoodFactsRepository(private val client: HttpClient) {

    suspend fun getProduct(barcode: String): OffProduct? {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return null

        val codes = mutableListOf(trimmed)

        // UPC-A / EAN-13 Normalization
        if (trimmed.length == 12) {
            codes.add("0$trimmed")
        } else if (trimmed.length == 13 && trimmed.startsWith("0")) {
            codes.add(trimmed.substring(1))
        }

        for (code in codes) {
            val product = fetchProductWithRetry(code)
            if (product != null) return product
        }
        return null
    }

    private suspend fun fetchProductWithRetry(code: String, maxRetries: Int = 3): OffProduct? {
        var currentDelay = 1000L
        for (attempt in 0..maxRetries) {
            val url = "https://world.openfoodfacts.org/api/v2/product/$code.json"
            try {
                val httpResponse = client.get(url) {
                    header(HttpHeaders.UserAgent, "VisualPantry/1.1 (Android; support@visualpantry.organiser.com)")
                    parameter("fields", "product_name,product_name_en,generic_name,brands,brand_owner,quantity,image_front_small_url,categories_tags")
                }

                if (httpResponse.status == HttpStatusCode.TooManyRequests) {
                    Log.w("OFF", "Rate limited (429) for $code. Attempt ${attempt + 1}, backing off for ${currentDelay}ms")
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                }

                if (httpResponse.status == HttpStatusCode.OK) {
                    val response: OffResponse = httpResponse.body()
                    if (response.status == 1 && response.product != null) {
                        Log.d("OFF", "Success for: $code")
                        return response.product
                    } else {
                        Log.d("OFF", "Not found: $code (Status: ${response.status})")
                        return null
                    }
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.TooManyRequests) {
                    Log.w("OFF", "ClientRequestException 429 for $code. Attempt ${attempt + 1}, backing off for ${currentDelay}ms")
                    delay(currentDelay)
                    currentDelay *= 2
                    continue
                } else {
                    Log.e("OFF", "HTTP error for $code: ${e.message}")
                    return null
                }
            } catch (e: Exception) {
                Log.e("OFF", "Network error for $code: ${e.message}")
                return null
            }
        }
        return null
    }
}
