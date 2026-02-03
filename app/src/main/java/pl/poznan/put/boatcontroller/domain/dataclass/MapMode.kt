package pl.poznan.put.boatcontroller.domain.dataclass

sealed class MapMode {
    sealed class Waypoint : MapMode() {
        data object Add : Waypoint()
        data object Delete : Waypoint()
        data object Move : Waypoint()
    }

    sealed class Ship : MapMode() {
        data object DefaultMove : Ship()
        data object ReverseMove : Ship()
    }

    data object None : MapMode()
}