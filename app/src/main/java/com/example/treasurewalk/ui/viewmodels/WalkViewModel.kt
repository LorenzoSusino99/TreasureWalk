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

class WalkViewModel(private val treasureDao: TreasureDao) : ViewModel() {

    private val routeGenerator = RouteGenerator()
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
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)

                    treasureManager.checkProximity(userLatLng) { raccolto ->
                        // Quando la callback ci avvisa che abbiamo preso un tesoro, lo salviamo nel DB!
                        salvaTesoroNelDb(raccolto, userLatLng)
                    }
                }
            }
        }
    }

    fun startNewMission(startLoc: LatLng, targetKm: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            // Generiamo il percorso
            val route = routeGenerator.generateLoop(startLoc, targetKm.toDouble())
            _plannedRoute.value = route

            // EXTRA: Generiamo anche 3 tesori casuali lungo la strada per iniziare!
            treasureManager.spawnTreasureNear(startLoc, minRadius = 100.0, maxRadius = (targetKm * 1000) / 2.0)
            treasureManager.spawnTreasureNear(startLoc, minRadius = 100.0, maxRadius = (targetKm * 1000) / 2.0)
            treasureManager.spawnTreasureNear(startLoc, minRadius = 100.0, maxRadius = (targetKm * 1000) / 2.0)
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
    fun onTreasureCollected(lat: Double, lng: Double, type: TreasureRarity, xp: Int) {
        viewModelScope.launch {
            val newTreasure = TreasureEntity(
                type = type,
                lat = lat,
                lng = lng,
                xpAwarded = xp
            )
            treasureDao.insertTreasure(newTreasure)
        }
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