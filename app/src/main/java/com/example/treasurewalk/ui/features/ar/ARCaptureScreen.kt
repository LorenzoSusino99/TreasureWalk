package com.example.treasurewalk.ui.features.ar

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

@Composable
fun ARCaptureScreen(
    targetTreasure: Treasure,
    userLocation: LatLng,
    onCaptured: () -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    val coroutineScope = rememberCoroutineScope()

    // STATI REATTIVI
    var frame by remember { mutableStateOf<Frame?>(null) }
    var isAnchored by remember { mutableStateOf(false) }

    // Nuovo stato per evitare lo schermo bianco (introdotto dalla tua prima versione)
    var isEngineReady by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // IL MOTORE AR
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.AUTO
            },
            onSessionUpdated = { session, updatedFrame ->
                frame = updatedFrame
                // Appena la fotocamera inizia a generare frame, togliamo la schermata di caricamento
                if (!isEngineReady && updatedFrame != null) {
                    isEngineReady = true
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    // CASO A: Tocco sul forziere
                    if (node != null) {
                        onCaptured()
                    }
                    // CASO B: Tocco sul pavimento
                    else if (!isAnchored) {
                        val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)

                        hitResults?.firstOrNull { it.trackable is Plane }?.let { hitResult ->

                            val anchor = hitResult.createAnchor()
                            val anchorNode = AnchorNode(engine, anchor)

                            // Caricamento del modello (qui usiamo createModelInstance)
                            val modelInstance = modelLoader.createModelInstance("models/treasure_chest.glb")

                            // Se il file esiste, lo posizioniamo
                            if (modelInstance != null) {
                                val modelNode = ModelNode(
                                    modelInstance = modelInstance,
                                    scaleToUnits = 0.5f
                                )
                                anchorNode.addChildNode(modelNode)
                                childNodes.add(anchorNode)
                                isAnchored = true
                            }
                        }
                    }
                }
            )
        )

        // UI SOVRAPPOSTA

        // 1. Schermata di Caricamento Iniziale (Evita il panico da "schermo bianco")
        if (!isEngineReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFFD166))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Avvio fotocamera AR...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            // 2. HUD e Istruzioni (Mostrati solo quando l'AR è pronto)
            ARGuidanceOverlay()

            val instructionText = if (!isAnchored) {
                "Scansiona il pavimento e tocca la griglia per piazzare il tesoro!"
            } else {
                "Eccolo! Tocca il forziere per aprirlo!"
            }

            Text(
                text = instructionText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium)
                    .padding(16.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ARGuidanceOverlay() {
    // Questo è il tuo overlay provvisorio per le frecce direzionali
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = Color(0xFFFFD166).copy(alpha = 0.5f), // Leggermente trasparente
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer(rotationZ = -90f)
        )
    }
}