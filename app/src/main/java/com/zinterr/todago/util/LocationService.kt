package com.zinterr.todago.util

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.*
import androidx.core.app.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.remoteConfig
import com.zinterr.todago.LoginActivity
import com.zinterr.todago.R
import com.zinterr.todago.model.Global.toLatLng
import com.zinterr.todago.model.Global.toLatLngString
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.getLocation
import com.zinterr.todago.model.Global.toLocalAddress
import com.zinterr.todago.network.RetrofitClient
import retrofit2.*
import kotlin.math.roundToInt

class LocationService: Service() {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var geocodeResponse: Callback<GeocodeResponse>? = null
    private var activeEventListener: ValueEventListener? = null
    private var rideEventListener: ValueEventListener? = null
    private var locationCallback: LocationCallback? = null
    private var geocodeCall: Call<GeocodeResponse>? = null
    private val uid = Firebase.auth.currentUser?.uid!!
    private var activeRef: DatabaseReference? = null
    private val remoteConfig = Firebase.remoteConfig
    private val dbRef = Firebase.database.reference
    private var rideRef: DatabaseReference? = null
    private var currentRideUID: String? = null
    private var currentCity: String? = null
    private var discount: Boolean? = null
    private var driver: Driver? = null
    private var fee: Double? = null
    private var key: String? = null
    private var total: Int? = null

    companion object {
        var instance: LocationService? = null
        lateinit var prefs: SharedPreferences
        private var appInForeground = true
        const val EXTRA_CITY = "CITY"
        const val EXTRA_RIDE_UID = "RIDE_UID"
        const val NOTIFICATION_ID = 1001

        fun bind(service: LocationService) {
            instance = service
        }

        fun startService(context: Context, city: String, rideUID: String, discount: Boolean) {
            prefs = context.getSharedPreferences("session_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("appInForeground", true)
                .putBoolean("discount", discount)
                .apply()
            if (instance != null) instance?.stopLocationService()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(context, LocationService::class.java).apply {
                    putExtra(EXTRA_CITY, city)
                    putExtra(EXTRA_RIDE_UID, rideUID)
                }
                ContextCompat.startForegroundService(context, intent)
            }, 0)
        }

        fun stopService() {
            instance?.stopLocationService()
        }

        fun setAppInForeground(foreground: Boolean) {
            if (instance != null) {
                appInForeground = foreground
                prefs.edit().putBoolean("appInForeground", foreground).apply()
                if (!foreground) {
                    instance?.currentCity?.let { city ->
                        instance?.currentRideUID?.let { rideUID ->
                            instance?.startRideMonitor(city, rideUID)
                        }
                    }
                } else if (instance?.rideEventListener != null)
                    instance?.rideRef?.removeEventListener(instance?.rideEventListener!!)
            }
        }

        fun startLocationUpdates() {
            instance?.currentCity?.let { city ->
                instance?.currentRideUID?.let { rideUID ->
                    instance?.startLocationUpdates(city, rideUID)
                }
            }
        }

