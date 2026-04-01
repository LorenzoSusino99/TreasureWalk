package com.example.treasurewalk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.example.treasurewalk.data.local.TreasureDao
import com.example.treasurewalk.data.local.TreasureEntity
import com.example.treasurewalk.data.local.TreasureRarity
import com.example.treasurewalk.services.WalkTrackingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.treasurewalk.data.manager.RouteGenerator
import com.example.treasurewalk.data.manager.Treasure
import kotlinx.coroutines.Dispatchers
import com.example.treasurewalk.data.manager.TreasureManager
import com.example.treasurewalk.data.remote.RoutingRepository

class WalkViewModel(private val treasureDao: TreasureDao) : ViewModel() {

    private val routeGenerator = RouteGenerator()

    private val routingRepository = RoutingRepository()
    private val treasureManager = TreasureManager()
    val treasuresOnMap = treasureManager.activeTreasures
    // Percorso pianificato (quello grigio da seguire)
    private val _plannedRoute = MutableStateFlow<List<LatLng>>(emptyList())
    val plannedRoute = _plannedRoute.asStateFlow()

    // 1. Osserva la posizione e il percorso dal Service
    val currentLocation = WalkTrackingService.currentLocation
    val pathPoints = WalkTrackingService.pathPoints

    val activeTreasures = treasureManager.activeTreasures

    // 2. Stato dei tesori raccolti (dal DB)
    val collectedTreasures = treasureDao.getAllCollectedTreasures()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // 3. Il "Motore del Gioco": ogni volta che il GPS si aggiorna,
        // diciamo al TreasureManager di controllare le distanze!
        viewModelScope.launch {
            currentLocation.collect { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)

                    treasureManager.checkProximity(userLatLng)
                }
            }
        }
    }

    fun startNewMission(startLoc: LatLng, targetKm: Float) {
        viewModelScope.launch(Dispatchers.Default) {

            WalkTrackingService.clearPath()
            // 1. Generiamo i vertici "sbilenchi" con la tua matematica
            val rawRoute = routeGenerator.generateLoop(startLoc, targetKm.toDouble())

            // 2. Chiediamo a ORS di calcolare i marciapiedi reali tra questi punti!
            val realWalkingRoute = routingRepository.getRealWalkingPath(rawRoute)

            // 3. Diamo alla mappa il percorso reale, non quello dritto
            _plannedRoute.value = realWalkingRoute

            // 4. Seminiamo i tesori!
            // Invece di usare rawRoute, usiamo i punti del percorso reale!
            val numberOfTreasures = (targetKm * 50).toInt().coerceAtLeast(3)

            // Dividiamo la lunghezza totale della lista reale per distribuire i tesori in modo equo
            if (realWalkingRoute.isNotEmpty()) {
                val step = realWalkingRoute.size / numberOfTreasures
                for (i in 1..numberOfTreasures) {
                    // Prendiamo un punto esatto lungo il sentiero calcolato
                    val spawnIndex = (i * step).coerceAtMost(realWalkingRoute.size - 1)
                    val spawnPoint = realWalkingRoute[spawnIndex]
                    treasureManager.spawnTreasureNear(spawnPoint, minRadius = 5.0, maxRadius = 30.0)
                }
            }
        }
    }
    private fun salvaTesoroNelDb(treasure: Treasure, userLatLng: LatLng) {
        viewModelScope.launch {
            treasureDao.insertTreasure(
                TreasureEntity(
                    type = TreasureRarity.valueOf(treasure.type.name),
                    lat = userLatLng.latitude,
                    lng = userLatLng.longitude,
                    xpAwarded = treasure.type.xp
                )
            )
        }
    }
    // 3. XP Totale calcolato automaticamente dal DB
    val totalXp = treasureDao.getTotalXp()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // 4. Logica per calcolare la distanza totale percorsa
    val totalDistance = pathPoints.map { points ->
        calculateTotalDistance(points)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    // Funzione per raccogliere un tesoro
    fun onTreasureCollected(treasureId: String, lat: Double, lng: Double, type: TreasureRarity, xp: Int) {
        // 1. Salviamo nel database locale (la tua logica originale perfetta)
        viewModelScope.launch {
            val newTreasure = TreasureEntity(
                type = type,
                lat = lat,
                lng = lng,
                xpAwarded = xp
            )
            treasureDao.insertTreasure(newTreasure)
        }

        // 2. Diciamo al manager di farlo sparire dalla mappa!
        treasureManager.removeTreasure(treasureId)
    }

    // Algoritmo di Haversine per calcolare la distanza tra punti GPS
    private fun calculateTotalDistance(points: List<LatLng>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
                results
            )
            distance += results[0]
        }
        return distance / 1000.0 // Convertiamo in km
    }

}