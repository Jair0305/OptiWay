package com.example.routeoptimizer.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicio Retrofit para la API de Distance Matrix de Google
 */
interface DistanceMatrixService {
    
    @GET("distancematrix/json")
    suspend fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("key") apiKey: String,
        @Query("departure_time") departureTime: String = "now",
        @Query("traffic_model") trafficModel: String = "best_guess",
        @Query("mode") mode: String = "driving"
    ): DistanceMatrixResponse
}

/**
 * Servicio para obtener rutas con polylines reales
 */
interface DirectionsService {
    
    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String = "",
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving",
        @Query("departure_time") departureTime: String = "now",
        @Query("alternatives") alternatives: String = "false"
    ): DirectionsResponse
}

// === Modelos de respuesta de Directions ===

data class DirectionsResponse(
    val routes: List<DirectionsRoute>,
    val status: String
)

data class DirectionsRoute(
    val overview_polyline: OverviewPolyline,
    val legs: List<RouteLeg>,
    val summary: String = ""
)

data class OverviewPolyline(
    val points: String
)

data class RouteLeg(
    val distance: ValueText?,
    val duration: ValueText?,
    val duration_in_traffic: ValueText?,
    val durationInTraffic: ValueText? = null,
    val start_address: String?,
    val end_address: String?
)

data class ValueText(
    val value: Int,
    val text: String
)

