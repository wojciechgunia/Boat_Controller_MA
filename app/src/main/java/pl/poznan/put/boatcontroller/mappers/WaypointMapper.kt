package pl.poznan.put.boatcontroller.mappers

import pl.poznan.put.boatcontroller.dataclass.POIObject
import pl.poznan.put.boatcontroller.dataclass.PointOfInterestDto

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