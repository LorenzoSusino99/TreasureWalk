package com.example.treasurewalk.data.manager

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Definizione del tipo di tesoro e delle sue proprietà
enum class TreasureType(val xp: Int, val color: String) {
    COMMON(50, "#45D084"),    // Verde
    RARE(150, "#3B82F6"),     // Blu
    LEGENDARY(500, "#FFD166") // Oro
}

data class Treasure(
    val id: String = UUID.randomUUID().toString(),
    val position: LatLng,
    val type: TreasureType,
    val isVisible: Boolean = false,
    val isCollected: Boolean = false
)

class TreasureManager {

    // Lista dei tesori attivi nella sessione corrente
    private val _activeTreasures = MutableStateFlow<List<Treasure>>(emptyList())
    val activeTreasures = _activeTreasures.asStateFlow()

    /**
     * Genera un nuovo tesoro casuale intorno alla posizione dell'utente.
     * Utilizziamo un raggio casuale tra minRadius e maxRadius (in metri).
     */
    fun spawnTreasureNear(userLocation: LatLng, minRadius: Double = 100.0, maxRadius: Double = 300.0) {
        val radiusInDegrees = maxRadius / 111320.0 // Approssimazione metri -> gradi
        val minRadiusInDegrees = minRadius / 111320.0

        // Calcolo posizione casuale in un anello (Circle Spawning)
        val r = Random.nextDouble(minRadiusInDegrees, radiusInDegrees)
        val theta = Random.nextDouble(0.0, 2.0 * Math.PI)

        val newLat = userLocation.latitude + r * cos(theta)
        val newLng = userLocation.longitude + r * sin(theta) / cos(Math.toRadians(userLocation.latitude))

        val type = when (Random.nextInt(100)) {
            in 0..70 -> TreasureType.COMMON
            in 71..95 -> TreasureType.RARE
            else -> TreasureType.LEGENDARY
        }

        val newTreasure = Treasure(position = LatLng(newLat, newLng), type = type)
        _activeTreasures.update { currentList -> currentList + newTreasure }
    }

    /**
     * Controlla la distanza tra l'utente e i tesori.
     * Restituisce il tesoro se l'utente è abbastanza vicino da raccoglierlo (< 15 metri).
     */
    fun checkProximity(userLocation: LatLng, onTreasureFound: (Treasure) -> Unit) {

        // .update è una funzione di Kotlin Coroutines per modificare gli StateFlow in modo sicuro
        _activeTreasures.update { currentList ->

            // mapNotNull analizza ogni tesoro della lista vecchia per creare la lista nuova.
            // Regola d'oro: se restituisci 'null', il tesoro viene CANCELLATO.
            currentList.mapNotNull { treasure ->

                // 1. Calcoliamo la distanza
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    userLocation.latitude, userLocation.longitude,
                    treasure.position.latitude, treasure.position.longitude,
                    results
                )
                val distance = results[0]

                // 2. Decidiamo il destino di questo singolo tesoro
                when {
                    // CASO A: Il giocatore è sopra il tesoro (< 15 metri).
                    distance < 15f -> {
                        onTreasureFound(treasure) // Avvisiamo l'app che abbiamo vinto!
                        null //mapNotNull ignora i null, quindi il tesoro sparisce dalla mappa.
                    }

                    // CASO B: Il giocatore si avvicina (< 100 metri) ma il tesoro era invisibile.
                    distance < 100f && !treasure.isVisible -> {
                        treasure.copy(isVisible = true)
                    }

                    // CASO C: Il giocatore è lontano, oppure il tesoro era già visibile e rimane tale.
                    else -> {
                        treasure
                    }
                }
            }
        }
    }
}