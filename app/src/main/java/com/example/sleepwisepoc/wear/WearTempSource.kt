package com.example.sleepwisepoc.wear

import android.util.Log

/**
 * In-memory rolling buffer of skin-temperature samples streamed from the wear
 * companion (Samsung Health Sensor SDK, SKIN_TEMPERATURE_CONTINUOUS).
 *
 * Each sample is (timestampMs, tempCelsius). Per-epoch features match how the
 * combined model was trained (features.py extract_temp_features):
 *   temp_mean, temp_std, temp_trend (least-squares slope over the epoch).
 */
object WearTempSource {
    private const val TAG = "WearTempSource"
    private const val RETENTION_MS = 60L * 60 * 1000 // 1 hour

    data class TempSample(val timestamp: Long, val tempC: Float)

    private val lock = Any()
    private val samples = ArrayDeque<TempSample>()
    @Volatile var totalSamplesReceived: Long = 0L
        private set

    fun appendBatch(batch: List<Pair<Long, Float>>) {
        if (batch.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            batch.forEach { (ts, t) -> samples.addLast(TempSample(ts, t)) }
            val cutoff = nowMs - RETENTION_MS
            while (samples.isNotEmpty() && samples.first().timestamp < cutoff) samples.removeFirst()
            totalSamplesReceived += batch.size
        }
        Log.d(TAG, "appended batch size=${batch.size} bufferNow=${samples.size}")
    }

    /**
     * (temp_mean, temp_std, temp_trend) over [startMs, endMs), or null if too few
     * physiologically valid (30–40 °C) samples. temp_trend = OLS slope vs sample index.
     */
    fun tempFeaturesForWindow(startMs: Long, endMs: Long): Triple<Float, Float, Float>? {
        val vals: List<Float>
        synchronized(lock) {
            vals = samples.filter { it.timestamp in startMs until endMs && it.tempC in 30f..40f }
                .map { it.tempC }
        }
        if (vals.size < 5) return null
        val mean = vals.average()
        val std = kotlin.math.sqrt(vals.map { (it - mean) * (it - mean) }.average())
        // least-squares slope (polyfit deg 1) with x = 0..n-1
        val n = vals.size
        val xMean = (n - 1) / 2.0
        var num = 0.0; var den = 0.0
        vals.forEachIndexed { i, v -> num += (i - xMean) * (v - mean); den += (i - xMean) * (i - xMean) }
        val slope = if (den != 0.0) num / den else 0.0
        return Triple(mean.toFloat(), std.toFloat(), slope.toFloat())
    }

    fun bufferSize(): Int = synchronized(lock) { samples.size }
    fun lagMillis(): Long = synchronized(lock) {
        val latest = samples.lastOrNull()?.timestamp ?: return -1L
        System.currentTimeMillis() - latest
    }
}
