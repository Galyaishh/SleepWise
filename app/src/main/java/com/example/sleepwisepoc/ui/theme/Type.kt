@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.sleepwisepoc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.R

// ══════════════════════════════════════════════════════════════════════════════
//  NIGHTFOLD typography — three families, bundled as res/font TTFs.
//  · Instrument Serif  — display: all times, scores, big headings (tabular nums)
//  · IBM Plex Sans     — all UI text and body
//  · IBM Plex Mono     — labels, axis ticks, metadata, uppercase eyebrows
// ══════════════════════════════════════════════════════════════════════════════

val InstrumentSerif = FontFamily(Font(R.font.instrument_serif, FontWeight.Normal))

// IBM Plex Sans ships from Google Fonts as a single variable font; derive the
// weights we use via the wght variation axis (minSdk 29 → FontVariation is fine).
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.ibm_plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

// Material typography — bodies/labels use Plex Sans, display roles use the serif.
val Typography = Typography(
    displayLarge   = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp),
    displaySmall   = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineLarge  = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall  = TextStyle(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge     = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium    = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall     = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge      = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium     = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall      = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge     = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium    = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 2.4.sp),
    labelSmall     = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
)
