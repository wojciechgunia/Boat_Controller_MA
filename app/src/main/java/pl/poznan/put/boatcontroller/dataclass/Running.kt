package pl.poznan.put.boatcontroller.dataclass

data class RunningDto(
    val id: Int,
    val missionId: Int,
    val date: String,
    val stats: String
)

data class RunningCreateRequest(
    val missionId: Int,
    val stats: String
)

