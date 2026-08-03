# SleepWise Android — Handoff Brief

Auto-loaded by Claude Code. Read this first.
**Last verified against actual code: 2026-08-02 (full audit of all 3 repos).**

## TL;DR — actual state as of 2026-08-02

- **Android app** (this repo, branch `feature/light-mode-fixes`): builds and runs. Full Wear OS streaming pipeline implemented. Alarm ring path fully implemented.
- **Backend** (`~/PycharmProjects/SleepWise-Backend/`): FastAPI, **deployed on Render** at `https://sleepwise-backend-8kvx.onrender.com/`. Custom bearer-token auth (not Firebase).
- **ML model** (`~/PycharmProjects/SleepWise-model/`): Dense Neural Network trained on Walch 2019. Artifacts already shipped to `app/src/main/assets/`.

## Repo map

```
~/PycharmProjects/
├── SleepWise/              ← THIS REPO. Android app + Wear OS app (Kotlin/Gradle).
├── SleepWise-Backend/      ← FastAPI server. Deployed on Render.
│   └── server/{main,auth,db,logging_config}.py + Dockerfile + railway.toml
└── SleepWise-model/        ← ML training (Python/TensorFlow).
    └── src/{build_final_model,features,load_sleepaccel,compare_datasets,...}.py
```

## Architecture (verified from code)

```
Galaxy Watch (Wear OS, minSdk=30)
  └── wear/HrStreamService.kt
        ├── ExerciseClient (WORKOUT type) → HEART_RATE_BPM continuous
        ├── SensorManager (TYPE_ACCELEROMETER, SENSOR_DELAY_NORMAL ~200ms)
        └── every 5s: MessageClient.sendMessage()
              /sleepwise/hr   → "ts,bpm;ts,bpm;..."
              /sleepwise/accel → "ts,|magnitude-9.81|;..."

Android Phone
  ├── wear/WearMessageListener.kt  (WearableListenerService, pathPrefix=/sleepwise)
  │     ├── WearHrSource.appendBatch()    (rolling 1h buffer)
  │     ├── WearAccelSource.appendBatch() (rolling buffer, g units)
  │     └── SleepMonitoringService.onWearDataArrived()
  │
  └── service/SleepMonitoringService.kt  (ForegroundService, dataSync)
        ├── Trigger A: every WearData push (event-driven)
        ├── Trigger B: AlarmManager backstop setExactAndAllowWhileIdle every 5min
        └── runPredictionPass():
              featuresFromWearSamples() → 32 features (ANDROID_ORDER)
              Samsung Health = FALLBACK only (secondary source)
              TFLiteSleepPredictor.addEpoch() → predict()
                Dense NN [Input(32)→Dense(64,ReLU)+Dropout→Dense(32,ReLU)+Dropout→Dense(2,Softmax)]
                EMA α=0.3 | Hysteresis TO_DEEP=0.55 / TO_LIGHT=0.35 | Stability=3 consecutive
              favorable = insideWindow AND epochAge<10min AND stage==Light AND isStable
              if favorable → AlarmScheduler → AlarmRingService → AlarmRingingActivity

Backend (Render — https://sleepwise-backend-8kvx.onrender.com)
  POST /devices/register  → {user_id (uuid4), token (random 32-byte)}
  POST /sessions          → upload night data (Bearer token)
  GET  /sessions/{id}/weekly → history
  SQLite/Postgres via SQLAlchemy 2.0
```

## File inventory (Android — app/src/main/java/com/example/sleepwisepoc/)

| File | Status | Role |
|---|---|---|
| `MainActivity.kt` | Active | Auth routing → MainScaffold (4 tabs). Off-thread SDK init. |
| `TFLiteSleepPredictor.kt` | Active | Dense NN inference + EMA + Hysteresis + Stability. `NUM_FEATURES=32`. |
| `SleepWiseApi.kt` + `ApiClient` | Active | Retrofit. `PROD_BASE_URL = https://sleepwise-backend-8kvx.onrender.com/`. Firebase ID token as Bearer (see auth note). |
| `SamsungHealthManager.kt` | Active (fallback) | HR + temp reads. Secondary source only. |
| `service/SleepMonitoringService.kt` | Active | Core prediction loop (event-driven + backstop). |
| `service/SessionLog.kt` | Active | File-backed session logger. |
| `wear/WearHrSource.kt` | Active | Primary HR buffer (1h rolling). |
| `wear/WearAccelSource.kt` | Active | Accel buffer. acc_std + acc_move_ratio in g. MOVE_THRESH_G=0.02f. |
| `wear/WearMessageListener.kt` | Active | WearableListenerService. Receives HR/accel batches. |
| `wear/WearCommand.kt` | Active | Sends start/stop to watch. |
| `wear/WearProtocol.kt` | Active | Paths + codec. |
| `alarm/AlarmScheduler.kt` | Active | setExactAndAllowWhileIdle (REQUEST_CODE 42=alarm, 43=tick). |
| `alarm/AlarmReceiver.kt` | Active | BroadcastReceiver: ACTION_FIRE_ALARM → AlarmRingService; ACTION_TICK → triggerTick(). |
| `alarm/AlarmRingService.kt` | **Fully implemented** | Looping MediaPlayer + VibrationEffect + wake lock. Auto-release after 5min. |
| `alarm/AlarmRingingActivity.kt` | **Fully implemented** | Over-lock-screen dismiss UI. |
| `auth/` | Active | Firebase Auth (email+password + Google via CredentialManager). |
| `tonight/`, `schedule/`, `report/`, `profile/` | Active | UI tabs. |
| `ThemeStore.kt` | Active | Dark/light mode SharedPreferences. |
| `HealthConnectManager.kt` | **DEAD CODE** | Never called. |
| `SleepPredictor.kt` | **DEAD CODE** | Old rule-based stub. Never called. |
| `DemoScreen.kt`, `DemoNightSimulator.kt` | Demo only | Screenshot path. |
| `PermissionsRationaleActivity.kt` | Manifest-only | Health Connect rationale for system; not in production flow. |
| `DeviceRepository.kt`, `DeviceStore.kt` | **DO NOT EXIST** | Mentioned in old docs. Files not present in repo. |

