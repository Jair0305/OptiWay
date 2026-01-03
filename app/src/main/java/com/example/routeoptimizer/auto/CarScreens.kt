package com.example.routeoptimizer.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.example.routeoptimizer.data.sync.NavigationState
import com.example.routeoptimizer.data.sync.RouteStateManager

/**
 * Pantalla principal en Android Auto
 * Muestra las opciones principales de la app
 */
class MainCarScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val routePoints = RouteStateManager.routePoints.value
        val navigationState = RouteStateManager.navigationState.value
        
        // Si hay navegación activa, ir directo a esa pantalla
        if (navigationState == NavigationState.NAVIGATING || navigationState == NavigationState.PAUSED) {
            screenManager.push(ActiveNavigationScreen(carContext))
        }
        
        val listBuilder = ItemList.Builder()
        
        // Opción 1: Iniciar nueva ruta o ver ruta actual
        if (routePoints.isNotEmpty()) {
            val optimizedRoute = RouteStateManager.optimizedRoute.value
            
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🗺️ Ruta Actual (${routePoints.size} paradas)")
                    .addText(
                        if (optimizedRoute != null) "✅ Ruta optimizada - Listo para navegar"
                        else "⏳ Pendiente de optimizar"
                    )
                    .setOnClickListener {
                        screenManager.push(RouteDetailsScreen(carContext))
                    }
                    .build()
            )
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("📱 Configurar desde teléfono")
                    .addText("Agrega paradas en tu teléfono")
                    .build()
            )
        }
        
        // Opción 2: Navegar rápido (si hay ruta optimizada)
        val optimizedRoute = RouteStateManager.optimizedRoute.value
        if (optimizedRoute != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🚀 ¡Iniciar Navegación!")
                    .addText("${optimizedRoute.orderedPoints.size - 1} paradas optimizadas")
                    .setOnClickListener {
                        RouteStateManager.startNavigation()
                        screenManager.push(ActiveNavigationScreen(carContext))
                    }
                    .build()
            )
        }
        
        // Opción 3: Abrir en Google Maps
        if (optimizedRoute != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🗺️ Abrir en Google Maps")
                    .addText("Navegar con Google Maps")
                    .setOnClickListener {
                        launchGoogleMapsNavigation()
                    }
                    .build()
            )
        }
        
        // Opción de refrescar
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Sincronizar")
                    .setOnClickListener {
                        invalidate() // Refresca la pantalla
                        CarToast.makeText(carContext, "Sincronizado", CarToast.LENGTH_SHORT).show()
                    }
                    .build()
            )
            .build()
        
        return ListTemplate.Builder()
            .setTitle("Route Optimizer")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
    
    private fun launchGoogleMapsNavigation() {
        val route = RouteStateManager.optimizedRoute.value ?: return
        val points = route.orderedPoints.drop(1) // Sin el punto de inicio
        
        if (points.isEmpty()) return
        
        val destination = points.last()
        val waypoints = points.dropLast(1)
        
        val waypointsParam = if (waypoints.isNotEmpty()) {
            "|" + waypoints.joinToString("|") { "${it.latLng.latitude},${it.latLng.longitude}" }
        } else ""
        
        val uri = Uri.parse(
            "google.navigation:q=${destination.latLng.latitude},${destination.latLng.longitude}" +
            "&waypoints=$waypointsParam"
        )
        
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        carContext.startCarApp(intent)
    }
}

/**
 * Pantalla de detalles de la ruta
 */
class RouteDetailsScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val routePoints = RouteStateManager.routePoints.value
        val optimizedRoute = RouteStateManager.optimizedRoute.value
        
        val listBuilder = ItemList.Builder()
        
        // Mostrar puntos en orden (optimizado si está disponible)
        val pointsToShow = optimizedRoute?.orderedPoints?.drop(1) ?: routePoints
        
        pointsToShow.forEachIndexed { index, point ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${index + 1}. ${point.name}")
                    .addText(point.address)
                    .build()
            )
        }
        
        // Acciones
        val actionStripBuilder = ActionStrip.Builder()
        
        if (optimizedRoute != null) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle("Navegar")
                    .setOnClickListener {
                        RouteStateManager.startNavigation()
                        screenManager.push(ActiveNavigationScreen(carContext))
                    }
                    .build()
            )
        }
        
        return ListTemplate.Builder()
            .setTitle("Detalles de Ruta")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStripBuilder.build())
            .build()
    }
}

/**
 * Pantalla de navegación activa
 */
class ActiveNavigationScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val currentStop = RouteStateManager.getCurrentStop()
        val nextStop = RouteStateManager.getNextStop()
        val remainingStops = RouteStateManager.getRemainingStops()
        val navigationState = RouteStateManager.navigationState.value
        
        // Verificar notificaciones pendientes
        val notification = RouteStateManager.notifications.value
        if (notification != null) {
            CarToast.makeText(carContext, notification.message, CarToast.LENGTH_SHORT).show()
            RouteStateManager.clearNotification()
        }
        
        if (navigationState == NavigationState.COMPLETED) {
            return MessageTemplate.Builder("🎉 ¡Ruta Completada!")
                .setTitle("Route Optimizer")
                .setHeaderAction(Action.BACK)
                .addAction(
                    Action.Builder()
                        .setTitle("Volver al inicio")
                        .setOnClickListener {
                            RouteStateManager.stopNavigation()
                            screenManager.popToRoot()
                        }
                        .build()
                )
                .build()
        }
        
        val currentName = currentStop?.name ?: "Destino"
        val nextName = nextStop?.name ?: "Fin de ruta"
        
        // Construir mensaje
        val message = StringBuilder()
        message.appendLine("📍 Ahora: $currentName")
        message.appendLine()
        message.appendLine("➡️ Siguiente: $nextName")
        message.appendLine()
        message.appendLine("📊 Faltan: $remainingStops paradas")
        
        return MessageTemplate.Builder(message.toString())
            .setTitle("Navegando...")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Completada")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener {
                        RouteStateManager.nextStop()
                        invalidate() // Refrescar pantalla
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Saltar")
                    .setOnClickListener {
                        RouteStateManager.skipStop()
                        invalidate() // Refrescar pantalla
                    }
                    .build()
            )
            .build()
    }
}
