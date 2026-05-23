package com.example.sleepwisepoc.wear

import android.util.Log
import com.example.sleepwisepoc.SamsungHealthManager

/**
 * In-memory rolling buffer of HR samples streamed live from the wear companion.
 *
 * Lifetime is process-wide: `WearMessageListener` calls [appendBatch] every
 * time a batch lands, the sleep-tracking service calls [recentHr] each tick
 * to ask "give me HR samples in the last X minutes". When the wear stream is
 * active and the watch is on, this buffer is the *primary* HR data source —
 * not Samsung Health.
 *
 * Buffer caps at ~1 hour of 1Hz data (3600 samples). Older samples drop off
 * to keep memory bounded — the model only ever asks for the last 1h anyway.
 */
object WearHrSource {
    private const val TAG = "WearHrSource"
    private const val RETENTION_MS = 60L * 60 * 1000 // 1 hour

    private val lock = Any()
    private val samples = ArrayDeque<SamsungHealthManager.HeartRateSample>()
    @Volatile var lastBatchReceivedAt: Long = 0L
        private set
    @Volatile var totalSamplesReceived: Long = 0L
        private set

    fun appendBatch(batch: List<Pair<Long, Float>>) {
        if (batch.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            batch.forEach { (ts, bpm) ->
                samples.addLast(SamsungHealthManager.HeartRateSample(bpm.toInt(), ts))
            }
            // Drop anything older than the retention window.
            val cutoff = nowMs - RETENTION_MS
            while (samples.isNotEmpty() && samples.first().timestamp < cutoff) {
                samples.removeFirst()
            }
            lastBatchReceivedAt = nowMs
            totalSamplesReceived += batch.size
        }
        Log.d(TAG, "appended batch size=${batch.size} bufferNow=${samples.size}")
    }

    /** Snapshot of samples in the last [minutesBack] minutes (defaults to 1h). */
    fun recentHr(minutesBack: Int = 60): List<SamsungHealthManager.HeartRateSample> {
        val cutoff = System.currentTimeMillis() - minutesBack * 60_000L
        synchronized(lock) {
            return samples.filter { it.timestamp >= cutoff }
        }
    }

    /** How fresh is the most recent sample we have? Negative if none. */
    fun lagMillis(): Long {
        synchronized(lock) {
            val latest = samples.lastOrNull()?.timestamp ?: return -1L
            return System.currentTimeMillis() - latest
        }
    }

    fun bufferSize(): Int = synchronized(lock) { samples.size }
}
