package com.example.sleepwisepoc.wear

import android.util.Log

/**
 * In-memory rolling buffer of inter-beat-interval (IBI) samples streamed from
 * the wear companion (Samsung Health Sensor SDK, HEART_RATE_CONTINUOUS → IBI).
 *
 * Each sample is (timestampMs, ibiMs). Per-epoch time-domain HRV matches the
 * combined-model training (extract_dreamt_hrv.hrv_for_epoch):
 *   hrv_rmssd, hrv_sdnn, hrv_pnn50, hrv_ibi_mean. (No LF/HF — the deployed
 *   model uses time-domain HRV only, so no on-device FFT is needed.)
 */
object WearHrvSource {
    private const val TAG = "WearHrvSource"
    private const val RETENTION_MS = 60L * 60 * 1000 // 1 hour

    data class IbiSample(val timestamp: Long, val ibiMs: Float)

    private val lock = Any()
    private val samples = ArrayDeque<IbiSample>()
    @Volatile var totalSamplesReceived: Long = 0L
        private set

    fun appendBatch(batch: List<Pair<Long, Float>>) {
        if (batch.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            batch.forEach { (ts, ibi) -> samples.addLast(IbiSample(ts, ibi)) }
            val cutoff = nowMs - RETENTION_MS
            while (samples.isNotEmpty() && samples.first().timestamp < cutoff) samples.removeFirst()
            totalSamplesReceived += batch.size
        }
        Log.d(TAG, "appended batch size=${batch.size} bufferNow=${samples.size}")
    }

    /** (rmssd, sdnn, pnn50, ibiMean) over [startMs, endMs) — RR gated 300–2000 ms;
     *  null if fewer than 5 valid beats (matches the training gate). */
    fun hrvFeaturesForWindow(startMs: Long, endMs: Long): FloatArray? {
        val rr: List<Float>
        synchronized(lock) {
            rr = samples.filter { it.timestamp in startMs until endMs && it.ibiMs in 300f..2000f }
                .map { it.ibiMs }
        }
        if (rr.size < 5) return null
        val diffs = rr.zipWithNext { a, b -> b - a }
        val rmssd = kotlin.math.sqrt(diffs.map { (it * it).toDouble() }.average())
        val mean = rr.average()
        val sdnn = kotlin.math.sqrt(rr.map { (it - mean) * (it - mean) }.average())
        val pnn50 = diffs.count { kotlin.math.abs(it) > 50f }.toFloat() / diffs.size * 100f
        return floatArrayOf(rmssd.toFloat(), sdnn.toFloat(), pnn50, mean.toFloat())
    }

    fun bufferSize(): Int = synchronized(lock) { samples.size }
    fun lagMillis(): Long = synchronized(lock) {
        val latest = samples.lastOrNull()?.timestamp ?: return -1L
        System.currentTimeMillis() - latest
    }
}
