package com.example.sleepwisepoc

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite Sleep Stage Predictor (Stage 3: 4-signal, multi-cohort model).
 *
 * Model: Dense NN trained on Walch 2019 + DREAMT + Wearanize+ (3 datasets, 3
 * devices, 120 subjects) using HR + accelerometer + REAL skin temperature + HRV,
 * with missingness masks (has_temp/has_hrv) so it degrades gracefully when a
 * signal is absent. Binary Deep(N3) vs Light. Feature order + scaler + operating
 * threshold come from tflite_metadata.json (45 features).
 *
 * The 45-feature vector is rebuilt on-device to match the Python training pipeline
 * EXACTLY: base + temporal (add_temporal_features) + causal (add_causal: expanding
 * personalized z-scores, cummax drop, rolling 5/10/15) + temp + time-domain HRV +
 * personalized temp/HRV z-scores + presence masks. Missing values are standardized
 * to zero (the mask carries presence), identical to the training-time handling.
 */
class TFLiteSleepPredictor(private val context: Context) {

    companion object {
        private const val TAG = "TFLiteSleepPredictor"
        private const val MODEL_FILE = "sleep_stage_model.tflite"
        private const val METADATA_FILE = "tflite_metadata.json"
        // Second, DISPLAY-ONLY model: 4-stage (Wake/Light/Deep/REM) morning report.
        // Same 45-feature vector; NOT used for the wake decision (that stays binary).
        private const val REPORT_MODEL_FILE = "report_model.tflite"
        private const val REPORT_METADATA_FILE = "report_metadata.json"

        const val WARMUP_EPOCHS = 5    // min history before predicting (rolling windows warm up)
        const val NUM_CLASSES = 2
        const val SMOOTHING_WINDOW = 3

        const val EMA_ALPHA = 0.3f
        const val THRESHOLD_TO_DEEP = 0.55f
        const val THRESHOLD_TO_LIGHT = 0.35f
    }

    private var interpreter: Interpreter? = null
    private var scalerMean: FloatArray? = null
    private var scalerScale: FloatArray? = null
    private var featureNames: List<String> = emptyList()
    private var classNames: List<String> = listOf("Light", "Deep")

    // 4-stage report model (display-only). Own interpreter + scaler; same feature order.
    private var reportInterpreter: Interpreter? = null
    private var reportScalerMean: FloatArray? = null
    private var reportScalerScale: FloatArray? = null
    private var reportClassNames: List<String> = listOf("Wake", "Light", "Deep", "REM")

    // Full-night per-epoch histories (needed for expanding/rolling causal features).
    private val hrMeanHist = mutableListOf<Float>()
    private val accStdHist = mutableListOf<Float>()
    private val tempHist = mutableListOf<Float?>()      // null when temp absent this epoch
    private val rmssdHist = mutableListOf<Float?>()
    private val ibiMeanHist = mutableListOf<Float?>()
    private val latestEpochs = mutableListOf<EpochFeatures>()

    private val predictionHistory = mutableListOf<String>()
    private var emaDeepProb = 0.5f
    private var currentState = "Light"

    data class SleepPrediction(
        val sleepStage: String,
        val confidence: Float,
        val probabilities: Map<String, Float>,
        val shouldWake: Boolean,
        val message: String,
        val isStable: Boolean = false,
        val consecutiveCount: Int = 1,
        val emaDeepProb: Float = 0.5f,
        val rawDeepProb: Float = 0.5f
    )

    /**
     * One epoch of raw per-signal stats. Temp/HRV are nullable — when null (signal
     * not streaming this epoch) the corresponding presence mask is 0 and the model
     * ignores those features. The live wear path supplies real temp+HRV; the
     * emulator/mock and Samsung-fallback paths leave them null (masks 0).
     */
    data class EpochFeatures(
        val hrMean: Float, val hrStd: Float, val hrMin: Float, val hrMax: Float,
        val hrRange: Float, val hrCv: Float, val hrMedian: Float, val hrIqr: Float, val hrSkew: Float,
        val accStd: Float = 0f, val accMoveRatio: Float = 0f,
        val tempMean: Float? = null, val tempStd: Float? = null, val tempTrend: Float? = null,
        val hrvRmssd: Float? = null, val hrvSdnn: Float? = null,
        val hrvPnn50: Float? = null, val hrvIbiMean: Float? = null,
    ) {
        val hasTemp: Boolean get() = tempMean != null
        val hasHrv: Boolean get() = hrvRmssd != null
    }

