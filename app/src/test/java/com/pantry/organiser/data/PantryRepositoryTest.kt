package com.pantry.organiser.data

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class PantryRepositoryTest {

    private val pantryDao = mockk<PantryDao>(relaxed = true)
    private val pocketBaseApi = mockk<PocketBaseApi>(relaxed = true)
    
    private fun createRepository(testScope: TestScope) = PantryRepository(
        pantryDao = pantryDao,
        pocketBaseApi = pocketBaseApi,
        scope = testScope
    )

    @Test
    fun `deleteItem with local ID only deletes locally`() = runTest {
        val repository = createRepository(this)
        val item = PantryItem(id = "local_123", name = "Test", shelfNumber = 1, zoneIndex = 1)
        
        repository.deleteItem(item)
        
        coVerify { pantryDao.deleteItem(item) }
        coVerify(exactly = 0) { pocketBaseApi.deleteItem(any()) }
    }

    @Test
    fun `updateItem with local ID only updates locally`() = runTest {
        val repository = createRepository(this)
        val item = PantryItem(id = "local_123", name = "Test", shelfNumber = 1, zoneIndex = 1)
        
        repository.updateItem(item)
        
        coVerify { pantryDao.updateItem(item) }
        coVerify(exactly = 0) { pocketBaseApi.updateItem(any(), any()) }
    }

    @Test
    fun `addItem swaps local ID for remote ID when creation succeeds`() = runTest {
        val repository = createRepository(this)
        val item = PantryItem(id = "", name = "New Item", shelfNumber = 1, zoneIndex = 1)
        val remoteItem = PocketBasePantryItem(id = "remote_abc", name = "New Item")
        
        coEvery { pocketBaseApi.createItem(any()) } returns remoteItem
        // Mock getItemById to return the item (it exists)
        coEvery { pantryDao.getItemById(any()) } returns item

        repository.addItem(item)
        
        // Wait for the async remote call to finish
        advanceUntilIdle()

        coVerify { 
            pantryDao.insertItem(match { it.id.startsWith("local_") })
            pantryDao.deleteItem(match { it.id.startsWith("local_") })
            pantryDao.insertItem(match { it.id == "remote_abc" })
        }
    }

    @Test
    fun `addItem does not insert remote item if local item was deleted before remote finished`() = runTest {
        val repository = createRepository(this)
        val item = PantryItem(id = "", name = "New Item", shelfNumber = 1, zoneIndex = 1)
        val remoteItem = PocketBasePantryItem(id = "remote_abc", name = "New Item")
        
        coEvery { pocketBaseApi.createItem(any()) } returns remoteItem
        // Mock getItemById to return null (item was deleted)
        coEvery { pantryDao.getItemById(any()) } returns null

        repository.addItem(item)
        
        advanceUntilIdle()

        coVerify { pantryDao.insertItem(match { it.id.startsWith("local_") }) }
        coVerify(exactly = 0) { pantryDao.insertItem(match { it.id == "remote_abc" }) }
    }

    @Test
    fun `toPocketBase excludes IDs from JSON payload`() {
        val localItem = PantryItem(id = "local_123", name = "Test", shelfNumber = 1, zoneIndex = 1)
        val remoteItem = PantryItem(id = "remote_123", name = "Test", shelfNumber = 1, zoneIndex = 1)
        
        assert(localItem.toPocketBase().id == null)
        assert(remoteItem.toPocketBase().id == null)
    }

    @Test
    fun `toLocal handles invalid enum values gracefully`() {
        val invalidPbItem = PocketBasePantryItem(
            id = "123",
            name = "Test",
            activeFill = "INVALID"
        )
        
        val localItem = invalidPbItem.toLocal()
        
        assert(localItem.activeFill == FillLevel.FULL)
    }
}
