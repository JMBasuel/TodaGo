package com.zinterr.todago.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.zinterr.todago.model.Ride
import com.zinterr.todago.repository.RideRepository

class RideViewModel(app: Application) : AndroidViewModel(app) {

    val ride: LiveData<Ride?> = RideRepository.ride

    fun setRidePath(city: String, rideUID: String) {
        RideRepository.setRidePath(city, rideUID)
    }

    fun resetRide() {
        RideRepository.resetRide()
    }
}