package com.example.sleepwisepoc.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepwisepoc.ThemeStore
import com.example.sleepwisepoc.ui.theme.LocalSleepColors
import com.google.firebase.auth.FirebaseAuth

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

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(ThemeStore.isDark(context)) }

    var isWatchConnected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isWatchConnected = try {
            Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
        } catch (_: Exception) { false }
    }
    val deviceDetail = if (isWatchConnected) "Connected" else "Not connected"
    val deviceColor  = if (isWatchConnected) c.success else c.textSecondary

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Support", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text("SleepWise uses your Galaxy Watch sensors to detect the optimal wake moment within your set window.", fontSize = 14.sp, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("For support, contact:", fontSize = 14.sp, color = c.textSecondary)
                    Text("galyaish10@gmail.com", fontSize = 14.sp, color = c.primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:galyaish10@gmail.com"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK", color = c.primary)
                }
            },
            containerColor = c.surface,
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to sign out?", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut()
                }) {
                    Text("Sign Out", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
            containerColor = c.surface,
        )
    }

    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            title = { Text("Connect Galaxy Watch", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Choose an option to set up your Galaxy Watch.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showConnectDialog = false
                    val intent = context.packageManager
                        .getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
                        ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Pair with Galaxy Watch", color = c.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConnectDialog = false
                    // The watch companion isn't on the Play Store yet, so a market://
                    // link would dead-end on "item not found". Until it's published,
                    // show a friendly note instead. (When published, restore the
                    // market://details?id=<package> intent here for one-tap install.)
                    Toast.makeText(
                        context,
                        "The watch app is coming to the Play Store. For now it's installed directly from Android Studio.",
                        Toast.LENGTH_LONG,
                    ).show()
                }) {
                    Text("Install SleepWise on watch", color = c.textSecondary)
                }
            },
            containerColor = c.surface,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Profile", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text("Everything connected", fontSize = 14.sp, color = c.textSecondary)
        }

        Spacer(Modifier.height(24.dp))

        // User identity card
        ProfileCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarLetter,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: "Signed in",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                    )
                    if (email != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = email, fontSize = 13.sp, color = c.textSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // My Devices
        SectionLabel("MY DEVICES")
        ProfileCard {
            DeviceRow(
                icon        = Icons.Outlined.Watch,
                name        = "Galaxy Watch",
                detail      = deviceDetail,
                detailColor = deviceColor,
                onClick     = if (!isWatchConnected) ({ showConnectDialog = true }) else null,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Integrations
        SectionLabel("INTEGRATIONS")
        ProfileCard {
            ProfileRow(
                icon = Icons.Outlined.Lock,
                label = "Permissions",
                detail = "Manage",
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Settings
        SectionLabel("SETTINGS")
        ProfileCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Apps, null, tint = c.textAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text("Dark mode", fontSize = 15.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = isDark,
                    onCheckedChange = {
                        isDark = it
                        onToggleTheme()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.textPrimary,
                        checkedTrackColor = c.primary,
                    ),
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            ProfileRow(
                icon = Icons.Outlined.Help,
                label = "Help & Support",
                onClick = { showHelpDialog = true },
            )
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            ProfileRow(icon = Icons.Outlined.Info, label = "About SleepWise", detail = "v1.0")
        }

        Spacer(Modifier.height(16.dp))

        // Sign out
        ProfileCard {
            ProfileRow(
                icon       = Icons.Outlined.Logout,
                label      = "Sign Out",
                labelColor = Color(0xFFFF6B6B),
                iconTint   = Color(0xFFFF6B6B),
                onClick    = { showSignOutDialog = true },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalSleepColors.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.textSecondary,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSleepColors.current
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface),
        content = content,
    )
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    detail: String,
    detailColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalSleepColors.current
    val resolvedDetailColor = detailColor ?: c.textAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.textAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = resolvedDetailColor)
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    labelColor: Color? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalSleepColors.current
    val resolvedLabelColor = labelColor ?: c.textPrimary
    val resolvedIconTint = iconTint ?: c.textAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = resolvedIconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = resolvedLabelColor, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(detail, fontSize = 13.sp, color = c.textSecondary)
        }
    }
}
