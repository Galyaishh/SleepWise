# Stage 2 — exact edits to wire the two signals

## A) Watch — `wear/.../HrStreamService.kt`
1. Field:
   ```kotlin
   private var samsung: SamsungSensorTracker? = null
   ```
2. On session start (where you start ExerciseClient / register the accel listener):
   ```kotlin
   samsung = SamsungSensorTracker(this).also { it.start() }
   ```
3. In the 5-second flush loop (next to `flushBatch()` / `flushAccelBatch()`):
   ```kotlin
   samsung?.drainTemp()?.takeIf { it.isNotEmpty() }?.let {
       messageClient.sendMessage(node, WearProtocol.PATH_TEMP_BATCH, WearProtocol.encodeBatch(it))
   }
   samsung?.drainIbi()?.takeIf { it.isNotEmpty() }?.let {
       messageClient.sendMessage(node, WearProtocol.PATH_IBI_BATCH, WearProtocol.encodeBatch(it))
   }
   ```
4. On stop / `onDestroy` / `/sleepwise/cmd/stop`:
   ```kotlin
   samsung?.stop(); samsung = null
   ```

## B) Phone — `WearMessageListener`
Add two branches beside the existing HR/accel handling:
```kotlin
WearProtocol.PATH_TEMP_BATCH -> WearTempSource.addBatch(WearProtocol.decodeBatch(event.data))
WearProtocol.PATH_IBI_BATCH  -> WearIbiSource.addBatch(WearProtocol.decodeBatch(event.data))
```
Create `WearTempSource` and `WearIbiSource` as rolling buffers modeled on the
existing `WearHrSource` (keep the last ~1 h of `(timestampMs, value)`).

## C) Phone — feature extraction (`SleepMonitoringService.featuresFromWearSamples`)
- **Skin temp:** replace the hardcoded constants with the epoch's real values:
  ```kotlin
  val temps = WearTempSource.samplesInEpoch(epochStart, epochEnd)   // add this query
  tempMean = temps.averageOrNull() ?: 34.0f      // fall back to constant if none
  tempStd  = temps.stdOrNull() ?: 0.0f
  tempTrend = temps.trendOrNull() ?: 0.0f
  ```
- **HRV:** compute from the epoch's IBIs:
  ```kotlin
  val ibis = WearIbiSource.samplesInEpoch(epochStart, epochEnd).map { it.value }
  val hrv  = HrvFeatures.fromEpoch(ibis, totalBeats = ibis.size /* + rejected count if tracked */)
  // then append hrv.meanRr / sdnn / rmssd / pnn50 to the feature vector
  ```

## ⚠️ The model gate (important, honest)
Streaming temp + HRV is only half the job. **The model only benefits once it's
retrained on data that CONTAINS these signals:**
- **Walch 2019 has NO temperature and NO usable RR** → real temp/HRV fed to the
  current model land in slots that were trained on constants (≈ ignored).
- To actually use them, retrain (Stage 3) on a dataset with the signal:
  - **skin temp / IBI:** DREAMT (Empatica E4 has TEMP + IBI + BVP) — wrist-worn, ideal match.
  - **PPG-HRV:** MESA (needs the NSRR DUA).
- Until then, keep the **Stage-1 v2 binary model** as the shipped alarm; use Stage 2
  purely to **collect** temp+HRV on your own overnight runs so Stage 3 has real
  in-domain data to train/validate on.

## Suggested order once the SDK is in
1. Wire A+B, confirm temp + IBI batches arrive on the phone (log counts) — a good
   demo/validation on its own.
2. Record a few labeled nights (compare against Samsung staging as reference).
3. Stage 3: retrain including DREAMT (temp+IBI) → 3-class Wake/NREM/REM, keep
   binary Deep/Light as the alarm decision.
