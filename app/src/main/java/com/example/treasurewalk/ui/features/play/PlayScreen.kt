package com.example.treasurewalk.ui.features.play

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.ui.viewmodels.WalkViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
    // Osserviamo i dati dal ViewModel
    val userLocation by viewModel.currentLocation.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val totalDistance by viewModel.totalDistance.collectAsState()
    val totalXp by viewModel.totalXp.collectAsState()
    val treasures by viewModel.treasuresOnMap.collectAsState()
    
    // Nuovi dati di sessione (XP e tesori della camminata attuale)
    val sessionXp by viewModel.sessionXp.collectAsState()

    // 1. ASCOLTO PER LA VIBRAZIONE
    LaunchedEffect(Unit) {
        viewModel.newTreasureDiscovered.collect {
            triggerMapVibration(context)
        }
    }

    // Stato della telecamera
    val cameraPositionState = rememberCameraPositionState()
    var isMapCentered by remember { mutableStateOf(false) }
    var pendingTreasureId by remember { mutableStateOf<String?>(null) }

    // 2. IL LANCIATORE DEL PERMESSO FOTOCAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                pendingTreasureId?.let { id ->
                    onNavigateToAR(id)
                }
            }
            pendingTreasureId = null
        }
    )

    LaunchedEffect(userLocation) {
        val location = userLocation
        if (location != null && !isMapCentered) {
            val startLatLng = LatLng(location.latitude, location.longitude)

            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    startLatLng, 16f
                )
            )

            // Avviamo la missione SOLO la prima volta che la mappa si centra
            viewModel.startNewMission(startLoc = startLatLng, targetKm = targetKm)
            isMapCentered = true 
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
            // Percorso pianificato (blu tratteggiato)
            if (plannedRoute.isNotEmpty()) {
                Polyline(
                    points = plannedRoute,
                    color = Color(0xFF3B82F6),
                    width = 14f,
                    pattern = listOf(Dash(20f), Gap(10f)),
                    jointType = JointType.ROUND
                )
            }

            // Percorso effettivo (verde pieno)
            if (pathPoints.isNotEmpty()) {
                Polyline(
                    points = pathPoints,
                    color = Color(0xFF45D084),
                    width = 18f,
                    jointType = JointType.ROUND
                )
            }

            val treasureIcon = remember {
                val size = 96
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#80000000")
                    textSize = 58f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 58f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                val cx = size / 2f
                val cy = size / 2f + textPaint.textSize / 3f
                canvas.drawText("?", cx + 2f, cy + 2f, shadowPaint)
                canvas.drawText("?", cx, cy, textPaint)
                BitmapDescriptorFactory.fromBitmap(bmp)
            }

            treasures.forEach { treasure ->
                if (treasure.isVisible) {
                    Circle(
                        center = treasure.position,
                        radius = 5.0,
                        fillColor = Color(0x33FFF9C0),
                        strokeColor = Color(0xFFF9A825),
                        strokeWidth = 3f
                    )
                    Marker(
                        state = MarkerState(position = treasure.position),
                        title = "Tesoro",
                        icon = treasureIcon,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            val loc = userLocation
                            if (loc != null) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    loc.latitude, loc.longitude,
                                    treasure.position.latitude, treasure.position.longitude,
                                    results
                                )
                                val distanceInMeters = results[0]
                                if (distanceInMeters < 20f) {
                                    pendingTreasureId = treasure.id
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatChip(text = "${String.format("%.2f", totalDistance)} KM", color = Color.White)
            // Mostriamo l'XP totale o di sessione? In questo caso mostriamo il totale accumulato
            StatChip(text = "$totalXp XP", color = Color(0xFFFFD166), icon = Icons.Default.Star)
        }

        // 3. Tasto TERMINA in basso
        Button(
            onClick = onStopClick, // ✅ Naviga direttamente al Summary tramite la rotta definita in Navigation
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .height(60.dp)
                .fillMaxWidth(0.5f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(30.dp),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFFFFFF))
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

fun triggerMapVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    } catch (e: Exception) { }
}
