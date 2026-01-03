package com.example.routeoptimizer.util

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Utilidades para crear marcadores personalizados en el mapa
 */
object MarkerUtils {
    
    /**
     * Crea un marcador circular con número
     */
    fun createNumberedMarker(
        context: Context,
        number: Int,
        backgroundColor: Int,
        textColor: Int = android.graphics.Color.WHITE,
        size: Int = 100
    ): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Círculo de fondo
        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(100, 0, 0, 0))
        }
        
        val radius = size / 2f - 8
        canvas.drawCircle(size / 2f, size / 2f, radius, backgroundPaint)
        
        // Borde blanco
        val borderPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)
        
        // Número
        val textPaint = Paint().apply {
            color = textColor
            isAntiAlias = true
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val textBounds = Rect()
        val text = number.toString()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = size / 2f + textBounds.height() / 2f
        
        canvas.drawText(text, size / 2f, textY, textPaint)
        
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    
    /**
     * Crea marcador de origen (estrella o punto especial)
     */
    fun createOriginMarker(
        context: Context,
        backgroundColor: Int = android.graphics.Color.parseColor("#00BCD4"),
        size: Int = 100
    ): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Círculo exterior (halo)
        val haloPaint = Paint().apply {
            color = backgroundColor
            alpha = 80
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, haloPaint)
        
        // Círculo interior
        val innerPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, innerPaint)
        
        // Centro blanco
        val centerPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 6f, centerPaint)
        
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    
    /**
     * Crea marcador de destino (bandera)
     */
    fun createDestinationMarker(
        context: Context,
        backgroundColor: Int = android.graphics.Color.parseColor("#4CAF50"),
        size: Int = 100
    ): BitmapDescriptor {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Círculo de fondo
        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
            setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(100, 0, 0, 0))
        }
        
        val radius = size / 2f - 8
        canvas.drawCircle(size / 2f, size / 2f, radius, backgroundPaint)
        
        // Borde blanco
        val borderPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)
        
        // Bandera (emoji simulado con triángulo y línea)
        val flagPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        // Línea del mástil
        canvas.drawRect(
            size * 0.35f, size * 0.25f,
            size * 0.40f, size * 0.75f,
            flagPaint
        )
        
        // Bandera triangular
        val path = Path().apply {
            moveTo(size * 0.40f, size * 0.25f)
            lineTo(size * 0.70f, size * 0.38f)
            lineTo(size * 0.40f, size * 0.50f)
            close()
        }
        canvas.drawPath(path, flagPaint)
        
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    
    // Colores predefinidos
    object Colors {
        val Origin = android.graphics.Color.parseColor("#00BCD4")      // Cyan
        val FirstStop = android.graphics.Color.parseColor("#4CAF50")   // Verde
        val LastStop = android.graphics.Color.parseColor("#2196F3")    // Azul
        val FixedStop = android.graphics.Color.parseColor("#FF9800")   // Naranja
        val NormalStop = android.graphics.Color.parseColor("#F44336")  // Rojo
        val SelectedStop = android.graphics.Color.parseColor("#9C27B0") // Morado
    }
}
