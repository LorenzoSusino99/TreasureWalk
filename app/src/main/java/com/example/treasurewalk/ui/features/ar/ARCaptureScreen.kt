package com.example.treasurewalk.ui.features.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.treasurewalk.data.manager.Treasure
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ARPhase {
    SEARCHING, // Cerca la posizione seguendo la freccia
    DIGGING,   // Sei arrivato! Scava 3 volte
    REVEALED,  // Il tesoro è apparso
    COLLECTED  // Hai raccolto il tesoro (Schermata di Vittoria!)
}

@Composable
fun ARCaptureScreen(
    targetTreasure: Treasure,
    userLocation: LatLng,
    onCaptured: () -> Unit
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    val coroutineScope = rememberCoroutineScope()

    var frame by remember { mutableStateOf<Frame?>(null) }
    var isEngineReady by remember { mutableStateOf(false) }

    var hasVibrated by remember(targetTreasure.id) { mutableStateOf(false) }
    var showDigWarning by remember(targetTreasure.id) { mutableStateOf(false) }

    // Partiamo dalla fase SEARCHING ora!
    var currentPhase by remember(targetTreasure.id) { mutableStateOf(ARPhase.SEARCHING) }
    var digCount by remember(targetTreasure.id) { mutableStateOf(0) }

    // --- LOGICA BUSSOLA E DISTANZA ---
    val heading = rememberCompassHeading()
    var distanceToTreasure by remember { mutableStateOf(999f) }
    var bearingToTreasure by remember { mutableStateOf(0f) }

    // Calcoliamo la distanza e l'angolo ogni volta che l'utente si muove
    LaunchedEffect(userLocation) {
        val userLoc = Location("").apply { latitude = userLocation.latitude; longitude = userLocation.longitude }
        val targetLoc = Location("").apply { latitude = targetTreasure.position.latitude; longitude = targetTreasure.position.longitude }

        distanceToTreasure = userLoc.distanceTo(targetLoc)
        bearingToTreasure = userLoc.bearingTo(targetLoc)

        if (currentPhase == ARPhase.SEARCHING && distanceToTreasure < 5f) {
            if (!hasVibrated) {
                triggerVibration(context)
                hasVibrated = true
            }
            currentPhase = ARPhase.DIGGING
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.AUTO
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onSessionUpdated = { session, updatedFrame ->
                frame = updatedFrame
                if (!isEngineReady && updatedFrame != null) {
                    isEngineReady = true
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    when (currentPhase) {
                        ARPhase.DIGGING -> {
                            val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                            val planeHit = hitResults?.firstOrNull { it.trackable is Plane }

                            if (planeHit != null) {
                                // HA COLPITO IL PAVIMENTO!
                                showDigWarning = false
                                digCount++
                                if (digCount >= 3) {
                                    val anchor = planeHit.createAnchor()
                                    val anchorNode = AnchorNode(engine, anchor)
                                    val modelInstance = modelLoader.createModelInstance("models/treasure_chest.glb")

                                    val modelNode = ModelNode(modelInstance = modelInstance, scaleToUnits = 0.5f)
                                    anchorNode.addChildNode(modelNode)
                                    childNodes.add(anchorNode)
                                    currentPhase = ARPhase.REVEALED

                                }
                            } else {
                                showDigWarning = true
                            }
                        }
                        ARPhase.REVEALED -> {
                            if (node != null) {
                                currentPhase = ARPhase.COLLECTED
                                // Aspettiamo 4 secondi per far godere la schermata di vittoria all'utente!
                                coroutineScope.launch {
                                    delay(4000)
                                    onCaptured()
                                }
                            }
                        }
                        else -> {}
                    }
                }
            )
        )

        // --- INTERFACCIA UTENTE ---
        if (!isEngineReady) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD166))
            }
        } else {

            // 1. LA FRECCIA (Mostrata solo durante la ricerca)
            if (currentPhase == ARPhase.SEARCHING) {
                // Calcoliamo la rotazione: Direzione del tesoro MENO dove sta guardando il telefono
                val arrowRotation = (bearingToTreasure - heading + 360) % 360

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Direzione",
                        tint = Color(0xFFFFD166).copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer(rotationZ = arrowRotation)
                    )
                    // Testo sotto la freccia con i metri mancanti
                    Text(
                        text = "${distanceToTreasure.toInt()} metri",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 140.dp).background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(8.dp)
                    )
                }
            }

            // 2. SCHERMATA DI VITTORIA (Il Bottino!)
            AnimatedVisibility(
                visible = currentPhase == ARPhase.COLLECTED,
                enter = fadeIn(tween(500)) + scaleIn(tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(32.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("MISTERO SVELATO!", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Tesoro ${targetTreasure.type.name}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "+${targetTreasure.type.xp} XP", color = Color(0xFF45D084), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. ISTRUZIONI IN BASSO
            if (currentPhase != ARPhase.COLLECTED) {
                val instructionText = when (currentPhase) {
                    ARPhase.SEARCHING -> "Segui la freccia per trovare il tesoro!"
                    ARPhase.DIGGING -> {
                        if (showDigWarning) "Inquadra meglio il pavimento prima di scavare!"
                        else if (digCount == 0) "Ci sei! Scava (tocca il pavimento) 3 volte!"
                        else "Scava... $digCount/3"
                    }
                    ARPhase.REVEALED -> "L'hai trovato! Tocca il forziere per aprirlo!"
                    else -> ""
                }

                Text(
                    text = instructionText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- FUNZIONI HELPER ---

// 1. Legge la bussola del telefono per capire dove stai guardando
@Composable
fun rememberCompassHeading(): Float {
    val context = LocalContext.current
    var heading by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // Convertiamo da radianti a gradi
                    heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return heading
}

// 2. Fa vibrare il telefono quando sei vicino
fun triggerVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
}