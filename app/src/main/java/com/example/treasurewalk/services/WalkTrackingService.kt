package com.example.treasurewalk.services

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.pm.ServiceInfo

class WalkTrackingService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "walk_channel"
        private const val NOTIFICATION_ID = 1

        // Stato statico per permettere alla UI di osservare i dati senza bound service complesso
        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation = _currentLocation.asStateFlow()

        private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
        val pathPoints = _pathPoints.asStateFlow()
        fun clearPath() {
            _pathPoints.value = emptyList()
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundService()
        startLocationUpdates()
        // START_NOT_STICKY: se il sistema uccide il processo, NON riavviare il servizio.
        // Questo evita che la localizzazione rimanga attiva dopo un crash o kill.
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camminata in corso...")
            .setContentText("L'app sta cercando tesori intorno a te.")
            .setSmallIcon(R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        // Verifica permessi dovrebbe essere fatta prima di chiamare questo,
        // ma per sicurezza usiamo un try-catch o controllo soppresso
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
        } catch (unlikely: SecurityException) {
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _currentLocation.value = location

                // Aggiorniamo la lista dei punti per il disegno della polilinea
                val newLatLng = LatLng(location.latitude, location.longitude)
                _pathPoints.value = _pathPoints.value + newLatLng
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Tracking Camminata",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * Chiamato quando l'utente rimuove l'app dai recenti (swipe).
     * Ferma immediatamente la localizzazione e il servizio.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopLocationAndCleanup()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationAndCleanup()
    }

    /**
     * Rimuove gli aggiornamenti di posizione dal GPS.
     * Chiamato sia da onDestroy() che da onTaskRemoved() per sicurezza.
     */
    private fun stopLocationAndCleanup() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}