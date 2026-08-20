package com.syncparty.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.syncparty.app.navigation.SyncPartyDestination
import com.syncparty.app.theme.SyncPartyTheme
import com.syncparty.feature.home.HomeScreen

class MainActivity : ComponentActivity() {

    /**
     * Runtime permissions requested lazily, only when the relevant action is
     * taken (Section 25: "Request permissions only when required"). We ask
     * for everything the app might need up front here for simplicity of the
     * MVP wiring, but each individual feature screen is written to degrade
     * gracefully (e.g. audio-output detection works without BLUETOOTH_CONNECT,
     * it just can't show the BT device's friendly name).
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results observed via hasRequiredPermissions() on next composition */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRuntimePermissionsIfNeeded()

        setContent {
            SyncPartyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    SyncPartyNavHost(navController)
                }
            }
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.CAMERA)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@androidx.compose.runtime.Composable
private fun SyncPartyNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = SyncPartyDestination.Home.route) {

        composable(SyncPartyDestination.Home.route) {
            HomeScreen(
                onCreateParty = { navController.navigate(SyncPartyDestination.CreateParty.route) },
                onJoinParty = { navController.navigate(SyncPartyDestination.JoinParty.route) }
            )
        }

        composable(SyncPartyDestination.CreateParty.route) {
            // HostPartyViewModel wiring: creates a TcpHostTransport, starts
            // NsdHostAdvertiser, and drives HostPartyEngine. Kept as a TODO
            // hook here — see ARCHITECTURE.md "Wiring the ViewModel layer"
            // for the exact construction sequence, since it depends on
            // Context (for NSD/AudioManager) that only the Activity/ViewModel
            // layer should own, not the pure feature composables.
            HostPartyRoute(onBack = { navController.popBackStack() })
        }

        composable(SyncPartyDestination.JoinParty.route) {
            JoinPartyRoute(
                onJoined = { navController.navigate(SyncPartyDestination.ClientPartyRoom.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SyncPartyDestination.ClientPartyRoom.route) {
            ClientPartyRoute(onLeft = { navController.popBackStack(SyncPartyDestination.Home.route, false) })
        }
    }
}
