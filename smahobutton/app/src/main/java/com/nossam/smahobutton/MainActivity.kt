package com.nossam.smahobutton

import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.nossam.smahobutton.backup.DriveClient
import com.nossam.smahobutton.backup.DriveFailure
import com.nossam.smahobutton.logic.DriveMessages
import com.nossam.smahobutton.logic.DriveNotice
import java.util.concurrent.atomic.AtomicBoolean
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
    var driveNotice by mutableStateOf<String?>(null)
    private var driveName: String? = null
    private var driveContent: String? = null
    private val driveBusy = AtomicBoolean(false)
    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onDriveConsent(result.resultCode, result.data)
    }

    fun saveToDrive(name: String, content: String) {
        try {
            if (name.isBlank() || name.length > 200 || content.length > 8_000_000) {
                finishDrive(DriveNotice("error", DriveMessages.GENERIC))
                return
            }
            if (!driveOnline()) {
                finishDrive(DriveNotice("offline", DriveMessages.OFFLINE))
                return
            }
            if (!driveBusy.compareAndSet(false, true)) return
            driveName = name
            driveContent = content
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveMessages.SCOPE)))
                .build()
            Identity.getAuthorizationClient(this)
                .authorize(request)
                .addOnSuccessListener(this) { auth ->
                    try {
                        if (auth.hasResolution()) {
                            val pending = auth.pendingIntent
                            if (pending == null) {
                                finishDrive(DriveMessages.noticeForStatus(-1))
                            } else {
                                driveConsent.launch(IntentSenderRequest.Builder(pending).build())
                            }
                        } else {
                            val token = auth.accessToken
                            if (token.isNullOrBlank()) finishDrive(DriveMessages.noticeForStatus(-1))
                            else uploadDrive(token)
                        }
                    } catch (error: Throwable) {
                        Log.e(TAG, "drive consent could not start", error)
                        finishDrive(DriveMessages.noticeForStatus(-1))
                    }
                }
                .addOnFailureListener(this) { error ->
                    val code = (error as? ApiException)?.statusCode ?: -1
                    finishDrive(DriveMessages.noticeForStatus(code))
                }
        } catch (error: Throwable) {
            Log.e(TAG, "drive save crashed", error)
            finishDrive(DriveNotice("error", DriveMessages.GENERIC))
        }
    }

    private fun onDriveConsent(resultCode: Int, data: Intent?) {
        try {
            if (resultCode != RESULT_OK || data == null) {
                finishDrive(DriveNotice("cancel", DriveMessages.CANCEL))
                return
            }
            val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val token = auth.accessToken
            if (token.isNullOrBlank()) finishDrive(DriveMessages.noticeForStatus(-1))
            else uploadDrive(token)
        } catch (error: ApiException) {
            finishDrive(DriveMessages.noticeForStatus(error.statusCode))
        } catch (error: Throwable) {
            Log.e(TAG, "drive consent failed", error)
            finishDrive(DriveNotice("error", DriveMessages.GENERIC))
        }
    }

    private fun uploadDrive(token: String) {
        val name = driveName
        val content = driveContent
        if (name == null || content == null) {
            finishDrive(DriveMessages.noticeForStatus(-1))
            return
        }
        Thread {
            val message = try {
                val report = DriveClient.save(token, name, content, DriveMessages.NAME_PREFIX)
                DriveMessages.success(report.name, report.cleaned, report.unverified, report.cleanErr)
            } catch (error: DriveFailure) {
                error.notice.message
            } catch (error: Throwable) {
                Log.e(TAG, "drive upload failed", error)
                DriveMessages.GENERIC
            }
            runOnUiThread { finishDrive(message) }
        }.start()
    }

    private fun finishDrive(notice: DriveNotice) {
        finishDrive(notice.message)
    }

    private fun finishDrive(message: String) {
        driveBusy.set(false)
        driveNotice = message
    }

    private fun driveOnline(): Boolean = try {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (error: Throwable) {
        Log.w(TAG, "could not read the network state", error)
        true
    }

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
        private const val TAG = "SmahoDrive"
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
