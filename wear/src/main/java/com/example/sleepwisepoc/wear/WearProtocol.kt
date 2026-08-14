package com.example.sleepwisepoc.wear

/**
 * Wearable Data Layer message paths + payload format shared between watch and phone.
 *
 * Payloads are UTF-8 strings of comma-separated long+float pairs:
 *     "<timestampMs>,<bpm>;<timestampMs>,<bpm>;..."
 *
 * One message per batch (every ~5s during a session). Keeping it tiny so it
 * fits in a single BT frame and parses cheaply on the phone side.
 */
object WearProtocol {
    const val PATH_CMD_START = "/sleepwise/cmd/start"
    const val PATH_CMD_STOP = "/sleepwise/cmd/stop"
    const val PATH_HR_BATCH = "/sleepwise/hr"
    // Accelerometer magnitude samples (m/s² with gravity removed). Same
    // "ts,value;ts,value;..." encoding as HR — phone-side decoders reuse decodeBatch.
    const val PATH_ACCEL_BATCH = "/sleepwise/accel"
    // Stage 2 (Samsung Health Sensor SDK): live skin temperature (°C) and
    // inter-beat intervals (ms). Same "ts,value;..." encoding — reuse decodeBatch.
    const val PATH_TEMP_BATCH = "/sleepwise/temp"
    const val PATH_IBI_BATCH = "/sleepwise/ibi"

    fun encodeBatch(samples: List<Pair<Long, Float>>): ByteArray =
        samples.joinToString(";") { "${it.first},${it.second}" }.toByteArray(Charsets.UTF_8)

    fun decodeBatch(bytes: ByteArray): List<Pair<Long, Float>> {
        val s = bytes.toString(Charsets.UTF_8).trim()
        if (s.isEmpty()) return emptyList()
        return s.split(";").mapNotNull { piece ->
            val parts = piece.split(",")
            if (parts.size != 2) return@mapNotNull null
            val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
            val value = parts[1].toFloatOrNull() ?: return@mapNotNull null
            ts to value
        }
    }
}
