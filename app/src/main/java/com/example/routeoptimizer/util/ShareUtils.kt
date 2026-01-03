package com.example.routeoptimizer.util

import android.content.Context
import android.content.Intent
import com.example.routeoptimizer.data.local.SavedRoute
import com.example.routeoptimizer.data.model.OptimizedRoute

/**
 * Utilidades para compartir rutas
 */
object ShareUtils {
    
    /**
     * Crea un texto formateado para compartir la ruta
     */
    fun formatRouteForSharing(
        route: OptimizedRoute,
        formatDuration: (Int) -> String,
        formatDistance: (Int) -> String
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("🗺️ *Mi Ruta Optimizada*")
        sb.appendLine()
        sb.appendLine("📊 *Resumen:*")
        sb.appendLine("⏱️ Tiempo total: ${formatDuration(route.totalDurationSeconds)}")
        sb.appendLine("📏 Distancia: ${formatDistance(route.totalDistanceMeters)}")
        
        if (route.savingsVsOriginalSeconds > 0) {
            sb.appendLine("✅ Ahorro: ${formatDuration(route.savingsVsOriginalSeconds)}")
        }
        
        sb.appendLine()
        sb.appendLine("📍 *Paradas (${route.orderedPoints.size}):*")
        
        route.orderedPoints.forEachIndexed { index, point ->
            val emoji = when (index) {
                0 -> "🟢" // Inicio
                route.orderedPoints.lastIndex -> "🏁" // Final
                else -> "📍"
            }
            sb.appendLine("$emoji ${index + 1}. ${point.name}")
            if (point.address.isNotBlank() && point.address != point.name) {
                sb.appendLine("   ${point.address}")
            }
        }
        
        sb.appendLine()
        sb.appendLine("🔗 Generado con Route Optimizer")
        
        return sb.toString()
    }
    
    /**
     * Crea un texto formateado desde una ruta guardada
     */
    fun formatSavedRouteForSharing(
        route: SavedRoute,
        formatDuration: (Int) -> String,
        formatDistance: (Int) -> String
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("🗺️ *${route.name}*")
        sb.appendLine()
        sb.appendLine("📊 *Resumen:*")
        sb.appendLine("⏱️ Tiempo total: ${formatDuration(route.totalDurationSeconds)}")
        sb.appendLine("📏 Distancia: ${formatDistance(route.totalDistanceMeters)}")
        
        if (route.savingsSeconds > 0) {
            sb.appendLine("✅ Ahorro: ${formatDuration(route.savingsSeconds)}")
        }
        
        sb.appendLine()
        sb.appendLine("📍 *Paradas (${route.points.size}):*")
        
        route.points.forEachIndexed { index, point ->
            val emoji = when (index) {
                0 -> "🟢"
                route.points.lastIndex -> "🏁"
                else -> "📍"
            }
            sb.appendLine("$emoji ${index + 1}. ${point.name}")
        }
        
        sb.appendLine()
        sb.appendLine("🔗 Generado con Route Optimizer")
        
        return sb.toString()
    }
    
    /**
     * Crea un link de Google Maps con todas las paradas
     */
    fun createGoogleMapsLink(route: OptimizedRoute): String {
        if (route.orderedPoints.isEmpty()) return ""
        
        val origin = route.orderedPoints.first().latLng
        val destination = route.orderedPoints.last().latLng
        
        val waypointsStr = if (route.orderedPoints.size > 2) {
            route.orderedPoints.drop(1).dropLast(1).joinToString("|") { 
                "${it.latLng.latitude},${it.latLng.longitude}" 
            }
        } else ""
        
        var url = "https://www.google.com/maps/dir/${origin.latitude},${origin.longitude}"
        
        if (waypointsStr.isNotEmpty()) {
            route.orderedPoints.drop(1).dropLast(1).forEach { point ->
                url += "/${point.latLng.latitude},${point.latLng.longitude}"
            }
        }
        
        url += "/${destination.latitude},${destination.longitude}"
        
        return url
    }
    
    /**
     * Comparte la ruta usando el intent de Android
     */
    fun shareRoute(
        context: Context,
        route: OptimizedRoute,
        formatDuration: (Int) -> String,
        formatDistance: (Int) -> String
    ) {
        val text = formatRouteForSharing(route, formatDuration, formatDistance)
        val mapsLink = createGoogleMapsLink(route)
        
        val fullText = if (mapsLink.isNotEmpty()) {
            "$text\n🗺️ Ver en Google Maps:\n$mapsLink"
        } else {
            text
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mi Ruta Optimizada")
            putExtra(Intent.EXTRA_TEXT, fullText)
        }
        
        context.startActivity(Intent.createChooser(intent, "Compartir ruta"))
    }
    
    /**
     * Comparte una ruta guardada
     */
    fun shareSavedRoute(
        context: Context,
        route: SavedRoute,
        formatDuration: (Int) -> String,
        formatDistance: (Int) -> String
    ) {
        val text = formatSavedRouteForSharing(route, formatDuration, formatDistance)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, route.name)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        
        context.startActivity(Intent.createChooser(intent, "Compartir ruta"))
    }
}
