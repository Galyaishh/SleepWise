package com.example.sleepwisepoc.profile

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.ThemeStore
import com.example.sleepwisepoc.ui.theme.*
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    val c = LocalSleepColors.current
    val context = LocalContext.current
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val displayName = user?.displayName?.trim()?.takeIf { it.isNotBlank() }
    val email = user?.email?.takeIf { it.isNotBlank() }
    val avatarLetter = (displayName ?: email)?.firstOrNull()?.uppercase()?.first()?.toString() ?: "?"
    val initials = remember(displayName, avatarLetter) {
        val n = displayName
        if (n != null) {
            val parts = n.split(" ").filter { it.isNotBlank() }
            when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> avatarLetter
            }
        } else avatarLetter
    }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(ThemeStore.isDark(context)) }
    var startQuietly by remember { mutableStateOf(true) }
    var alarmSound by remember { mutableStateOf("Dawn Chorus") }

    var isWatchConnected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isWatchConnected = try {
            Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
        } catch (_: Exception) { false }
    }

    // OS ringtone / sound picker — no in-app list of sounds by design.
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            alarmSound = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: alarmSound
        }
    }
    val openSoundPicker: () -> Unit = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        soundPicker.launch(intent)
    }

    // The watch companion isn't on the Play Store yet, so a market:// link would
    // dead-end. Until it's published, show a friendly note instead.
    val installNote: () -> Unit = {
        Toast.makeText(
            context,
            "The watch app is coming to the Play Store. For now it's installed directly from Android Studio.",
            Toast.LENGTH_LONG,
        ).show()
    }

    // ── Dialogs (behavior preserved from prior screen) ────────────────────────
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Support", color = c.text, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "SleepWise uses your watch sensors to detect the optimal wake moment within your set window.",
                        fontSize = 14.sp, fontFamily = PlexSans, color = c.dim,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("For support, contact:", fontSize = 14.sp, fontFamily = PlexSans, color = c.dim)
                    Text(
                        "galyaish10@gmail.com", fontSize = 14.sp, fontFamily = PlexSans, color = c.accent,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:galyaish10@gmail.com"))
                            context.startActivity(intent)
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("OK", color = c.accent, fontFamily = PlexSans) }
            },
            containerColor = c.surface,
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out", color = c.text, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to sign out?", color = c.dim, fontFamily = PlexSans) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut()
                }) { Text("Sign out", color = c.accent, fontFamily = PlexSans) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = c.dim, fontFamily = PlexSans) }
            },
            containerColor = c.surface,
        )
    }

    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Connect Galaxy Watch", color = c.text, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold) },
            text = { Text("Choose an option to set up your Galaxy Watch.", color = c.dim, fontFamily = PlexSans) },
            confirmButton = {
                TextButton(onClick = {
                    showConnectDialog = false
                    val intent = context.packageManager
                        .getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
                        ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                }) { Text("Pair with Galaxy Watch", color = c.accent, fontFamily = PlexSans) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConnectDialog = false
                    installNote()
                }) { Text("Install SleepWise on watch", color = c.dim, fontFamily = PlexSans) }
            },
            containerColor = c.surface,
        )
    }

    // ── Screen ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 26.dp)) {
            Eyebrow("YOU")
            Spacer(Modifier.height(18.dp))

            // Identity row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(c.surface2)
                        .border(1.dp, c.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, fontFamily = InstrumentSerif, fontSize = 24.sp, color = c.text)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName ?: "Signed in",
                        fontFamily = InstrumentSerif, fontSize = 26.sp, color = c.text,
                    )
                    if (email != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(email, fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // Watch card
            NfCard(radius = 22.dp, padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = if (isWatchConnected) c.good else c.faint)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isWatchConnected) "Galaxy Watch" else "No watch paired",
                            fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isWatchConnected) "Connected · 82% · synced 4m ago" else "Pair one to get smart wake-ups",
                            fontFamily = PlexSans, fontSize = 13.sp, color = c.dim,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    text = if (isWatchConnected) "Manage watch" else "Pair watch",
                    onClick = { showConnectDialog = true },
                )
            }

            Spacer(Modifier.height(18.dp))

            // Alarm sound — opens the OS sound library
            NfCard(radius = 22.dp, padding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 66.dp)
                        .clickable(onClick = openSoundPicker)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alarmSound, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                        Spacer(Modifier.height(2.dp))
                        Text("Chosen from your phone's sounds", fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
                    }
                    Chevron()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Opens your phone's sound library, so you can wake to anything you already have.",
                modifier = Modifier.padding(horizontal = 4.dp),
                fontFamily = PlexSans, fontSize = 13.sp, color = c.faint,
            )

            Spacer(Modifier.height(26.dp))

            // Setting rows
            Column {
                SettingToggleRow(
                    label = "Night theme",
                    desc = if (isDark) "On — easier on tired eyes" else "Off — using the light theme",
                    checked = isDark,
                    onCheckedChange = {
                        isDark = it
                        onToggleTheme()
                    },
                    topDivider = false,
                )
                SettingToggleRow(
                    label = "Start quietly",
                    desc = "The alarm begins soft and gets louder over 45 seconds",
                    checked = startQuietly,
                    onCheckedChange = { startQuietly = it },
                )
                ChevronSettingRow(
                    label = "Permissions",
                    desc = "Location, sensors and notifications",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
                ChevronSettingRow(
                    label = "Help & support",
                    desc = "How SleepWise works, and how to reach us",
                    onClick = { showHelpDialog = true },
                )
            }

            Spacer(Modifier.height(26.dp))

            // Get the watch app banner
            NfCard(radius = 22.dp, fill = c.accentSoft, borderColor = c.accent, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WatchGlyph()
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Get the watch app", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "It does the listening while you sleep. Free, 4 MB.",
                            fontFamily = PlexSans, fontSize = 13.sp, color = c.dim,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SmallPill("INSTALL", onClick = installNote, filled = true)
                }
            }

            Spacer(Modifier.height(26.dp))

            // Sign out (accent text button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSignOutDialog = true }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sign out", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = c.accent)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "SleepWise 2.4.1",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = PlexMono, fontSize = 11.sp, letterSpacing = 1.3.sp, color = c.faint,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(34.dp))
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

