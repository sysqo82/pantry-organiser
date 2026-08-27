package com.pantry.organiser.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
}

class OpenFoodFactsRepository {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(DefaultRequest) {
            header(HttpHeaders.UserAgent, "VisualPantry/1.1 (Android; support@visualpantry.organiser.com)")
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
        }
    }

    suspend fun getProduct(barcode: String): OffProduct? = coroutineScope {
        val trimmed = barcode.trim()
        val codes = mutableSetOf(trimmed)
        
        // UPC-A / EAN-13 Normalization
        if (trimmed.length == 12) {
            codes.add("0$trimmed")
        } else if (trimmed.length == 13 && trimmed.startsWith("0")) {
            codes.add(trimmed.substring(1))
        }
        
        android.util.Log.d("OFF", "Starting parallel fetch for codes: $codes")
        
        val deferreds = codes.map { code ->
            async {
                val url = "https://world.openfoodfacts.org/api/v2/product/$code.json"
                try {
                    val response: OffResponse = client.get(url) {
                        parameter("fields", "product_name,product_name_en,generic_name,brands,brand_owner,quantity,image_front_small_url,categories_tags")
                    }.body()
                    
                    if (response.status == 1 && response.product != null) {
                        android.util.Log.d("OFF", "Success for: $code")
                        response.product 
                    } else {
                        android.util.Log.d("OFF", "Not found: $code (Status: ${response.status})")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OFF", "Network error for $code: ${e.message}")
                    null
                }
            }
        }
        
        deferreds.awaitAll().firstOrNull { it != null }
    }
}
