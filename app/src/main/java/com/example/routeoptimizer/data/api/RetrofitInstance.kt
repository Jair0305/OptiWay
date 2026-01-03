package com.example.routeoptimizer.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton para crear instancias de Retrofit
 */
object RetrofitInstance {
    
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val distanceMatrixService: DistanceMatrixService by lazy {
        retrofit.create(DistanceMatrixService::class.java)
    }
    
    val placesService: PlacesService by lazy {
        retrofit.create(PlacesService::class.java)
    }
    
    val directionsService: DirectionsService by lazy {
        retrofit.create(DirectionsService::class.java)
    }
}
