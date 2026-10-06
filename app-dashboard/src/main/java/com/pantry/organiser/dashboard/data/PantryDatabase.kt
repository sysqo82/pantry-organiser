package com.pantry.organiser.dashboard.data

import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.model.PantryTypeConverters
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PantryItem::class, SyncQueueItem::class, PastItem::class], version = 7, exportSchema = false)
@TypeConverters(PantryTypeConverters::class)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun pastItemDao(): PastItemDao
    companion object {
        @Volatile private var INSTANCE: PantryDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE pantry_items SET shelf_number = 5 - shelf_number WHERE shelf_number BETWEEN 1 AND 4")
                db.execSQL("UPDATE past_items SET shelf_number = 5 - shelf_number WHERE shelf_number BETWEEN 1 AND 4")
            }
        }

        fun getDatabase(context: Context): PantryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PantryDatabase::class.java,
                    "pantry_dashboard.db"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
