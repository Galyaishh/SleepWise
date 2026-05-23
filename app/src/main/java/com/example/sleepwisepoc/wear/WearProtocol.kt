package com.example.sleepwisepoc.wear

/**
 * Mirror of the wear-module `WearProtocol` — kept in this file so the phone APK
 * doesn't have to depend on the wear module's classes. Paths + payload format
 * must stay in sync with `:wear`'s copy.
 */
object WearProtocol {
    const val PATH_CMD_START = "/sleepwise/cmd/start"
    const val PATH_CMD_STOP = "/sleepwise/cmd/stop"
    const val PATH_HR_BATCH = "/sleepwise/hr"

    fun encodeBatch(samples: List<Pair<Long, Float>>): ByteArray =
        samples.joinToString(";") { "${it.first},${it.second}" }.toByteArray(Charsets.UTF_8)

    fun decodeBatch(bytes: ByteArray): List<Pair<Long, Float>> {
        val s = bytes.toString(Charsets.UTF_8).trim()
        if (s.isEmpty()) return emptyList()
        return s.split(";").mapNotNull { piece ->
            val parts = piece.split(",")
            if (parts.size != 2) return@mapNotNull null
            val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bpm = parts[1].toFloatOrNull() ?: return@mapNotNull null
            ts to bpm
        }
    }
}