    fun initialize(): Boolean {
        return try {
            interpreter = Interpreter(loadModelFile(MODEL_FILE))
            loadMetadata()
            Log.d(TAG, "TFLite loaded: ${featureNames.size} features, classes=$classNames")
            // Best-effort: the 4-stage report model is optional — a missing asset must
            // NOT break the alarm. If it loads, the morning report gets 4 stages.
            try {
                reportInterpreter = Interpreter(loadModelFile(REPORT_MODEL_FILE))
                loadReportMetadata()
                Log.d(TAG, "Report TFLite loaded: classes=$reportClassNames")
            } catch (e: Exception) {
                Log.w(TAG, "Report model not loaded (report falls back to binary): ${e.message}")
                reportInterpreter = null
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite: ${e.message}")
            false
        }
    }

    private fun loadModelFile(name: String): MappedByteBuffer {
        val fd = context.assets.openFd(name)
        val fileChannel = FileInputStream(fd.fileDescriptor).channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    private fun floatArr(json: JSONObject, key: String, nanFill: Float): FloatArray {
        val a = json.getJSONArray(key)
        return FloatArray(a.length()) { if (a.get(it).toString() == "NaN") nanFill else a.getDouble(it).toFloat() }
    }

    private fun loadMetadata() {
        val json = JSONObject(context.assets.open(METADATA_FILE).bufferedReader().use { it.readText() })
        val fa = json.getJSONArray("feature_names")
        featureNames = (0 until fa.length()).map { fa.getString(it) }
        val ca = json.getJSONArray("class_names")
        classNames = (0 until ca.length()).map { ca.getString(it) }
        scalerMean = floatArr(json, "scaler_mean", 0f)
        scalerScale = floatArr(json, "scaler_scale", 1f)
    }

    private fun loadReportMetadata() {
        val json = JSONObject(context.assets.open(REPORT_METADATA_FILE).bufferedReader().use { it.readText() })
        val ca = json.getJSONArray("class_names")
        reportClassNames = (0 until ca.length()).map { ca.getString(it) }
        reportScalerMean = floatArr(json, "scaler_mean", 0f)
        reportScalerScale = floatArr(json, "scaler_scale", 1f)
        // report model shares the binary model's feature order (same FEATS) — no separate list needed
    }

    fun addEpoch(f: EpochFeatures) {
        latestEpochs.add(f)
        hrMeanHist.add(f.hrMean)
        accStdHist.add(f.accStd)
        tempHist.add(f.tempMean)
        rmssdHist.add(f.hrvRmssd)
        ibiMeanHist.add(f.hrvIbiMean)
    }

    fun canPredict(): Boolean = hrMeanHist.size >= WARMUP_EPOCHS
    fun getBufferSize(): Int = hrMeanHist.size

    fun clearBuffer() {
        latestEpochs.clear(); hrMeanHist.clear(); accStdHist.clear()
        tempHist.clear(); rmssdHist.clear(); ibiMeanHist.clear()
        predictionHistory.clear(); emaDeepProb = 0.5f; currentState = "Light"
    }

    // ── pandas-equivalent helpers ─────────────────────────────────────────────
    private fun mean(xs: List<Float>): Float = if (xs.isEmpty()) 0f else xs.average().toFloat()
    /** sample std (ddof=1); 0 when fewer than 2 values (pandas NaN→fillna(0)). */
    private fun stdSample(xs: List<Float>): Float {
        if (xs.size < 2) return 0f
        val m = xs.average()
        return kotlin.math.sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)).toFloat()
    }
    /** expanding z of the last element of [hist] over its non-null values; NaN if last is null. */
    private fun expandingZ(hist: List<Float?>): Float {
        val cur = hist.lastOrNull() ?: return Float.NaN
        val vals = hist.filterNotNull()            // pandas expanding skips NaN
        val m = vals.average()
        val s = if (vals.size < 2) 1f else stdSample(vals)   // pandas .std().fillna(1)
        return ((cur - m) / (s + 1e-6f)).toFloat()
    }

