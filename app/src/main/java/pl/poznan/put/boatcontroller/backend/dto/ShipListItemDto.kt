package pl.poznan.put.boatcontroller.backend.dto

data class ShipListItemDto(
    val id: Int,
    val name: String,
    val connections: Int,
    val captain: String,
    val mission: String,
    )