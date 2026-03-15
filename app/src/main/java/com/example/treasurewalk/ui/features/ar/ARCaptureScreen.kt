package com.example.treasurewalk.ui.features.ar

import android.view.MotionEvent
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
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

@Composable
fun ARCaptureScreen(
    targetTreasure: Treasure,
    userLocation: LatLng,
    onCaptured: () -> Unit
) {
    // 1. IL "MOTORE" DELLA VERSIONE 2.3.3
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes() // La lista dei nodi presenti nella scena

    // 2. STATI REATTIVI
    var frame by remember { mutableStateOf<Frame?>(null) }
    var isAnchored by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            sessionConfiguration = { session, config ->
                // Cerchiamo solo pavimenti per non sprecare batteria
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            },
            onSessionUpdated = { session, updatedFrame ->
                // Salviamo il frame per poter calcolare dove l'utente tocca lo schermo
                frame = updatedFrame
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    // CASO A: L'utente ha toccato IL FORZIERE! (Vittoria)
                    if (node != null) {
                        onCaptured()
                    }
                    // CASO B: L'utente ha toccato il pavimento e il forziere non c'è ancora
                    else if (!isAnchored) {
                        // Facciamo un HitTest dal punto in cui ha toccato lo schermo
                        val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)

                        // Cerchiamo il primo punto che corrisponde a un pavimento
                        hitResults?.firstOrNull { it.trackable is Plane }?.let { hitResult ->

                            // 1. Creiamo un'ancora fisica in quel punto
                            val anchor = hitResult.createAnchor()
                            val anchorNode = AnchorNode(engine, anchor)

                            // 2. Carichiamo il modello 3D
                            val modelInstance = modelLoader.createModelInstance("models/treasure_chest.glb")
                            val modelNode = ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = 0.5f // Grandezza mezzo metro
                            )

                            // 3. Attacchiamo il modello all'ancora, e l'ancora alla scena!
                            anchorNode.addChildNode(modelNode)
                            childNodes.add(anchorNode)

                            // 4. Blocchiamo ulteriori spawn
                            isAnchored = true
                        }
                    }
                }
            )
        )

        // HUD: Frecce Direzionali
        ARGuidanceOverlay()

        // Istruzioni Dinamiche
        val instructionText = if (!isAnchored) {
            "Scansiona il pavimento e tocca la griglia per piazzare il tesoro!"
        } else {
            "Eccolo! Tocca il forziere per aprirlo!"
        }

        Text(
            text = instructionText,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ARGuidanceOverlay() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = Color(0xFFFFD166),
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer(rotationZ = -90f) // Punta verso l'alto (avanti)
        )
    }
}