package com.app.bebinim.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.app.bebinim.ui.screens.CreateJoinLobbyScreen
import com.app.bebinim.ui.screens.HomeScreen
import com.app.bebinim.ui.screens.LoginScreen
import com.app.bebinim.ui.screens.MusicLobbyScreen
import com.app.bebinim.ui.screens.MyPlansScreen
import com.app.bebinim.ui.screens.PlansScreen
import com.app.bebinim.ui.screens.ProfileScreen
import com.app.bebinim.ui.screens.RankingScreen
import com.app.bebinim.ui.screens.RegisterScreen
import com.app.bebinim.ui.screens.VideoPlayerScreen
import com.app.bebinim.viewmodel.AuthViewModel

@Composable
fun AppNavigation(navHostController: NavHostController, authViewModel: AuthViewModel) {
    val isLoggedIn = authViewModel.isLoggedIn.value

    // wire the create/join screen navigation bridge
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.app.bebinim.ui.screens.LobbyNav.host = object : com.app.bebinim.ui.screens.LobbyNav.LobbyNavHost {
            override fun navigateToLobby(code: String, type: String) {
                navHostController.navigate(Screen.Lobby.createRoute(code, type)) {
                    launchSingleTop = true // never stack a second instance of the same lobby
                }
            }
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navHostController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navHostController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navHostController.popBackStack() },
                onRegisterSuccess = {
                    navHostController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLobby = { navHostController.navigate(Screen.CreateJoinLobby.withType("movie")) },
                onNavigateToRanking = { navHostController.navigate(Screen.Ranking.route) },
                onNavigateToProfile = { navHostController.navigate(Screen.Profile.route) },
                onNavigateToEducation = { },
                onLogout = {
                    authViewModel.logout()
                    navHostController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Plans.route) {
            PlansScreen(onBack = { navHostController.popBackStack() })
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navHostController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    navHostController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MyPlans.route) {
            MyPlansScreen(onBack = { navHostController.popBackStack() })
        }

        composable(Screen.Ranking.route) {
            RankingScreen(onBack = { navHostController.popBackStack() })
        }

        composable(
            route = Screen.CreateJoinLobby.route + "?lobbyType={lobbyType}",
            arguments = listOf(navArgument("lobbyType") {
                type = NavType.StringType
                defaultValue = "movie"
            })
        ) { backStackEntry ->
            CreateJoinLobbyScreen(
                lobbyType = backStackEntry.arguments?.getString("lobbyType") ?: "movie",
                onBack = { navHostController.popBackStack() }
            )
        }

        composable(
            route = Screen.Lobby.route,
            arguments = listOf(
                navArgument("lobbyCode") { type = NavType.StringType },
                navArgument("lobbyType") { type = NavType.StringType; defaultValue = "movie" }
            )
        ) { backStackEntry ->
            val lobbyCode = backStackEntry.arguments?.getString("lobbyCode") ?: ""
            val lobbyType = backStackEntry.arguments?.getString("lobbyType") ?: "movie"
            if (lobbyType == "music") {
                MusicLobbyScreen(
                    navController = navHostController,
                    lobbyCode = lobbyCode
                )
            } else {
                com.app.bebinim.ui.screens.LobbyScreen(
                    navController = navHostController,
                    lobbyCode = lobbyCode,
                    lobbyType = lobbyType
                )
            }
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(navArgument("videoUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            VideoPlayerScreen(
                navController = navHostController,
                videoUrl = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("videoUrl") ?: "", "UTF-8"
                )
            )
        }
    }
}
