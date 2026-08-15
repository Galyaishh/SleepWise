# Test-Night Preparation — 4-Signal Alarm (Stage 3)

Validation run for branch **`stage3-4signal-alarm`** before merging to `main`.
Goal: confirm the alarm now feeds **real skin-temp + HRV** into the model end-to-end
on the real watch, and behaves correctly — without damaging the working alarm.

`main` is untouched. If anything looks wrong, we simply don't merge.

---

## What changed (what we're actually testing)

| Before | Now |
|---|---|
| 32-feature model, **temp hardcoded to 34 °C**, no HRV | **45-feature model** using HR + accel + **real skin-temp + HRV** |
| 1 dataset (Walch), 2 signals | 3 datasets (Walch+DREAMT+Wearanize+), 3 devices, 120 subjects |
| temp/IBI batches only logged | temp/IBI **buffered + turned into features** (`WearTempSource`/`WearHrvSource`) |

The model was chosen so this does **not** hurt deep-sleep detection (33% vs 35% precision —
within noise; the healthy Wearanize+ cohort closed the gap). Feature parity vs the Python
training pipeline is already verified (max diff ≤ 3.8e-6). This night tests the **live plumbing**.

---

## Pre-flight (before bed)

**1. Build + install the branch**
```
export JAVA_HOME=/snap/android-studio/235/jbr
cd ~/PycharmProjects/SleepWisenew
git checkout stage3-4signal-alarm
./gradlew :app:installDebug        # phone connected via adb
# watch app is unchanged this round — reinstall only if you changed the wear module
```

**2. Watch prerequisites (the temp/HRV source)**
- [ ] Watch on the **wrist**, snug (temp + HRV need skin contact — off-body = no data).
- [ ] **Health Platform developer mode ON** (the toggle from last session) — required for
      Samsung skin-temp + IBI. Without it you'll see HR+accel only (masks=0), which is a
      valid fallback but NOT what we're testing.
- [ ] Open the **watch** app, then start tracking from the **phone** as usual.

**3. Confirm all four streams are live (2-min sanity check)**
Watch the phone log for these four markers within ~2 minutes of wearing it:
```
adb -s <phone> logcat -s SessionLog | grep -E "WEAR_(HR|ACCEL|TEMP|IBI)_BATCH"
```
- [ ] `WEAR_HR_BATCH`     (heart rate)
- [ ] `WEAR_ACCEL_BATCH`  (movement)
- [ ] `WEAR_TEMP_BATCH … avgSkinTemp=~35C`   ← the real temp (not 34.0)
- [ ] `WEAR_IBI_BATCH … avgIBI=…ms`          ← HRV source

If temp/IBI are missing after ~2 min: dev-mode is off or the watch is off-body. Fix before sleeping.

---

## During the night — what "correct" looks like

Every prediction pass logs a per-epoch line:
```
epoch 03:14 hr=…smp stage=Light conf=0.72 stable=true
```
- [ ] Predictions appear once ~5 epochs have accumulated (warm-up).
- [ ] Stages look plausible over the night (mostly Light/Deep transitions, not stuck on one).
- [ ] No repeated `Inference failed` / `Not enough epochs` errors after warm-up.
- [ ] The smart alarm fires in the wake window on a stable **Light** reading (or the fallback
      fires at window end) — same behavior as before.

---

## Morning — go / no-go for merge

**PASS (merge `stage3-4signal-alarm` → `main`) if all hold:**
- [ ] All 4 batch types were present for most of the night (temp/IBI may drop briefly — OK).
- [ ] Per-epoch predictions ran the whole night with no error spam.
- [ ] The alarm fired appropriately (smart or fallback) — you woke up.
- [ ] Sanity: the stage track over the night looks reasonable (spot-check in the app / log).

**HOLD (don't merge, tell me) if:**
- [ ] Temp/IBI never arrived (→ dev-mode / wrist issue, not a code issue) — retest.
- [ ] Prediction errors after warm-up, or stages stuck / obviously wrong.
- [ ] Alarm didn't fire at all (check the fallback path too).

---

## Notes / expectations (honest)
- **temp/HRV won't visibly change accuracy** — they don't add much to staging (that's a
  measured, cited finding). The point of this build is "the model *uses* the sensors we now
  collect, without hurting the alarm," which is the presentation story.
- If a stream drops mid-night, the model auto-falls back (masks=0) — that's by design.
- **Rollback is free**: it's a branch. `git checkout main && ./gradlew installDebug` restores
  the current live alarm instantly.

## After a PASS
```
git checkout main
git merge --no-ff stage3-4signal-alarm
git push origin main
```
Then the 4-signal alarm is live, and the "3 datasets / 3 devices / 4 signals" system is
fully deployed end-to-end.
