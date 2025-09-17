package pl.poznan.put.boatcontroller.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDataDao {
    @Insert
    suspend fun insert(recipes: UserData)

    @Query("SELECT * FROM userData WHERE uid = 1")
    fun get(): Flow<UserData>

    @Query("DELETE FROM userData")
    suspend fun clear()

    @Query("UPDATE userData SET login = :login, password = :password WHERE uid = 1")
    suspend fun edit(login: String, password: String)

    @Query("UPDATE userData SET ipAddress = :ipAddress, port = :port WHERE uid = 1")
    suspend fun editServer(ipAddress: String, port: String)

    @Query("UPDATE userData SET isRemembered = :isRemembered WHERE uid = 1")
    suspend fun editRemember(isRemembered: Boolean)

    @Query("SELECT COUNT(*) FROM userData WHERE uid = 1")
    suspend fun getCount(): Int
}