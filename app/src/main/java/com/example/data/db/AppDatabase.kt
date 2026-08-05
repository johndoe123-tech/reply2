package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Contact::class,
        ContactMemory::class,
        BehaviorProfile::class,
        Message::class,
        PersonalMemory::class,
        KnownRelation::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun contactMemoryDao(): ContactMemoryDao
    abstract fun behaviorProfileDao(): BehaviorProfileDao
    abstract fun messageDao(): MessageDao
    abstract fun personalMemoryDao(): PersonalMemoryDao
    abstract fun knownRelationDao(): KnownRelationDao

    suspend fun clearUserData() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            clearAllTables()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN doNotRespond INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN gender TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autoreply_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
