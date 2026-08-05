# SleepWise — Android & Wear OS App

Smart sleep alarm that monitors sleep stages in real-time using a Galaxy Watch and wakes the user during **light sleep** within a chosen wake-up window.

> **Related repositories:**
> - [SleepWise-Backend](https://github.com/Galyaishh/SleepWise-Backend) — FastAPI server (deployed on Render)
> - [SleepWise-Model](https://github.com/afiksoco/SleepWise) — TFLite model training (Walch 2019 dataset)

---

## System Architecture

```
Galaxy Watch (Wear OS)
  └── HrStreamService
        ├── ExerciseClient → continuous HR (beats/min)
        ├── SensorManager → accelerometer (200 ms)
        └── every 5 s → MessageClient.sendMessage()

Android Phone
  ├── WearMessageListener  ← receives HR/accel batches (event-driven)
  ├── WearHrSource / WearAccelSource  ← rolling 1-hour buffers
  └── SleepMonitoringService
        ├── featuresFromWearSamples() → 32-feature vector
        ├── TFLiteSleepPredictor → Dense NN inference
        │     EMA α=0.3 · Hysteresis 0.55/0.35 · Stability gate (3 epochs)
        └── favorable? → AlarmScheduler → AlarmRingService → AlarmRingingActivity
```

---

## Key Code Sections

| # | File | Lines | Description |
|---|------|-------|-------------|
| 1 | [`TFLiteSleepPredictor.kt`](app/src/main/java/com/example/sleepwisepoc/TFLiteSleepPredictor.kt#L34-L42) | L34–42 | **EMA & Hysteresis constants** — `EMA_ALPHA=0.3`, `THRESHOLD_TO_DEEP=0.55`, `THRESHOLD_TO_LIGHT=0.35` and stability window |
| 2 | [`TFLiteSleepPredictor.kt`](app/src/main/java/com/example/sleepwisepoc/TFLiteSleepPredictor.kt#L295-L390) | L295–390 | **`predict()`** — runs TFLite inference, applies EMA smoothing, applies hysteresis state machine, returns `SleepPrediction` with stability flag |
| 3 | [`SleepMonitoringService.kt`](app/src/main/java/com/example/sleepwisepoc/service/SleepMonitoringService.kt#L261-L350) | L261–350 | **`runPredictionPass()`** — core prediction loop triggered by Wear data or AlarmManager backstop every 5 min |
| 4 | [`SleepMonitoringService.kt`](app/src/main/java/com/example/sleepwisepoc/service/SleepMonitoringService.kt#L344-L346) | L344–346 | **Favorable condition** — `insideWindow && fresh (< 10 min) && stage == Light && isStable` → fires alarm |
| 5 | [`SleepMonitoringService.kt`](app/src/main/java/com/example/sleepwisepoc/service/SleepMonitoringService.kt#L434) | L434–end | **`featuresFromWearSamples()`** — extracts 32-feature vector (9 HR statistics, accelerometer stats, temporal lag/rolling features) from live Wear OS sensor data |
| 6 | [`WearMessageListener.kt`](app/src/main/java/com/example/sleepwisepoc/wear/WearMessageListener.kt#L17) | L17–51 | **Event-driven trigger** — `WearableListenerService` receives `/sleepwise/hr` and `/sleepwise/accel` messages, calls `onWearDataArrived()` immediately (bypasses Android Doze) |
| 7 | [`HrStreamService.kt`](wear/src/main/java/com/example/sleepwisepoc/wear/HrStreamService.kt#L191) | L191–230 | **Watch-side HR streaming** — `ExerciseClient.startExerciseAsync(WORKOUT)` chosen over `MeasureClient` (cancels on inactivity) and `ChannelClient` (higher overhead) |
| 8 | [`AlarmRingService.kt`](app/src/main/java/com/example/sleepwisepoc/alarm/AlarmRingService.kt#L65) | L65–160 | **Smart alarm ring** — looping `MediaPlayer` + `VibrationEffect.createWaveform` + `SCREEN_BRIGHT_WAKE_LOCK` with 5-minute auto-release |

---

## Build & Install

```bash
# Build debug APK
JAVA_HOME=/snap/android-studio/current/jbr \
ANDROID_HOME=/home/gal.yaish/Android/Sdk \
  ./gradlew :app:assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** Android SDK 34, Gradle 8+, Galaxy Watch with Wear OS (minSdk 30), Samsung Health SDK.

---

## ML Model

- **Architecture:** `Input(32) → Dense(64, ReLU) + Dropout(0.3) → Dense(32, ReLU) + Dropout(0.2) → Dense(2, Softmax)`
- **Dataset:** Walch 2019 sleep-accel (PhysioNet, 31 subjects, Apple Watch + PSG)
- **Performance:** Deep sleep recall 83%, precision 24% (grouped 5-fold cross-validation)
- **Artifacts:** `sleep_stage_model.tflite` + `tflite_metadata.json` in `app/src/main/assets/`
