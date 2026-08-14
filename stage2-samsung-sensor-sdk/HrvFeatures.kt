package com.example.sleepwisepoc

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure-Kotlin HRV feature extraction (no Samsung SDK dependency) — runs on the
 * PHONE. Given the inter-beat intervals (ms) that fell inside one 60-second epoch,
 * returns standard time-domain HRV features + a quality flag.
 *
 * These features feed the sleep-stage model — but note: the current model was
 * trained WITHOUT HRV, so it only benefits after retraining on a dataset that
 * has RR/IBI (e.g. DREAMT E4, or MESA PPG/ECG). This class produces the inputs;
 * the model work is Stage 3.
 */
data class HrvFeatures(
    val meanRr: Float,   // mean inter-beat interval (ms)
    val sdnn: Float,     // std of RR — overall HRV
    val rmssd: Float,    // root-mean-square of successive differences — parasympathetic tone
    val pnn50: Float,    // % of successive RR pairs differing > 50 ms
    val valid: Boolean,  // false when too few / too noisy to trust this epoch
) {
    companion object {
        private const val MIN_BEATS = 20          // ~ a third of a minute of beats
        private const val MAX_BAD_FRACTION = 0.25 // literature's >25% bad-interval rule

        /** @param ibiMs valid IBIs (ms) in the epoch; @param totalBeats incl. rejected, for QC */
        fun fromEpoch(ibiMs: List<Float>, totalBeats: Int): HrvFeatures {
            val badFrac = if (totalBeats > 0) 1.0 - ibiMs.size.toDouble() / totalBeats else 1.0
            if (ibiMs.size < MIN_BEATS || badFrac > MAX_BAD_FRACTION)
                return HrvFeatures(0f, 0f, 0f, 0f, valid = false)

            val n = ibiMs.size
            val mean = ibiMs.average().toFloat()
            val sdnn = sqrt(ibiMs.map { val d = it - mean; (d * d).toDouble() }.average()).toFloat()
            val diffs = (1 until n).map { ibiMs[it] - ibiMs[it - 1] }
            val rmssd = if (diffs.isEmpty()) 0f
                else sqrt(diffs.map { (it * it).toDouble() }.average()).toFloat()
            val pnn50 = if (diffs.isEmpty()) 0f
                else 100f * diffs.count { abs(it) > 50f } / diffs.size
            return HrvFeatures(mean, sdnn, rmssd, pnn50, valid = true)
        }

        /** Neutral values for epochs with no usable IBI (keeps the feature vector shape stable). */
        val EMPTY = HrvFeatures(0f, 0f, 0f, 0f, valid = false)
    }
}
