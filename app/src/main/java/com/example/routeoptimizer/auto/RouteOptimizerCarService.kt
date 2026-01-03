package com.example.routeoptimizer.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Servicio de Android Auto - Este es el punto de entrada
 * cuando la app se ejecuta en el carro
 */
class RouteOptimizerCarService : CarAppService() {
    
    override fun createHostValidator(): HostValidator {
        // Para desarrollo/uso local, aceptamos cualquier host
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // En producción, solo hosts conocidos (Google, Samsung, etc.)
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }
    
    override fun onCreateSession(): Session {
        return RouteOptimizerSession()
    }
}

/**
 * Sesión de Android Auto - Maneja el ciclo de vida de la app en el carro
 */
class RouteOptimizerSession : Session() {
    
    override fun onCreateScreen(intent: Intent): Screen {
        return MainCarScreen(carContext)
    }
}
