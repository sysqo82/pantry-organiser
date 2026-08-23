package com.pantry.organiser.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PantryDaoTest {

    private lateinit var database: PantryDatabase
    private lateinit var dao: PantryDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PantryDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.pantryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetItem() = runBlocking {
        val item = PantryItem(
            id = "pb1",
            name = "Milk",
            barcode = "123456789",
            shelfNumber = 4,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL
        )
        dao.insertItem(item)

        val allItems = dao.getAllItems().first()
        assertEquals(1, allItems.size)
        assertEquals("Milk", allItems[0].name)
    }

    @Test
    fun getItemByBarcode() = runBlocking {
        val item = PantryItem(
            id = "pb2",
            name = "Pasta",
            barcode = "987654321",
            shelfNumber = 1,
            zoneIndex = 1
        )
        dao.insertItem(item)

        val found = dao.getItemByBarcode("987654321")
        assertNotNull(found)
        assertEquals("Pasta", found?.name)
    }

    @Test
    fun updateItem() = runBlocking {
        val item = PantryItem(
            id = "pb3",
            name = "Rice",
            shelfNumber = 2,
            zoneIndex = 1,
            sealedCount = 1
        )
        dao.insertItem(item)
        val inserted = dao.getAllItems().first()[0]

        val updated = inserted.copy(sealedCount = 5)
        dao.updateItem(updated)

        val result = dao.getAllItems().first()[0]
        assertEquals(5, result.sealedCount)
    }

    @Test
    fun deleteItem() = runBlocking {
        val item = PantryItem(
            id = "pb4",
            name = "Delete Me",
            shelfNumber = 1,
            zoneIndex = 1
        )
        dao.insertItem(item)
        val inserted = dao.getAllItems().first()[0]
        
        dao.deleteItem(inserted)
        
        val allItems = dao.getAllItems().first()
        assertEquals(0, allItems.size)
    }
}
