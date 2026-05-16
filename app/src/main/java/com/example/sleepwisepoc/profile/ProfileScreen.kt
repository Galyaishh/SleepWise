package com.example.sleepwisepoc.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightBorder
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightSuccess
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightSurface2
import com.example.sleepwisepoc.ui.theme.NightTextAccent
import com.example.sleepwisepoc.ui.theme.NightTextPrimary
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import com.google.firebase.auth.FirebaseAuth

private fun isEmulator(): Boolean =
    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
    Build.MODEL.contains("emulator", ignoreCase = true) ||
    Build.MODEL.contains("sdk", ignoreCase = true) ||
    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
    Build.HARDWARE.contains("goldfish", ignoreCase = true)

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
) {
    val context = LocalContext.current
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val displayName = user?.displayName?.trim()?.takeIf { it.isNotBlank() }
    val email = user?.email?.takeIf { it.isNotBlank() }
    val avatarLetter = (displayName ?: email)?.firstOrNull()?.uppercase()?.first()?.toString() ?: "?"

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val deviceConnected = !isEmulator()
    val deviceDetail = if (deviceConnected) "Connected" else "Not connected"
    val deviceColor = if (deviceConnected) NightSuccess else NightTextSecondary

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Support", color = NightTextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text("SleepWise uses your Galaxy Watch sensors to detect the optimal wake moment within your set window.", fontSize = 14.sp, color = NightTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("For support, contact:", fontSize = 14.sp, color = NightTextSecondary)
                    Text("galyaish10@gmail.com", fontSize = 14.sp, color = NightPrimary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:galyaish10@gmail.com"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK", color = NightPrimary)
                }
            },
            containerColor = NightSurface,
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", color = NightTextPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to sign out?", color = NightTextSecondary) },
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
                    Text("Cancel", color = NightTextSecondary)
                }
            },
            containerColor = NightSurface,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Profile", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = NightTextPrimary)
            Spacer(Modifier.height(3.dp))
            Text("Everything connected", fontSize = 14.sp, color = NightTextSecondary)
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
                        .background(NightPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarLetter,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NightTextPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: "Signed in",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NightTextPrimary,
                    )
                    if (email != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = email, fontSize = 13.sp, color = NightTextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // My Devices
        SectionLabel("MY DEVICES")
        ProfileCard {
            DeviceRow(
                icon = Icons.Outlined.Watch,
                name = "Galaxy Watch",
                detail = deviceDetail,
                detailColor = deviceColor,
                onClick = if (!deviceConnected) ({
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) else null,
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
            ProfileRow(
                icon = Icons.Outlined.Help,
                label = "Help & Support",
                onClick = { showHelpDialog = true },
            )
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            ProfileRow(icon = Icons.Outlined.Info, label = "About SleepWise", detail = "v1.0")
        }

        Spacer(Modifier.height(16.dp))

        // Sign out
        ProfileCard {
            ProfileRow(
                icon = Icons.Outlined.Logout,
                label = "Sign Out",
                labelColor = Color(0xFFFF6B6B),
                iconTint = Color(0xFFFF6B6B),
                onClick = { showSignOutDialog = true },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = NightTextSecondary,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface),
        content = content,
    )
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    detail: String,
    detailColor: Color = NightTextAccent,
    onClick: (() -> Unit)? = null,
) {
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
                .background(NightSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = NightTextAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, color = NightTextPrimary, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = detailColor)
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    labelColor: Color = NightTextPrimary,
    iconTint: Color = NightTextAccent,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = labelColor, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(detail, fontSize = 13.sp, color = NightTextSecondary)
        }
    }
}
