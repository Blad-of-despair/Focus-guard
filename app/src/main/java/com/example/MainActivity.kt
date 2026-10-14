package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.local.SecurityManager
import com.example.ui.navigation.Screen
import com.example.ui.screens.AppLockActivity
import com.example.ui.screens.DetoxScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RestrictionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.FocusGuardTheme
import com.example.ui.viewmodel.FocusGuardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FocusGuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide recents screen content
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isPinSet by viewModel.isPinSet.collectAsState()
            val lockFocusGuard by viewModel.lockFocusGuard.collectAsState()

            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            // Check if app lock is required on resume/launch
            LaunchedEffect(isPinSet, lockFocusGuard) {
                if (isPinSet && lockFocusGuard && !SecurityManager.isSessionUnlocked) {
                    val lockIntent = Intent(this@MainActivity, AppLockActivity::class.java).apply {
                        putExtra(AppLockActivity.EXTRA_MODE, AppLockActivity.MODE_UNLOCK)
                    }
                    startActivity(lockIntent)
                }
            }

            FocusGuardTheme(darkTheme = darkTheme) {
                MainContent(
                    viewModel = viewModel,
                    onOpenPinSetup = {
                        val pinSetupIntent = Intent(this@MainActivity, AppLockActivity::class.java).apply {
                            putExtra(AppLockActivity.EXTRA_MODE, AppLockActivity.MODE_SETUP)
                        }
                        startActivity(pinSetupIntent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshStats()
    }
}

@Composable
fun MainContent(
    viewModel: FocusGuardViewModel,
    onOpenPinSetup: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                val items = listOf(
                    Triple(Screen.Home, Icons.Filled.Home, Icons.Outlined.Home),
                    Triple(Screen.Restrictions, Icons.Filled.Shield, Icons.Outlined.Shield),
                    Triple(Screen.Detox, Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement),
                    Triple(Screen.Stats, Icons.Filled.BarChart, Icons.Outlined.BarChart),
                    Triple(Screen.Settings, Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                for ((screen, filledIcon, outlinedIcon) in items) {
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) filledIcon else outlinedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        modifier = Modifier.testTag("nav_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRestrictions = { currentScreen = Screen.Restrictions },
                    onNavigateToDetox = { currentScreen = Screen.Detox },
                    onNavigateToSettings = { currentScreen = Screen.Settings }
                )
                Screen.Restrictions -> RestrictionsScreen(viewModel = viewModel)
                Screen.Detox -> DetoxScreen(viewModel = viewModel)
                Screen.Stats -> StatsScreen(viewModel = viewModel)
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenPinSetup = onOpenPinSetup
                )
            }
        }
    }
}
