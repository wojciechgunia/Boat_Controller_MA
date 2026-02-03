package pl.poznan.put.boatcontroller.backend.dto

data class RunningDto(
    val id: Int,
    val missionId: Int,
    val date: String,
    val stats: String
)