@Composable
private fun Chevron() {
    val c = LocalSleepColors.current
    Text("›", fontFamily = InstrumentSerif, fontSize = 24.sp, color = c.dim)
}

@Composable
private fun SettingRowFrame(topDivider: Boolean, content: @Composable () -> Unit) {
    val c = LocalSleepColors.current
    if (topDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    content()
}

@Composable
private fun SettingToggleRow(
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    topDivider: Boolean = true,
) {
    val c = LocalSleepColors.current
    SettingRowFrame(topDivider) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(label, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text(desc, fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
            }
            NfToggle(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ChevronSettingRow(
    label: String,
    desc: String,
    onClick: () -> Unit,
    topDivider: Boolean = true,
) {
    val c = LocalSleepColors.current
    SettingRowFrame(topDivider) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(label, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text(desc, fontFamily = PlexSans, fontSize = 13.sp, color = c.dim)
            }
            Chevron()
        }
    }
}

/** 46×58 outlined watch glyph, drawn from primitives (no assets). */
@Composable
private fun WatchGlyph() {
    val c = LocalSleepColors.current
    Canvas(modifier = Modifier.size(width = 46.dp, height = 58.dp)) {
        val stroke = 1.5.dp.toPx()
        val faceW = size.width
        val faceH = 40.dp.toPx()
        val faceTop = (size.height - faceH) / 2f
        val lugW = 20.dp.toPx()
        val lugH = 8.dp.toPx()
        val lugX = (faceW - lugW) / 2f
        // top lug
        drawRoundRect(
            color = c.accent, topLeft = Offset(lugX, faceTop - lugH + 2f),
            size = Size(lugW, lugH), cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = stroke),
        )
        // bottom lug
        drawRoundRect(
            color = c.accent, topLeft = Offset(lugX, faceTop + faceH - 2f),
            size = Size(lugW, lugH), cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = stroke),
        )
        // watch face
        drawRoundRect(
            color = c.accent, topLeft = Offset(0f, faceTop),
            size = Size(faceW, faceH), cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = stroke),
        )
        // crown
        drawRoundRect(
            color = c.accent, topLeft = Offset(faceW - 1.5.dp.toPx(), faceTop + faceH / 2f - 5.dp.toPx()),
            size = Size(3.dp.toPx(), 10.dp.toPx()), cornerRadius = CornerRadius(2f, 2f),
        )
    }
}
