package com.nossam.smahobutton

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nossam.smahobutton.apps.InstalledApps
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.service.OverlayService
import com.nossam.smahobutton.ui.AppPickerScreen
import com.nossam.smahobutton.ui.ButtonScreen
import com.nossam.smahobutton.ui.HomeScreen
import com.nossam.smahobutton.ui.SettingsViewModel
import com.nossam.smahobutton.ui.VolumeScreen
import com.nossam.smahobutton.ui.theme.SmahoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val start = when (intent?.getStringExtra(EXTRA_SCREEN)) {
            "buttons" -> ROUTE_BUTTONS
            "volumes" -> ROUTE_VOLUMES
            else -> ROUTE_HOME
        }
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            SmahoTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val current = settings
                    if (current == null) {
                        Loading()
                    } else {
                        AppNav(current, viewModel, start)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = (application as SmahoApp).repository.state.value?.enabled == true
        if (enabled) OverlayService.start(this)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@Composable
private fun Loading() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AppNav(settings: SettingsSpec, viewModel: SettingsViewModel, start: String) {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    NavHost(navController = nav, startDestination = start) {
        composable(ROUTE_HOME) {
            HomeScreen(
                settings = settings,
                onToggle = viewModel::setEnabled,
                onButtons = { nav.navigate(ROUTE_BUTTONS) },
                onVolumes = { nav.navigate(ROUTE_VOLUMES) },
                onRestore = viewModel::replaceAll,
            )
        }
        composable(ROUTE_BUTTONS) {
            ButtonScreen(
                settings = settings,
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onPickApp = { nav.navigate(pickerRoute("action")) },
            )
        }
        composable(ROUTE_VOLUMES) {
            VolumeScreen(
                settings = settings,
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(pickerRoute("volume")) },
            )
        }
        composable(
            route = ROUTE_PICKER,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "volume"
            AppPickerScreen(
                onBack = { nav.popBackStack() },
                onPick = { packageName, label ->
                    viewModel.onAppPicked(
                        mode = mode,
                        packageName = packageName,
                        label = label,
                        volume = InstalledApps.musicVolume(context),
                    )
                    nav.popBackStack()
                },
            )
        }
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_BUTTONS = "buttons"
private const val ROUTE_VOLUMES = "volumes"
private const val ROUTE_PICKER = "picker/{mode}"

private fun pickerRoute(mode: String) = "picker/$mode"
