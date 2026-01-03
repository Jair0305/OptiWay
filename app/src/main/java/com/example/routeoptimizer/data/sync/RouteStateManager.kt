package com.example.routeoptimizer.data.sync

import com.example.routeoptimizer.data.model.OptimizedRoute
import com.example.routeoptimizer.data.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton que mantiene el estado compartido entre la app del teléfono y Android Auto.
 * Actúa como un "puente" para sincronizar datos en tiempo real.
 */
object RouteStateManager {
    
    // Estado actual de los puntos de la ruta
    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()
    
    // Ruta optimizada actual
    private val _optimizedRoute = MutableStateFlow<OptimizedRoute?>(null)
    val optimizedRoute: StateFlow<OptimizedRoute?> = _optimizedRoute.asStateFlow()
    
    // Estado de la navegación
    private val _navigationState = MutableStateFlow(NavigationState.IDLE)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()
    
    // Índice de la parada actual (durante navegación)
    private val _currentStopIndex = MutableStateFlow(0)
    val currentStopIndex: StateFlow<Int> = _currentStopIndex.asStateFlow()
    
    // Mensajes/notificaciones para mostrar en Android Auto
    private val _notifications = MutableStateFlow<RouteNotification?>(null)
    val notifications: StateFlow<RouteNotification?> = _notifications.asStateFlow()
    
    // === Métodos para actualizar desde el teléfono ===
    
    fun updateRoutePoints(points: List<RoutePoint>) {
        _routePoints.value = points
        // Notificar a Android Auto
        _notifications.value = RouteNotification(
            type = NotificationType.ROUTE_UPDATED,
            message = "Ruta actualizada: ${points.size} paradas"
        )
    }
    
    fun setOptimizedRoute(route: OptimizedRoute) {
        _optimizedRoute.value = route
        _currentStopIndex.value = 0
        _notifications.value = RouteNotification(
            type = NotificationType.ROUTE_OPTIMIZED,
            message = "Ruta optimizada - ${route.orderedPoints.size - 1} paradas"
        )
    }
    
    fun startNavigation() {
        _navigationState.value = NavigationState.NAVIGATING
        _currentStopIndex.value = 0
        _notifications.value = RouteNotification(
            type = NotificationType.NAVIGATION_STARTED,
            message = "¡Navegación iniciada!"
        )
    }
    
    fun pauseNavigation() {
        _navigationState.value = NavigationState.PAUSED
    }
    
    fun resumeNavigation() {
        _navigationState.value = NavigationState.NAVIGATING
    }
    
    fun stopNavigation() {
        _navigationState.value = NavigationState.IDLE
        _currentStopIndex.value = 0
    }
    
    fun nextStop() {
        val route = _optimizedRoute.value ?: return
        val currentIndex = _currentStopIndex.value
        
        if (currentIndex < route.orderedPoints.size - 1) {
            _currentStopIndex.value = currentIndex + 1
            val nextPoint = route.orderedPoints[currentIndex + 1]
            _notifications.value = RouteNotification(
                type = NotificationType.STOP_COMPLETED,
                message = "Siguiente: ${nextPoint.name}"
            )
        } else {
            // Ruta completada
            _navigationState.value = NavigationState.COMPLETED
            _notifications.value = RouteNotification(
                type = NotificationType.ROUTE_COMPLETED,
                message = "¡Ruta completada!"
            )
        }
    }
    
    fun skipStop() {
        val route = _optimizedRoute.value ?: return
        val currentIndex = _currentStopIndex.value
        
        if (currentIndex < route.orderedPoints.size - 1) {
            val skippedPoint = route.orderedPoints[currentIndex]
            _currentStopIndex.value = currentIndex + 1
            _notifications.value = RouteNotification(
                type = NotificationType.STOP_SKIPPED,
                message = "Saltada: ${skippedPoint.name}"
            )
        }
    }
    
    fun addStopDuringNavigation(point: RoutePoint) {
        val currentRoute = _optimizedRoute.value ?: return
        val currentIndex = _currentStopIndex.value
        
        // Insertar el nuevo punto después de la posición actual
        val newPoints = currentRoute.orderedPoints.toMutableList()
        newPoints.add(currentIndex + 1, point)
        
        // Actualizar la ruta
        _optimizedRoute.value = currentRoute.copy(
            orderedPoints = newPoints
        )
        
        _notifications.value = RouteNotification(
            type = NotificationType.STOP_ADDED,
            message = "Nueva parada agregada: ${point.name}"
        )
    }
    
    fun removeStopDuringNavigation(pointId: String) {
        val currentRoute = _optimizedRoute.value ?: return
        
        val newPoints = currentRoute.orderedPoints.filter { it.id != pointId }
        
        _optimizedRoute.value = currentRoute.copy(
            orderedPoints = newPoints
        )
        
        _notifications.value = RouteNotification(
            type = NotificationType.STOP_REMOVED,
            message = "Parada eliminada"
        )
    }
    
    fun clearNotification() {
        _notifications.value = null
    }
    
    fun clearAll() {
        _routePoints.value = emptyList()
        _optimizedRoute.value = null
        _navigationState.value = NavigationState.IDLE
        _currentStopIndex.value = 0
        _notifications.value = null
    }
    
    // === Información de la parada actual ===
    
    fun getCurrentStop(): RoutePoint? {
        val route = _optimizedRoute.value ?: return null
        val index = _currentStopIndex.value
        return route.orderedPoints.getOrNull(index)
    }
    
    fun getNextStop(): RoutePoint? {
        val route = _optimizedRoute.value ?: return null
        val index = _currentStopIndex.value
        return route.orderedPoints.getOrNull(index + 1)
    }
    
    fun getRemainingStops(): Int {
        val route = _optimizedRoute.value ?: return 0
        return route.orderedPoints.size - _currentStopIndex.value - 1
    }
}

enum class NavigationState {
    IDLE,       // No hay navegación activa
    NAVIGATING, // Navegando activamente
    PAUSED,     // Navegación pausada
    COMPLETED   // Ruta completada
}

enum class NotificationType {
    ROUTE_UPDATED,
    ROUTE_OPTIMIZED,
    NAVIGATION_STARTED,
    STOP_COMPLETED,
    STOP_SKIPPED,
    STOP_ADDED,
    STOP_REMOVED,
    ROUTE_COMPLETED,
    REROUTING,      // Recalculando por desvío
    TRAFFIC_ALERT   // Alerta de tráfico
}

data class RouteNotification(
    val type: NotificationType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
