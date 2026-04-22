package com.example.treasurewalk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.treasurewalk.data.local.TreasureDao
import com.example.treasurewalk.data.local.TreasureEntity
import com.example.treasurewalk.data.local.TreasureRarity
import com.example.treasurewalk.data.manager.RouteGenerator
import com.example.treasurewalk.data.manager.Treasure
import com.example.treasurewalk.data.manager.TreasureManager
import com.example.treasurewalk.data.remote.RoutingRepository
import com.example.treasurewalk.services.WalkTrackingService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class WalkViewModel(private val treasureDao: TreasureDao) : ViewModel() {

    private val routeGenerator = RouteGenerator()
    private val routingRepository = RoutingRepository()
    private val treasureManager = TreasureManager()
    
    val treasuresOnMap = treasureManager.activeTreasures
    val newTreasureDiscovered = treasureManager.newTreasureDiscovered

    private val _plannedRoute = MutableStateFlow<List<LatLng>>(emptyList())
    val plannedRoute = _plannedRoute.asStateFlow()

    val currentLocation = WalkTrackingService.currentLocation
    val pathPoints = WalkTrackingService.pathPoints

    val activeTreasures = treasureManager.activeTreasures

    val collectedTreasures = treasureDao.getAllCollectedTreasures()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- STATI SESSIONE ---
    private val _sessionXp = MutableStateFlow(0)
    val sessionXp = _sessionXp.asStateFlow()

    private val _sessionTreasuresCount = MutableStateFlow(0)
    val sessionTreasuresCount = _sessionTreasuresCount.asStateFlow()

    // Flag per evitare reset multipli della stessa missione
    private var isMissionActive = false

    init {
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
        // Se la missione è già attiva, non resettare nulla!
        // Questo evita reset quando si torna dalla schermata AR.
        if (isMissionActive) return
        
        isMissionActive = true
        _sessionXp.value = 0
        _sessionTreasuresCount.value = 0
        
        viewModelScope.launch(Dispatchers.Default) {
            WalkTrackingService.clearPath()
            val rawRoute = routeGenerator.generateLoop(startLoc, targetKm.toDouble())
            val realWalkingRoute = routingRepository.getRealWalkingPath(rawRoute)
            _plannedRoute.value = realWalkingRoute

            // Spawn tesori
            repeat(1) {
                treasureManager.spawnTreasureNear(startLoc, minRadius = 2.0, maxRadius = 10.0)
            }

            // Numero di tesori basato sulla distanza (3 tesori per ogni km)
            val numberOfTreasures = (targetKm * 3).toInt().coerceAtLeast(1)
            
            if (realWalkingRoute.isNotEmpty()) {
                val step = realWalkingRoute.size / numberOfTreasures
                for (i in 1..numberOfTreasures) {
                    val spawnIndex = (i * step).coerceAtMost(realWalkingRoute.size - 1)
                    val spawnPoint = realWalkingRoute[spawnIndex]
                    treasureManager.spawnTreasureNear(spawnPoint, minRadius = 5.0, maxRadius = 30.0)
                }
            }
        }
    }

    /**
     * Resetta completamente i dati della missione corrente.
     * Da chiamare quando si torna alla Home o si conclude definitivamente una partita.
     */
    fun resetMissionData() {
        isMissionActive = false
        _plannedRoute.value = emptyList()
        _sessionXp.value = 0
        _sessionTreasuresCount.value = 0
        treasureManager.clearTreasures()
        WalkTrackingService.clearPath()
    }

    fun endMission() {
        isMissionActive = false
    }

    val totalXp = treasureDao.getTotalXp()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalDistance = pathPoints.map { points ->
        calculateTotalDistance(points)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun onTreasureCollected(treasureId: String, lat: Double, lng: Double, type: TreasureRarity, xp: Int) {
        viewModelScope.launch {
            val newTreasure = TreasureEntity(
                type = type,
                lat = lat,
                lng = lng,
                xpAwarded = xp
            )
            treasureDao.insertTreasure(newTreasure)
        }

        // Incrementiamo i valori di sessione
        _sessionXp.value += xp
        _sessionTreasuresCount.value += 1

        treasureManager.removeTreasure(treasureId)
    }

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
        return distance / 1000.0
    }
}
