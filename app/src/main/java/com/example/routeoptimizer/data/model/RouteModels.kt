package com.example.routeoptimizer.data.model

import com.google.android.gms.maps.model.LatLng

/**
 * Representa un punto de entrega/parada en la ruta
 */
data class RoutePoint(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val address: String,
    val latLng: LatLng,
    val order: Int = 0,
    // Nueva propiedad: posición fija/anclada
    val fixedPosition: FixedPosition = FixedPosition.NONE
)

/**
 * Define si una parada tiene una posición fija
 */
enum class FixedPosition {
    NONE,       // Puede moverse libremente en la optimización
    FIRST,      // Debe ser la primera parada (después del inicio)
    LAST,       // Debe ser la última parada (destino final)
    FIXED       // Mantiene su posición relativa actual
}

/**
 * Modo de viaje para calcular rutas
 */
enum class TravelMode {
    DRIVING,    // 🚗 Ruta por carretera
    DIRECT      // ✈️ Línea recta (distancia directa)
}

/**
 * Representa la matriz de distancias/tiempos entre puntos
 */
data class DistanceMatrix(
    val origins: List<RoutePoint>,
    val destinations: List<RoutePoint>,
    val durations: Array<IntArray>, // Tiempo en segundos entre cada par de puntos
    val distances: Array<IntArray>  // Distancia en metros entre cada par de puntos
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DistanceMatrix
        return origins == other.origins && destinations == other.destinations
    }

    override fun hashCode(): Int {
        var result = origins.hashCode()
        result = 31 * result + destinations.hashCode()
        return result
    }
}

/**
 * Resultado de la optimización de ruta
 */
data class OptimizedRoute(
    val orderedPoints: List<RoutePoint>,
    val totalDurationSeconds: Int,
    val totalDistanceMeters: Int,
    val savingsVsOriginalSeconds: Int = 0,
    val savingsVsOriginalMeters: Int = 0,
    val routeIndex: Int = 0,
    val polylinePoints: List<LatLng> = emptyList()
)

/**
 * Contiene múltiples alternativas de ruta
 */
data class RouteAlternatives(
    val routes: List<OptimizedRoute>,
    val selectedIndex: Int = 0
) {
    val bestRoute: OptimizedRoute get() = routes.first()
    val selectedRoute: OptimizedRoute get() = routes.getOrElse(selectedIndex) { bestRoute }
}

/**
 * Estado de la UI
 */
sealed class RouteState {
    object Idle : RouteState()
    object LoadingLocation : RouteState()
    object AddingPoints : RouteState()
    object Optimizing : RouteState()
    data class Optimized(val alternatives: RouteAlternatives) : RouteState()
    data class Error(val message: String) : RouteState()
}

/**
 * Resultado de búsqueda de lugares
 */
data class PlaceSearchResult(
    val placeId: String,
    val name: String,
    val address: String,
    val latLng: LatLng? = null
)

/**
 * Sugerencia de ruta alternativa por tráfico
 */
data class TrafficSuggestion(
    val currentRouteDuration: Int,        // Duración actual en segundos
    val suggestedRouteDuration: Int,      // Duración sugerida en segundos
    val trafficDelay: Int,                // Retraso por tráfico en segundos
    val suggestedRouteIndex: Int,         // Índice de la ruta sugerida
    val reason: String                    // "Tráfico intenso detectado"
) {
    val savingsSeconds: Int get() = currentRouteDuration - suggestedRouteDuration
    val hasSignificantSavings: Boolean get() = savingsSeconds >= 120 // 2+ minutos
}

/**
 * Paso de navegación turn-by-turn
 */
data class NavigationStep(
    val instruction: String,          // "Gira a la derecha en Av. Constitución"
    val distance: Int,                // Metros
    val duration: Int,                // Segundos
    val maneuver: String,             // "turn-right", "straight", etc.
    val startLocation: LatLng,
    val endLocation: LatLng
)

/**
 * Información extendida de ruta (ETA, costo, etc.)
 */
data class RouteInfo(
    val eta: Long,                    // Timestamp de llegada estimada
    val fuelCostEstimate: Double,     // Costo de gasolina estimado
    val tollCostEstimate: Double,     // Costo de casetas estimado
    val trafficCondition: TrafficCondition,
    val steps: List<NavigationStep>
)

enum class TrafficCondition {
    LIGHT,      // Verde - Fluido
    MODERATE,   // Amarillo - Moderado
    HEAVY,      // Rojo - Intenso
    UNKNOWN
}

