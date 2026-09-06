package com.pantry.organiser.dashboard.data

import android.util.Log
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.network.OffProduct
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class OpenFoodFactsProberTest {

    private val pantryRepository = mockk<PantryRepository>(relaxed = true)
    private val openFoodFactsRepository = mockk<OpenFoodFactsRepository>(relaxed = true)
    private val prober = OpenFoodFactsProber(pantryRepository, openFoodFactsRepository)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `Rule 1 - Unknown product without identifiable entries updates everything from Open Food Facts`() {
        val unknownItem = PantryItem(
            id = "item_1",
            name = "Unknown Product",
            barcode = "5000123456789",
            brand = null,
            packageQuantity = null,
            imageUrl = null,
            apiImageUrl = null,
            shelfNumber = 1,
            zoneIndex = 1
        )

        val offProduct = OffProduct(
            productName = "Baked Beans in Tomato Sauce",
            brands = "Heinz",
            weight = "415g",
            imageUrl = "https://images.openfoodfacts.org/beans.jpg"
        )

        val merged = OpenFoodFactsProber.probeAndMergeItem(unknownItem, offProduct)

        assertNotNull(merged)
        assertEquals("Baked Beans in Tomato Sauce", merged!!.name)
        assertEquals("Heinz", merged.brand)
        assertEquals("415g", merged.packageQuantity)
        assertEquals("https://images.openfoodfacts.org/beans.jpg", merged.apiImageUrl)
        assertEquals("https://images.openfoodfacts.org/beans.jpg", merged.imageUrl)
    }

    @Test
    fun `Rule 2 - Existing product with OFF entries updates only fields with new values`() {
        val existingItem = PantryItem(
            id = "item_2",
            name = "Baked Beans",
            barcode = "5000123456789",
            brand = "Heinz",
            packageQuantity = "400g",
            imageUrl = "https://images.openfoodfacts.org/old_beans.jpg",
            apiImageUrl = "https://images.openfoodfacts.org/old_beans.jpg",
            shelfNumber = 2,
            zoneIndex = 1
        )

        val offProduct = OffProduct(
            productName = "Baked Beans",
            brands = "Heinz",
            weight = "415g", // Updated quantity from OFF
            imageUrl = "https://images.openfoodfacts.org/new_beans.jpg" // Updated image URL from OFF
        )

        val merged = OpenFoodFactsProber.probeAndMergeItem(existingItem, offProduct)

        assertNotNull(merged)
        assertEquals("Baked Beans", merged!!.name) // Name kept
        assertEquals("Heinz", merged.brand) // Brand kept
        assertEquals("415g", merged.packageQuantity) // Updated from 400g to 415g
        assertEquals("https://images.openfoodfacts.org/new_beans.jpg", merged.apiImageUrl)
        assertEquals("https://images.openfoodfacts.org/new_beans.jpg", merged.imageUrl)
    }

    @Test
    fun `Rule 3 - Local image is preserved when API image URL is updated`() {
        val localImageItem = PantryItem(
            id = "item_3",
            name = "Organic Olive Oil",
            barcode = "5000987654321",
            brand = "Filippo Berio",
            packageQuantity = "500ml",
            imageUrl = "https://images.openfoodfacts.org/old.jpg",
            apiImageUrl = "https://images.openfoodfacts.org/old.jpg",
            localImageUrl = "file:///storage/emulated/0/pantry/oil_local.jpg",
            localImageUri = "content://media/external/images/media/123",
            shelfNumber = 3,
            zoneIndex = 2
        )

        val offProduct = OffProduct(
            productName = "Organic Olive Oil",
            brands = "Filippo Berio",
            weight = "500ml",
            imageUrl = "https://images.openfoodfacts.org/new_api_image.jpg"
        )

        val merged = OpenFoodFactsProber.probeAndMergeItem(localImageItem, offProduct)

        assertNotNull(merged)
        assertEquals("https://images.openfoodfacts.org/new_api_image.jpg", merged!!.apiImageUrl)
        // Local image URI/URL must be completely preserved!
        assertEquals("file:///storage/emulated/0/pantry/oil_local.jpg", merged.localImageUrl)
        assertEquals("content://media/external/images/media/123", merged.localImageUri)
        // imageUrl is kept as the old/local reference, preserving active image source priority
        assertEquals("https://images.openfoodfacts.org/old.jpg", merged.imageUrl)
        assertEquals("content://media/external/images/media/123", merged.activeImageSource)
    }

    @Test
    fun `probeAndSync queries OFF for barcoded items and updates modified ones`() = runTest {
        val item1 = PantryItem(id = "1", name = "Unknown Product", barcode = "11111", shelfNumber = 1, zoneIndex = 1)
        val item2 = PantryItem(id = "2", name = "Salt", barcode = "22222", brand = "Tesco", shelfNumber = 1, zoneIndex = 2)

        coEvery { pantryRepository.allItems } returns flowOf(listOf(item1, item2))

        val offProduct1 = OffProduct(productName = "Tomato Soup", brands = "Heinz", weight = "400g")
        val offProduct2 = OffProduct(productName = "Salt", brands = "Tesco") // No changes

        coEvery { openFoodFactsRepository.getProduct("11111") } returns offProduct1
        coEvery { openFoodFactsRepository.getProduct("22222") } returns offProduct2

        val updatedCount = prober.probeAndSync()

        assertEquals(1, updatedCount)
        coVerify(exactly = 1) { pantryRepository.updateItem(any()) }
    }
}
