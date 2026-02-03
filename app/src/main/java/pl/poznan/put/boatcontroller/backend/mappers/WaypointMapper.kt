package pl.poznan.put.boatcontroller.backend.mappers

import pl.poznan.put.boatcontroller.domain.dataclass.POIObject
import pl.poznan.put.boatcontroller.backend.dto.PointOfInterestDto
import pl.poznan.put.boatcontroller.backend.dto.WaypointDto
import pl.poznan.put.boatcontroller.domain.dataclass.WaypointObject

fun PointOfInterestDto.toDomain(): POIObject {
    return POIObject(
        id = id,
        missionId = missionId,
        lon = lon.toDoubleOrNull() ?: 0.0,
        lat = lat.toDoubleOrNull() ?: 0.0,
        name = name,
        description = description,
        pictures = pictures
    )
}

fun WaypointDto.toDomain(): WaypointObject {
    return WaypointObject(
        no = no,
        lon = lon.toDoubleOrNull() ?: 0.0,
        lat = lat.toDoubleOrNull() ?: 0.0,
        isCompleted = false
    )
}