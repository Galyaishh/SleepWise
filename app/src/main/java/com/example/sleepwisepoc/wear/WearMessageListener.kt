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
            else -> Log.d(TAG, "ignored path=${event.path}")
        }
    }

    companion object {
        private const val TAG = "WearMessageListener"
    }
}
