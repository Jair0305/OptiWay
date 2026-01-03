package com.example.routeoptimizer.data.repository

import com.example.routeoptimizer.data.api.DistanceMatrixResponse
import com.example.routeoptimizer.data.api.RetrofitInstance
import com.example.routeoptimizer.data.model.RoutePoint

/**
 * Repositorio para obtener datos de distancias/tiempos de la API de Google
 */
class RouteRepository {
    
    private val distanceMatrixService = RetrofitInstance.distanceMatrixService
    
    /**
     * Obtiene la matriz de tiempos de viaje entre todos los puntos
     * Usa tráfico en tiempo real
     * 
     * @param points Lista de todos los puntos (incluyendo el origen)
     * @param apiKey Tu API key de Google Maps
     */
    suspend fun getDistanceMatrix(
        points: List<RoutePoint>,
        apiKey: String
    ): DistanceMatrixResponse {
        // Construir string de orígenes y destinos
        val locations = points.joinToString("|") { 
            "${it.latLng.latitude},${it.latLng.longitude}" 
        }
        
        return distanceMatrixService.getDistanceMatrix(
            origins = locations,
            destinations = locations,
            apiKey = apiKey,
            departureTime = "now", // Usar tráfico actual
            trafficModel = "best_guess"
        )
    }
    
    /**
     * Convierte la respuesta de la API a matrices de enteros
     */
    fun parseMatrixResponse(
        response: DistanceMatrixResponse,
        pointCount: Int
    ): Pair<Array<IntArray>, Array<IntArray>> {
        val timeMatrix = Array(pointCount) { IntArray(pointCount) }
        val distanceMatrix = Array(pointCount) { IntArray(pointCount) }
        
        for (i in 0 until pointCount) {
            for (j in 0 until pointCount) {
                val element = response.rows[i].elements[j]
                
                // Usar duration_in_traffic si está disponible, sino duration normal
                timeMatrix[i][j] = element.durationInTraffic?.value 
                    ?: element.duration?.value 
                    ?: Int.MAX_VALUE
                    
                distanceMatrix[i][j] = element.distance?.value ?: Int.MAX_VALUE
            }
        }
        
        return Pair(timeMatrix, distanceMatrix)
    }
}