    /** Build the 45-feature vector in metadata (featureNames) order — matches training exactly. */
    private fun computeFeatureVector(): FloatArray {
        val n = hrMeanHist.size
        val i = n - 1
        val cur = latestEpochs.last()
        val hr = hrMeanHist
        fun lag(k: Int) = if (i - k >= 0) hr[i - k] else hr[i]
        val last4 = hr.takeLast(4)
        val trendBase = if (i - 4 >= 0) hr[i - 4] else null
        val f = HashMap<String, Float>(64)

        // base HR stats (this epoch)
        f["hr_mean"] = cur.hrMean; f["hr_std"] = cur.hrStd; f["hr_min"] = cur.hrMin
        f["hr_max"] = cur.hrMax; f["hr_range"] = cur.hrRange; f["hr_cv"] = cur.hrCv
        f["hr_median"] = cur.hrMedian; f["hr_iqr"] = cur.hrIqr; f["hr_skew"] = cur.hrSkew
        // temporal (add_temporal_features, lookback=4)
        f["hr_mean_lag1"] = lag(1); f["hr_mean_lag2"] = lag(2); f["hr_mean_lag3"] = lag(3); f["hr_mean_lag4"] = lag(4)
        f["hr_mean_rolling_mean"] = mean(last4)
        f["hr_mean_rolling_std"] = stdSample(last4)
        f["hr_mean_trend"] = if (trendBase != null) cur.hrMean - trendBase else 0f
        f["hr_mean_roc"] = if (trendBase != null && trendBase != 0f) (cur.hrMean - trendBase) / trendBase else 0f
        f["hr_stability"] = stdSample(last4)
        f["sleep_cycle_position"] = (i % 90) / 90f
        f["acc_std"] = cur.accStd; f["acc_move_ratio"] = cur.accMoveRatio
        // causal (add_causal)
        f["min_since_onset"] = i.toFloat()
        f["hr_z"] = expandingZ(hr.map { it as Float? })
        f["hr_drop_from_max"] = (hr.max()) - cur.hrMean
        f["hr_rmean5"] = mean(hr.takeLast(5)); f["hr_rmean10"] = mean(hr.takeLast(10)); f["hr_rmean15"] = mean(hr.takeLast(15))
        f["hr_rstd5"] = stdSample(hr.takeLast(5)); f["hr_rstd10"] = stdSample(hr.takeLast(10)); f["hr_rstd15"] = stdSample(hr.takeLast(15))
        f["acc_rmean5"] = mean(accStdHist.takeLast(5)); f["acc_rmean10"] = mean(accStdHist.takeLast(10)); f["acc_rmean15"] = mean(accStdHist.takeLast(15))
        // temp (real or NaN)
        f["temp_mean"] = cur.tempMean ?: Float.NaN; f["temp_std"] = cur.tempStd ?: Float.NaN; f["temp_trend"] = cur.tempTrend ?: Float.NaN
        // time-domain HRV
        f["hrv_rmssd"] = cur.hrvRmssd ?: Float.NaN; f["hrv_sdnn"] = cur.hrvSdnn ?: Float.NaN
        f["hrv_pnn50"] = cur.hrvPnn50 ?: Float.NaN; f["hrv_ibi_mean"] = cur.hrvIbiMean ?: Float.NaN
        // personalized expanding z-scores
        f["temp_mean_z"] = expandingZ(tempHist)
        f["hrv_rmssd_z"] = expandingZ(rmssdHist)
        f["hrv_ibi_mean_z"] = expandingZ(ibiMeanHist)
        // presence masks
        f["has_temp"] = if (cur.hasTemp) 1f else 0f
        f["has_hrv"] = if (cur.hasHrv) 1f else 0f

        return FloatArray(featureNames.size) { idx -> f[featureNames[idx]] ?: Float.NaN }
    }

