package com.zinterr.todago.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.zinterr.todago.model.*
import kotlinx.coroutines.flow.MutableStateFlow

class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val _account = MutableStateFlow<Account?>(null)
    val account = _account
    private val _deviceID = MutableStateFlow<String?>(null)
    val deviceID = _deviceID
    private val _city = MutableStateFlow<String?>(null)
    val city = _city
    private val _key = MutableStateFlow<String?>(null)
    val key = _key
    private val _fee = MutableStateFlow<Double?>(null)
    val fee = _fee

    fun setAccount(value: Account?) {
        _account.value = value
    }

    fun setDeviceID(value: String?) {
        _deviceID.value = value
    }

    fun setCity(value: String?) {
        _city.value = value
    }

    fun setKey(value: String?) {
        _key.value = value
    }

    fun setFee(value: Double?) {
        _fee.value = value
    }
}