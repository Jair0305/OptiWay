package com.example.routeoptimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== PALETA PREMIUM ====================

// Primary - Azul vibrante profesional
val PrimaryLight = Color(0xFF1565C0)
val PrimaryDark = Color(0xFF64B5F6)
val OnPrimaryLight = Color.White
val OnPrimaryDark = Color(0xFF0D47A1)

// Secondary - Verde éxito/optimizado
val SecondaryLight = Color(0xFF43A047)
val SecondaryDark = Color(0xFF81C784)
val OnSecondaryLight = Color.White
val OnSecondaryDark = Color(0xFF1B5E20)

// Tertiary - Naranja acción
val TertiaryLight = Color(0xFFFF6F00)
val TertiaryDark = Color(0xFFFFB74D)

// Backgrounds
val BackgroundLight = Color(0xFFFAFAFA)
val BackgroundDark = Color(0xFF121212)

val SurfaceLight = Color.White
val SurfaceDark = Color(0xFF1E1E1E)

val SurfaceVariantLight = Color(0xFFF5F5F5)
val SurfaceVariantDark = Color(0xFF2D2D2D)

// Containers
val PrimaryContainerLight = Color(0xFFE3F2FD)
val PrimaryContainerDark = Color(0xFF1E3A5F)
val OnPrimaryContainerLight = Color(0xFF0D47A1)
val OnPrimaryContainerDark = Color(0xFFBBDEFB)

val SecondaryContainerLight = Color(0xFFE8F5E9)
val SecondaryContainerDark = Color(0xFF1B3D1F)
val OnSecondaryContainerLight = Color(0xFF1B5E20)
val OnSecondaryContainerDark = Color(0xFFC8E6C9)

// Error
val ErrorLight = Color(0xFFD32F2F)
val ErrorDark = Color(0xFFEF5350)
val OnErrorLight = Color.White
val OnErrorDark = Color(0xFF7F0000)

val ErrorContainerLight = Color(0xFFFFEBEE)
val ErrorContainerDark = Color(0xFF4A1A1A)

// Text
val OnBackgroundLight = Color(0xFF1A1A1A)
val OnBackgroundDark = Color(0xFFE0E0E0)

val OnSurfaceLight = Color(0xFF1A1A1A)
val OnSurfaceDark = Color(0xFFE0E0E0)

val OnSurfaceVariantLight = Color(0xFF666666)
val OnSurfaceVariantDark = Color(0xFFB0B0B0)

// Outline
val OutlineLight = Color(0xFFBDBDBD)
val OutlineDark = Color(0xFF424242)

val OutlineVariantLight = Color(0xFFE0E0E0)
val OutlineVariantDark = Color(0xFF303030)

// ==================== COLORES ESPECIALES PARA RUTAS ====================

object RouteColors {
    // Colores para marcadores según posición
    val Origin = Color(0xFF00BCD4)      // Cyan - Tu ubicación
    val FirstStop = Color(0xFF4CAF50)   // Verde - Primera parada
    val LastStop = Color(0xFF2196F3)    // Azul - Última parada
    val FixedStop = Color(0xFFFF9800)   // Naranja - Parada fija
    val NormalStop = Color(0xFFF44336)  // Rojo - Parada normal
    
    // Colores para rutas
    val DrivingRoute = Color(0xFF4CAF50)    // Verde - Ruta en carro
    val DirectRoute = Color(0xFF2196F3)      // Azul - Línea recta
    val PreviewRoute = Color(0xFF9E9E9E)     // Gris - Preview antes de optimizar
    
    // Colores para estados
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFF44336)
    val Info = Color(0xFF2196F3)
    
    // Gradientes
    val PrimaryGradient = listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
    val SuccessGradient = listOf(Color(0xFF43A047), Color(0xFF81C784))
    val AccentGradient = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
}