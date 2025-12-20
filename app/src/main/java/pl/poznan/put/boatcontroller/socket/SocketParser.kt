package pl.poznan.put.boatcontroller.socket

object SocketParser {
    fun parse(msg: String): SocketEvent? {
        return try {
            when {
                msg.startsWith("BI:") -> parseBI(msg)
                msg.startsWith("BIC:") -> parseBIC(msg)
                msg.startsWith("PA:") -> parsePA(msg)
                msg.startsWith("SI:") -> parseSI(msg)
                msg.startsWith("WI:") -> parseWI(msg)
                msg.startsWith("LI:") -> parseLI(msg)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun validFrame(msg: String, code: String): Boolean {
        return msg.startsWith("$code:") && msg.endsWith(":$code")
    }

    private fun parseBI(msg: String): SocketEvent? {
        if (!validFrame(msg, "BI")) return null
        val p = msg.split(":")
        if (p.size < 5) return null

        return SocketEvent.BoatInformation(p[1], p[2], p[3])
    }

    private fun parseBIC(msg: String): SocketEvent? {
        if (!validFrame(msg, "BIC")) return null
        val p = msg.split(":")
        if (p.size < 5) return null

        return SocketEvent.BoatInformationChange(p[1], p[2], p[3])
    }

    private fun parsePA(msg: String): SocketEvent? {
        if (!validFrame(msg, "PA")) return null
        val p = msg.split(":")
        if (p.size < 6) return null

        return SocketEvent.PositionActualisation(
            lon = p[1].toDoubleOrNull() ?: 0.0,
            lat = p[2].toDoubleOrNull() ?: 0.0,
            speed = p[3].toDoubleOrNull() ?: 0.0,
            sNum = p[4].toIntOrNull() ?: 0
        )
    }

    private fun parseSI(msg: String): SocketEvent? {
        if (!validFrame(msg, "SI")) return null
        val p = msg.split(":")
        if (p.size < 4) return null

        // p[1] zawiera dane z czujników oddzielone przecinkami:
        // accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,angle_x,angle_y,angle_z
        val magData = p[1].split(",")
        
        // Parsuj wszystkie wartości (powinno być 12 wartości)
        if (magData.size < 12) {
            // Jeśli brakuje danych, wypełnij zerami
            val filledData = magData.toMutableList()
            while (filledData.size < 12) {
                filledData.add("0.0")
            }
            return parseSensorData(filledData, p[2])
        }
        
        return parseSensorData(magData, p[2])
    }
    
    private fun parseSensorData(magData: List<String>, depthStr: String): SocketEvent.SensorInformation {
        // Akcelerometr (g) - indeksy 0, 1, 2
        val accelX = magData[0].toDoubleOrNull() ?: 0.0
        val accelY = magData[1].toDoubleOrNull() ?: 0.0
        val accelZ = magData[2].toDoubleOrNull() ?: 0.0
        
        // Żyroskop (deg/s) - indeksy 3, 4, 5
        val gyroX = magData[3].toDoubleOrNull() ?: 0.0
        val gyroY = magData[4].toDoubleOrNull() ?: 0.0
        val gyroZ = magData[5].toDoubleOrNull() ?: 0.0
        
        // Magnetometr (µT) - indeksy 6, 7, 8
        val magX = magData[6].toDoubleOrNull() ?: 0.0
        val magY = magData[7].toDoubleOrNull() ?: 0.0
        val magZ = magData[8].toDoubleOrNull() ?: 0.0
        
        // Kąty (deg) - indeksy 9, 10, 11
        val angleX = magData[9].toDoubleOrNull() ?: 0.0
        val angleY = magData[10].toDoubleOrNull() ?: 0.0
        val angleZ = magData[11].toDoubleOrNull() ?: 0.0
        
        // Głębokość (m) - może być "todo" lub liczba
        val depth = when {
            depthStr.equals("todo", ignoreCase = true) -> 0.0
            else -> depthStr.toDoubleOrNull() ?: 0.0
        }
        
        return SocketEvent.SensorInformation(
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            magX = magX,
            magY = magY,
            magZ = magZ,
            angleX = angleX,
            angleY = angleY,
            angleZ = angleZ,
            depth = depth
        )
    }

    private fun parseWI(msg: String): SocketEvent? {
        if (!validFrame(msg, "WI")) return null
        val p = msg.split(":")
        if (p.size < 3) return null

        return SocketEvent.WarningInformation(infoCode = p[1])
    }

    private fun parseLI(msg: String): SocketEvent? {
        if (!validFrame(msg, "LI")) return null
        val p = msg.split(":")
        if (p.size < 3) return null

        return SocketEvent.LostInformation(
            sNum = p[1].toInt()
        )
    }
}
