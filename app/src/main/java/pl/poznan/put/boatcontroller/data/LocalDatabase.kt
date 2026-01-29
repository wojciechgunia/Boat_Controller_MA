package pl.poznan.put.boatcontroller.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserData::class], version = 3, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao

    companion object {
        @Volatile private var db: LocalDatabase? = null
        
        // Migracja z wersji 2 do 3 - dodanie nowych kolumn dla misji i statku
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE userData ADD COLUMN selectedMissionId INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE userData ADD COLUMN selectedMissionName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE userData ADD COLUMN selectedShipName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE userData ADD COLUMN selectedShipRole TEXT NOT NULL DEFAULT ''")
            }
        }
        
        fun getInstance(context: Context): LocalDatabase =
            db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalDatabase::class.java,
                    "user_db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration() // Tymczasowo - usuwa starą bazę jeśli migracja się nie powiedzie
                .build().also { db = it }
            }
    }
}