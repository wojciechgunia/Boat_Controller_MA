package pl.poznan.put.boatcontroller.backend.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "userData")
data class UserData(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    val login: String,
    val password: String,
    val ipAddress: String,
    val port: String,
    val isRemembered: Boolean = false,
    val selectedMissionId: Int = -1,
    val selectedMissionName: String = "",
    val selectedShipName: String = "",
    val selectedShipRole: String = "")