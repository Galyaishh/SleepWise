package com.example.sleepwisepoc

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.device.DeviceGroup
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.request.ReadSourceFilter
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SamsungHealthManager - Reads health data from Samsung Health via SDK
 *
 * Collects all relevant data for sleep stage classification:
 * - Heart Rate (for HR statistics per epoch)
 * - Skin Temperature (if available)
 * - Sleep sessions and stages
 * - Blood Oxygen (SpO2)
 *
 * Data flow: Galaxy Watch -> Samsung Health -> This App (via SDK) -> Model Prediction
 */
class SamsungHealthManager(private val context: Context) {

    companion object {
        private const val TAG = "SamsungHealth"

        // Epoch duration in milliseconds (1 minute = 60000ms)
        const val EPOCH_DURATION_MS = 60000L

        // Permissions we need - all sleep-relevant data types the SDK exposes.
        // Each new entry triggers a Samsung-Health-side permission prompt on next launch.
        val PERMISSIONS = setOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.SKIN_TEMPERATURE, AccessType.READ),
            Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ),
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
            Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ),
        )
    }

    private var healthDataStore: HealthDataStore? = null

    // Initialize the SDK
    fun initialize(): Boolean {
        return try {
            healthDataStore = HealthDataService.getStore(context)
            Log.d(TAG, "SamsungHealthManager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Samsung Health SDK", e)
            false
        }
    }

    // Check and request permissions
    suspend fun checkAndRequestPermissions(activity: Activity): Boolean {
        val store = healthDataStore ?: return false

        return try {
            val grantedPermissions = store.getGrantedPermissions(PERMISSIONS)

            if (grantedPermissions.containsAll(PERMISSIONS)) {
                Log.d(TAG, "All permissions already granted")
                true
            } else {
                Log.d(TAG, "Requesting permissions...")
                store.requestPermissions(PERMISSIONS, activity)
                // Check again after request
                val newGranted = store.getGrantedPermissions(PERMISSIONS)
                newGranted.containsAll(PERMISSIONS)
            }
        } catch (error: HealthDataException) {
            if (error is ResolvablePlatformException && error.hasResolution) {
                Log.d(TAG, "Resolving platform exception...")
                error.resolve(activity)
            }
            Log.e(TAG, "Permission error", error)
            false
        }
    }

    // ==================== DATA CLASSES ====================

    data class HeartRateSample(
        val bpm: Int,
        val timestamp: Long  // epoch millis
    )

    data class TemperatureSample(
        val tempCelsius: Float,
        val timestamp: Long
    )

    data class SpO2Sample(
        val percentage: Int,
        val timestamp: Long
    )

    data class BodyTempSample(
        val tempCelsius: Float,
        val timestamp: Long,
    )

    data class ExerciseSample(
        val startTime: Long,
        val endTime: Long,
        val durationMinutes: Int,
    )

    data class EnergyScoreSample(
        val score: Int,
        val timestamp: Long,
    )

    data class SleepSession(
        val startTime: Long,
        val endTime: Long,
        val stages: List<SleepStage>
    )

    data class SleepStage(
        val stage: String,  // "Awake", "Light", "Deep", "REM"
        val startTime: Long,
        val endTime: Long
    )

    /**
     * One epoch (1 minute) of aggregated data for the sleep model
     * Matches the features expected by our TFLite model
     */
    data class SleepEpoch(
        val timestamp: Long,
        val timeString: String,
        // HR features (9)
        val hrMean: Float,
        val hrStd: Float,
        val hrMin: Float,
        val hrMax: Float,
        val hrRange: Float,
        val hrCv: Float,
        val hrMedian: Float,
        val hrIqr: Float,
        val hrSkew: Float,
        // Temperature features (3)
        val tempMean: Float,
        val tempStd: Float,
        val tempTrend: Float,
        // Extra info
        val hrSampleCount: Int,
        val tempSampleCount: Int,
        val actualSleepStage: String? = null  // Ground truth if available
    )

    // ==================== DATA READING ====================

    // Read heart rate data
    suspend fun readHeartRate(hoursBack: Int = 24): List<HeartRateSample> {
        val store = healthDataStore ?: return emptyList()

        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusHours(hoursBack.toLong())
            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.ASC)  // Oldest first for processing
                .build()

            val response = store.readData(readRequest)
            val samples = mutableListOf<HeartRateSample>()

            response.dataList.forEach { dataPoint ->
                try {
                    // Watch HR is packaged as series inside each data-point container.
                    // SERIES_DATA holds the individual samples; the top-level
                    // HEART_RATE field is only an aggregate. Prefer the series.
                    val series = dataPoint.getValue(DataType.HeartRateType.SERIES_DATA)
                    if (series != null && series.isNotEmpty()) {
                        series.forEach { hr ->
                            if (hr.heartRate > 0) {
                                samples.add(
                                    HeartRateSample(hr.heartRate.toInt(), hr.startTime.toEpochMilli())
                                )
                            }
                        }
                    } else {
                        val bpm = dataPoint.getValue(DataType.HeartRateType.HEART_RATE)
                        if (bpm != null && bpm > 0) {
                            samples.add(HeartRateSample(bpm.toInt(), dataPoint.startTime.toEpochMilli()))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing heart rate data point", e)
                }
            }

            Log.d(TAG, "Read ${samples.size} heart rate samples")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read heart rate", e)
            emptyList()
        }
    }

    // Read skin temperature data
    suspend fun readSkinTemperature(hoursBack: Int = 24): List<TemperatureSample> {
        val store = healthDataStore ?: return emptyList()

        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusHours(hoursBack.toLong())
            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            val readRequest = DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.ASC)
                .build()

            val response = store.readData(readRequest)
            val samples = mutableListOf<TemperatureSample>()

            response.dataList.forEach { dataPoint ->
                try {
                    val series = dataPoint.getValue(DataType.SkinTemperatureType.SERIES_DATA)
                    if (series != null && series.isNotEmpty()) {
                        series.forEach { st ->
                            samples.add(
                                TemperatureSample(st.skinTemperature, st.startTime.toEpochMilli())
                            )
                        }
                    } else {
                        val temp = dataPoint.getValue(DataType.SkinTemperatureType.SKIN_TEMPERATURE)
                        if (temp != null) {
                            samples.add(TemperatureSample(temp, dataPoint.startTime.toEpochMilli()))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing temperature data point", e)
                }
            }

            Log.d(TAG, "Read ${samples.size} temperature samples")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read skin temperature", e)
            emptyList()
        }
    }

    // Read blood oxygen (SpO2) data
    // Note: SpO2 field access may vary by SDK version - currently simplified
    suspend fun readBloodOxygen(hoursBack: Int = 24): List<SpO2Sample> {
        val store = healthDataStore ?: return emptyList()

        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusHours(hoursBack.toLong())
            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            val readRequest = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.ASC)
                .build()

            val response = store.readData(readRequest)
            val samples = mutableListOf<SpO2Sample>()

            response.dataList.forEach { dataPoint ->
                try {
                    val series = dataPoint.getValue(DataType.BloodOxygenType.SERIES_DATA)
                    if (series != null && series.isNotEmpty()) {
                        series.forEach { os ->
                            if (os.oxygenSaturation > 0) {
                                samples.add(
                                    SpO2Sample(os.oxygenSaturation.toInt(), os.startTime.toEpochMilli())
                                )
                            }
                        }
                    } else {
                        val spo2 = dataPoint.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)
                        if (spo2 != null && spo2 > 0) {
                            samples.add(SpO2Sample(spo2.toInt(), dataPoint.startTime.toEpochMilli()))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SpO2 data point", e)
                }
            }

            Log.d(TAG, "Read ${samples.size} SpO2 samples")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blood oxygen", e)
            emptyList()
        }
    }

    // Read sleep sessions with stages
    suspend fun readSleep(daysBack: Int = 7): List<SleepSession> {
        val store = healthDataStore ?: return emptyList()

        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusDays(daysBack.toLong())
            val localTimeFilter = LocalTimeFilter.of(startTime, endTime)

            val readRequest = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(localTimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val response = store.readData(readRequest)
            val sessions = mutableListOf<SleepSession>()

            response.dataList.forEach { dataPoint ->
                try {
                    val sessionStart = dataPoint.startTime.toEpochMilli()
                    val sessionEnd = dataPoint.endTime?.toEpochMilli() ?: sessionStart

                    // Try to get sleep stages if available
                    val stages = mutableListOf<SleepStage>()
                    // Note: Samsung Health SDK sleep stage parsing depends on SDK version
                    // The stages might be in sub-data or separate queries

                    sessions.add(SleepSession(
                        startTime = sessionStart,
                        endTime = sessionEnd,
                        stages = stages
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing sleep data point", e)
                }
            }

            Log.d(TAG, "Read ${sessions.size} sleep sessions")
            sessions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sleep data", e)
            emptyList()
        }
    }

    // Read core body temperature (separate from skin temperature)
    suspend fun readBodyTemperature(hoursBack: Int = 24): List<BodyTempSample> {
        val store = healthDataStore ?: return emptyList()
        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusHours(hoursBack.toLong())
            val readRequest = DataTypes.BODY_TEMPERATURE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
                .setOrdering(Ordering.ASC)
                .build()
            val response = store.readData(readRequest)
            val samples = mutableListOf<BodyTempSample>()
            response.dataList.forEach { dataPoint ->
                try {
                    val temp = dataPoint.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE)
                    if (temp != null) {
                        samples.add(BodyTempSample(temp, dataPoint.startTime.toEpochMilli()))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing body-temp data point", e)
                }
            }
            Log.d(TAG, "Read ${samples.size} body-temp samples")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read body temperature", e)
            emptyList()
        }
    }

    // Read exercise sessions (workouts)
    suspend fun readExercise(daysBack: Int = 7): List<ExerciseSample> {
        val store = healthDataStore ?: return emptyList()
        return try {
            val endTime = LocalDateTime.now()
            val startTime = endTime.minusDays(daysBack.toLong())
            val readRequest = DataTypes.EXERCISE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startTime, endTime))
                .setOrdering(Ordering.DESC)
                .build()
            val response = store.readData(readRequest)
            val samples = mutableListOf<ExerciseSample>()
            response.dataList.forEach { dp ->
                try {
                    val start = dp.startTime.toEpochMilli()
                    val end = dp.endTime?.toEpochMilli() ?: start
                    samples.add(
                        ExerciseSample(
                            startTime = start,
                            endTime = end,
                            durationMinutes = ((end - start) / 60000L).toInt(),
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing exercise data point", e)
                }
            }
            Log.d(TAG, "Read ${samples.size} exercise sessions")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read exercise", e)
            emptyList()
        }
    }

    // Read daily energy score (Samsung's recovery/readiness metric, 0-100).
    // EnergyScore uses LocalDate (per-day), unlike the other types.
    suspend fun readEnergyScore(daysBack: Int = 7): List<EnergyScoreSample> {
        val store = healthDataStore ?: return emptyList()
        return try {
            val endDate = java.time.LocalDate.now()
            val startDate = endDate.minusDays(daysBack.toLong())
            val readRequest = DataTypes.ENERGY_SCORE.readDataRequestBuilder
                .setLocalDateFilter(com.samsung.android.sdk.health.data.request.LocalDateFilter.of(startDate, endDate))
                .setOrdering(Ordering.DESC)
                .build()
            val response = store.readData(readRequest)
            val samples = mutableListOf<EnergyScoreSample>()
            response.dataList.forEach { dp ->
                try {
                    val score = dp.getValue(DataType.EnergyScoreType.ENERGY_SCORE)
                    if (score != null) {
                        samples.add(EnergyScoreSample(score.toInt(), dp.startTime.toEpochMilli()))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing energy-score data point", e)
                }
            }
            Log.d(TAG, "Read ${samples.size} energy-score entries")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read energy score", e)
            emptyList()
        }
    }

    // ==================== DATA PROCESSING ====================

    /**
     * Process raw data into epochs suitable for the sleep model
     * Each epoch is 1 minute of aggregated sensor data
     */
    suspend fun processDataIntoEpochs(hoursBack: Int = 8): List<SleepEpoch> {
        // Read all raw data
        val hrSamples = readHeartRate(hoursBack)
        val tempSamples = readSkinTemperature(hoursBack)

        if (hrSamples.isEmpty()) {
            Log.w(TAG, "No HR data to process")
            return emptyList()
        }

        // Find time range
        val startTime = hrSamples.minOf { it.timestamp }
        val endTime = hrSamples.maxOf { it.timestamp }

        val epochs = mutableListOf<SleepEpoch>()
        var epochStart = startTime

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Previous epoch for trend calculation
        var prevTempMean: Float? = null

        while (epochStart < endTime) {
            val epochEnd = epochStart + EPOCH_DURATION_MS

            // Get HR samples in this epoch
            val epochHR = hrSamples.filter { it.timestamp in epochStart until epochEnd }

            // Get temp samples in this epoch
            val epochTemp = tempSamples.filter { it.timestamp in epochStart until epochEnd }

            if (epochHR.isNotEmpty()) {
                val hrValues = epochHR.map { it.bpm.toFloat() }
                val tempValues = if (epochTemp.isNotEmpty()) {
                    epochTemp.map { it.tempCelsius }
                } else {
                    listOf(34.0f)  // Default skin temp if no data
                }

                // Calculate HR statistics
                val hrMean = hrValues.average().toFloat()
                val hrStd = hrValues.std()
                val hrMin = hrValues.minOrNull() ?: 0f
                val hrMax = hrValues.maxOrNull() ?: 0f
                val hrRange = hrMax - hrMin
                val hrCv = if (hrMean > 0) (hrStd / hrMean) * 100 else 0f
                val hrMedian = hrValues.median()
                val hrIqr = hrValues.iqr()
                val hrSkew = hrValues.skewness()

                // Calculate temp statistics
                val tempMean = tempValues.average().toFloat()
                val tempStd = if (tempValues.size > 1) tempValues.std() else 0f
                val tempTrend = if (prevTempMean != null) tempMean - prevTempMean!! else 0f

                epochs.add(SleepEpoch(
                    timestamp = epochStart,
                    timeString = dateFormatter.format(Date(epochStart)),
                    hrMean = hrMean,
                    hrStd = hrStd,
                    hrMin = hrMin,
                    hrMax = hrMax,
                    hrRange = hrRange,
                    hrCv = hrCv,
                    hrMedian = hrMedian,
                    hrIqr = hrIqr,
                    hrSkew = hrSkew,
                    tempMean = tempMean,
                    tempStd = tempStd,
                    tempTrend = tempTrend,
                    hrSampleCount = epochHR.size,
                    tempSampleCount = epochTemp.size
                ))

                prevTempMean = tempMean
            }

            epochStart = epochEnd
        }

        Log.d(TAG, "Processed ${epochs.size} epochs from raw data")
        return epochs
    }

    // ==================== EXPORT FUNCTIONS ====================

    /**
     * Export all sleep-relevant data to CSV for analysis
     */
    suspend fun exportDataToCSV(hoursBack: Int = 24): String {
        val epochs = processDataIntoEpochs(hoursBack)

        if (epochs.isEmpty()) {
            return "No data to export"
        }

        val csv = StringBuilder()

        // Header
        csv.appendLine("timestamp,time,hr_mean,hr_std,hr_min,hr_max,hr_range,hr_cv,hr_median,hr_iqr,hr_skew,temp_mean,temp_std,temp_trend,hr_samples,temp_samples")

        // Data rows
        epochs.forEach { epoch ->
            csv.appendLine("${epoch.timestamp},${epoch.timeString},${epoch.hrMean},${epoch.hrStd},${epoch.hrMin},${epoch.hrMax},${epoch.hrRange},${epoch.hrCv},${epoch.hrMedian},${epoch.hrIqr},${epoch.hrSkew},${epoch.tempMean},${epoch.tempStd},${epoch.tempTrend},${epoch.hrSampleCount},${epoch.tempSampleCount}")
        }

        // Save to file
        try {
            val filename = "sleepwise_data_${System.currentTimeMillis()}.csv"
            val file = File(context.getExternalFilesDir(null), filename)
            file.writeText(csv.toString())
            Log.d(TAG, "Exported data to ${file.absolutePath}")
            return "Exported ${epochs.size} epochs to:\n${file.absolutePath}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save CSV", e)
            return csv.toString()
        }
    }

    // ==================== DISPLAY FUNCTIONS ====================

    /**
     * Get comprehensive health data summary for display
     */
    suspend fun getFormattedHealthData(): String {
        val output = StringBuilder()
        val dateFormatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        output.append("=== SAMSUNG HEALTH DATA ===\n\n")

        // Heart Rate
        output.append("--- HEART RATE (24h) ---\n")
        val heartRates = readHeartRate(hoursBack = 24)
        if (heartRates.isEmpty()) {
            output.append("No heart rate data\n")
        } else {
            val hrValues = heartRates.map { it.bpm }
            output.append("Samples: ${heartRates.size}\n")
            output.append("Avg: ${hrValues.average().toInt()} bpm\n")
            output.append("Min: ${hrValues.minOrNull()} | Max: ${hrValues.maxOrNull()}\n")
            output.append("Latest: ${heartRates.last().bpm} bpm @ ${dateFormatter.format(Date(heartRates.last().timestamp))}\n")
        }

        // Temperature
        output.append("\n--- SKIN TEMPERATURE (24h) ---\n")
        val temps = readSkinTemperature(hoursBack = 24)
        if (temps.isEmpty()) {
            output.append("No temperature data\n")
        } else {
            val tempValues = temps.map { it.tempCelsius }
            output.append("Samples: ${temps.size}\n")
            output.append("Avg: ${String.format("%.1f", tempValues.average())}°C\n")
            output.append("Min: ${String.format("%.1f", tempValues.minOrNull())} | Max: ${String.format("%.1f", tempValues.maxOrNull())}\n")
        }

        // Blood Oxygen
        output.append("\n--- BLOOD OXYGEN (24h) ---\n")
        val spo2 = readBloodOxygen(hoursBack = 24)
        if (spo2.isEmpty()) {
            output.append("No SpO2 data\n")
        } else {
            val spo2Values = spo2.map { it.percentage }
            output.append("Samples: ${spo2.size}\n")
            output.append("Avg: ${spo2Values.average().toInt()}%\n")
            output.append("Min: ${spo2Values.minOrNull()}% | Max: ${spo2Values.maxOrNull()}%\n")
        }

        // Sleep
        output.append("\n--- SLEEP (7 days) ---\n")
        val sleepSessions = readSleep(daysBack = 7)
        if (sleepSessions.isEmpty()) {
            output.append("No sleep sessions\n")
        } else {
            output.append("Sessions: ${sleepSessions.size}\n")
            sleepSessions.take(3).forEach { session ->
                val durationHours = (session.endTime - session.startTime) / 3600000.0
                val startStr = dateFormatter.format(Date(session.startTime))
                output.append("$startStr - ${String.format("%.1f", durationHours)}h\n")
            }
        }

        // Body Temperature
        output.append("\n--- BODY TEMPERATURE (24h) ---\n")
        val bodyTemps = readBodyTemperature(hoursBack = 24)
        if (bodyTemps.isEmpty()) {
            output.append("No body-temp data\n")
        } else {
            val values = bodyTemps.map { it.tempCelsius }
            output.append("Samples: ${bodyTemps.size}\n")
            output.append("Avg: ${String.format("%.1f", values.average())}°C | Min ${String.format("%.1f", values.minOrNull())} | Max ${String.format("%.1f", values.maxOrNull())}\n")
        }

        // Exercise
        output.append("\n--- EXERCISE (7 days) ---\n")
        val exercises = readExercise(daysBack = 7)
        if (exercises.isEmpty()) {
            output.append("No exercise sessions\n")
        } else {
            output.append("Sessions: ${exercises.size}\n")
            exercises.take(3).forEach { ex ->
                val startStr = dateFormatter.format(Date(ex.startTime))
                output.append("$startStr — ${ex.durationMinutes} min\n")
            }
        }

        // Energy Score
        output.append("\n--- ENERGY SCORE (7 days) ---\n")
        val scores = readEnergyScore(daysBack = 7)
        if (scores.isEmpty()) {
            output.append("No energy-score data\n")
        } else {
            output.append("Days: ${scores.size}\n")
            scores.take(3).forEach { s ->
                output.append("${dateFormatter.format(Date(s.timestamp))} → ${s.score}\n")
            }
        }

        // Epochs summary
        output.append("\n--- EPOCHS (8h) ---\n")
        val epochs = processDataIntoEpochs(hoursBack = 8)
        if (epochs.isEmpty()) {
            output.append("No epochs processed\n")
        } else {
            output.append("Total: ${epochs.size} epochs\n")
            val avgHR = epochs.map { it.hrMean }.average()
            val avgTemp = epochs.map { it.tempMean }.average()
            output.append("Avg HR: ${String.format("%.1f", avgHR)} bpm\n")
            output.append("Avg Temp: ${String.format("%.1f", avgTemp)}°C\n")
        }

        return output.toString()
    }

    /**
     * Convert epoch to TFLiteSleepPredictor.EpochFeatures for prediction
     */
    fun epochToFeatures(epoch: SleepEpoch): TFLiteSleepPredictor.EpochFeatures {
        return TFLiteSleepPredictor.EpochFeatures(
            hrMean = epoch.hrMean,
            hrStd = epoch.hrStd,
            hrMin = epoch.hrMin,
            hrMax = epoch.hrMax,
            hrRange = epoch.hrRange,
            hrCv = epoch.hrCv,
            hrMedian = epoch.hrMedian,
            hrIqr = epoch.hrIqr,
            hrSkew = epoch.hrSkew,
            tempMean = epoch.tempMean,
            tempStd = epoch.tempStd,
            tempTrend = epoch.tempTrend
        )
    }
}

// ==================== EXTENSION FUNCTIONS ====================

private fun List<Float>.std(): Float {
    if (size < 2) return 0f
    val mean = average()
    val variance = map { (it - mean).pow(2) }.average()
    return sqrt(variance).toFloat()
}

private fun List<Float>.median(): Float {
    if (isEmpty()) return 0f
    val sorted = sorted()
    return if (size % 2 == 0) {
        (sorted[size / 2 - 1] + sorted[size / 2]) / 2
    } else {
        sorted[size / 2]
    }
}

private fun List<Float>.iqr(): Float {
    if (size < 4) return 0f
    val sorted = sorted()
    val q1Idx = size / 4
    val q3Idx = (3 * size) / 4
    return sorted[q3Idx] - sorted[q1Idx]
}

private fun List<Float>.skewness(): Float {
    if (size < 3) return 0f
    val mean = average().toFloat()
    val std = std()
    if (std == 0f) return 0f

    val n = size.toFloat()
    val skew = map { ((it - mean) / std).pow(3) }.sum() * (n / ((n - 1) * (n - 2)))
    return skew
}
