package pl.poznan.put.boatcontroller

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pl.poznan.put.boatcontroller.data.LocalDatabase
import pl.poznan.put.boatcontroller.data.UserData
import pl.poznan.put.boatcontroller.data.UserDataDao

class Repository(context: Context): UserDataDao {
    private val dao = LocalDatabase.Companion.getInstance(context).userDataDao()
    override suspend fun insert(recipe: UserData) = withContext(Dispatchers.IO) {
        dao.insert(recipe)
    }

    override fun get(): Flow<UserData> {
        return dao.get()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    override suspend fun edit(
        login: String,
        password: String,
        ipAddress: String,
        port: String
    ) = withContext(Dispatchers.IO) {
        dao.edit(login, password, ipAddress, port)
    }

    override suspend fun editRemember(isRemembered: Boolean) = withContext(Dispatchers.IO) {
        dao.editRemember(isRemembered)
    }

    override suspend fun getCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getCount()
        }
    }

}