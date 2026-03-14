package com.example.treasurewalk.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// 1. IL MODELLO DI RICHIESTA
// ORS vuole una lista di coordinate nel formato [Longitudine, Latitudine]
data class OrsRequest(
    val coordinates: List<List<Double>>
)

// 2. IL MODELLO DI RISPOSTA
data class OrsResponse(
    val routes: List<OrsRoute>
)

data class OrsRoute(
    val geometry: String // Questa è la "Encoded Polyline" (la stringa criptata col percorso)
)

// 3. L'INTERFACCIA RETROFIT
interface OrsApi {
    @POST("v2/directions/foot-walking")
    suspend fun getWalkingRoute(
        @Header("Authorization") apiKey: String,
        @Body request: OrsRequest
    ): OrsResponse
}