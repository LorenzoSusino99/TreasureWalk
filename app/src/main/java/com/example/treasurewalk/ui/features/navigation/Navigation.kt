package com.example.treasurewalk.ui.features.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.treasurewalk.data.local.WalkDatabase
import com.example.treasurewalk.services.WalkTrackingService
import com.example.treasurewalk.ui.features.home.HomeScreen
import com.example.treasurewalk.ui.features.landing.LandingScreen
import com.example.treasurewalk.ui.features.play.PlayScreen
import com.example.treasurewalk.ui.viewmodels.WalkViewModel
import com.example.treasurewalk.ui.viewmodels.WalkViewModelFactory
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.treasurewalk.ui.components.PermissionProvider
import com.example.treasurewalk.ui.features.inventory.InventoryScreen
import com.example.treasurewalk.ui.features.profile.ProfileScreen
import com.example.treasurewalk.ui.features.setup.SetupScreen
import com.example.treasurewalk.ui.features.summary.SummaryScreen

object Routes {
    const val SETUP = "setup"
    const val LANDING = "landing"
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val PROFILE = "profile"
    const val SUMMARY = "summary"
    const val PLAY = "play/{targetKm}"

    fun createPlayRoute(km: Float): String {
        return "play/$km"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Inizializziamo il Database e il ViewModel
    // (In un progetto reale useremmo Hilt/Koin, ma qui seguiamo la via diretta)
    val database = WalkDatabase.getDatabase(context)
    val viewModel: WalkViewModel = viewModel(
        factory = WalkViewModelFactory(database.treasureDao())
    )

    NavHost(
        navController = navController,
        startDestination = Routes.LANDING
    ) {
        // 1. LANDING
        composable(Routes.LANDING) {
            LandingScreen(onTimeout = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LANDING) { inclusive = true }
                }
            })
        }

        // 2. HOME
        composable(Routes.HOME) {
            PermissionProvider(
                onPermissionsGranted = {
                    navController.navigate(Routes.SETUP)
                }
            ) { requestPermissions ->
                HomeScreen(
                    onPlayClick = { requestPermissions() }, // Chiama il controllo permessi
                    onInventoryClick = { navController.navigate(Routes.INVENTORY) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) }
                )
            }
        }

        composable(Routes.SETUP) {
            SetupScreen(
                onStartMission = { chosenKm ->
                    // ORA che abbiamo i KM, accendiamo il GPS!
                    val intent = Intent(context, WalkTrackingService::class.java)
                    ContextCompat.startForegroundService(context, intent)

                    // Navighiamo verso PLAY passandogli i KM scelti
                    navController.navigate(Routes.createPlayRoute(chosenKm))
                }
            )
        }

        // 3. PLAY (LA MAPPA)
        composable(
            route = Routes.PLAY,
            // Diciamo a Compose che ci aspettiamo un numero decimale (Float)
            arguments = listOf(navArgument("targetKm") { type = NavType.FloatType })
        ) { backStackEntry ->

            // Estraiamo il numero dal "pacchetto" (se per caso manca, usiamo 2.0f di default)
            val extractedKm = backStackEntry.arguments?.getFloat("targetKm") ?: 2f

            // Creiamo il ViewModel legato a questa singola partita
            val viewModel: WalkViewModel = viewModel(
                factory = WalkViewModelFactory(database.treasureDao())
            )

            // Qui dovrai passare targetKm alla UI o usarlo direttamente
            PlayScreen(
                viewModel = viewModel,
                targetKm = extractedKm,
                onStopClick = {
                    context.stopService(Intent(context, WalkTrackingService::class.java))
                    navController.popBackStack(Routes.HOME, inclusive = false) // Torniamo dritti alla Home
                }
            )
        }
        composable(Routes.INVENTORY) {
            InventoryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.SUMMARY) {
            // Recuperiamo i dati correnti dal ViewModel prima che vengano resettati
            val distance by viewModel.totalDistance.collectAsState()
            val pathPoints by viewModel.pathPoints.collectAsState()
            val treasures by viewModel.collectedTreasures.collectAsState()
            // Nota: in un'app reale filtreresti i tesori presi solo "oggi"

            SummaryScreen(
                distance = distance,
                xpGained = (distance * 100).toInt(), // Esempio di XP basato su KM
                treasuresCount = 3, // Questo dato andrebbe passato dinamicamente
                pathPoints = pathPoints,
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}