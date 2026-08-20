package com.syncparty.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.syncparty.core.common.DiscoveredHost
import com.syncparty.feature.createparty.CreatePartyCodeCard
import com.syncparty.feature.joinparty.JoinPartyScreen
import com.syncparty.feature.medialibrary.MediaLibraryScreen
import com.syncparty.feature.party.ClientPartyScreen
import com.syncparty.feature.party.HostPartyScreen

/**
 * HOST route: shows the party code / QR at top, connected devices + transport
 * controls below (Section 6), backed by a picker to add local media
 * (Section 9 MVP: user-selected local audio/video).
 */
@Composable
fun HostPartyRoute(onBack: () -> Unit) {
    val viewModel: HostPartyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val tracks by viewModel.localTracks.collectAsState()
    var showLibrary by remember { mutableStateOf(true) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.addLocalTrack(it, displayName = it.lastPathSegment ?: "track", isVideo = false)
        }
    }

    if (showLibrary) {
        MediaLibraryScreen(
            tracks = tracks,
            onPickFile = { pickFileLauncher.launch(arrayOf("audio/*", "video/*")) },
            onSelectTrack = { track ->
                viewModel.playTrack(track)
                showLibrary = false
            }
        )
    } else {
        val currentTrack = tracks.find { it.id == uiState.currentTrackId }
        HostPartyScreen(
            state = uiState,
            trackTitle = currentTrack?.name.orEmpty(),
            trackArtist = currentTrack?.artist.orEmpty(),
            positionMs = uiState.positionMs,
            durationMs = currentTrack?.durationMs ?: 0L,
            onPlayPause = { viewModel.togglePlayPause() },
            onPrevious = { /* Section 21 next/prev track — wired once a queue exists */ },
            onNext = { showLibrary = true },
            onSeek = { viewModel.seek(it) },
            topContent = {
                CreatePartyCodeCard(
                    partyId = uiState.partyId,
                    hostAddress = connectionInfo?.hostAddress,
                    port = connectionInfo?.controlPort
                )
            }
        )
    }
}

/** JOIN route: discovery list + manual code entry (Section 5), then hands off to ClientPartyRoute. */
@Composable
fun JoinPartyRoute(onJoined: () -> Unit, onBack: () -> Unit) {
    val viewModel: ClientPartyViewModel = viewModel()
    val discoveredHosts by viewModel.discoveredHosts.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    var manualCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startDiscovery() }

    JoinPartyScreen(
        discoveredHosts = discoveredHosts,
        isDiscovering = isDiscovering,
        manualCode = manualCode,
        onManualCodeChange = { manualCode = it },
        onJoinManualCode = {
            // Manual code path requires resolving code -> host address via the
            // same NSD browse results filtered by partyId, since the code
            // alone doesn't carry an IP (Section 5 Option A). The party code
            // doubles as the auth token (see HostPartyEngine.token).
            discoveredHosts.find { it.partyId == manualCode }?.let { host ->
                viewModel.joinParty(host.hostAddress, host.port, partyToken = host.partyId)
                onJoined()
            }
        },
        onJoinDiscoveredHost = { host: DiscoveredHost ->
            viewModel.joinParty(host.hostAddress, host.port, partyToken = host.partyId)
            onJoined()
        },
        onScanQr = { /* Launch a QR scan activity (zxing-embedded IntentIntegrator) from here */ }
    )
}

/** CLIENT party room: sync status, no independent playback controls (Section 7). */
@Composable
fun ClientPartyRoute(onLeft: () -> Unit) {
    val viewModel: ClientPartyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    ClientPartyScreen(
        state = uiState,
        trackTitle = uiState.currentTrackId.orEmpty(),
        onLeaveParty = {
            viewModel.leaveParty()
            onLeft()
        }
    )
}
