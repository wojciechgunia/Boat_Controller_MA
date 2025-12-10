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

        return SocketEvent.SensorInformation(
            magnetic = p[1].toDouble(),
            depth = p[2].toDouble()
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
