package com.zinterr.todago.viewmodel

import androidx.lifecycle.*

class VerifiedViewModel : ViewModel() {

    private val _isVerified = MutableLiveData(false)
    val isVerified: LiveData<Boolean> get() = _isVerified

    fun setVerified(value: Boolean) {
        _isVerified.value = value
    }
}