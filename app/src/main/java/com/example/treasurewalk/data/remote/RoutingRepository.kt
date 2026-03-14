package com.example.treasurewalk.data.remote

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RoutingRepository {

    // Inizializziamo Retrofit
    private val api: OrsApi = Retrofit.Builder()
        .baseUrl("https://api.openrouteservice.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OrsApi::class.java)

    // INSERISCI QUI LA TUA API KEY DI ORS
    private val apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjBmYjVhNGE3YjIwYTQ3MWVhMWUxZDY5ZTEwY2U2N2JhIiwiaCI6Im11cm11cjY0In0="

    suspend fun getRealWalkingPath(rawWaypoints: List<LatLng>): List<LatLng> {
        return withContext(Dispatchers.IO) { // Spostiamo il lavoro fuori dal Main Thread
            try {
                // 1. Convertiamo LatLng di Google in [Lng, Lat] per ORS
                val orsCoordinates = rawWaypoints.map {
                    listOf(it.longitude, it.latitude)
                }

                // 2. Facciamo la chiamata HTTP
                val response = api.getWalkingRoute(
                    apiKey = apiKey,
                    request = OrsRequest(coordinates = orsCoordinates)
                )

                // 3. Estraiamo la stringa magica
                val encodedPolyline = response.routes.firstOrNull()?.geometry

                if (encodedPolyline != null) {
                    // 4. Usiamo l'utility di Google per trasformare la stringa in veri LatLng!
                    PolyUtil.decode(encodedPolyline)
                } else {
                    rawWaypoints // Fallback: se fallisce, restituiamo i punti dritti (Gesù e i Muri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                rawWaypoints // Fallback in caso di mancanza di internet
            }
        }
    }
}