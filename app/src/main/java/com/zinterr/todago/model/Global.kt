package com.zinterr.todago.model

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng

object Global {

    const val VERSION = "1.0.0"
    var historyPrev: String? = null
    private var lastClickTime = 0L
    var navPosition: Int = 0
    var app: App? = null

    fun View.setOnDebouncedClickListener(interval: Long = 750, onClick: (View) -> Unit) {
        setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime >= interval) {
                lastClickTime = currentTime
                onClick(it)
            }
        }
    }

    fun timeTo12(time: String): String {
        val (hours, minutes) = time.split(":").map(String::toInt)
        val period = if (hours < 12) "AM" else "PM"
        val hour = (if (hours % 12 == 0) 12 else hours % 12).toString().padStart(2, '0')
        return "$hour:${minutes.toString().padStart(2, '0')} $period"
    }

    fun LatLngLiteral.toLatLng(): LatLng = LatLng(lat, lng)

    fun String.toLatLng(): LatLng = LatLng(substringBefore(',').toDouble(), substringAfter(',').toDouble())

    fun LatLng.toLatLngString(): String = "$latitude,$longitude"

    fun Location.toLatLngString(): String = "$latitude,$longitude"

    fun isNotificationDisabled(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun getLocation(context: Context, onComplete: (LatLng?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onComplete(null)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) onComplete(LatLng(location.latitude, location.longitude))
            else {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                    .setWaitForAccurateLocation(true)
                    .setMaxUpdates(1)
                    .setMinUpdateIntervalMillis(0)
                    .build()
                fusedLocationClient.requestLocationUpdates(request,
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedLocationClient.removeLocationUpdates(this)
                            val loc = result.lastLocation
                            if (loc != null) onComplete(LatLng(loc.latitude, loc.longitude))
                            else onComplete(null)
                        }
                    }, Looper.getMainLooper())
            }
        }.addOnFailureListener {
            onComplete(null)
        }
    }

    fun GeocodeResponse.toLocalAddress(): LocalAddress {
        val results = this.results
        var province: String? = null
        var city: String? = null
        var locality: String? = null
        var extra: String? = null
        results.forEach { result ->
            result.addressComponents.forEach { comp ->
                when {
                    province == null && comp.types.contains("administrative_area_level_2") -> province = comp.longName
                    city == null && comp.types.contains("locality") -> city = comp.longName
                    locality == null && comp.types.contains("sublocality") || comp.types.contains("sublocality_level_2") -> locality = comp.longName
                    extra == null && comp.types.contains("route") -> extra = comp.longName
                }
            }
        }
        return LocalAddress(province = province, city = city,
            locality = locality, extra = extra)
    }

    fun checkGPS(context: Context,
        locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onComplete: (String?) -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0).build()
        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        val settingsClient = LocationServices.getSettingsClient(context)
        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                onComplete(null)
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Log.e("Location", "Error prompting GPS enable: ${sendEx.message}")
                        onComplete("Location is not available")
                    }
                }
            }
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Activity.hideKeyboard() {
        val view = currentFocus ?: window.decorView
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun Context.hideKeyboard(view: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}