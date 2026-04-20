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

    // Partiamo dalla fase SEARCHING
    var currentPhase by remember(targetTreasure.id) { mutableStateOf(ARPhase.SEARCHING) }
    var digCount by remember(targetTreasure.id) { mutableStateOf(0) }

    val heading = rememberCompassHeading()
    var distanceToTreasure by remember { mutableStateOf(999f) }
    var bearingToTreasure by remember { mutableStateOf(0f) }

    var treasureAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var terriccioModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var shovelModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var arSession by remember { mutableStateOf<com.google.ar.core.Session?>(null) }

    val breadcrumbs = remember { mutableStateListOf<Node>() }

    // --- LOGICA STABILIZZAZIONE GPS (Smoothing) ---
    LaunchedEffect(userLocation) {
        val userLoc = Location("").apply { latitude = userLocation.latitude; longitude = userLocation.longitude }
        val targetLoc = Location("").apply { latitude = targetTreasure.position.latitude; longitude = targetTreasure.position.longitude }
        
        val newRawDistance = userLoc.distanceTo(targetLoc)
        val newRawBearing = userLoc.bearingTo(targetLoc)

        if (distanceToTreasure >= 999f) {
            // Prima lettura: inizializzazione diretta
            distanceToTreasure = newRawDistance
            bearingToTreasure = newRawBearing
        } else {
            // Filtro Passa-Basso: attenua i balzi del GPS
            val jump = kotlin.math.abs(newRawDistance - distanceToTreasure)
            // Se lo sbalzo è irrealistico (>40m), pesiamo il nuovo dato pochissimo
            val alpha = if (jump > 40f) 0.05f else 0.25f
            
            distanceToTreasure = (distanceToTreasure * (1f - alpha)) + (newRawDistance * alpha)
            
            // Smoothing del bearing (direzione)
            bearingToTreasure = (bearingToTreasure * (1f - alpha)) + (newRawBearing * alpha)
        }

        // Passiamo alla fase di scavo solo se la distanza STABILIZZATA è < 5m
        if (currentPhase == ARPhase.SEARCHING && distanceToTreasure < 5f) {
            if (!hasVibrated) {
                triggerVibration(context)
                hasVibrated = true
            }
            currentPhase = ARPhase.DIGGING
        }
    }

    // --- LOGICA BREADCRUMBS ---
    LaunchedEffect(isEngineReady, currentPhase) {
        if (isEngineReady && currentPhase == ARPhase.SEARCHING && breadcrumbs.isEmpty()) {
            val totalDist = distanceToTreasure
            val bearing = bearingToTreasure
            val maxBreadcrumbDist = totalDist.coerceAtMost(45f)
            for (d in 3..maxBreadcrumbDist.toInt() step 3) {
                val relAngle = Math.toRadians((bearing - heading).toDouble())
                val x = (d * sin(relAngle)).toFloat()
                val z = -(d * cos(relAngle)).toFloat()
                try {
                    val ballInstance = modelLoader.createModelInstance("models/sphere.glb")
                    if (ballInstance != null) {
                        val ballNode = ModelNode(modelInstance = ballInstance, scaleToUnits = 0.2f).apply {
                            position = Position(x, -0.5f, z)
                        }
                        childNodes.add(ballNode)
                        breadcrumbs.add(ballNode)
                    }
                } catch (e: Exception) {}
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
                                dirtNode.scale = Scale(0.01f, 0.01f, 0.01f)
                                
                                anchorNode.addChildNode(dirtNode)
                                childNodes.add(anchorNode)
                                
                                // Ruota il terriccio per guardare l'utente ma solo sull'asse Y (rimane piatto)
                                val camPose = frame.camera.pose
                                dirtNode.lookAt(Position(camPose.tx(), dirtNode.position.y, dirtNode.position.z))
                                
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
                if (!isEngineReady && updatedFrame != null) isEngineReady = true

                if (currentPhase == ARPhase.SEARCHING && updatedFrame != null) {
                    val cameraPose = updatedFrame.camera.pose
                    val iterator = breadcrumbs.listIterator()
                    while (iterator.hasNext()) {
                        val node = iterator.next()
                        val dx = node.position.x - cameraPose.tx()
                        val dy = node.position.y - cameraPose.ty()
                        val dz = node.position.z - cameraPose.tz()
                        if (dx*dx + dy*dy + dz*dz < 1.0f) {
                            childNodes.remove(node)
                            iterator.remove()
                            triggerVibration(context)
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
                                            // Ruotiamo la pala in modo che il manico sia rivolto verso l'alto
                                            shovelModelNode?.rotation = Rotation(x = -45f, y = 0f, z = 0f)
                                            // Alziamo la pala sopra il terriccio
                                            shovelModelNode?.position = Position(y = 0.5f)
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
                CircularProgressIndicator(color = Color(0xFFFFD166))
            }
        } else {
            if (currentPhase == ARPhase.SEARCHING) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "${distanceToTreasure.toInt()} metri",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 64.dp)
                            .background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
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
                    ARPhase.SEARCHING -> "Segui le palline azzurre per trovare il tesoro!"
                    ARPhase.DIGGING -> {
                        when (digCount) {
                            0 -> "Inquadra il pavimento in piano per far apparire il tesoro..."
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
