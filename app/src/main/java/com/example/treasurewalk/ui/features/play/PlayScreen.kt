package com.example.treasurewalk.ui.features.play

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.ui.viewmodels.WalkViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.gms.maps.CameraUpdateFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import com.example.treasurewalk.data.manager.TreasureType
import com.example.treasurewalk.ui.features.navigation.Routes
import com.example.treasurewalk.ui.features.summary.SummaryScreen
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType

@Composable
fun PlayScreen(
    viewModel: WalkViewModel,
    targetKm: Float,
    onNavigateToAR: (String) -> Unit,
    onStopClick: () -> Unit
) {
    // Osserviamo i dati dal ViewModel
    val userLocation by viewModel.currentLocation.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val totalDistance by viewModel.totalDistance.collectAsState()
    val totalXp by viewModel.totalXp.collectAsState()
    val treasures by viewModel.treasuresOnMap.collectAsState()

    // Stato della telecamera: si centra sull'utente quando la posizione cambia
    val cameraPositionState = rememberCameraPositionState()

    var isMapCentered by remember { mutableStateOf(false) }

    var pendingTreasureId by remember { mutableStateOf<String?>(null) }

    // 2. IL LANCIATORE DEL PERMESSO FOTOCAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Permesso accordato! Ora possiamo navigare in sicurezza
                pendingTreasureId?.let { id ->
                    onNavigateToAR(id)
                }
            } else {
                // Permesso negato
                //Toast.makeText(context, "Serve la fotocamera per l'AR!", Toast.LENGTH_SHORT).show()
            }
            // Svuotiamo la memoria
            pendingTreasureId = null
        }
    )

    LaunchedEffect(userLocation) {
        val location = userLocation
        // Centriamo la telecamera SOLO se abbiamo una posizione E non l'abbiamo ancora mai centrata
        if (location != null && !isMapCentered) {
            val startLatLng = LatLng(location.latitude, location.longitude)

            // Azione A: Centriamo la telecamera sull'utente
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    startLatLng, 16f
                )
            )

            // Azione B: GENERIAMO L'ANELLO E I TESORI!
            viewModel.startNewMission(startLoc = startLatLng, targetKm = targetKm)
            isMapCentered = true // Aggiorniamo il flag: non lo faremo più!
        }
    }

    var showSummary by remember { mutableStateOf(false) }

    // Se showSummary è vero, mostriamo la tua nuova schermata
    if (showSummary) {
        // Calcoliamo i dati "al volo" dalla sessione corrente
        val sessionTreasures = treasures.filter { it.isCollected }
        val xpGained = sessionTreasures.sumOf { it.type.xp }

        SummaryScreen(
            distance = totalDistance,
            xpGained = xpGained,
            treasuresCount = sessionTreasures.size,
            pathPoints = pathPoints,
            onHomeClick = {
                // QUI chiudiamo definitivamente tutto!
                onStopClick()
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. LA MAPPA
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                // Disegna il percorso fatto finora
                if (plannedRoute.isNotEmpty()) {
                    Polyline(
                        points = plannedRoute,
                        color = Color.Gray.copy(alpha = 0.5f),
                        width = 12f,
                        pattern = listOf(Dash(20f), Gap(10f)),
                        jointType = JointType.ROUND
                    )
                }

                // 2. DISEGNA IL PERCORSO EFFETTIVO (VERDE PIENO)
                if (pathPoints.isNotEmpty()) {
                    Polyline(
                        points = pathPoints,
                        color = Color(0xFF45D084),
                        width = 18f,
                        jointType = JointType.ROUND
                    )
                }
                treasures.forEach { treasure ->
                    if (treasure.isVisible) {
                        Marker(
                            state = MarkerState(position = treasure.position),
                            title = "Tesoro ${treasure.type.name}",
                            icon = when (treasure.type) {
                                TreasureType.COMMON -> BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_GREEN
                                )

                                TreasureType.RARE -> BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_AZURE
                                )

                                TreasureType.LEGENDARY -> BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_ORANGE
                                ) // HUE_GOLD non esiste, ORANGE è il più vicino!
                            },
                            onClick = {
                                // 1. Assicuriamoci che l'utente abbia una posizione valida
                                val loc = userLocation
                                if (loc != null) {
                                    // 2. Calcoliamo la distanza in metri tra l'utente e QUESTO specifico tesoro
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        loc.latitude, loc.longitude,
                                        treasure.position.latitude, treasure.position.longitude,
                                        results
                                    )
                                    val distanceInMeters = results[0]

                                    // 3. Eseguiamo il tuo controllo!
                                    if (distanceInMeters < 20f) { // 20 metri di raggio
                                        pendingTreasureId = treasure.id
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)} else {
                                        // Sostituisci questo con un vero Toast o una Snackbar
                                        // Toast.makeText(context, "Sei a ${distanceInMeters.toInt()}m. Avvicinati ancora!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                true
                            }
                        )
                    }
                }
            }

            // 2. HUD - Statistiche in alto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(text = "${String.format("%.2f", totalDistance)} KM", color = Color.White)
                StatChip(text = "$totalXp XP", color = Color(0xFFFFD166), icon = Icons.Default.Star)
            }

            // 3. Tasto STOP in basso
            Button(
                onClick = { showSummary = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .height(60.dp)
                    .fillMaxWidth(0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(30.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("TERMINA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun StatChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}