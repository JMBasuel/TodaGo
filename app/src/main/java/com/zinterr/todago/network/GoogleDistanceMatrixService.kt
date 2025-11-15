package com.zinterr.todago.network

import com.zinterr.todago.model.DistanceMatrixResponse
import retrofit2.Call
import retrofit2.http.*

interface GoogleDistanceMatrixService {
    @GET("distancematrix/json")
    fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("key") apiKey: String,
        @Query("language") language: String = "en",
        @Query("mode") mode: String = "driving",
        @Query("departure_time") departureTime: String = "now",
        @Query("traffic_model") trafficModel: String = "best_guess",
        @Query("units") units: String = "metric",
        @Query("avoid") avoid: String = "tolls|highways|ferries"
    ): Call<DistanceMatrixResponse>
}