package com.syncparty.app.navigation

sealed class SyncPartyDestination(val route: String) {
    data object Home : SyncPartyDestination("home")
    data object CreateParty : SyncPartyDestination("create_party")
    data object JoinParty : SyncPartyDestination("join_party")
    data object HostPartyRoom : SyncPartyDestination("host_party_room")
    data object ClientPartyRoom : SyncPartyDestination("client_party_room")
    data object MediaLibrary : SyncPartyDestination("media_library")
    data object Diagnostics : SyncPartyDestination("diagnostics")
}
