package pl.poznan.put.boatcontroller.domain.models

data class RunningCreateRequest(
    val missionId: Int,
    val stats: String
)