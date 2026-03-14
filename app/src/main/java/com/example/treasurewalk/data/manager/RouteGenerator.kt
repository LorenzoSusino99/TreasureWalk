package com.example.treasurewalk.data.manager

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*
import kotlin.random.Random

class RouteGenerator {

    /**
     * Genera una lista di punti LatLng che formano un anello.
     * @param start Posizione iniziale dell'utente
     * @param totalKm Distanza totale che l'utente vuole percorrere
     */
    fun generateLoop(start: LatLng, totalKm: Double): List<LatLng> = buildList {
        val sideLengthKm = totalKm / 4.0
        val radiusDeg = sideLengthKm / 111.0

        add(start)

        val p1 = calculateOffset(start, radiusDeg, 0.0 + Random.nextDouble(-20.0, 20.0))
        add(p1)

        val p2 = calculateOffset(p1, radiusDeg, 90.0 + Random.nextDouble(-20.0, 20.0))
        add(p2)

        val p3 = calculateOffset(p2, radiusDeg, 180.0 + Random.nextDouble(-20.0, 20.0))
        add(p3)

        add(start) // Chiudiamo il poligono
    }

    /**
     * Calcola una nuova coordinata partendo da un punto, una distanza in gradi e un angolo (bearing).
     */
    private fun calculateOffset(from: LatLng, distanceDeg: Double, bearingDeg: Double): LatLng {
        val bearingRad = Math.toRadians(bearingDeg)

        val newLat = from.latitude + distanceDeg * cos(bearingRad)
        // La longitudine va corretta in base alla latitudine per mantenere la proporzione
        val newLng = from.longitude + (distanceDeg * sin(bearingRad)) / cos(Math.toRadians(from.latitude))

        return LatLng(newLat, newLng)
    }
}