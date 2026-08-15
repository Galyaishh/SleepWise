package com.example.sleepwisepoc.wear

import android.util.Log
import com.example.sleepwisepoc.service.SessionLog
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives HR batches from the watch and forwards them into [WearHrSource].
 * Registered in the AndroidManifest with an intent filter on /sleepwise/hr.
 *
 * Logs every batch to SessionLog so we can verify in tomorrow morning's
 * pull whether the watch was streaming or silent during the night.
 */
class WearMessageListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.PATH_HR_BATCH -> {
                val samples = WearProtocol.decodeBatch(event.data)
                if (samples.isEmpty()) return
                WearHrSource.appendBatch(samples)
                SessionLog.log(
                    this,
                    "WEAR_HR_BATCH received=${samples.size} bufferNow=${WearHrSource.bufferSize()} " +
                            "lagMin=${WearHrSource.lagMillis() / 60_000}"
                )
                // Event-driven prediction: a fresh HR batch is the trigger for a
                // prediction pass. This delivery is OS-pushed so it pierces Doze,
                // unlike the old coroutine delay() loop that Doze froze.
                com.example.sleepwisepoc.service.SleepMonitoringService.instance
                    ?.onWearDataArrived("hr")
            }
            WearProtocol.PATH_ACCEL_BATCH -> {
                val samples = WearProtocol.decodeBatch(event.data)
                if (samples.isEmpty()) return
                WearAccelSource.appendBatch(samples)
                val events1min = WearAccelSource.movementEventsInWindow(60_000)
                SessionLog.log(
                    this,
                    "WEAR_ACCEL_BATCH received=${samples.size} bufferNow=${WearAccelSource.bufferSize()} " +
                            "movementEvents_last1m=$events1min"
                )
            }
            WearProtocol.PATH_TEMP_BATCH -> {
                val s = WearProtocol.decodeBatch(event.data)
                if (s.isEmpty()) return
                WearTempSource.appendBatch(s)
                val avg = s.map { it.second }.average()
                SessionLog.log(this, "WEAR_TEMP_BATCH received=${s.size} avgSkinTemp=${"%.2f".format(avg)}C")
            }
            WearProtocol.PATH_IBI_BATCH -> {
                val s = WearProtocol.decodeBatch(event.data)
                if (s.isEmpty()) return
                WearHrvSource.appendBatch(s)
                val avg = s.map { it.second }.average()
                val bpm = if (avg > 0) 60000.0 / avg else 0.0
                SessionLog.log(this, "WEAR_IBI_BATCH received=${s.size} avgIBI=${"%.0f".format(avg)}ms (~${"%.0f".format(bpm)}bpm)")
            }
            else -> Log.d(TAG, "ignored path=${event.path}")
        }
    }

    companion object {
        private const val TAG = "WearMessageListener"
    }
}
