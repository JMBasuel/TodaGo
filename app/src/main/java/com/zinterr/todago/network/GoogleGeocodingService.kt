package com.zinterr.todago.network

import com.zinterr.todago.model.GeocodeResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleGeocodingService {
    @GET("geocode/json")
    fun reverseGeocode(
        @Query("latlng") latLng: String,
        @Query("key") apiKey: String
    ): Call<GeocodeResponse>
}