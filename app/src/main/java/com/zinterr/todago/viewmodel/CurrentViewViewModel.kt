package com.zinterr.todago.viewmodel

import android.view.View
import androidx.lifecycle.*

class CurrentViewViewModel: ViewModel() {

    private val _currentView = MutableLiveData<View?>()
    val currentView: LiveData<View?> get() = _currentView

    fun setCurrentView(value: View) {
        _currentView.value = value
    }
}