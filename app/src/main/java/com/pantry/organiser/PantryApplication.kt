package com.pantry.organiser

import android.app.Application
import com.pantry.organiser.data.PantryDatabase
import com.pantry.organiser.data.PantryRepository

class PantryApplication : Application() {
    private val database by lazy { PantryDatabase.getDatabase(this) }
    val repository by lazy { 
        PantryRepository(
            pantryDao = database.pantryDao(),
            filesDir = filesDir
        ) 
    }
}
