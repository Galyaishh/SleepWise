package com.example.sleepwisepoc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.sleepwisepoc.auth.AppRoute
import com.example.sleepwisepoc.auth.AuthScreen
import com.example.sleepwisepoc.auth.AuthViewModel
import com.example.sleepwisepoc.auth.OnboardingScreen
import com.example.sleepwisepoc.auth.SetupWizardScreen
import com.example.sleepwisepoc.auth.SplashScreen
import com.example.sleepwisepoc.profile.ProfileScreen
import com.example.sleepwisepoc.report.SleepReportScreen
import com.example.sleepwisepoc.schedule.ScheduleScreen
import com.example.sleepwisepoc.tonight.TonightScreen
import com.example.sleepwisepoc.ui.theme.NightBg
import com.example.sleepwisepoc.ui.theme.NightBorder
import com.example.sleepwisepoc.ui.theme.NightPrimary
import com.example.sleepwisepoc.ui.theme.NightSurface
import com.example.sleepwisepoc.ui.theme.NightTextSecondary
import com.example.sleepwisepoc.ui.theme.SleepWisePOCTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {

    private var samsungHealthManager: SamsungHealthManager? = null
    private var tfLitePredictor: TFLiteSleepPredictor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Heavy SDK init off the main thread — avoids blocking setContent and causing ANR
        lifecycleScope.launch(Dispatchers.IO) {
            val isEmulator = android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                android.os.Build.MODEL.contains("emulator", ignoreCase = true) ||
                android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                android.os.Build.HARDWARE.contains("goldfish", ignoreCase = true)

            // Samsung Health SDK crashes system_server on emulator (no Samsung Health service)
            if (!isEmulator) {
                samsungHealthManager = SamsungHealthManager(this@MainActivity)
                val sdkOk = samsungHealthManager?.initialize() == true
                if (sdkOk) {
                    val granted = samsungHealthManager?.checkAndRequestPermissions(this@MainActivity) == true
                    Log.d("MainActivity", "Samsung Health permissions granted=$granted")
                }
            }
            tfLitePredictor = TFLiteSleepPredictor(this@MainActivity)
            val tfliteInitialized = tfLitePredictor?.initialize() ?: false
            Log.d("MainActivity", "isEmulator=$isEmulator, TFLite: $tfliteInitialized")
        }

        // Runtime permissions: POST_NOTIFICATIONS (Android 13+) for the smart-alarm
        // notification, BLUETOOTH_CONNECT (Android 12+) for the watch-paired check.
        val toRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            toRequest += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            toRequest += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 100)
        }

        setContent {
            SleepWisePOCTheme {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.state.collectAsState()

                when (authState.screen) {
                    AppRoute.SPLASH     -> SplashScreen()
                    AppRoute.ONBOARDING -> OnboardingScreen(onComplete = authViewModel::onOnboardingComplete)
                    AppRoute.AUTH       -> AuthScreen(viewModel = authViewModel)
                    AppRoute.SETUP      -> SetupWizardScreen(onComplete = authViewModel::onSetupComplete)
                    AppRoute.MAIN       -> MainScaffold(onSignOut = authViewModel::signOut)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tfLitePredictor?.close()
    }
}

// ─── Main app scaffold (after auth) ──────────────────────────────────────────

@Composable
private fun MainScaffold(onSignOut: () -> Unit = {}) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NightBg,
        bottomBar = {
            SleepWiseNavBar(
                selectedTab   = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> TonightScreen(modifier = Modifier.padding(innerPadding))
            1 -> SleepReportScreen(modifier = Modifier.padding(innerPadding))
            2 -> ScheduleScreen(modifier = Modifier.padding(innerPadding))
            3 -> ProfileScreen(modifier = Modifier.padding(innerPadding), onSignOut = onSignOut)
        }
    }
}

// ─── Bottom navigation bar ────────────────────────────────────────────────────

@Composable
private fun SleepWiseNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = NightSurface,
        tonalElevation = 0.dp,
    ) {
        data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
        val items = listOf(
            NavItem("Tonight",  Icons.Outlined.Home),
            NavItem("Sleep",    Icons.Outlined.Bedtime),
            NavItem("Schedule", Icons.Outlined.CalendarMonth),
            NavItem("Profile",  Icons.Outlined.Person),
        )
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected        = selectedTab == index,
                onClick         = { onTabSelected(index) },
                icon            = { Icon(item.icon, contentDescription = item.label) },
                label           = { Text(item.label) },
                alwaysShowLabel = true,
                colors          = NavigationBarItemDefaults.colors(
                    selectedIconColor   = NightPrimary,
                    selectedTextColor   = NightPrimary,
                    unselectedIconColor = NightTextSecondary,
                    unselectedTextColor = NightTextSecondary,
                    indicatorColor      = NightSurface,
                ),
            )
        }
    }
}

