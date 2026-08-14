// ─── Merge these into wear/.../WearProtocol.kt (same object) ───────────────────
// New Wearable Data Layer paths for the two Stage-2 signals. Payloads reuse the
// existing "ts,value;ts,value;..." encoding, so the phone side reuses decodeBatch().

// Skin temperature in °C: "ts,tempC;ts,tempC;..."
const val PATH_TEMP_BATCH = "/sleepwise/temp"

// Inter-beat intervals in milliseconds: "ts,ibiMs;ts,ibiMs;..."
// (The phone computes HRV features per epoch from these — see HrvFeatures.kt.)
const val PATH_IBI_BATCH = "/sleepwise/ibi"

// encodeBatch()/decodeBatch() already handle these — no change needed.
