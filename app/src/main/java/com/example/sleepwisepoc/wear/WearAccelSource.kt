package com.example.sleepwisepoc.wear

import android.util.Log

/**
 * In-memory rolling buffer of accelerometer-magnitude samples streamed live
 * from the wear companion.
 *
 * Each sample is (timestampMs, magnitudeMS²-with-gravity-removed). The watch
 * does the gravity subtraction; we just track the absolute magnitude.
 *
 * "Movement event count per epoch" is the model-relevant aggregate. For now
 * we expose raw samples and the per-window event count helper; the smart-wake
 * gate that uses it is intentionally NOT wired yet — observability first.
 */
object WearAccelSource {
    private const val TAG = "WearAccelSource"
    private const val RETENTION_MS = 60L * 60 * 1000 // 1 hour
    /** Magnitude (m/s², gravity removed) above which we count a "movement event". */
    const val MOVEMENT_THRESHOLD_M_S2 = 0.5f

    data class AccelSample(val timestamp: Long, val magnitude: Float)

    private val lock = Any()
    private val samples = ArrayDeque<AccelSample>()
    @Volatile var totalSamplesReceived: Long = 0L
        private set

    fun appendBatch(batch: List<Pair<Long, Float>>) {
        if (batch.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            batch.forEach { (ts, mag) ->
                samples.addLast(AccelSample(ts, mag))
            }
            val cutoff = nowMs - RETENTION_MS
            while (samples.isNotEmpty() && samples.first().timestamp < cutoff) {
                samples.removeFirst()
            }
            totalSamplesReceived += batch.size
        }
        Log.d(TAG, "appended batch size=${batch.size} bufferNow=${samples.size}")
    }

    /** Movement-event count in a time window — samples whose magnitude crosses the threshold. */
    fun movementEventsInWindow(windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        synchronized(lock) {
            return samples.count {
                it.timestamp >= cutoff && it.magnitude >= MOVEMENT_THRESHOLD_M_S2
            }
        }
    }

    /** Samples in the last [minutesBack] minutes — for the predictor / diagnostics. */
    fun recent(minutesBack: Int = 60): List<AccelSample> {
        val cutoff = System.currentTimeMillis() - minutesBack * 60_000L
        synchronized(lock) {
            return samples.filter { it.timestamp >= cutoff }
        }
    }

    /**
     * Per-epoch accelerometer features for the window [startMs, endMs), matched
     * to how the model was trained on DREAMT:
     *   acc_std        = std of gravity-removed magnitude, in g
     *   acc_move_ratio = fraction of samples above MOVE_THRESH_G
     *
     * The watch sends magnitude already gravity-removed in m/s²; we divide by
     * 9.81 to get g (training used gravity-removed g). Returns (0,0) if too few
     * samples in the window.
     */
    fun accFeaturesForWindow(startMs: Long, endMs: Long): Pair<Float, Float> {
        val G = 9.81f
        val MOVE_THRESH_G = 0.02f
        val inWindow: List<Float>
        synchronized(lock) {
            inWindow = samples.filter { it.timestamp in startMs until endMs }.map { it.magnitude / G }
        }
        if (inWindow.size < 3) return 0f to 0f
        val mean = inWindow.average()
        val std = kotlin.math.sqrt(inWindow.map { (it - mean) * (it - mean) }.average()).toFloat()
        val moveRatio = inWindow.count { it > MOVE_THRESH_G }.toFloat() / inWindow.size
        return std to moveRatio
    }

    fun bufferSize(): Int = synchronized(lock) { samples.size }

    /** How fresh is the most recent sample. Negative if none. */
    fun lagMillis(): Long {
        synchronized(lock) {
            val latest = samples.lastOrNull()?.timestamp ?: return -1L
            return System.currentTimeMillis() - latest
        }
    }
}
