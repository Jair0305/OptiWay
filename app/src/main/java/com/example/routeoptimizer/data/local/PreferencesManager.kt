package com.example.routeoptimizer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.routeoptimizer.data.model.OptimizedRoute
import com.example.routeoptimizer.data.model.RoutePoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "route_optimizer_prefs")

/**
 * Maneja la persistencia de preferencias y rutas guardadas
 */
class PreferencesManager(private val context: Context) {
    
    companion object {
        // Keys
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SAVED_ROUTES = stringPreferencesKey("saved_routes")
        val LAST_LOCATION_LAT = doublePreferencesKey("last_location_lat")
        val LAST_LOCATION_LNG = doublePreferencesKey("last_location_lng")
        val PREFERRED_TRAVEL_MODE = stringPreferencesKey("preferred_travel_mode")
        val ROUTE_HISTORY = stringPreferencesKey("route_history")
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // ==================== ONBOARDING ====================
    
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }
    
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = true
        }
    }
    
    // ==================== ÚLTIMA UBICACIÓN ====================
    
    val lastLocation: Flow<LatLng?> = context.dataStore.data.map { prefs ->
        val lat = prefs[LAST_LOCATION_LAT]
        val lng = prefs[LAST_LOCATION_LNG]
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
    
    suspend fun saveLastLocation(latLng: LatLng) {
        context.dataStore.edit { prefs ->
            prefs[LAST_LOCATION_LAT] = latLng.latitude
            prefs[LAST_LOCATION_LNG] = latLng.longitude
        }
    }
    
    // ==================== MODO DE VIAJE PREFERIDO ====================
    
    val preferredTravelMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREFERRED_TRAVEL_MODE] ?: "DRIVING"
    }
    
    suspend fun setPreferredTravelMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PREFERRED_TRAVEL_MODE] = mode
        }
    }
    
    // ==================== HISTORIAL DE RUTAS ====================
    
    val routeHistory: Flow<List<SavedRoute>> = context.dataStore.data.map { prefs ->
        val jsonString = prefs[ROUTE_HISTORY] ?: "[]"
        try {
            json.decodeFromString<List<SavedRoute>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun saveRoute(route: SavedRoute) {
        context.dataStore.edit { prefs ->
            val current = prefs[ROUTE_HISTORY] ?: "[]"
            val routes = try {
                json.decodeFromString<MutableList<SavedRoute>>(current)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // Agregar al inicio (más reciente primero)
            routes.add(0, route)
            
            // Mantener solo las últimas 20 rutas
            val trimmed = routes.take(20)
            
            prefs[ROUTE_HISTORY] = json.encodeToString(trimmed)
        }
    }
    
    suspend fun deleteRoute(routeId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[ROUTE_HISTORY] ?: "[]"
            val routes = try {
                json.decodeFromString<MutableList<SavedRoute>>(current)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            routes.removeAll { it.id == routeId }
            prefs[ROUTE_HISTORY] = json.encodeToString(routes)
        }
    }
    
    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs[ROUTE_HISTORY] = "[]"
        }
    }
}

/**
 * Representa una ruta guardada en el historial
 */
@Serializable
data class SavedRoute(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val points: List<SavedPoint>,
    val totalDurationSeconds: Int,
    val totalDistanceMeters: Int,
    val savingsSeconds: Int = 0,
    val savingsMeters: Int = 0
)

@Serializable
data class SavedPoint(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val order: Int
)

// Extensiones para convertir entre modelos
fun RoutePoint.toSavedPoint() = SavedPoint(
    name = name,
    address = address,
    lat = latLng.latitude,
    lng = latLng.longitude,
    order = order
)

fun SavedPoint.toRoutePoint() = RoutePoint(
    id = java.util.UUID.randomUUID().toString(),
    name = name,
    address = address,
    latLng = LatLng(lat, lng),
    order = order
)

fun OptimizedRoute.toSavedRoute(name: String) = SavedRoute(
    name = name,
    points = orderedPoints.map { it.toSavedPoint() },
    totalDurationSeconds = totalDurationSeconds,
    totalDistanceMeters = totalDistanceMeters,
    savingsSeconds = savingsVsOriginalSeconds,
    savingsMeters = savingsVsOriginalMeters
)
