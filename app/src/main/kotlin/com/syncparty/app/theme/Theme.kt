package com.syncparty.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Section 30 requires dark-mode compatibility; this app is dark-first by
 * design (used at parties/outdoors, large controls, high contrast against
 * varied ambient light) rather than merely "compatible" with dark mode.
 */
private val SyncPartyDarkScheme = darkColorScheme(
    primary = SyncPartyColors.actionPrimary,
    onPrimary = SyncPartyColors.textPrimary,
    secondary = SyncPartyColors.signalExcellent,
    background = SyncPartyColors.backgroundBase,
    onBackground = SyncPartyColors.textPrimary,
    surface = SyncPartyColors.surfaceRaised,
    onSurface = SyncPartyColors.textPrimary,
    surfaceVariant = SyncPartyColors.surfaceRaisedHigh,
    onSurfaceVariant = SyncPartyColors.textSecondary,
    outline = SyncPartyColors.outline,
    error = SyncPartyColors.signalResyncing
)

@Composable
fun SyncPartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SyncPartyDarkScheme,
        typography = SyncPartyTypography,
        content = content
    )
}
