package com.example.routeoptimizer.data.api

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de la API de Distance Matrix de Google
 */
data class DistanceMatrixResponse(
    @SerializedName("destination_addresses")
    val destinationAddresses: List<String>,
    @SerializedName("origin_addresses")
    val originAddresses: List<String>,
    val rows: List<Row>,
    val status: String
)

data class Row(
    val elements: List<Element>
)

data class Element(
    val distance: Distance?,
    val duration: Duration?,
    @SerializedName("duration_in_traffic")
    val durationInTraffic: DurationInTraffic?,
    val status: String
)

data class Distance(
    val text: String,
    val value: Int // metros
)

data class Duration(
    val text: String,
    val value: Int // segundos
)

data class DurationInTraffic(
    val text: String,
    val value: Int // segundos con tráfico
)
