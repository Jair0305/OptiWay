package com.example.routeoptimizer.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Servicio para Google Places API (autocompletado y detalles)
 */
interface PlacesService {
    
    @GET("place/autocomplete/json")
    suspend fun autocomplete(
        @Query("input") input: String,
        @Query("key") apiKey: String,
        @Query("location") location: String? = null,
        @Query("radius") radius: Int = 50000,
        @Query("language") language: String = "es",
        @Query("components") components: String = "country:mx"
    ): PlacesAutocompleteResponse
    
    @GET("place/details/json")
    suspend fun getPlaceDetails(
        @Query("place_id") placeId: String,
        @Query("key") apiKey: String,
        @Query("fields") fields: String = "geometry,formatted_address,name"
    ): PlaceDetailsResponse
    
    @GET("geocode/json")
    suspend fun geocode(
        @Query("address") address: String,
        @Query("key") apiKey: String,
        @Query("region") region: String = "mx"
    ): GeocodeResponse
}

// === Modelos de respuesta ===

data class PlacesAutocompleteResponse(
    val predictions: List<PlacePrediction>,
    val status: String
)

data class PlacePrediction(
    val place_id: String,
    val description: String,
    val structured_formatting: StructuredFormatting?
)

data class StructuredFormatting(
    val main_text: String,
    val secondary_text: String?
)

data class PlaceDetailsResponse(
    val result: PlaceDetails?,
    val status: String
)

data class PlaceDetails(
    val name: String?,
    val formatted_address: String?,
    val geometry: Geometry?
)

data class Geometry(
    val location: LatLngLiteral
)

data class LatLngLiteral(
    val lat: Double,
    val lng: Double
)

data class GeocodeResponse(
    val results: List<GeocodeResult>,
    val status: String
)

data class GeocodeResult(
    val formatted_address: String,
    val geometry: Geometry
)
