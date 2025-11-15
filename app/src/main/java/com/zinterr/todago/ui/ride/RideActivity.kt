package com.zinterr.todago.ui.ride

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.maps.android.PolyUtil
import com.zinterr.todago.R
import com.zinterr.todago.databinding.ActivityRideBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.Global.toLatLng
import com.zinterr.todago.network.RetrofitClient
import com.zinterr.todago.ui.popup.InfoDialog
import com.zinterr.todago.util.LocationService
import com.zinterr.todago.viewmodel.RideViewModel
import retrofit2.*
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.picasso.Picasso
import com.zinterr.todago.adapter.PassengerAdapter
import com.zinterr.todago.model.Global.getLocation
import com.zinterr.todago.ui.popup.ConfirmDialog
import com.zinterr.todago.util.snackBar
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n")
class RideActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRideBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private val viewModel: RideViewModel by viewModels()
    private lateinit var polylines: ArrayList<Polyline>
    private var confirmDialog: ConfirmDialog? = null
    private var bounds: LatLngBounds.Builder? = null
    private lateinit var markers: ArrayList<Marker>
    private lateinit var dbRef: DatabaseReference
    private var infoDialog: InfoDialog? = null
    private lateinit var account: Account
    private var currentRide: Ride? = null
    private var location: LatLng? = null
    private lateinit var map: GoogleMap
    private var isView: Boolean? = null
    private var driver: Driver? = null
    private var total: Int? = null
    private var canceled = false
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rideActivity) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.bottomSheet.setPadding(0, 0, 0, 40 + navBars.bottom)
            insets
        }
        initialize()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_ride) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fetchAPI {
            if (isView == true) setupView()
            else viewModel.ride.observe(this) { ride ->
                observeRide(ride)
            }
        }

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.rvPassenger.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@RideActivity)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        if (isView != true) binding.btnCamera.setOnDebouncedClickListener {
            animateZoom()
        }

        binding.btnCall.setOnDebouncedClickListener {
            callDriver()
        }

        binding.btnChat.setOnDebouncedClickListener {
            chatDriver()
        }

        binding.btnComplete.setOnDebouncedClickListener {
            completeRide()
        }

        binding.btnCancel.setOnDebouncedClickListener {
            cancelRide()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(
            this, R.raw.map_style))
        map.setMinZoomPreference(10f)
        map.setMaxZoomPreference(40f)
        if (isView == true) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
            } else snackBar(binding.root, "Location permission denied")
            val mapView = (supportFragmentManager.findFragmentById(R.id.map_ride) as SupportMapFragment).view
            mapView?.findViewWithTag<View>("GoogleMapMyLocationButton")?.let { button ->
                val params = button.layoutParams as RelativeLayout.LayoutParams
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                params.setMargins(0, 0, 0, (binding.mapRide.height * 0.01).toInt())
                button.layoutParams = params
            }
            getLocation(this) { latLng ->
                if (latLng != null) {
                    location = latLng
                    map.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                }
            }
            map.setOnMyLocationButtonClickListener {
                location?.let {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(location!!, 16f))
                }
                true
            }
        }
        setupBottomSheet()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
    }

    private fun initialize() {
        remoteConfig = Firebase.remoteConfig
        dbRef = Firebase.database.reference
        account = Global.account!!
        isView = intent.getBooleanExtra("VIEW", false)
        markers = arrayListOf()
        polylines = arrayListOf()
    }

    private fun setupView() {
        setupProgress("Loading data")
        dbRef.child("Ride/${Global.city}/Driver")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val locations = mutableListOf<LatLng>()
                        for (item in snapshot.children) {
                            val driver = item.getValue(Driver::class.java)!!
                            if (driver.busy == false && driver.active == true)
                                locations.add(driver.current!!.toLatLng())
                        }
                        if (locations.isEmpty()) {
                            snackBar(binding.root, "Sorry. There are no available rides near you.")
                        } else displayAvailableRides(locations)
                    } else snackBar(binding.root, "Sorry. There are no available rides near you.")
                    endProgress()
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(binding.root, "Error: ${error.message}")
                }
            })
    }

    private fun displayAvailableRides(locations: List<LatLng>) {
        markers.forEach { it.remove() }
        markers.clear()
        val bounds = LatLngBounds.Builder()
        locations.forEach { latLng ->
            bounds.include(latLng)
            val marker = map.addMarker(MarkerOptions().position(latLng).icon(
                BitmapDescriptorFactory.fromBitmap(getMarker(true))))!!
            markers.add(marker)
        }
        map.setOnMyLocationButtonClickListener {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 250))
            true
        }
    }

    private fun callDriver() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:${driver?.phone}".toUri()
        }
        startActivity(intent)
    }

    private fun chatDriver() {
        snackBar(binding.root, "WIP: Live chat feature")
    }

    private fun completeRide() {
        currentRide?.let { ride ->
            confirmDialog = ConfirmDialog("Finish ride",
                "Have you reached your desired destination?",
                "Finish", "Cancel") { confirm ->
                if (confirm) complete(ride)
            }
            confirmDialog!!.show(supportFragmentManager, "ConfirmDialog")
        }
    }

    private fun complete(ride: Ride) {
        setupProgress("Processing")
        val status = ride.commuter?.getOrDefault(account.uid!!, null)?.status
        val states = status?.split("|")?.toMutableList()
        states?.add("COMPLETED_COMMUTER")
        dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/commuter/${account.uid}/status")
            .setValue(states?.joinToString("|") ?: "").addOnCompleteListener { task ->
                if (task.isSuccessful) snackBar(binding.root, "We'll wait for the driver's confirmation on this")
                else snackBar(binding.root, "Error: ${task.exception!!.message}")
                endProgress()
            }
    }

    private fun cancelRide() {
        currentRide?.let { ride ->
            val commuter = ride.commuter?.getOrDefault(account.uid!!, null)
            confirmDialog = ConfirmDialog("Cancel ride",
                "Are you sure you want to cancel your ride?${if (commuter?.status?.contains("DRIVING") == true ||
                    isRideActive(ride, commuter)) " Since this " +
                        "action may cause inconvenience to your driver, you may receive a penalty " +
                        "on your Profile Trust Rating." else ""}",
                "Proceed", "No") { confirm ->
                if (confirm) cancel(ride, isRideActive(ride, commuter))
            }
            confirmDialog!!.show(supportFragmentManager, "ConfirmDialog")
        }
    }

    private fun cancel(ride: Ride, penalty: Boolean) {
        canceled = true
        setupProgress("Processing")
        dbRef.child("Ride/${Global.city}/Rides/${ride.uid}")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val weight = currentData.child("weight").getValue(Int::class.java)
                    val commuter = currentData.child("commuter").childrenCount
                    val status = currentData.child("status").getValue(String::class.java)
                    if (commuter > 1) {
                        if (status?.isEmpty() == true) currentData.child("status").value = "WAITING_PASSENGER"
                        currentData.child("commuter/${account.uid}").value = null
                        currentData.child("passenger").value = commuter.minus(
                            ride.commuter?.getOrDefault(account.uid!!, null)?.passenger!!)
                        currentData.child("weight").value = weight?.minus(
                            ride.commuter.getOrDefault(account.uid!!, null)?.weight!!)
                    } else currentData.value = null
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) cancel(ride, penalty)
                    else if (committed) {
                        if (penalty) {
                            dbRef.child("Account/TodaGo/${account.uid}")
                                .updateChildren(mapOf("/rating" to account.rating?.plus(2F),
                                    "/rates" to account.rates?.plus(1)))
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        snackBar(binding.root, "You have canceled your ride")
                                        LocationService.stopService()
                                        viewModel.resetRide()
                                        finish()
                                    }
                                    endProgress()
                                }
                        } else {
                            endProgress()
                            snackBar(binding.root, "You have canceled your ride")
                            LocationService.stopService()
                            viewModel.resetRide()
                            finish()
                        }
                    } else canceled = false
                }
            })
    }

    private fun observeRide(ride: Ride?) {
        if (ride != null) {
            currentRide = ride
            setupDriver(ride)
            setupPassenger(ride)
            rideStatus(ride)
            updateMap(ride)
            rideMonitor(ride)
            setupPrice(ride)
        } else if (currentRide != null && !canceled) {
            saveHistory(currentRide!!)
            LocationService.stopService()
            dbRef.child("Account/TodaGo/${account.uid}").updateChildren(
                mapOf("/rating" to account.rating?.plus(5F),
                    "/rates" to account.rates?.plus(1)))
            infoDialog = InfoDialog("Yey! You have arrived at your destination. Thank you for commuting with TodaGo!") {
                viewModel.resetRide()
                finish()
            }
            infoDialog!!.show(supportFragmentManager, "InfoDialog")
        }
    }

    private fun setupPassenger(ride: Ride?) {
        if (ride?.commuter != null) {
            setPadding(binding.rideActivity.height, binding.bottomSheet.top)
            binding.passenger.visibility = View.VISIBLE
            binding.rvPassenger.adapter = PassengerAdapter(ArrayList(ride.commuter.values), account)
            if (ride.passenger!! > 1) binding.passengerCount.text = "${ride.passenger}/3"
            else {
                binding.passengerTitle.text = "Passenger"
                binding.passengerCount.text = ""
            }
        }
    }

    private fun setupDriver(ride: Ride?) {
        setPadding(binding.rideActivity.height, binding.bottomSheet.top)
        if (ride?.driver != null) {
            if (ride.driver.profile != null) Picasso.get().load(ride.driver.profile)
                .placeholder(R.drawable.tricycle)
                .into(binding.driverProfile)
            else binding.driverProfile.setImageResource(R.drawable.tricycle)
            binding.driverName.text = ride.driver.name
            binding.driverStars.rating = ride.driver.rate!!.toFloat()
            binding.driverRate.text = "${ride.driver.rate.toFloat()}"
            binding.driverPlate.text = ride.driver.plate
            binding.driverStatus.text = when {
                ride.status!!.contains("APPROACHING") -> "Approaching"
                else -> "On standby"
            }
            binding.cvDriver.visibility = View.VISIBLE
        } else binding.cvDriver.visibility = View.GONE
    }

    private fun setupPrice(ride: Ride?) {
        val commuter = ride?.commuter?.getOrDefault(account.uid!!, null) ?: return
        if (ride.driver != null) {
            if (isSpecial(ride, commuter.distance!!)) {
                binding.minimum.text = "Minimum (Special)"
                binding.minimumPrice.text = "${ride.driver.matrix?.special}"
                binding.perKM.text = "Fare per KM (Special)"
                binding.perKMPrice.text = "${ride.driver.matrix?.specialPerKM}"
            } else {
                binding.minimumPrice.text = "${ride.driver.matrix?.regular}"
                binding.perKMPrice.text = "${ride.driver.matrix?.perKm}"
            }
            if (account.discount != null) {
                binding.discount.apply {
                    text = "Discount - ${account.discount!!.discountType}"
                    visibility = View.VISIBLE
                }
                binding.discountPercent.apply {
                    text = "${ride.driver.matrix?.discount}%"
                    visibility = View.VISIBLE
                }
            }
            val cost = getRideCost(ride, commuter.distance)
            val rideCost = cost.first - cost.second
            binding.costPrice.text = "₱ ${rideCost.roundToInt()}.00"
            val serviceFee = getServiceFee(commuter, cost.first)
            binding.serviceFee.text = "₱ $serviceFee.00"
            total = (rideCost + (if (isSpecial(ride, commuter.distance)) 0F else (cost.first * (commuter.passenger!! - 1))) + serviceFee).roundToInt()
            binding.totalPrice.text = "₱ $total.00"
            binding.priceBreakdown.visibility = View.VISIBLE
            binding.pricePending.visibility = View.GONE
            if (ride.commuter.getOrDefault(account.uid!!, null)?.price != total)
                dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/commuter/${account.uid}/price")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            currentData.getValue(Int::class.java)?.let {
                                return Transaction.abort()
                            }
                            currentData.value = total
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
                    })
        } else {
            if (isSpecial(ride, commuter.distance!!))
                binding.pricePending.text = "Pending (SPECIAL)"
            binding.priceBreakdown.visibility = View.GONE
            binding.pricePending.visibility = View.VISIBLE
        }
    }

    private fun getServiceFee(commuter: Commuter, cost: Float): Int = ((cost * commuter.passenger!!) * (Global.fee!!/100))
        .roundToInt().coerceAtLeast(1)

    private fun getRideCost(ride: Ride, distance: Int): Pair<Float, Float> {
        val perKM = if (isSpecial(ride, distance)) ride.driver!!.matrix?.specialPerKM else ride.driver!!.matrix?.perKm
        val discount = if (account.discount != null) ride.driver.matrix?.discount else 0
        val discounted = ((distance/1000F) * perKM!!) * discount!!
        return Pair(((distance/1000F) * perKM).coerceAtLeast(if (isSpecial(ride, distance)) 100F else 40F),
            discounted)
    }

    private fun isSpecial(ride: Ride, distance: Int): Boolean = ride.solo == true &&
            ride.passenger!! <= 2 && distance / 1000F > 1.5F

    private fun updateMap(ride: Ride?) {
        val commuter = ride?.commuter?.get(account.uid!!) ?: return
        val status = commuter.status
        var origin = if (status?.contains("DRIVING") == true) commuter.current else commuter.start
        val dest = commuter.end ?: return
        collectWaypoints(ride, origin!!, ride.driver != null) { waypoints ->
            if (ride.driver != null) origin = ride.driver.current
            displayRoute(origin!!, dest, ride.driver != null, waypoints.joinToString("|"))
        }
    }

    // ADD CHECK FOR RIDE ETA AND DESTINATION ETA FOR COMMUTER SAFETY FEATURE
    private fun rideMonitor(ride: Ride?) {
        ride?.commuter?.getOrDefault(account.uid!!, null)?.let { commuter ->
            if (ride.driver != null) {
                driver = ride.driver
                LocationService.startLocationUpdates()
            } else LocationService.stopLocationUpdates()
            val commuters = ride.commuter.values.filter { it.status!!.contains("ACTIVE") }
            if (commuters.isNotEmpty() && commuters.all { it.status!!.contains("DRIVING") } && ride.status?.contains("DRIVING") == false) {
                dbRef.child("Ride/${Global.city}/Rides/${ride.uid}/status")
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
                if (ride.status!!.contains("APPROACHING") || ride.status?.contains("DRIVING") == true)
                    binding.btnCancel.text = "Cancel Ride (Penalty)"
                if (!commuter.status!!.contains("DRIVING")) {
                    val distance = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver?.current!!.toLatLng())
                    if (distance < 15) setCommuterStatus(ride.uid!!, "DRIVING")
                }
                if (!commuter.status.contains("COMPLETED") && commuter.status.contains("DRIVING")) {
                    binding.btnComplete.visibility = View.VISIBLE
                    val current = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver?.current!!.toLatLng())
                    val end = distanceBetween(commuter.end!!.toLatLng(),
                        commuter.current.toLatLng())
                    if (current > 15 && end < 15) setCommuterStatus(ride.uid!!, "COMPLETED")
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

    private fun setCommuterStatus(uid: String, status: String) {
        dbRef.child("Ride/${Global.city}/Rides/$uid/commuter/${account.uid}/status")
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

    private fun isRideActive(ride: Ride, commuter: Commuter?): Boolean =
        ride.driver != null && commuter?.status?.contains("ACTIVE") == true &&
                ride.status?.contains("APPROACHING") == true

    private fun rideStatus(ride: Ride?) {
        ride?.commuter?.getOrDefault(account.uid!!, null)?.status?.let { status ->
            binding.rideDurationDriver.visibility = View.GONE
            binding.rideDurationRide.visibility = View.GONE
            if (status.contains("ACTIVE") || status.contains("COMPLETED_COMMUTER")) {
                ride.status?.let {
                    when {
                        it.contains("WAITING_DRIVER") -> binding.rideStatus.text = "Drivers are being notified . . ."
                        it.contains("WAITING_PASSENGER") -> binding.rideStatus.text = "Waiting for other passengers . . ."
                        it.contains("APPROACHING") -> {
                            binding.rideStatus.text = "Your driver is coming for pickup . . ."
                            binding.rideDurationDriver.visibility = View.VISIBLE
                            binding.rideDurationRide.visibility = View.GONE
                        }
                        else -> binding.rideStatus.text = "Driver is on standby . . ."
                    }
                }
                if (status.contains("DRIVING")) {
                    binding.rideStatus.text = "You will reach your destination soon . . ."
                    binding.rideDurationDriver.visibility = View.GONE
                    binding.rideDurationRide.visibility = View.VISIBLE
                }
                if (driver != null && ride.driver == null) {
                    driver = null
                    infoDialog = InfoDialog("Sorry. Your driver has cancelled the ride " +
                            "with you. Don't worry, we've penalized the driver's Profile Trust Rating " +
                            "and notifying other drivers to take your ride.")
                    infoDialog!!.show(supportFragmentManager, "InfoDialog")
                }
            } else {
                saveHistory(ride)
                LocationService.stopService()
                viewModel.resetRide()
                dbRef.child("Account/TodaGo/${account.uid}").updateChildren(
                    mapOf("/rating" to account.rating?.plus(5F),
                        "/rates" to account.rates?.plus(1)))
                infoDialog = InfoDialog("Yey! You have arrived at your destination. Thank you for commuting with TodaGo!") {
                    finish()
                }
                infoDialog!!.show(supportFragmentManager, "InfoDialog")
            }
        }
    }

    private fun collectWaypoints(ride: Ride, origin: String, hasDriver: Boolean, onComplete: (List<String>) -> Unit) {
        val commuters = ride.commuter?.values?.filter { it.uid != account.uid }
            ?.filter { it.status?.contains("ACTIVE") == true } ?: emptyList()
        if (commuters.isEmpty()) {
            onComplete(emptyList())
            return
        }
        val pickups = commuters.map {
            if (it.status!!.contains("DRIVING")) it.current!! else it.start!!
        }
        val drops = commuters.map { it.end!! }
        val destinations = (pickups + drops).joinToString("|")
        RetrofitClient.distanceMatrixService.getDistanceMatrix(origin, destinations, Global.key!!)
            .enqueue(object : Callback<DistanceMatrixResponse> {
                override fun onResponse(call: Call<DistanceMatrixResponse>,
                    response: Response<DistanceMatrixResponse>
                ) {
                    if (!response.isSuccessful) {
                        onComplete(emptyList())
                        return
                    }
                    val matrix = response.body() ?: return
                    val elements = matrix.rows?.firstOrNull()?.elements ?: return
                    val allPairs = (pickups + drops).zip(elements).map { (loc, elem) ->
                        loc to (elem.distance?.value ?: Int.MAX_VALUE)
                    }
                    val sortedPickups = allPairs
                        .filter { pickups.contains(it.first) }
                        .sortedBy { it.second }
                    val sortedDrops = allPairs
                        .filter { drops.contains(it.first) }
                        .sortedByDescending { it.second }
                    val waypoints = mutableListOf<String>()
                    waypoints.addAll(sortedPickups.map { it.first })
                    waypoints.addAll(sortedDrops.map { it.first })
                    if (hasDriver) waypoints.add(origin)
                    onComplete(waypoints)
                }
                override fun onFailure(call: Call<DistanceMatrixResponse>, t: Throwable) {}
            })
    }

    private fun saveHistory(ride: Ride) {
        val history = History(
            ride.reference,
            ride.solo,
            ride.commuter?.getOrDefault(account.uid!!, null)?.startAddress,
            ride.commuter?.getOrDefault(account.uid!!, null)?.endAddress,
            ride.passenger,
            ride.commuter?.getOrDefault(account.uid!!, null)?.price,
            "${ride.driver!!.name}",
            ride.dateTime)
        dbRef.child("Account/TodaGo/${account.uid}")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.child("History/${history.uid}").value != null) return Transaction.abort()
                    val rating = currentData.child("rating").getValue(Float::class.java)
                    val rates = currentData.child("rates").getValue(Int::class.java)
                    currentData.child("History/${history.uid}").value = history
                    rating?.let { currentData.child("rating").value = it.plus(5F)}
                    rates?.let { currentData.child("rates").value = it.plus(1)}
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    if (error != null) saveHistory(ride)
                }
            })
    }

    private fun setupBottomSheet() {
        if (isView != true) {
            setPadding(binding.rideActivity.height, binding.bottomSheet.top)
            val behavior = BottomSheetBehavior.from(binding.bottomSheet)
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    setPadding(binding.rideActivity.height, bottomSheet.top)
                }
                override fun onStateChanged(bottomSheet: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED) {
                        animateZoom()
                    }
                }
            })
        } else {
            binding.camera.visibility = View.GONE
            binding.bottomSheet.visibility = View.GONE
        }
    }

    private fun setPadding(height: Int, top: Int) {
        map.setPadding(0, 0, 0, (height * 0.05).toInt())
        binding.mapRide.setPadding(0, 0, 0,
            ((height - top) - (height * 0.05)).toInt())
        binding.bottomSheet.requestLayout()
    }

    private fun fetchAPI(onComplete: () -> Unit) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Global.key = remoteConfig.getString("maps_api_key")
                Global.fee = remoteConfig.getDouble("service_fee")
            }
            endProgress()
            onComplete()
        }
    }

    private fun animateZoom() {
        val bounds = bounds!!.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
    }

    private fun displayRoute(origin: String, destination: String, driver: Boolean, waypoints: String? = null) {
        RetrofitClient.directionsService.getDirections(origin, destination, Global.key!!,
            "optimize:true|$waypoints")
            .enqueue(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.isSuccessful) {
                        markers.forEach { it.remove() }
                        markers.clear()
                        polylines.forEach { it.remove() }
                        polylines.clear()
                        var durationRide = 0L
                        var durationDriver = 0L
                        val route = response.body()?.routes?.firstOrNull()
                        route?.let {
                            bounds = LatLngBounds.Builder().include(it.bounds.northeast.toLatLng())
                                .include(it.bounds.southwest.toLatLng())
                            animateZoom()
                        }
                        route?.legs?.forEachIndexed { index, leg ->
                            leg.duration.value.let {
                                if (driver) if (index == 0) durationDriver += it
                                durationRide += it
                            }
                            leg.steps.forEach { step ->
                                val points = PolyUtil.decode(step.polyline.points)
                                drawPolyline(points)
                            }
                            val marker = if (index == 0) {
                                if (driver) map.addMarker(
                                    MarkerOptions().position(leg.start.toLatLng()).icon(
                                        BitmapDescriptorFactory.fromBitmap(getMarker(true))))!!
                                else map.addMarker(
                                    MarkerOptions().position(leg.start.toLatLng()).icon(
                                        BitmapDescriptorFactory.fromBitmap(getMarker(false,
                                            account.gender))))!!
                            } else map.addMarker(
                                MarkerOptions().position(leg.start.toLatLng()).icon(
                                    BitmapDescriptorFactory.fromBitmap(getMarker(false))))!!
                            markers.add(marker)
                            if (index == route.legs.lastIndex) {
                                val mark = map.addMarker(
                                    MarkerOptions().position(leg.end.toLatLng()).icon(
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))!!
                                markers.add(mark)
                            }
                        }
                        setupRide(currentRide!!, durationRide, durationDriver)
                    }
                }
                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {}
            })
    }

    private fun setupRide(ride: Ride, durationRide: Long, durationDriver: Long) {
        ride.commuter?.getOrDefault(account.uid!!, null)?.let { commuter ->
            binding.rideDuration.apply {
                if (text.isNullOrEmpty()) text = formatDuration(commuter.duration!!.toLong())
            }
            binding.rideDistance.apply {
                if (text.isNullOrEmpty()) text = "${commuter.distance!! / 1000F} km"
            }
            binding.rideDurationDriver.text = formatDuration(durationDriver, true)
            binding.rideDurationRide.text = formatDuration(durationRide, true)
        }
    }

    private fun formatDuration(seconds: Long, isSimple: Boolean = false): String {
        val hour = seconds / 3600
        val minute = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hour > 0 -> if (isSimple) "%dh".format(hour) else "%d hr %02d min %02d sec".format(hour, minute, secs)
            minute > 0 -> if (isSimple) "%dm".format(minute) else "%d min %02d sec".format(minute, secs)
            else -> "%ds".format(secs)
        }
    }

    private fun drawPolyline(points: List<LatLng>) {
        val polyline = map.addPolyline(PolylineOptions()
            .color("#1561FF".toColorInt())
            .addAll(points)
            .width(30f)
            .geodesic(true)
            .endCap(RoundCap())
            .startCap(RoundCap()))
        polylines.add(polyline)
    }

    @SuppressLint("InflateParams")
    private fun getMarker(driver: Boolean, gender: String? = null): Bitmap {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.marker_pin, null)

        val image = view.findViewById<ImageView>(R.id.icon)
        if (driver) image.setImageResource(R.drawable.tricycle)
        else when (gender) {
            "Female" -> image.setImageResource(R.drawable.head_female)
            "Male" -> image.setImageResource(R.drawable.head_male)
            else -> image.setImageResource(R.drawable.commuter)
        }

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        onBackPressedDispatcher.addCallback(this, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }
}