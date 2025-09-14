package pl.poznan.put.boatcontroller.mappers

import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.PointOfInterestDto
import pl.poznan.put.boatcontroller.dataclass.WaypointDto
import pl.poznan.put.boatcontroller.dataclass.WaypointObject

fun PointOfInterestDto.toDomain(): POIObject {
    return POIObject(
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