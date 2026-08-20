package com.syncparty.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for SyncParty.
 *
 * Direction: a late-night, backstage-control-room feel rather than a
 * generic "music app purple gradient." Base is near-black charcoal (not
 * pure black, which flattens depth on OLED party lighting conditions).
 * Two accents carry distinct meaning rather than being decorative:
 *   - Signal green: sync/connection health (the thing this whole app is about)
 *   - Warm coral: playback/transport actions (play, the human action)
 * This separation means color itself communicates "is this a status or a
 * control" at a glance, which matters on a screen people glance at from
 * across a room.
 */
object SyncPartyColors {
    val backgroundBase = Color(0xFF0E0C11)
    val surfaceRaised = Color(0xFF191620)
    val surfaceRaisedHigh = Color(0xFF211D2B)
    val outline = Color(0xFF332E3F)

    val textPrimary = Color(0xFFF3F1F7)
    val textSecondary = Color(0xFFA79FB5)
    val textDisabled = Color(0xFF5F5870)

    // Signal green — sync/connection status
    val signalExcellent = Color(0xFF00E5A8)
    val signalGood = Color(0xFF7FE0A8)
    val signalDrifting = Color(0xFFFFC24D)
    val signalResyncing = Color(0xFFFF5C4D)

    // Warm coral — transport controls, primary actions
    val actionPrimary = Color(0xFFFF6A55)
    val actionPrimaryPressed = Color(0xFFE8543F)

    val hostAccent = Color(0xFF7FE0A8)
    val clientAccent = Color(0xFF8FA9FF)
}
