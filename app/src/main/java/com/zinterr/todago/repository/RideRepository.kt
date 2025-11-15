package com.zinterr.todago.repository

import androidx.lifecycle.*
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.zinterr.todago.model.Ride

object RideRepository {
    private val _ride = MutableLiveData<Ride?>()
    val ride: LiveData<Ride?> get() = _ride

    private var dbRef: DatabaseReference? = null
    private var eventListener: ValueEventListener? = null

    fun setRidePath(city: String, rideUID: String) {
        if (!isRideActive()) {
            eventListener?.let { dbRef?.removeEventListener(it) }
            dbRef = Firebase.database.getReference("/Ride/$city/Rides/$rideUID")
            eventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(Ride::class.java)
                    _ride.postValue(value)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            dbRef?.addValueEventListener(eventListener!!)
        }
    }

    fun resetRide() {
        eventListener?.let { dbRef?.removeEventListener(it) }
        dbRef = null
        eventListener = null
        _ride.postValue(null)
    }

    fun isRideActive(): Boolean = eventListener != null && dbRef != null && _ride.value != null
}