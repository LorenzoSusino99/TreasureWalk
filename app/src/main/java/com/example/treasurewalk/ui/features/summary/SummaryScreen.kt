package com.example.treasurewalk.ui.features.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.ui.features.profile.StatCard
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SummaryScreen(
    distance: Double,
    xpGained: Int,
    treasuresCount: Int,
    pathPoints: List<LatLng>,
    onHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MISSIONE COMPIUTA!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF45D084)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Mappa statica del percorso effettuato
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            val cameraPositionState = rememberCameraPositionState {
                if (pathPoints.isNotEmpty()) {
                    position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(pathPoints.last(), 14f)
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false
                )
            ) {
                if (pathPoints.isNotEmpty()) {
                    Polyline(points = pathPoints, color = Color(0xFF45D084), width = 12f)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Griglia Statistiche Sessione
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                label = "Distanza",
                value = "${String.format(java.util.Locale.US, "%.2f", distance)} km",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "XP Presi",
                value = "+$xpGained",
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        StatCard(
            label = "Tesori Trovati",
            value = "$treasuresCount",
            icon = Icons.Default.EmojiEvents,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        // Azioni Finali
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { /* Azione Share */ },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("SHARE")
            }

            Button(
                onClick = onHomeClick,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("HOME")
            }
        }
    }
}