    /** StandardScaler, then NaN→0 (missing features carry no signal; masks flag presence). */
    private fun normalizeWith(features: FloatArray, mean: FloatArray?, scale: FloatArray?): FloatArray {
        if (mean == null || scale == null) return features
        return FloatArray(features.size) { i ->
            if (i < mean.size && i < scale.size && scale[i] != 0f && !features[i].isNaN())
                (features[i] - mean[i]) / scale[i] else 0f
        }
    }
    private fun normalizeFeatures(features: FloatArray): FloatArray =
        normalizeWith(features, scalerMean, scalerScale)

    /**
     * 4-stage report label for the current epoch (Wake/Light/Deep/REM), via the
     * separate report model. Reuses the SAME 45-feature vector as the alarm; just a
     * different scaler + softmax argmax (no EMA/hysteresis — the report shows the
     * per-minute stage). Returns null if the report model isn't loaded or we're
     * still in warmup. DISPLAY ONLY — never gates the alarm.
     */
    fun predictReportStage(): String? {
        val interp = reportInterpreter ?: return null
        if (!canPredict()) return null
        val normalized = normalizeWith(computeFeatureVector(), reportScalerMean, reportScalerScale)
        val output = Array(1) { FloatArray(reportClassNames.size) }
        return try {
            interp.run(arrayOf(normalized), output)
            var best = 0
            for (j in output[0].indices) if (output[0][j] > output[0][best]) best = j
            reportClassNames.getOrNull(best)
        } catch (e: Exception) {
            Log.w(TAG, "report inference failed: ${e.message}"); null
        }
    }

