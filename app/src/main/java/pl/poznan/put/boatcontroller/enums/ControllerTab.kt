package pl.poznan.put.boatcontroller.enums

/**
 * Enum reprezentujący dostępne taby w ControllerActivity.
 */
enum class ControllerTab {
    MAP,
    SONAR,
    SENSORS,
    CAMERA,
    NONE; // Dla stanu gdy aplikacja jest zminimalizowana
    
    /**
     * Zwraca sformatowaną nazwę taba - pierwsza litera wielka, reszta mała.
     * Przykład: MAP -> "Map", SONAR -> "Sonar"
     */
    val displayName: String
        get() = when (this) {
            MAP -> "Map"
            SONAR -> "Sonar"
            SENSORS -> "Sensors"
            CAMERA -> "Camera"
            NONE -> "None"
        }
}

