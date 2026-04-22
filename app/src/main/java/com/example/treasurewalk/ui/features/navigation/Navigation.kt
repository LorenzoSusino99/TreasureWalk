package com.example.treasurewalk.ui.features.navigation

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    const val SUMMARY = "summary/{targetKm}"
    const val PLAY = "play/{targetKm}"

    const val AR_CAPTURE = "ar_capture/{treasureId}"

    fun createCaptureRoute(treasureId: String): String {
        return "ar_capture/$treasureId"
    }
    fun createPlayRoute(km: Float): String {
        return "play/$km"
    }
    fun createSummaryRoute(km: Float): String {
        return "summary/$km"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val database = WalkDatabase.getDatabase(context)
    val sharedViewModel: WalkViewModel = viewModel(
        factory = WalkViewModelFactory(database.treasureDao())
    )

    NavHost(
        navController = navController,
        startDestination = Routes.LANDING
    ) {
        composable(Routes.LANDING) {
            LandingScreen(onTimeout = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LANDING) { inclusive = true }
                }
            })
        }

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

        composable(Routes.SETUP) {
            SetupScreen(
                onStartMission = { chosenKm ->
                    val intent = Intent(context, WalkTrackingService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    navController.navigate(Routes.createPlayRoute(chosenKm))
                }
            )
        }

        composable(
            route = Routes.PLAY,
            arguments = listOf(navArgument("targetKm") { type = NavType.FloatType })
        ) { backStackEntry ->
            val extractedKm = backStackEntry.arguments?.getFloat("targetKm") ?: 2f

            PlayScreen(
                viewModel = sharedViewModel,
                targetKm = extractedKm,
                onNavigateToAR = { treasureId ->
                    navController.navigate(Routes.createCaptureRoute(treasureId))
                },
                onStopClick = {
                    context.stopService(Intent(context, WalkTrackingService::class.java))
                    navController.navigate(Routes.createSummaryRoute(extractedKm)) 
                }
            )
        }

        composable(Routes.INVENTORY) {
            InventoryScreen(
                viewModel = sharedViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = sharedViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument("targetKm") { type = NavType.FloatType })
        ) { backStackEntry ->
            val targetKm = backStackEntry.arguments?.getFloat("targetKm") ?: 0f
            val distance by sharedViewModel.totalDistance.collectAsState()
            val pathPoints by sharedViewModel.pathPoints.collectAsState()
            
            val sessionXp by sharedViewModel.sessionXp.collectAsState()
            val sessionTreasuresCount by sharedViewModel.sessionTreasuresCount.collectAsState()

            SummaryScreen(
                distance = distance,
                targetDistance = targetKm,
                xpGained = sessionXp,
                treasuresCount = sessionTreasuresCount,
                pathPoints = pathPoints,
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.AR_CAPTURE,
            arguments = listOf(navArgument("treasureId") { type = NavType.StringType })
        ) { backStackEntry ->
            val treasureId = backStackEntry.arguments?.getString("treasureId")
            val treasures by sharedViewModel.treasuresOnMap.collectAsState()
            val targetTreasure = treasures.find { it.id == treasureId }
            val userLoc by sharedViewModel.currentLocation.collectAsState()

            if (targetTreasure != null && userLoc != null) {
                ARCaptureScreen(
                    targetTreasure = targetTreasure,
                    userLocation = com.google.android.gms.maps.model.LatLng(
                        userLoc!!.latitude,
                        userLoc!!.longitude
                    ),
                    onCaptured = {
                        navController.popBackStack()
                        sharedViewModel.onTreasureCollected(
                            treasureId = targetTreasure.id,
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
