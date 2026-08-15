package com.example.sleepwisepoc.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
//  NIGHTFOLD — SleepWise visual identity (design handoff)
//  Dark is primary; light is a warm paper theme. Depth comes from surface value
//  and hairlines, not shadows.
// ══════════════════════════════════════════════════════════════════════════════

// ─── Nightfold · dark ─────────────────────────────────────────────────────────
val NfDarkBg          = Color(0xFF0B1016)
val NfDarkSurface     = Color(0xFF131C25)
val NfDarkSurface2    = Color(0xFF1B2731)
val NfDarkRaise       = Color(0xFF23313D)
val NfDarkLine        = Color(0x1AF2EEE5) // rgba(242,238,229,.10)
val NfDarkLineStrong  = Color(0x2EF2EEE5) // rgba(242,238,229,.18)
val NfDarkText        = Color(0xFFF2EEE5)
val NfDarkDim         = Color(0xFF9CACB9)
val NfDarkFaint       = Color(0xFF6C7C89)
val NfDarkAccent      = Color(0xFFD9765A) // clay rose
val NfDarkAccentSoft  = Color(0x29D9765A) // rgba(217,118,90,.16)
val NfDarkOnAccent    = Color(0xFF2A0F0A)
val NfDarkGood        = Color(0xFF7FB69B)
val NfDarkStageAwake  = Color(0xFFDFA35F)
val NfDarkStageRem    = Color(0xFF8FB8D8)
val NfDarkStageLight  = Color(0xFF4E7E93)
val NfDarkStageDeep   = Color(0xFF2E5163)

// ─── Nightfold · light ────────────────────────────────────────────────────────
val NfLightBg         = Color(0xFFF4F1EA)
val NfLightSurface    = Color(0xFFFCFAF5)
val NfLightSurface2   = Color(0xFFEDE8DD)
val NfLightRaise      = Color(0xFFE3DCCE)
val NfLightLine       = Color(0x1A101922) // rgba(16,25,34,.10)
val NfLightLineStrong = Color(0x33101922) // rgba(16,25,34,.20)
val NfLightText       = Color(0xFF101922)
val NfLightDim        = Color(0xFF55636F)
val NfLightFaint      = Color(0xFF77848F)
val NfLightAccent     = Color(0xFFA2472F) // darkened for AA on light
val NfLightAccentSoft = Color(0x1AA2472F) // rgba(162,71,47,.10)
val NfLightOnAccent   = Color(0xFFFFF4F0)
val NfLightGood       = Color(0xFF3F7A5F)
val NfLightStageAwake = Color(0xFFB0703A)
val NfLightStageRem   = Color(0xFF4C7CA0)
val NfLightStageLight = Color(0xFF3B6B7E)
val NfLightStageDeep  = Color(0xFF23485A)

// ─── Semantic token bundle (read in composables via LocalSleepColors.current) ──
// Nightfold-native names are the source of truth; the legacy names (primary,
// textPrimary, textSecondary, border, primaryEnd, textAccent, success, warning)
// are kept as aliases so pre-redesign screens keep compiling during the migration.
data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val raise: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val good: Color,
    val stageAwake: Color,
    val stageRem: Color,
    val stageLight: Color,
    val stageDeep: Color,
) {
    // ── legacy aliases (do not use in new code) ──
    val border: Color get() = line
    val primary: Color get() = accent
    val primaryEnd: Color get() = accent
    val textPrimary: Color get() = text
    val textSecondary: Color get() = dim
    val textAccent: Color get() = accent
    val success: Color get() = good
    val warning: Color get() = stageAwake
}

val nightAppColors = AppColors(
    bg = NfDarkBg, surface = NfDarkSurface, surface2 = NfDarkSurface2, raise = NfDarkRaise,
    line = NfDarkLine, lineStrong = NfDarkLineStrong,
    text = NfDarkText, dim = NfDarkDim, faint = NfDarkFaint,
    accent = NfDarkAccent, accentSoft = NfDarkAccentSoft, onAccent = NfDarkOnAccent, good = NfDarkGood,
    stageAwake = NfDarkStageAwake, stageRem = NfDarkStageRem, stageLight = NfDarkStageLight, stageDeep = NfDarkStageDeep,
)

val dayAppColors = AppColors(
    bg = NfLightBg, surface = NfLightSurface, surface2 = NfLightSurface2, raise = NfLightRaise,
    line = NfLightLine, lineStrong = NfLightLineStrong,
    text = NfLightText, dim = NfLightDim, faint = NfLightFaint,
    accent = NfLightAccent, accentSoft = NfLightAccentSoft, onAccent = NfLightOnAccent, good = NfLightGood,
    stageAwake = NfLightStageAwake, stageRem = NfLightStageRem, stageLight = NfLightStageLight, stageDeep = NfLightStageDeep,
)

// ─── Legacy top-level color vals (referenced by not-yet-migrated screens) ──────
val SleepPrimary = NfDarkAccent
val SleepBackground = NfLightBg
val SleepSurface = NfLightSurface
val SleepOnBackground = NfLightText
val SleepTextSecondary = NfLightDim
val SleepError = Color(0xFFE08080)
val SleepSuccess = NfLightGood
val SleepAccentStart = NfDarkStageRem
val SleepAccentEnd = NfDarkAccent
val SleepInsightBg = NfLightSurface2
val SleepInsightText = NfLightAccent
val SleepDarkTextSecondary = NfDarkDim
val NightBg = NfDarkBg
val NightSurface = NfDarkSurface
val NightSurface2 = NfDarkSurface2
val NightBorder = NfDarkLineStrong
val NightPrimary = NfDarkAccent
val NightPrimaryEnd = NfDarkAccent
val NightTextPrimary = NfDarkText
val NightTextSecondary = NfDarkDim
val NightTextAccent = NfDarkAccent
val NightSuccess = NfDarkGood
val NightWarning = NfDarkStageAwake

// stage color aliases (dark) — used by the existing hypnogram until migrated
val SleepStageDeep = NfDarkStageDeep
val SleepStageLight = NfDarkStageLight
val SleepStageREM = NfDarkStageRem
val SleepStageWake = NfDarkStageAwake
val SleepStageDeepDark = NfDarkStageDeep
val SleepStageLightDark = NfDarkStageLight
val SleepStageREMDark = NfDarkStageRem
val SleepStageWakeDark = NfDarkStageAwake

// Material legacy
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