    fun predict(): SleepPrediction? {
        if (!canPredict()) {
            Log.w(TAG, "Not enough epochs: ${hrMeanHist.size}/$WARMUP_EPOCHS"); return null
        }
        val interp = interpreter ?: run { Log.e(TAG, "Interpreter not initialized"); return null }

        val normalized = normalizeFeatures(computeFeatureVector())
        val input = arrayOf(normalized)
        val output = Array(1) { FloatArray(NUM_CLASSES) }
        try { interp.run(input, output) } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}"); return null
        }

        val deepIdx = classNames.indexOf("Deep")
        val rawDeepProb = if (deepIdx >= 0) output[0][deepIdx] else 0f
        Log.d(TAG, "Model: Deep=${(rawDeepProb * 100).toInt()}% Light=${((1 - rawDeepProb) * 100).toInt()}%")

        emaDeepProb = EMA_ALPHA * rawDeepProb + (1f - EMA_ALPHA) * emaDeepProb
        val previousState = currentState
        val predictedClass = when {
            currentState == "Light" && emaDeepProb > THRESHOLD_TO_DEEP -> { currentState = "Deep"; "Deep" }
            currentState == "Deep" && emaDeepProb < THRESHOLD_TO_LIGHT -> { currentState = "Light"; "Light" }
            else -> currentState
        }
        Log.d(TAG, "EMA=${(emaDeepProb * 100).toInt()}% | $previousState -> $predictedClass")

        val confidence = if (predictedClass == "Deep") emaDeepProb else (1f - emaDeepProb)
        predictionHistory.add(predictedClass)
        if (predictionHistory.size > SMOOTHING_WINDOW) predictionHistory.removeAt(0)
        val consecutiveCount = countConsecutiveSame()
        val isStable = consecutiveCount >= SMOOTHING_WINDOW

        val probMap = mapOf(
            "Deep" to rawDeepProb, "Light" to (1f - rawDeepProb),
            "Deep (EMA)" to emaDeepProb, "Light (EMA)" to (1f - emaDeepProb)
        )
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isWakeWindow = currentHour in 5..10
        val shouldWake = predictedClass == "Light" && isStable && emaDeepProb < 0.3f && isWakeWindow

        val emaPercent = (emaDeepProb * 100).toInt(); val rawPercent = (rawDeepProb * 100).toInt()
        val stabilityInfo = if (isStable) "stable" else "$consecutiveCount/$SMOOTHING_WINDOW"
        val message = when (predictedClass) {
            "Deep" -> "Deep sleep (EMA: $emaPercent%, raw: $rawPercent%) [$stabilityInfo]"
            "Light" -> if (shouldWake) "Light sleep - optimal wake! (EMA: $emaPercent%) [$stabilityInfo]"
                       else "Light sleep (EMA: $emaPercent%, raw: $rawPercent%) [$stabilityInfo]"
            else -> "Unknown sleep stage."
        }
        return SleepPrediction(predictedClass, confidence, probMap, shouldWake, message,
            isStable, consecutiveCount, emaDeepProb, rawDeepProb)
    }

    private fun countConsecutiveSame(): Int {
        if (predictionHistory.isEmpty()) return 0
        val latest = predictionHistory.last(); var count = 0
        for (i in predictionHistory.indices.reversed()) {
            if (predictionHistory[i] == latest) count++ else break
        }
        return count
    }

    /** Synthetic epochs for the emulator demo (HR + temp only; no HRV → masks temp=1,hrv=0). */
    fun createMockEpoch(scenario: String, epochIndex: Int, totalEpochs: Int): EpochFeatures {
        val r = java.util.Random()
        return when (scenario.lowercase()) {
            "deep" -> EpochFeatures(
                hrMean = 60f + r.nextFloat() * 6, hrStd = 0.5f + r.nextFloat(), hrMin = 58f + r.nextFloat() * 4,
                hrMax = 64f + r.nextFloat() * 4, hrRange = 3f + r.nextFloat() * 3, hrCv = 1.0f + r.nextFloat() * 0.5f,
                hrMedian = 60f + r.nextFloat() * 6, hrIqr = 1f + r.nextFloat() * 2, hrSkew = -0.1f + r.nextFloat() * 0.2f,
                tempMean = 35.0f + r.nextFloat(), tempStd = 0.01f + r.nextFloat() * 0.02f, tempTrend = -0.001f + r.nextFloat() * 0.001f)
            "light" -> EpochFeatures(
                hrMean = 72f + r.nextFloat() * 8, hrStd = 3.0f + r.nextFloat() * 3, hrMin = 68f + r.nextFloat() * 6,
                hrMax = 78f + r.nextFloat() * 10, hrRange = 10f + r.nextFloat() * 8, hrCv = 4.0f + r.nextFloat() * 2,
                hrMedian = 72f + r.nextFloat() * 8, hrIqr = 5f + r.nextFloat() * 5, hrSkew = 0.1f + r.nextFloat() * 0.3f,
                tempMean = 33.0f + r.nextFloat(), tempStd = 0.04f + r.nextFloat() * 0.04f, tempTrend = 0.001f + r.nextFloat() * 0.002f)
            "rem" -> EpochFeatures(
                hrMean = 62f + r.nextFloat() * 6, hrStd = 1.0f + r.nextFloat() * 1.5f, hrMin = 60f + r.nextFloat() * 4,
                hrMax = 66f + r.nextFloat() * 6, hrRange = 4f + r.nextFloat() * 4, hrCv = 1.5f + r.nextFloat(),
                hrMedian = 62f + r.nextFloat() * 6, hrIqr = 2f + r.nextFloat() * 3, hrSkew = r.nextFloat() * 0.2f,
                tempMean = 34.8f + r.nextFloat(), tempStd = 0.02f + r.nextFloat() * 0.02f, tempTrend = -0.0005f + r.nextFloat() * 0.001f)
            "wake" -> EpochFeatures(
                hrMean = 75f + r.nextFloat() * 10, hrStd = 4.0f + r.nextFloat() * 4, hrMin = 70f + r.nextFloat() * 8,
                hrMax = 82f + r.nextFloat() * 12, hrRange = 12f + r.nextFloat() * 10, hrCv = 5.0f + r.nextFloat() * 2,
                hrMedian = 75f + r.nextFloat() * 10, hrIqr = 6f + r.nextFloat() * 6, hrSkew = 0.2f + r.nextFloat() * 0.4f,
                tempMean = 32.5f + r.nextFloat(), tempStd = 0.05f + r.nextFloat() * 0.05f, tempTrend = 0.002f + r.nextFloat() * 0.002f)
            else -> createMockEpoch("light", epochIndex, totalEpochs)
        }
    }

    fun close() {
        interpreter?.close(); interpreter = null
        reportInterpreter?.close(); reportInterpreter = null
    }
}