## Wear OS module (wear/)

- `wear/MainActivity.kt` — minimal launcher, requests BODY_SENSORS.
- `wear/HrStreamService.kt` — ExerciseClient + SensorManager + 5s MessageClient flush.
- `wear/PhoneCommandListener.kt` — WearableListenerService. /cmd/start → start HrStreamService.
- `wear/WearProtocol.kt` — same codec as phone side.

## ML Model (SleepWise-model/src/)

**Deployed model produced by:** `build_final_model.py`

- **Dataset:** Walch 2019 sleep-accel (PhysioNet, 31 healthy subjects, Apple Watch + PSG). NOT DREAMT.
- **Architecture:** `Input(32) → Dense(64,ReLU) + Dropout(0.3) → Dense(32,ReLU) + Dropout(0.2) → Dense(2,Softmax)`. **No GRU. No LSTM. No recurrence.**
- **Label scheme:** `binary_n3` — Deep=N3 only; Light=W+N1+N2+REM.
- **CV:** Grouped 5-fold by participant_id. Deep Recall=83%, Precision=24%.
- **Class weights:** balanced + Deep×1.5.
- **Normalization:** StandardScaler params in `tflite_metadata.json`.
- **Artifacts:** `sleep_stage_model.tflite` + `tflite_metadata.json` → `app/src/main/assets/`.
- `train_accel_dense.py` — trains on DREAMT. **Not the deployed model.**
- `compare_datasets.py` — documents why Walch was chosen over DREAMT.

## Auth flow (important — has a mismatch)

- Backend auth: custom UUID token (`POST /devices/register` → `{user_id, token}`). Stored in `devices` table. **Not Firebase.**
- Android `ApiClient`: fetches Firebase ID token and injects as `Authorization: Bearer`.
- **These are different token types.** The bridge (`DeviceRepository.ensureRegistered()`) is missing from the repo. The auth flow may not work end-to-end without it. The `shouldWake` field in `SleepPrediction` is computed but never read by `SleepMonitoringService` (service has its own `favorable` logic).

## Known open issues (from code audit)

- `TFLiteSleepPredictor.kt` header comment still says "DREAMT" and "46 features" — wrong, needs fixing.
- `server/main.py` docstring says "TFLite GRU" — wrong.
- `android:usesCleartextTraffic="true"` still in manifest — should be removed (backend is HTTPS).
- `DeviceRepository` / auth bridge missing.
- `shouldWake` in `SleepPrediction` computed but unused.
- `WearAccelSource` has two thresholds in two units (`MOVEMENT_THRESHOLD_M_S2=0.5` and `MOVE_THRESH_G=0.02`) for different purposes — not a bug but confusing.

## Quick commands

```bash
# Build
JAVA_HOME=/snap/android-studio/current/jbr ANDROID_HOME=/home/gal.yaish/Android/Sdk \
  ./gradlew :app:assembleDebug

# Install on device
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Backend is live at:
# https://sleepwise-backend-8kvx.onrender.com/
```

## Conventions

- Off-main-thread SDK init in `MainActivity.onCreate` is intentional — Samsung Health SDK on emulator crashes `system_server`. The `isEmulator` guard is load-bearing.
- Cross-midnight wake windows are NOT supported. `windowStart = wakeTime - windowMinutes`, slider 15–60 min only.
- Primary HR source = Wear OS (`WearHrSource`). Samsung Health = fallback. Do not assume Samsung is primary.
- The model is a **Dense NN** — stateless inference, no hidden state. Temporal context comes from lag/rolling features in the 32-feature vector, not recurrence.
