package com.zinterr.todago.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val MAPS_BASE_URL = "https://maps.googleapis.com/maps/api/"

    private val retrofit: Retrofit.Builder by lazy {
        Retrofit.Builder().addConverterFactory(GsonConverterFactory.create())
    }

    val directionsService: GoogleDirectionsService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleDirectionsService::class.java)
    }

    val distanceMatrixService: GoogleDistanceMatrixService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleDistanceMatrixService::class.java)
    }

    val geocodeService: GoogleGeocodingService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleGeocodingService::class.java)
    }
}