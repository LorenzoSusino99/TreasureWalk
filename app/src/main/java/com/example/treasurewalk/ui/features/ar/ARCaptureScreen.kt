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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ARPhase {
    SEARCHING,
    DIGGING,
    REVEALED,
    COLLECTED
}

@Composable
fun ARCaptureScreen(
    targetTreasure: Treasure,
    userLocation: LatLng,
    onCaptured: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    val coroutineScope = rememberCoroutineScope()

    val currentFrameHolder = remember { arrayOfNulls<Frame>(1) }
    var isEngineReady by remember { mutableStateOf(false) }
    var hasVibrated by remember(targetTreasure.id) { mutableStateOf(false) }

    // Fase del gioco
    var currentPhase by remember(targetTreasure.id) { mutableStateOf(ARPhase.SEARCHING) }
    var digCount by remember(targetTreasure.id) { mutableStateOf(0) }

    val heading = rememberCompassHeading()
    var distanceToTreasure by remember { mutableStateOf(999f) }

    var treasureAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var terriccioModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var shovelModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var arSession by remember { mutableStateOf<com.google.ar.core.Session?>(null) }

    val breadcrumbs = remember { mutableStateListOf<Node>() }

    // --- LOGICA POSIZIONAMENTO RELATIVO (Fissaggio Tesoro) ---
    // Calcoliamo la posizione relativa (metri N/E) UNA VOLTA all'inizio.
    // Poi usiamo SOLO AR tracking per navigare verso quel punto.
    var relativeTargetNorth by remember(targetTreasure.id) { mutableStateOf(0f) }
    var relativeTargetEast by remember(targetTreasure.id) { mutableStateOf(0f) }
    var remNorth by remember(targetTreasure.id) { mutableStateOf(0f) }
    var remEast by remember(targetTreasure.id) { mutableStateOf(0f) }
    var lockedTargetARPosition by remember(targetTreasure.id) { mutableStateOf<Position?>(null) }
    var initialHeading by remember(targetTreasure.id) { mutableStateOf<Float?>(null) }
    var relativeCoordinatesSet by remember(targetTreasure.id) { mutableStateOf(false) }

    LaunchedEffect(userLocation) {
        if (!relativeCoordinatesSet) {
            val latDiff = targetTreasure.position.latitude - userLocation.latitude
            val lngDiff = targetTreasure.position.longitude - userLocation.longitude
            
            relativeTargetNorth = (latDiff * 111320.0).toFloat()
            relativeTargetEast = (lngDiff * 111320.0 * cos(Math.toRadians(userLocation.latitude))).toFloat()
            
            val results = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                targetTreasure.position.latitude, targetTreasure.position.longitude,
                results
            )
            distanceToTreasure = results[0]
            remNorth = relativeTargetNorth
            remEast = relativeTargetEast
            relativeCoordinatesSet = true
        }
    }

    // --- LOGICA BREADCRUMBS ---
    LaunchedEffect(isEngineReady, currentPhase, lockedTargetARPosition) {
        if (isEngineReady && currentPhase == ARPhase.SEARCHING && breadcrumbs.isEmpty() && lockedTargetARPosition != null) {
            val target = lockedTargetARPosition!!
            val totalDist = sqrt(target.x * target.x + target.z * target.z)
            
            if (totalDist > 0) {
                val dirX = target.x / totalDist
                val dirZ = target.z / totalDist
                
                // Distribuiamo sfere ogni 4 metri verso il target
                val maxBreadcrumbDist = totalDist.coerceAtMost(60f)
                for (d in 4..maxBreadcrumbDist.toInt() step 4) {
                    val x = dirX * d
                    val z = dirZ * d
                    try {
                        val ballInstance = modelLoader.createModelInstance("models/sphere.glb")
                        if (ballInstance != null) {
                            val ballNode = ModelNode(modelInstance = ballInstance, scaleToUnits = 0.2f).apply {
                                position = Position(x, -0.6f, z)
                            }
                            childNodes.add(ballNode)
                            breadcrumbs.add(ballNode)
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        if (currentPhase != ARPhase.SEARCHING) {
            breadcrumbs.forEach { childNodes.remove(it) }
            breadcrumbs.clear()
        }
    }

    // --- LOGICA POSIZIONAMENTO (TERRICCIO) ---
    LaunchedEffect(currentPhase) {
        if (currentPhase == ARPhase.DIGGING && treasureAnchorNode == null) {
            while (currentPhase == ARPhase.DIGGING && treasureAnchorNode == null) {
                val frame = currentFrameHolder[0]
                val session = arSession
                if (frame != null && session != null && view.width > 0) {
                    val hitResults = frame.hitTest(view.width / 2f, view.height / 2f)
                    val groundHit = hitResults.firstOrNull { hit ->
                        val trackable = hit.trackable
                        trackable is Plane && trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                    }

                    if (groundHit != null) {
                        val anchor = groundHit.createAnchor()
                        val anchorNode = AnchorNode(engine, anchor)
                        try {
                            val terriccioInstance = modelLoader.createModelInstance("models/terriccio.glb")
                            if (terriccioInstance != null) {
                                val dirtNode = ModelNode(modelInstance = terriccioInstance, scaleToUnits = 0.8f)
                                val targetScale = dirtNode.scale
                                dirtNode.scale = Scale(0.007f, 0.007f, 0.007f)
                                
                                anchorNode.addChildNode(dirtNode)
                                childNodes.add(anchorNode)
                                
                                val camPose = frame.camera.pose
                                dirtNode.lookAt(Position(camPose.tx(), camPose.ty(), camPose.tz()))
                                dirtNode.rotation = Rotation(x = 0f, y = 90f, z = 0f)
                                treasureAnchorNode = anchorNode
                                terriccioModelNode = dirtNode
                                
                                coroutineScope.launch {
                                    for (i in 1..10) {
                                        val factor = i / 10f
                                        dirtNode.scale = Scale(targetScale.x * factor, targetScale.y * factor, targetScale.z * factor)
                                        delay(30)
                                    }
                                    dirtNode.scale = targetScale
                                }
                                digCount = 1
                                break
                            }
                        } catch (e: Exception) {}
                    }
                }
                delay(500)
            }
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
                arSession = session
                currentFrameHolder[0] = updatedFrame
                
                if (updatedFrame != null) {
                    // Inizializzazione AR: Lock della posizione geografica relativa nel sistema AR
                    if (!isEngineReady && relativeCoordinatesSet) {
                        isEngineReady = true
                        val hRad = Math.toRadians(heading.toDouble())
                        initialHeading = heading
                        val cosH = cos(hRad).toFloat()
                        val sinH = sin(hRad).toFloat()
                        
                        // Rotazione del vettore (East, North) nel sistema coordinate AR (X, Z)
                        val targetX = relativeTargetEast * cosH - relativeTargetNorth * sinH
                        val targetZ = -(relativeTargetEast * sinH + relativeTargetNorth * cosH)
                        
                        lockedTargetARPosition = Position(targetX, updatedFrame.camera.pose.ty(), targetZ)
                    }

                    if (currentPhase == ARPhase.SEARCHING) {
                        val cameraPose = updatedFrame.camera.pose
                        lockedTargetARPosition?.let { target ->
                            val dx = target.x - cameraPose.tx()
                            val dz = target.z - cameraPose.tz()
                            val arDist = sqrt(dx*dx + dz*dz)
                            
                            distanceToTreasure = arDist
                            
                            // Aggiorna metri N/E rimanenti per la UI (inversione della rotazione iniziale)
                            val h0 = Math.toRadians((initialHeading ?: heading).toDouble())
                            val cosH0 = cos(h0).toFloat()
                            val sinH0 = sin(h0).toFloat()
                            remEast = dx * cosH0 - dz * sinH0
                            remNorth = -dz * cosH0 - dx * sinH0
                            
                            // Passaggio alla fase DIGGING quando vicini (entro 3 metri AR)
                            if (arDist < 3.0f) {
                                if (!hasVibrated) {
                                    triggerVibration(context)
                                    hasVibrated = true
                                }
                                currentPhase = ARPhase.DIGGING
                            }
                        }

                        // Pulizia breadcrumbs toccati
                        val iterator = breadcrumbs.listIterator()
                        while (iterator.hasNext()) {
                            val node = iterator.next()
                            val dx = node.position.x - cameraPose.tx()
                            val dz = node.position.z - cameraPose.tz()
                            if (dx*dx + dz*dz < 2.0f) {
                                childNodes.remove(node)
                                iterator.remove()
                                triggerVibration(context)
                            }
                        }
                    }
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    when (currentPhase) {
                        ARPhase.DIGGING -> {
                            if (digCount in 1..3) {
                                triggerVibration(context)
                                if (digCount == 1) {
                                    try {
                                        val shovelInstance = modelLoader.createModelInstance("models/shovel.glb")
                                        if (shovelInstance != null) {
                                            shovelModelNode = ModelNode(modelInstance = shovelInstance, scaleToUnits = 0.6f)
                                            shovelModelNode?.rotation = Rotation(x = -45f, y = 0f, z = 0f)
                                            shovelModelNode?.position = Position(y = 0.1f)
                                            treasureAnchorNode?.addChildNode(shovelModelNode!!)
                                        }
                                    } catch (e: Exception) {}
                                }

                                coroutineScope.launch {
                                    val startX = -45f - ((digCount - 1) * 15f)
                                    val targetX = -45f - (digCount * 15f)
                                    for (i in 1..10) {
                                        shovelModelNode?.rotation = Rotation(x = startX + (targetX - startX) * (i / 10f), y = 0f, z = 0f)
                                        delay(16)
                                    }
                                }

                                digCount++
                                if (digCount > 3) {
                                    terriccioModelNode?.let { treasureAnchorNode?.removeChildNode(it) }
                                    shovelModelNode?.let { treasureAnchorNode?.removeChildNode(it) }
                                    try {
                                        val chestInstance = modelLoader.createModelInstance("models/treasure_chest.glb")
                                        if (chestInstance != null) {
                                            val chestModel = ModelNode(modelInstance = chestInstance, scaleToUnits = 0.5f)
                                            chestModel.rotation = Rotation(x = 0f, y = -90f, z = 0f)
                                            val tScale = chestModel.scale
                                            chestModel.scale = Scale(0.01f, 0.01f, 0.01f)
                                            treasureAnchorNode?.addChildNode(chestModel)
                                            coroutineScope.launch {
                                                for (i in 1..10) {
                                                    val f = i / 10f
                                                    chestModel.scale = Scale(tScale.x * f, tScale.y * f, tScale.z * f)
                                                    delay(30)
                                                }
                                                chestModel.scale = tScale
                                            }
                                        }
                                    } catch (e: Exception) {}
                                    currentPhase = ARPhase.REVEALED
                                }
                            }
                        }
                        ARPhase.REVEALED -> {
                            currentPhase = ARPhase.COLLECTED
                            coroutineScope.launch { delay(2000); onCaptured() }
                        }
                        else -> {}
                    }
                }
            )
        )

        if (!isEngineReady) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFFD166))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calibrazione AR in corso...", color = Color.White)
                }
            }
        } else {
            if (currentPhase == ARPhase.SEARCHING) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 64.dp)
                    ) {
                        Text(
                            text = "${distanceToTreasure.toInt()} metri",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val nText = if (remNorth >= 0) "${remNorth.toInt()}N" else "${(-remNorth).toInt()}S"
                        val eText = if (remEast >= 0) "${remEast.toInt()}E" else "${(-remEast).toInt()}O"
                        
                        Text(
                            text = "$nText, $eText",
                            color = Color(0xFFFFD166),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

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

            if (currentPhase != ARPhase.COLLECTED) {
                val instructionText = when (currentPhase) {
                    ARPhase.SEARCHING -> "Segui le indicazioni per trovare il punto esatto!"
                    ARPhase.DIGGING -> {
                        when (digCount) {
                            0 -> "Inquadra il pavimento per far apparire il tesoro..."
                            1 -> "Tocca il mucchio di terra per scavare! (1/3)"
                            2 -> "Continua così! (2/3)"
                            3 -> "Ultimo colpo! (3/3)"
                            else -> "Ottimo!"
                        }
                    }
                    ARPhase.REVEALED -> "L'hai trovato! Tocca il forziere per aprirlo!"
                    else -> ""
                }
                Text(
                    text = instructionText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
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

fun triggerVibration(context: Context) {
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
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    } catch (e: Exception) {}
}