        fun stopLocationUpdates() {
            instance?.removeLocationUpdates()
        }
    }

    override fun onCreate() {
        super.onCreate()
        bind(this)
        createNotificationChannel()
        fetchAPI()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val city = intent?.getStringExtra(EXTRA_CITY)
        val rideUID = intent?.getStringExtra(EXTRA_RIDE_UID)
        appInForeground = prefs.getBoolean("appInForeground", false)
        discount = prefs.getBoolean("discount", false)
        if (city != null && rideUID != null) {
            currentCity = city
            currentRideUID = rideUID
        } else checkActiveBooking { city, ride ->
            currentCity = city
            currentRideUID = ride.uid!!
            if (!appInForeground) instance?.startRideMonitor(currentCity!!, currentRideUID!!)
        }
        val notification = buildNotification("Your booking is active . . .")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun checkActiveBooking(onComplete: (String, Ride) -> Unit) {
        getLocation(this) { latLng ->
            if (latLng != null) {
                geocodeResponse = object : Callback<GeocodeResponse> {
                    override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                        if (response.isSuccessful) {
                            val city = response.body()?.toLocalAddress()!!.city!!
                            activeEventListener = object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    var currentRide: Ride? = null
                                    if (snapshot.exists()) {
                                        snapshot.children.forEach { item ->
                                            val ride = item.getValue(Ride::class.java)!!
                                            if (ride.commuter?.values?.firstOrNull { it.uid == Firebase.auth.currentUser?.uid} != null) {
                                                currentRide = ride
                                                return@forEach
                                            }
                                        }
                                        if (currentRide != null) onComplete(city, currentRide)
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {}
                            }
                            activeRef = dbRef.child("Ride/$city/Rides")
                            activeRef?.addListenerForSingleValueEvent(activeEventListener!!)
                        }
                    }
                    override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
                }
                if (key == null) fetchAPI {
                    geocodeCall = RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), key!!)
                    geocodeCall?.enqueue(geocodeResponse!!)
                } else {
                    geocodeCall = RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), key!!)
                    geocodeCall?.enqueue(geocodeResponse!!)
                }
            }
        }
    }

    private fun stopLocationService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates(city: String, rideUID: String) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
            5000).setMinUpdateIntervalMillis(3000).build()
        if (locationCallback == null && fusedLocationClient == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    super.onLocationResult(result)
                    val location = result.lastLocation ?: return
                    if (uid == Firebase.auth.currentUser?.uid) {
                        dbRef.child("Ride/$city/Rides/$rideUID/commuter/$uid/current")
                            .setValue(location.toLatLngString())
                        if (appInForeground) updateNotification("Your booking is active . . .")
                    } else stopLocationService()
                }
            }
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient?.requestLocationUpdates(
                    request, locationCallback!!, Looper.getMainLooper())
            }
        }
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) key = remoteConfig.getString("maps_api_key")
            onComplete?.invoke()
        }
    }

    private fun startRideMonitor(city: String, rideUID: String) {
        if (uid == Firebase.auth.currentUser?.uid) {
            rideEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val ride = snapshot.getValue(Ride::class.java)!!
                        rideStatus(ride)
                        rideMonitor(ride)
                        setupPrice(ride)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            rideRef = dbRef.child("Ride/$city/Rides/$rideUID")
            rideRef?.addValueEventListener(rideEventListener!!)
        } else stopLocationService()
    }

    private fun setupPrice(ride: Ride) {
        val commuter = ride.commuter?.getOrDefault(uid, null)
        if (ride.driver != null && commuter != null) {
            val cost = getRideCost(ride, commuter.distance!!)
            val rideCost = cost.first - cost.second
            val serviceFee = getServiceFee(commuter, cost.first)
            total = (rideCost + (if (isSpecial(ride, commuter.distance)) 0F else (cost.first * (commuter.passenger!! - 1))) + serviceFee).roundToInt()
            if (ride.commuter.getOrDefault(uid, null)?.price != total)
                dbRef.child("Ride/$currentCity/Rides/${ride.uid}/commuter/$uid/price")
                    .setValue(total)
        }
    }

    private fun getServiceFee(commuter: Commuter, cost: Float): Int = ((cost * commuter.passenger!!) * (fee!!/100))
        .roundToInt().coerceAtLeast(1)

    private fun getRideCost(ride: Ride, distance: Int): Pair<Float, Float> {
        val perKM = if (isSpecial(ride, distance)) ride.driver!!.matrix?.specialPerKM else ride.driver!!.matrix?.perKm
        val discount = if (discount == true) ride.driver.matrix?.discount else 0
        val discounted = ((distance/1000F) * perKM!!) * discount!!
        return Pair(((distance/1000F) * perKM).coerceAtLeast(if (isSpecial(ride, distance)) 100F else 40F),
            discounted)
    }

    private fun isSpecial(ride: Ride, distance: Int): Boolean = ride.solo == true &&
            ride.passenger!! <= 2 && distance / 1000F > 1.5F

    private fun rideStatus(ride: Ride) {
        ride.commuter?.getOrDefault(uid, null)?.status?.let { status ->
            if (status.contains("ACTIVE")) {
                ride.status?.let {
                    when {
                        it.contains("WAITING_DRIVER") -> updateNotification("Drivers are being notified . . .")
                        it.contains("WAITING_PASSENGER") -> updateNotification("Waiting for other passengers . . .")
                        it.contains("APPROACHING") -> updateNotification("Your driver is coming for pickup . . .")
                        else -> updateNotification("Driver is on standby . . .")
                    }
                }
                if (status.contains("DRIVING")) updateNotification("You will reach your destination soon . . .")
                if (driver != null && ride.driver == null) {
                    driver = null
                    updateNotification("Your rider changed their mind. Notifying other drivers . . .")
                }
            } else {
                updateNotification("Your ride has been completed")
                saveHistory(ride)
            }
        }
    }

    // ADD CHECK FOR RIDE ETA AND DESTINATION ETA FOR COMMUTER SAFETY FEATURE
    private fun rideMonitor(ride: Ride) {
        ride.commuter?.getOrDefault(uid, null)?.let { commuter ->
            if (ride.driver != null) {
                driver = ride.driver
                instance?.startLocationUpdates(currentCity!!, currentRideUID!!)
            } else stopLocationUpdates()
            val commuters = ride.commuter.values.filter { it.status!!.contains("ACTIVE") }
            if (commuters.isNotEmpty() && commuters.all { it.status!!.contains("DRIVING") } && ride.status?.contains("DRIVING") == false) {
                dbRef.child("Ride/$currentCity/Rides/${ride.uid}/status")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            currentData.getValue(String::class.java)?.contains("DRIVING")?.let {
                                if (!it) currentData.value = "DRIVING"
                            }
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
                    })
            }
            if (isRideActive(ride, commuter)) {
                if (!commuter.status!!.contains("DRIVING")) {
                    val distance = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver?.current!!.toLatLng())
                    if (distance < 15) setCommuterStatus(ride.uid!!, "DRIVING")
                }
                if (!commuter.status.contains("COMPLETED") && commuter.status.contains("DRIVING")) {
                    val current = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver?.current!!.toLatLng())
                    val end = distanceBetween(commuter.end!!.toLatLng(),
                        commuter.current.toLatLng())
                    if (current > 15 || end < 15) setCommuterStatus(ride.uid!!, "COMPLETED")
                }
            }
        }
    }

    private fun distanceBetween(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude,
            p2.latitude, p2.longitude, results)
        return results[0]
    }

    private fun setCommuterStatus(rideUID: String, status: String) {
        dbRef.child("Ride/$currentCity/Rides/$rideUID/commuter/$uid/status")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    currentData.getValue(String::class.java)?.contains(status)?.let {
                        if (it) return Transaction.abort()
                    }
                    val states = currentData.getValue(String::class.java)?.split("|")?.toMutableList()
                    if (status == "COMPLETED") states?.clear()
                    states?.add(status)
                    currentData.value = states?.joinToString("|") ?: ""
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
            })
    }

    private fun isRideActive(ride: Ride, commuter: Commuter): Boolean =
        ride.driver != null && commuter.status!!.contains("ACTIVE") &&
                ride.status!!.contains("APPROACHING")

    private fun saveHistory(ride: Ride) {
        val history = History(
            ride.reference,
            ride.solo,
            ride.commuter?.getOrDefault(uid, null)?.startAddress,
            ride.commuter?.getOrDefault(uid, null)?.endAddress,
            ride.passenger,
            ride.commuter?.getOrDefault(uid, null)?.price,
            "${ride.driver!!.name}",
            ride.dateTime)
        dbRef.child("Account/TodaGo/$uid/History")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.child("${history.uid}").value != null) return Transaction.abort()
                    currentData.child("${history.uid}").value = history
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) saveHistory(ride)
                    else if (committed) stopLocationService()
                }
            })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("toda_go_location_channel", "Location Service",
            NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "toda_go_location_channel")
            .setContentTitle("TodaGo is monitoring your location")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_todago)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_stat_todago))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (!appInForeground) notification.setContentIntent(pendingIntent)
            .addAction(0, "View", pendingIntent)
        return notification.build()
    }

    private fun updateNotification(message: String) {
        val notification = buildNotification(message)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeLocationUpdates()
        geocodeCall?.cancel()
        geocodeResponse = null
        geocodeCall = null
        activeEventListener?.let { activeRef?.removeEventListener(it) }
        activeEventListener = null
        activeRef = null
        rideEventListener?.let { rideRef?.removeEventListener(it) }
        rideEventListener = null
        rideRef = null
    }

    private fun removeLocationUpdates() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        locationCallback = null
        fusedLocationClient = null
    }
}