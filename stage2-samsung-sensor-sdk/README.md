# Stage 2 — Live skin temperature + HRV via Samsung Health Sensor SDK

Adds two **live, on-device** signals to the watch stream, alongside the existing
HR (ExerciseClient) + accel (SensorManager):

- **Skin temperature** (`SKIN_TEMPERATURE_CONTINUOUS`) → replaces the hardcoded
  `tempMean = 34.0f` constant with a real signal.
- **HRV** — computed on the phone from the **IBI (inter-beat interval) list** that
  the Samsung **`HEART_RATE_CONTINUOUS`** tracker returns at 1 Hz.

> These files live OUTSIDE the compiled source set on purpose — the Samsung SDK
> `.aar` is not in the repo, so compiling them now would break the build. Follow
> the steps below to bring them in once the SDK is added.

## Why the Samsung SDK (recap)
Google Health Services and `SensorManager` expose **only HR + accel** on a Galaxy
Watch5 Pro. Skin temperature and IBI/HRV are available **only** through the Samsung
Health Sensor SDK, and both stream **live on the watch** (this is NOT the
retrospective Samsung-Health/Health-Connect path). Real-time, 1 Hz.

## Prerequisites (you do these)
1. **Download the SDK**: developer.samsung.com/health/sensor → get the
   `samsung-health-sensor-api-*.aar` → drop into `wear/libs/`.
2. **Enable developer mode on the watch** (no partnership needed for your own device):
   Settings → Apps → **Health Sensor Service** → tap the title ~10× → enable
   *Developer mode*.
3. **Watch permission**: `BODY_SENSORS` (already requested for ExerciseClient).

## Integration steps
1. `wear/build.gradle.kts` → add:
   ```kotlin
   implementation(files("libs/samsung-health-sensor-api-1.3.0.aar"))
   // (match the filename you downloaded)
   ```
2. Merge the new message paths from `WearProtocol_additions.kt` into the real
   `wear/.../WearProtocol.kt`.
3. Copy `SamsungSensorTracker.kt` into `wear/src/main/java/com/example/sleepwisepoc/wear/`.
4. Copy `HrvFeatures.kt` into the **phone** app (`app/src/main/java/com/example/sleepwisepoc/`)
   — it's pure Kotlin, no SDK dependency; it turns an IBI list into HRV features.
5. Apply the edits in `INTEGRATION.md` to `HrStreamService.kt` (watch) and the
   phone-side listener + feature extractor.

## Honest caveats
- **Verify the exact `HealthTrackerType` / `ValueKey` names against the SDK version
  you download** — Samsung has renamed these across releases. The stubs use the
  common 1.x names and mark each spot with `// VERIFY`.
- Developer mode = **your own watch only**; public distribution needs a Samsung
  Partner Program approval.
- IBI quality degrades with wrist motion at night → `HrvFeatures` drops epochs
  whose bad-interval fraction exceeds a threshold (mirrors the literature's >25% rule).
- Extra continuous trackers add some battery/compute to the all-night session.
