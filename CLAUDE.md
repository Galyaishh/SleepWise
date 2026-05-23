# SleepWise Android — Handoff Brief

Auto-loaded by Claude Code. Read this first, then `memory/MEMORY.md` (see below).

## TL;DR (state at 2026-05-11 night)

- **This repo (`SleepWisenew`) = Android app.** Currently on branch `feature/updated_ui` (Gal's branch). Build passes (`./gradlew :app:assembleDebug`), no compile errors, three cosmetic deprecation warnings.
- **Sibling repo `~/PycharmProjects/SleepWise-Backend/` = FastAPI server.** Built by a parallel Claude (Track B). Has bearer-token auth + SQLAlchemy/Postgres + JSON logs + Dockerfile + railway.toml. Not yet deployed to Railway.
- **Project is no longer a POC** — the user reframed it as "production-grade, internal" on 2026-05-11 with ~3-4 days to ship. Final-project deadline driving everything.
- **Old `main` branch** of this repo has 12 unpushed commits from the same Claude session (Room DB + production loop + history viz). They're a parallel implementation that became redundant once `feature/updated_ui` came in with a more polished UI. Do not delete `main` — it's a safety net.

## The two repos at a glance

```
~/PycharmProjects/
├── SleepWisenew/         ← THIS REPO. Android app (Kotlin/Gradle).
│   └── feature/updated_ui   ← current branch. Auth + tabbed UI + alarm subsystem.
├── SleepWise-Backend/    ← FastAPI server. Tracked separately on GitHub.
│   └── server/{main,auth,db,logging_config}.py + Dockerfile + railway.toml
└── SleepWise/            ← OLD Python ML repo (training, TFLite conversion, notebooks).
                            Not actively touched in this sprint — model is already shipped
                            as app/src/main/assets/sleep_stage_model.tflite.
```

## Where the durable context lives

Read these in order:
1. `~/.claude/projects/-home-afik-s-PycharmProjects-SleepWisenew/memory/MEMORY.md` — index of all memory entries.
2. `production_rebuild_may_2026.md` — sprint scope decision, Railway hosting choice, two-Claude collaboration model, architecture decisions for the production loop.
3. `engineering_doc_commitments.md` — what the Jan 8 2026 מסמך הנדסי חלק ב commits to (edge-first, NFR3 offline alarm). Binding for grading.
4. `server_api_contract.md` — locked API contract between Android and backend. Don't drift.
5. `doc_change_log_entries.md` — Hebrew change-log rows to paste into טבלת ריכוז השינויים reconciling 7 doc-vs-code divergences.
6. `project_sleepwise_overview.md` + `todo_production.md` — high-level overview and migration status of the two POC limitations (auth, deploy).

## Architecture map of `feature/updated_ui` (this branch)

Top-level packages under `app/src/main/java/com/example/sleepwisepoc/`:

- `MainActivity.kt` — splash/onboarding/auth/setup routing then tabbed `MainScaffold` (Tonight / Sleep / Schedule / Profile). Off-main-thread SDK init.
- `auth/` — `SplashScreen`, `OnboardingScreen`, `AuthScreen`, `SetupWizardScreen`, `AuthViewModel`. The auth flow doesn't currently call the backend's `POST /devices/register` itself — that's done by `DeviceRepository.ensureRegistered()` (root-level file).
- `tonight/` — `TonightScreen` + `TonightViewModel`. Reads `SleepScheduleStore`, tomorrow's day-of-week decides weekday-vs-weekend window, "Start tracking" calls `SleepMonitoringService.start(context, windowStart, wakeTime)`.
- `schedule/` — `SleepSchedule` + `DaySchedule` data classes inline in `SleepScheduleStore.kt`. UI in `ScheduleScreen.kt` with weekday/weekend tabs and Material3 time picker.
- `alarm/` — `AlarmScheduler`, `AlarmReceiver`, `AlarmViewModel`, `AlarmWindowStore`, `SmartAlarmScreen`. **NOTE:** Manifest declares `.alarm.AlarmRingService` and `.alarm.AlarmRingingActivity` but those files don't exist. They're not referenced from code so the build succeeds, but firing the alarm will NPE at runtime. Stub them when alarm path needs to work.
- `service/SleepMonitoringService.kt` — foreground service, predict loop. Currently mock-epoch path; real HC integration is a TODO at the `acquireEpoch` boundary.
- `report/` — `SleepReportScreen` + hypnogram viz + `SleepMockData`.
- `profile/ProfileScreen.kt` — user profile.
- `DeviceRepository.kt` + `DeviceStore.kt` — bearer-token registration (`POST /devices/register`) + DataStore persistence.
- `SleepWiseApi.kt` — Retrofit client. Models: `SessionUpload`, `StageTick`, `SessionRecord`, `WeeklyReport`, `DeviceRegisterResponse`. Endpoints match `SleepWise-Backend/server/main.py`.
- `SamsungHealthManager.kt` — Samsung Health Data SDK reads (from `libs/samsung-health-data-api-1.0.0.aar`). This is how the watch data gets in.
- `TFLiteSleepPredictor.kt` — TFLite GRU + EMA + hysteresis. **This is the heart of the model.** Don't touch the smoothing parameters without a good reason — they were tuned (Tier 1 of the Jan 23 research backlog).
- `DemoScreen.kt` + `DemoNightSimulator.kt` — mock-data demo path. Still useful for screenshots.
- `HealthConnectManager.kt`, `PermissionsRationaleActivity.kt`, `SleepPredictor.kt` — present on this branch but DEAD CODE in practice. Health Connect was never wired (Samsung SDK is used directly); the rule-based `SleepPredictor` was the pre-TFLite stub.

## What's known to work

- Compile + assembleDebug + testDebugUnitTest all green.
- Auth flow renders.
- Tabbed nav.
- `TFLiteSleepPredictor` shipped Tier 1 smoothing on 2026-03-24 (EMA α=0.3, hysteresis 0.55/0.35).
- `DeviceRepository` flow: app→backend `/devices/register`→token in DataStore→OkHttp interceptor adds `Authorization: Bearer …`.
- Backend repo has Dockerfile + railway.toml ready.

## What's known broken or open

- **Alarm-ring runtime path** — `AlarmRingService` + `AlarmRingingActivity` declared in manifest, not implemented. The alarm subsystem otherwise compiles but can't actually ring.
- **No real device test yet** — NFR3 (alarm fires with Wi-Fi+cellular off) is binding from the engineering doc but hasn't been verified.
- **Backend not deployed to Railway** — Dockerfile and railway.toml exist; needs a `railway up` (or the GitHub-deploy click) and then update `ApiClient`'s `BASE_URL`. Watch the $5 free credit cap.
- **Android `BASE_URL` may still point at LAN IP** — verify in `SleepWiseApi.kt` before the demo.
- **`SleepMonitoringService.acquireEpoch` is mock-only** — real Samsung Health → 30-feature epoch path is a TODO at that boundary.
- **Doc reconciliation entries not yet pasted into the actual engineering doc** — they're drafted in `memory/doc_change_log_entries.md` but the Hebrew document file lives outside the repos (probably a Google Doc).

## What to do next (priority order)

1. **Stub `AlarmRingService` and `AlarmRingingActivity`** so the alarm path can fire. Even a 50-line stub that plays the default alarm tone and shows over the lock screen is enough.
2. **Deploy the backend.** Inside `~/PycharmProjects/SleepWise-Backend/`, run `railway up` (assuming the user has linked the project). Get the HTTPS URL, swap into `ApiClient.DEFAULT_BASE_URL`, drop `android:usesCleartextTraffic="true"` from the manifest.
3. **Wire the real-sensor `acquireEpoch` in `SleepMonitoringService`** — port the `samsung.processDataIntoEpochs(hoursBack = 1)` + `epochToFeatures(...)` calls.
4. **NFR3 device test** — plug in a real device, set a 5-minute fake window, airplane-mode it, confirm alarm fires.
5. **Push the doc change-log entries into the actual engineering doc** (the Hebrew file).

## Quick commands

```bash
# Build
JAVA_HOME=/snap/android-studio/current/jbr ANDROID_HOME=/home/afik.s/Android/Sdk \
  ./gradlew :app:assembleDebug

# Unit tests
JAVA_HOME=/snap/android-studio/current/jbr ANDROID_HOME=/home/afik.s/Android/Sdk \
  ./gradlew :app:testDebugUnitTest

# Connected device install (when device is plugged in)
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# See what's pending on the OLD main branch (safety net of an earlier rebuild)
git log --oneline origin/main..main
```

## Conventions

- Off-main-thread SDK init in `MainActivity.onCreate` is intentional — Samsung Health SDK on emulator crashes `system_server`. The `isEmulator` guard is load-bearing.
- Health Connect permissions in the manifest exist but the app uses Samsung Health Data SDK directly. Don't be tempted to delete those manifest entries without checking nothing else in the team's checked-out code depends on them.
- Cross-midnight wake windows are NOT supported (e.g., 23:30→06:00). `DaySchedule.windowStart` is derived as `wakeTime - windowMinutes` and the slider only goes 15–60 minutes, so this is fine by construction.
- The user (`afik.s@razor-labs.com`) is on the Android side. Their partner `Gal` is on this same repo via GitHub. Be careful about pushing to `main` — open a branch.
