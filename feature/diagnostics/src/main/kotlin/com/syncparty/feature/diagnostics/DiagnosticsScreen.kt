package com.syncparty.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syncparty.core.common.DeviceRole
import com.syncparty.core.common.SyncQuality

data class DiagnosticsData(
    val role: DeviceRole,
    val localIp: String,
    val transportName: String,
    val latencyMs: Long,
    val clockOffsetMs: Long,
    val expectedPositionMs: Long,
    val actualPositionMs: Long,
    val driftMs: Long,
    val bufferedMs: Long,
    val audioOutputLabel: String,
    val syncQuality: SyncQuality
)

/** Section 29: developer diagnostics screen, essential for on-device sync testing. */
@Composable
fun DiagnosticsScreen(data: DiagnosticsData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DiagnosticsSection(title = "ROLE") {
            DiagnosticsRow("Role", data.role.name)
        }
        DiagnosticsSection(title = "NETWORK") {
            DiagnosticsRow("Local IP", data.localIp)
            DiagnosticsRow("Transport", data.transportName)
            DiagnosticsRow("Latency", "${data.latencyMs} ms")
        }
        DiagnosticsSection(title = "CLOCK") {
            DiagnosticsRow("Clock offset", "${data.clockOffsetMs} ms")
        }
        DiagnosticsSection(title = "PLAYBACK") {
            DiagnosticsRow("Expected position", "${data.expectedPositionMs} ms")
            DiagnosticsRow("Actual position", "${data.actualPositionMs} ms")
            DiagnosticsRow("Drift", "${if (data.driftMs >= 0) "+" else ""}${data.driftMs} ms")
        }
        DiagnosticsSection(title = "BUFFER") {
            DiagnosticsRow("Buffered", "${data.bufferedMs / 1000.0}s")
        }
        DiagnosticsSection(title = "AUDIO") {
            DiagnosticsRow("Output", data.audioOutputLabel)
        }
        DiagnosticsSection(title = "SYNC STATUS") {
            DiagnosticsRow("Quality", data.syncQuality.name)
        }
    }
}

@Composable
private fun DiagnosticsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                Modifier.padding(bottom = 4.dp)
            )
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .then(Modifier)
        ) {
            content()
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
