package com.example.treasurewalk.ui.features.navigation

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.treasurewalk.data.local.TreasureRarity
import com.example.treasurewalk.ui.components.PermissionProvider
import com.example.treasurewalk.ui.features.ar.ARCaptureScreen
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

    const val AR_CAPTURE = "ar_capture/{treasureId}"

    fun createCaptureRoute(treasureId: String): String {
        return "ar_capture/$treasureId"
    }
    fun createPlayRoute(km: Float): String {
        return "play/$km"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 1. PRIMA inizializziamo il Database
    val database = WalkDatabase.getDatabase(context)

    // 2. POI creiamo l'UNICO ViewModel globale di tutta l'app, usando la Factory corretta!
    val sharedViewModel: WalkViewModel = viewModel(
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
                    onPlayClick = { requestPermissions() },
                    onInventoryClick = { navController.navigate(Routes.INVENTORY) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) }
                )
            }
        }

        // 3. SETUP
        composable(Routes.SETUP) {
            SetupScreen(
                onStartMission = { chosenKm ->
                    val intent = Intent(context, WalkTrackingService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    navController.navigate(Routes.createPlayRoute(chosenKm))
                }
            )
        }

        // 4. PLAY (LA MAPPA)
        composable(
            route = Routes.PLAY,
            arguments = listOf(navArgument("targetKm") { type = NavType.FloatType })
        ) { backStackEntry ->
            val extractedKm = backStackEntry.arguments?.getFloat("targetKm") ?: 2f

            PlayScreen(
                viewModel = sharedViewModel, // ✅ Usiamo il ViewModel globale
                targetKm = extractedKm,
                onNavigateToAR = { treasureId ->
                    navController.navigate(Routes.createCaptureRoute(treasureId))
                },
                onStopClick = {
                    context.stopService(Intent(context, WalkTrackingService::class.java))
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        // 5. INVENTARIO E PROFILO
        composable(Routes.INVENTORY) {
            InventoryScreen(
                viewModel = sharedViewModel, // ✅ Usiamo il ViewModel globale
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = sharedViewModel, // ✅ Usiamo il ViewModel globale
                onBackClick = { navController.popBackStack() }
            )
        }

        // 6. SUMMARY
        composable(Routes.SUMMARY) {
            val distance by sharedViewModel.totalDistance.collectAsState()
            val pathPoints by sharedViewModel.pathPoints.collectAsState()

            // Leggiamo i dati VERI dal ViewModel
            val allTreasures by sharedViewModel.collectedTreasures.collectAsState()
            val totalXp by sharedViewModel.totalXp.collectAsState()

            SummaryScreen(
                distance = distance,
                xpGained = totalXp, // Usa l'XP vero!
                treasuresCount = allTreasures.size, // Usa il conto vero!
                pathPoints = pathPoints,
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // 7. AR CAPTURE
        composable(
            route = Routes.AR_CAPTURE,
            arguments = listOf(navArgument("treasureId") { type = NavType.StringType })
        ) { backStackEntry ->
            val treasureId = backStackEntry.arguments?.getString("treasureId")

            val treasures by sharedViewModel.treasuresOnMap.collectAsState()
            // ✅ Cerchiamo il tesoro nella lista globale
            val targetTreasure = treasures.find { it.id == treasureId } // Oppure it.id.toString() se id è Int
            val userLoc by sharedViewModel.currentLocation.collectAsState()

            if (targetTreasure != null && userLoc != null) {
                ARCaptureScreen(
                    targetTreasure = targetTreasure,
                    userLocation = com.google.android.gms.maps.model.LatLng(
                        userLoc!!.latitude,
                        userLoc!!.longitude
                    ),
                    onCaptured = {
                        // Prima facciamo il pop per evitare crash di Sceneview 
                        // se l'oggetto sparisce mentre lo schermo è ancora attivo
                        navController.popBackStack()

                        // Poi passiamo l'ID e i dati al ViewModel
                        sharedViewModel.onTreasureCollected(
                            treasureId = targetTreasure.id, // L'ID cruciale!
                            lat = targetTreasure.position.latitude,
                            lng = targetTreasure.position.longitude,
                            type = com.example.treasurewalk.data.local.TreasureRarity.valueOf(targetTreasure.type.name),
                            xp = targetTreasure.type.xp
                        )
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}