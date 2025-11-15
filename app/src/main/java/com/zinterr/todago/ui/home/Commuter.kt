package com.zinterr.todago.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.zinterr.todago.databinding.CommuterBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Commuter
import com.zinterr.todago.model.Global.checkGPS
import com.zinterr.todago.model.Global.getLocation
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.Global.toLatLng
import com.zinterr.todago.model.Global.toLatLngString
import com.zinterr.todago.model.Global.toLocalAddress
import com.zinterr.todago.network.RetrofitClient
import com.zinterr.todago.ui.popup.*
import com.zinterr.todago.ui.ride.RideActivity
import com.zinterr.todago.util.*
import com.zinterr.todago.viewmodel.*
import retrofit2.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

// ADD SCHEDULE BOOKING
@SuppressLint("SetTextI18n")
class Commuter : Fragment() {

    private lateinit var binding: CommuterBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val verifiedViewModel: VerifiedViewModel by activityViewModels()
    private val rideViewModel: RideViewModel by activityViewModels()
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var ridePickerDialog: RidePickerDialog? = null
    private lateinit var dbRef: DatabaseReference
    private var infoDialog: InfoDialog? = null
    private lateinit var account: Account
    private var driver: Driver? = null
    private var phone: String? = null
    private var solo: Boolean? = null
    private var total: Int? = null
    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) checkGPS(requireContext(), locationSettingsLauncher) { if (solo == true) bookSolo() else if (solo == false) bookShare() } else requireLocationService() }
    private val locationSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (solo == true) bookSolo() else if (solo == false) bookShare()
        } else requireGPS() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CommuterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchAPI { checkActiveBooking() }

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        if (account.emailVerified == false)
            verifiedViewModel.isVerified.observe(viewLifecycleOwner) { verified ->
                if (verified) binding.restrictUnverified.visibility = View.GONE
                else binding.restrictUnverified.visibility = View.VISIBLE
        }

        rideViewModel.ride.observe(viewLifecycleOwner) { ride ->
            observeRide(ride)
        }

        binding.rideSolo.setOnDebouncedClickListener {
            bookSolo()
        }

        binding.rideShare.setOnDebouncedClickListener {
            bookShare()
        }

        binding.btnView.setOnDebouncedClickListener {
            viewRides()
        }

        binding.btnViewRide.setOnDebouncedClickListener {
            val intent = Intent(requireContext(), RideActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
    }

    private fun initialize() {
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        account = Global.account!!
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                Global.key = remoteConfig.getString("maps_api_key")
                Global.fee = remoteConfig.getDouble("service_fee")
            }
            endProgress()
            onComplete?.invoke()
        }
    }

    private fun checkActiveBooking() {
        setupProgress("Loading data")
        getLocation(requireContext()) { latLng ->
            if (latLng != null) {
                getCity(latLng) { success ->
                    if (success) getRides()
                    else checkActiveBooking()
                }
            } else checkActiveBooking()
        }
    }

    private fun getRides() {
        dbRef.child("Ride/${Global.city}/Rides").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (item in snapshot.children) {
                            val ride = item.getValue(Ride::class.java)!!
                            if (ride.commuter?.containsKey(account.uid!!) == true) {
                                // NEED TO CHECK FOR COMPLETED RIDE IN CASE BOTH COMMUTER AND DRIVER GOES OFFLINE DURING THE RIDE
                                if (ride.uid == null) dbRef.child("Ride/${Global.city}/Rides/${item.key}").setValue(null)
                                else if (ride.commuter.getValue(account.uid!!).status!!.contains("ACTIVE")) {
                                    if (isRideOld(ride)) dbRef.child("Ride/${Global.city}/Rides/${ride.uid}").setValue(null)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                infoDialog = InfoDialog("Sorry. Your booking has exceeded waiting time and expired.")
                                                infoDialog!!.show(childFragmentManager, "InfoDialog")
                                            }
                                        }
                                    else {
                                        rideViewModel.setRidePath(Global.city!!, ride.uid)
                                        LocationService.startService(requireContext(),
                                            Global.city!!, ride.uid)
                                    }
                                }
                            }
                        }
                    }
                    endProgress()
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun isRideOld(ride: Ride): Boolean {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dateTime = LocalDateTime.parse(ride.dateTime, formatter)
        val timeDiff = ChronoUnit.HOURS.between(dateTime, LocalDateTime.now())
        return timeDiff >= 3
    }

    private fun getCity(latLng: LatLng, onComplete: (Boolean) -> Unit) {
        RetrofitClient.geocodeService.reverseGeocode(latLng.toLatLngString(), Global.key!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        Global.city = response.body()?.toLocalAddress()!!.city
                        onComplete(true)
                    } else onComplete(false)
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    onComplete(false)
                }
            })
    }

    private fun bookShare() {
        solo = false
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            ridePickerDialog = RidePickerDialog(Global.key!!) { city, origin, originAddress, destination, destinationAddress, count, baggage, current ->
                fetchRoute(origin, destination) { route ->
                    val leg = route.legs.firstOrNull()
                    checkShareRides(city) { rides ->
                        val ref = dbRef.child("Ride/$city/Rides").push()
                        val rideUID = ref.key
                        var location = ""
                        if (current != null) location = current
                        else getLocation(requireContext()) { location = it?.toLatLngString() ?: "" }
                        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val commuter = Commuter(
                            account.uid,
                            account.name,
                            account.phone,
                            "%.2f".format(account.rating?.div(account.rates!!)),
                            count,
                            leg?.start?.toLatLng()?.toLatLngString(),
                            originAddress,
                            leg?.end?.toLatLng()?.toLatLngString(),
                            destinationAddress,
                            location,
                            leg?.distance?.value,
                            leg?.duration?.value,
                            "ACTIVE",
                            time,
                            baggage,
                            account.discount?.discountType)
                        val new = Ride(
                            rideUID,
                            false,
                            count,
                            mapOf(commuter.uid!! to commuter),
                            "WAITING_DRIVER|WAITING_PASSENGER",
                            "${UUID.randomUUID()}",
                            time,
                            baggage)
                        if (rides.isNotEmpty()) rides.firstOrNull { checkIsShareable(origin, destination, it) }?.let {
                            addToRide(ref, it.uid!!, new, commuter, city)
                        } ?: setRide(ref, new, city)
                        else setRide(ref, new, city)
                    }
                }
            }
            ridePickerDialog?.show(childFragmentManager, "RidePickerDialog")
        } else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun addToRide(ref: DatabaseReference, uid: String, ride: Ride, commuter: Commuter, city: String) {
        setupProgress("Processing")
        dbRef.child("Ride/$city/Rides/$uid").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val passenger = currentData.child("passenger").getValue(Int::class.java)
                passenger?.plus(commuter.passenger!!)?.let { if (it > 3)return Transaction.abort() }
                val weight = currentData.child("weight").getValue(Int::class.java)
                val status = currentData.child("status").getValue(String::class.java)
                weight?.plus(commuter.weight!!)?.let { if (it > 5) return Transaction.abort() }
                if (status?.contains("WAITING_PASSENGER") == false) return Transaction.abort()

                currentData.child("commuter/${account.uid}").value = commuter
                if (passenger?.plus(commuter.passenger!!) == 3 || weight?.plus(commuter.weight!!) == 5) {
                    val state = status?.split('|')?.toMutableList()
                    state?.remove("WAITING_PASSENGER")
                    currentData.child("status").value = state?.joinToString("|") ?: ""
                }
                currentData.child("passenger").value = passenger?.plus(commuter.passenger!!)
                currentData.child("weight").value = weight?.plus(commuter.weight!!)

                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                if (error != null) snackBar(view, "Error: ${error.message}")
                else if (committed) {
                    rideViewModel.setRidePath(city, uid)
                    LocationService.startService(requireContext(), city, uid)
                    val intent = Intent(requireContext(), RideActivity::class.java)
                    startActivity(intent)
                } else setRide(ref, ride, city)
                endProgress()
            }
        })
    }

    private fun checkIsShareable(origin: LatLng, destination: LatLng, ride: Ride): Boolean {
        val threshold = 500
        return ride.commuter!!.values.any { commuter ->
            val pickup = distanceBetween(origin, commuter.start!!.toLatLng())
            val drop = distanceBetween(destination, commuter.end!!.toLatLng())
            pickup < threshold && drop < threshold
        }
    }

    private fun checkShareRides(city: String, onComplete: (List<Ride>) -> Unit) {
        setupProgress("Processing")
        dbRef.child("Ride/$city/Rides").orderByChild("solo").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rides: ArrayList<Ride> = arrayListOf()
                    if (snapshot.exists()) for (item in snapshot.children) {
                        val ride = item.getValue(Ride::class.java)!!
                        if (ride.status?.contains("WAITING_DRIVER") == true ||
                            ride.status?.contains("WAITING_PASSENGER") == true) rides.add(ride)
                    }
                    onComplete(rides)
                    endProgress()
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun bookSolo() {
        solo = true
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            ridePickerDialog = RidePickerDialog(Global.key!!, "SOLO") { city, origin, originAddress, destination, destinationAddress, count, baggage, current ->
                fetchRoute(origin, destination) { route ->
                    val leg = route.legs.firstOrNull()
                    val ref = dbRef.child("Ride/$city/Rides").push()
                    val rideUID = ref.key
                    var location = ""
                    if (current != null) location = current
                    else getLocation(requireContext()) { location = it?.toLatLngString() ?: "" }
                    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    val commuter = Commuter(
                        account.uid,
                        account.name,
                        account.phone,
                        "%.2f".format(account.rating?.div(account.rates!!)),
                        count,
                        leg?.start?.toLatLng()?.toLatLngString(),
                        originAddress,
                        leg?.end?.toLatLng()?.toLatLngString(),
                        destinationAddress,
                        location,
                        leg?.distance?.value,
                        leg?.duration?.value,
                        "ACTIVE",
                        time,
                        baggage,
                        account.discount?.discountType)
                    val ride = Ride(
                        rideUID,
                        true,
                        count,
                        mapOf(commuter.uid!! to commuter),
                        "WAITING_DRIVER",
                        "${UUID.randomUUID()}",
                        time,
                        baggage)
                    setRide(ref, ride, city)
                }
            }
            ridePickerDialog?.show(childFragmentManager, "RidePickerDialog")
        } else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun setRide(ref: DatabaseReference, ride: Ride, city: String) {
        setupProgress("Processing")
        ref.setValue(ride).addOnCompleteListener { task ->
            endProgress()
            if (task.isSuccessful) {
                rideViewModel.setRidePath(city, ride.uid!!)
                LocationService.startService(requireContext(), city, ride.uid)
                val intent = Intent(requireContext(), RideActivity::class.java)
                startActivity(intent)
            } else snackBar(view, "Error: ${task.exception!!.message}")
        }
    }

    private fun observeRide(ride: Ride?) {
        if (ride != null) {
            toggleRide(true)
            rideStatus(ride)
            rideMonitor(ride)
            setupPrice(ride)
        } else toggleRide(false)
    }

    private fun setupPrice(ride: Ride) {
        val commuter = ride.commuter?.getOrDefault(account.uid!!, null)
        if (ride.driver != null) {
            val cost = getRideCost(ride, commuter?.distance!!)
            val rideCost = cost.first - cost.second
            val serviceFee = getServiceFee(commuter, cost.first)
            total = (rideCost + (if (isSpecial(ride, commuter.distance)) 0F else (cost.first * (commuter.passenger!! - 1))) + serviceFee).roundToInt()
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

    // ADD CHECK FOR RIDE ETA AND DESTINATION ETA FOR COMMUTER SAFETY FEATURE
    private fun rideMonitor(ride: Ride) {
        ride.commuter?.getOrDefault(account.uid!!, null)?.let { commuter ->
            if (ride.driver != null) LocationService.startLocationUpdates()
            else LocationService.stopLocationUpdates()
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
                driver = ride.driver!!
                phone = ride.driver.phone
                if (!commuter.status!!.contains("DRIVING")) {
                    val distance = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver.current!!.toLatLng())
                    if (distance < 15) setCommuterStatus(ride.uid!!, "DRIVING")
                }
                if (!commuter.status.contains("COMPLETED") && commuter.status.contains("DRIVING")) {
                    val current = distanceBetween(commuter.current!!.toLatLng(),
                        ride.driver.current!!.toLatLng())
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

    private fun isRideActive(ride: Ride, commuter: Commuter): Boolean =
        ride.driver != null && commuter.status!!.contains("ACTIVE") &&
                ride.status!!.contains("APPROACHING")

    private fun rideStatus(ride: Ride) {
        ride.commuter?.getOrDefault(account.uid!!, null)?.status!!.let { status ->
            if (status.contains("ACTIVE") || status.contains("COMPLETED_COMMUTER")) {
                ride.status?.let {
                    when {
                        it.contains("WAITING_DRIVER") -> binding.rideStatus.text = "Drivers are being notified . . ."
                        it.contains("WAITING_PASSENGER") -> binding.rideStatus.text = "Waiting for other passengers . . ."
                        it.contains("APPROACHING") -> binding.rideStatus.text = "Your driver is coming for pickup . . ."
                        else -> binding.rideStatus.text = "Driver is on standby . . ."
                    }
                }
                if (status.contains("DRIVING")) binding.rideStatus.text = "You will reach your destination soon . . ."
                if (driver != null && ride.driver == null) {
                    infoDialog = InfoDialog("Sorry. Your driver has cancelled the ride " +
                            "with you. Don't worry, we've penalized the driver's Trust Rating " +
                            "and notifying other drivers to take your ride.")
                    infoDialog!!.show(childFragmentManager, "InfoDialog")
                }
            } else {
                saveHistory(ride)
                LocationService.stopService()
                rideViewModel.resetRide()
                dbRef.child("Account/TodaGo/${account.uid}").updateChildren(
                    mapOf("/rating" to account.rating?.plus(5F),
                        "/rates" to account.rates?.plus(1)))
                infoDialog = InfoDialog("Yey! You have arrived at your destination. Thank you for commuting with TodaGo!")
                infoDialog!!.show(childFragmentManager, "InfoDialog")
            }
        }
    }

    private fun toggleRide(isShow: Boolean) {
        binding.rideSolo.isEnabled = !isShow
        binding.rideShare.isEnabled = !isShow
        binding.ride.visibility = if (isShow) View.VISIBLE else View.GONE
        if (!isShow) binding.rideStatus.text = null
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
        dbRef.child("Account/TodaGo/${account.uid}/History")
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
                }
            })
    }

    private fun fetchRoute(origin: LatLng, destination: LatLng, onComplete: (Route) -> Unit) {
        setupProgress("Calculating your route")
        RetrofitClient.directionsService.getDirections(origin.toLatLngString(),
            destination.toLatLngString(), Global.key!!)
            .enqueue(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.isSuccessful) {
                        val route = response.body()?.routes?.firstOrNull()
                        onComplete(route!!)
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    endProgress()
                    snackBar(view, "Error: ${t.message}")
                }
            })
    }

    private fun viewRides() {
        val intent = Intent(requireContext(), RideActivity::class.java).apply {
            putExtra("VIEW", true)
        }
        startActivity(intent)
    }

    private fun requireLocationService() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Access to your location service is required for this feature to work")
            .setPositiveButton("Understood") { dialog, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                dialog.dismiss()
            }
            .show()
    }

    private fun requireGPS() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Enabling your location service is required for this feature to work")
            .setPositiveButton("Understood") { dialog, _ ->
                checkGPS(requireContext(), locationSettingsLauncher) { if (solo == true) bookSolo() else if (solo == false) bookShare() }
                dialog.dismiss()
            }
            .show()
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        Home().addCallbackFalse()
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        Home().addCallbackTrue()
    }
}