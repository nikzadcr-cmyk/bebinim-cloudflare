package com.app.bebinim.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Routes — identical to the original app.
 * (tickets / ticket_detail / new_ticket removed by design)
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Plans : Screen("plans")
    object MyPlans : Screen("my_plans")
    object Profile : Screen("profile")
    object Ranking : Screen("ranking")
    object CreateJoinLobby : Screen("create_join_lobby") {
        fun withType(lobbyType: String): String = "$route?lobbyType=$lobbyType"
    }
    object Lobby : Screen("lobby/{lobbyCode}/{lobbyType}") {
        fun createRoute(lobbyCode: String, lobbyType: String = "movie"): String =
            "lobby/$lobbyCode/$lobbyType"
    }
    object VideoPlayer : Screen("video_player/{videoUrl}") {
        fun createRoute(videoUrl: String): String =
            "video_player/${android.net.Uri.encode(videoUrl, "UTF-8")}"
    }
}
