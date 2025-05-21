package pl.poznan.put.boatcontroller.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserData::class], version = 1)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao

    companion object {
        @Volatile private var db: LocalDatabase? = null
        fun getInstance(context: Context): LocalDatabase =
            db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalDatabase::class.java,
                    "user_db"
                ).build().also { db = it }
            }
    }
}