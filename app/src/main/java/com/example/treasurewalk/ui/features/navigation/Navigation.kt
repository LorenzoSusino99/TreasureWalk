package com.tuonome.progetto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.treasurewalk.ui.features.home.HomeScreen
import com.example.treasurewalk.ui.features.landing.LandingScreen

object Routes {
    const val LANDING = "landing"
    const val HOME = "home"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LANDING
    ) {
        composable(Routes.LANDING) {
            LandingScreen(onTimeout = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) {
                        inclusive = true
                    }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}