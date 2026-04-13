package com.example.treasurewalk.ui.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.ui.components.MenuButton

@Composable
fun SetupScreen(
    onStartMission: (Float) -> Unit
) {
    var distanceTarget by remember { mutableStateOf(2f) } // Default 2km

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsWalk,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF3B82F6)
        )

        Text(
            text = "CONFIGURA MISSIONE",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1F2937)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Card per lo slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${String.format("%.1f", distanceTarget)} KM",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3B82F6)
                )

                Text(
                    text = "Tempo stimato: ${(distanceTarget * 12).toInt()} min",
                    fontSize = 16.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = distanceTarget,
                    onValueChange = { distanceTarget = it },
                    valueRange = 1f..10f,
                    steps = 18, // Incrementi di 0.5km
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF3B82F6),
                        activeTrackColor = Color(0xFF3B82F6)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Usiamo il tuo MenuButton personalizzato
        MenuButton(
            text = "GENERA PERCORSO",
            icon = Icons.Filled.PlayArrow,
            backgroundColor = Color(0xFF45D084),
            textColor = Color.White,
            onClick = { onStartMission(distanceTarget) }
        )
    }
}
