package com.example.treasurewalk.ui.features.play

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

@Composable
fun PlayScreen(
    viewModel: WalkViewModel,
    onStopClick: () -> Unit
) {
    // Osserviamo i dati dal ViewModel
    val userLocation by viewModel.currentLocation.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()
    val totalDistance by viewModel.totalDistance.collectAsState()
    val totalXp by viewModel.totalXp.collectAsState()

    // Stato della telecamera: si centra sull'utente quando la posizione cambia
    val cameraPositionState = rememberCameraPositionState()

    var isMapCentered by remember { mutableStateOf(false) }

    LaunchedEffect(userLocation) {
        val location = userLocation
        // Centriamo la telecamera SOLO se abbiamo una posizione E non l'abbiamo ancora mai centrata
        if (location != null && !isMapCentered) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    17f
                )
            )
            isMapCentered = true // Aggiorniamo il flag: non lo faremo più!
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. LA MAPPA
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            // Disegna il percorso fatto finora
            if (pathPoints.isNotEmpty()) {
                Polyline(
                    points = pathPoints,
                    color = Color(0xFF45D084),
                    width = 15f
                )
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
            onClick = onStopClick,
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