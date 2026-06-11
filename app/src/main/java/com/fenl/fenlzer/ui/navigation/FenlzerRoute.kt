package com.fenl.fenlzer.ui.navigation

sealed class FenlzerRoute(val route: String, val label: String) {
    data object Home : FenlzerRoute("home", "Home")
    data object Playlists : FenlzerRoute("playlists", "Playlists")
    data object Import : FenlzerRoute("import", "Import")
    data object Settings : FenlzerRoute("settings", "Settings")
    data object Statistics : FenlzerRoute("statistics", "Statistics")
    data object Queue : FenlzerRoute("queue", "Queue")
    data object Player : FenlzerRoute("player", "Player")
}
