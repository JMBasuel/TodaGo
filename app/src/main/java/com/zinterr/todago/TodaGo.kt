package com.zinterr.todago

import android.app.Application
import androidx.lifecycle.*
import com.zinterr.todago.util.LocationService

class TodaGo : Application(), DefaultLifecycleObserver {

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        LocationService.setAppInForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        LocationService.setAppInForeground(false)
    }
}