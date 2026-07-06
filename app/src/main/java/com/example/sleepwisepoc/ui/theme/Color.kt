package com.example.sleepwisepoc.ui.theme

import androidx.compose.ui.graphics.Color

// ─── SleepWise Brand Palette ──────────────────────────────────────────────────

// Primary: muted lavender-indigo
val SleepPrimary            = Color(0xFF6875C4)
val SleepPrimaryLight       = Color(0xFF9AAEE8)
val SleepPrimaryContainer   = Color(0xFFE8EAFF)
val SleepOnPrimary          = Color(0xFFFFFFFF)
val SleepOnPrimaryContainer = Color(0xFF1A2060)

// Secondary: soft purple
val SleepSecondary          = Color(0xFF9B84C8)
val SleepSecondaryContainer = Color(0xFFF0EAFF)
val SleepOnSecondaryContainer = Color(0xFF2A1060)

// Time-picker accent colors
val SleepAccentStart        = Color(0xFF6B9FCC)   // soft blue  — Earliest time
val SleepAccentEnd          = Color(0xFFAB84C8)   // soft purple — Latest time

// Backgrounds
val SleepBackground         = Color(0xFFF7F4EF)   // warm off-white / cream
val SleepSurface            = Color(0xFFFFFFFF)
val SleepSurfaceVariant     = Color(0xFFF2EFEA)   // slightly warm card tint

// Text
val SleepOnBackground       = Color(0xFF1A1A2E)   // deep navy
val SleepTextSecondary      = Color(0xFF6B7380)
val SleepTextTertiary       = Color(0xFF9CA3AF)

// Semantic
val SleepSuccess            = Color(0xFF5BAD8F)
val SleepError              = Color(0xFFE08080)

// Insight / info card
val SleepInsightBg          = Color(0xFFEEEFFB)
val SleepInsightText        = Color(0xFF4A4E8C)

// ─── Dark Mode ────────────────────────────────────────────────────────────────

val SleepDarkBackground         = Color(0xFF0D1017)
val SleepDarkSurface            = Color(0xFF141929)
val SleepDarkSurfaceVariant     = Color(0xFF1C2235)
val SleepDarkPrimary            = Color(0xFFA8B8EC)
val SleepDarkOnPrimary          = Color(0xFF1A2060)
val SleepDarkPrimaryContainer   = Color(0xFF2A3478)
val SleepDarkOnBackground       = Color(0xFFE4E1F4)
val SleepDarkTextSecondary      = Color(0xFF8B8FA8)
val SleepDarkInsightBg          = Color(0xFF1E2240)
val SleepDarkInsightText        = Color(0xFFA8AACC)

// ─── Sleep Stage Colors ───────────────────────────────────────────────────────

// Light mode
val SleepStageDeep  = Color(0xFF5B6FD6)   // rich indigo — deepest sleep
val SleepStageLight = Color(0xFF4A9EC4)   // sky blue — light NREM
val SleepStageREM   = Color(0xFF9375C4)   // soft violet — REM
val SleepStageWake  = Color(0xFFD4924A)   // warm amber — awake

// Dark mode (slightly lighter / more vivid for contrast on dark backgrounds)
val SleepStageDeepDark  = Color(0xFF7B8FE8)
val SleepStageLightDark = Color(0xFF68BDE0)
val SleepStageREMDark   = Color(0xFFB09ADE)
val SleepStageWakeDark  = Color(0xFFE8B06A)

// ─── Night palette — Tonight screen & global dark theme ──────────────────────

val NightBg            = Color(0xFF0A0C18)
val NightSurface       = Color(0xFF141829)
val NightSurface2      = Color(0xFF1C2242)
val NightBorder        = Color(0xFF252B4A)
val NightPrimary       = Color(0xFF7B6FE8)
val NightPrimaryEnd    = Color(0xFF4A8FD4)
val NightTextPrimary   = Color(0xFFFFFFFF)
val NightTextSecondary = Color(0xFF7B82A4)
val NightTextAccent    = Color(0xFF9DA8E8)
val NightSuccess       = Color(0xFF4ADE80)
val NightWarning       = Color(0xFFFBBF24)

// ─── Semantic app color tokens ────────────────────────────────────────────────

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val primary: Color,
    val primaryEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textAccent: Color,
    val success: Color,
    val warning: Color,
)

val nightAppColors = AppColors(
    bg            = NightBg,
    surface       = NightSurface,
    surface2      = NightSurface2,
    border        = NightBorder,
    primary       = NightPrimary,
    primaryEnd    = NightPrimaryEnd,
    textPrimary   = NightTextPrimary,
    textSecondary = NightTextSecondary,
    textAccent    = NightTextAccent,
    success       = NightSuccess,
    warning       = NightWarning,
)

val dayAppColors = AppColors(
    bg            = SleepBackground,
    surface       = SleepSurface,
    surface2      = SleepSurfaceVariant,
    border        = Color(0xFFDDDAF0),
    primary       = SleepPrimary,
    primaryEnd    = SleepAccentStart,
    textPrimary   = SleepOnBackground,
    textSecondary = SleepTextSecondary,
    textAccent    = SleepPrimary,
    success       = SleepSuccess,
    warning       = Color(0xFFD48B24),
)

// ─── Legacy (kept for backward compatibility with existing POC screens) ────────
val Purple80    = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80      = Color(0xFFEFB8C8)
val Purple40    = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40      = Color(0xFF7D5260)
