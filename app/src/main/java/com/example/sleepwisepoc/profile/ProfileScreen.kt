package com.example.sleepwisepoc.profile

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
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

        // My Devices
        SectionLabel("MY DEVICES")
        ProfileCard {
            DeviceRow(
                icon = Icons.Outlined.Watch,
                name = "Galaxy Watch 6",
                detail = "Connected",
                detailColor = NightSuccess,
                badge = "78%",
            )
        }

        Spacer(Modifier.height(16.dp))

        // Integrations
        SectionLabel("INTEGRATIONS")
        ProfileCard {
            ProfileRow(icon = Icons.Outlined.Apps, label = "Spotify", detail = "Connect")
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            ProfileRow(icon = Icons.Outlined.Lock, label = "Permissions", detail = "Manage")
        }

        Spacer(Modifier.height(16.dp))

        // Settings
        SectionLabel("SETTINGS")
        ProfileCard {
            ProfileRow(icon = Icons.Outlined.Settings, label = "App settings")
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            ProfileRow(icon = Icons.Outlined.Help, label = "Help & Support")
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
            ProfileRow(icon = Icons.Outlined.Info, label = "About SleepWise", detail = "v1.0")
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
    detailColor: androidx.compose.ui.graphics.Color = NightTextAccent,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        if (badge != null) {
            Text(badge, fontSize = 13.sp, color = NightTextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = NightTextAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = NightTextPrimary, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(detail, fontSize = 13.sp, color = NightTextSecondary)
        }
    }
}
