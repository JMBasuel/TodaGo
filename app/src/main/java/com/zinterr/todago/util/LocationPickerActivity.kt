package com.zinterr.todago.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.text.*
import android.view.*
import android.widget.*
import androidx.activity.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.*
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.*
import com.zinterr.todago.R
import com.zinterr.todago.TodaGo
import com.zinterr.todago.databinding.ActivityLocationPickerBinding
import com.zinterr.todago.model.Global.getLocation
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.Global.toLatLngString
import com.zinterr.todago.viewmodel.SessionViewModel

@SuppressLint("SetTextI18n")
class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityLocationPickerBinding
    private lateinit var predictions: ArrayList<AutocompletePrediction>
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var searchBar: AutoCompleteTextView
    private lateinit var session: SessionViewModel
    private lateinit var places: PlacesClient
    private lateinit var map: GoogleMap
    private var current: String? = null
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.locationPicker) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = binding.btnSelect.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = 40 + navBars.bottom
            binding.btnSelect.layoutParams = params
            insets
        }
        initialize()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.mapType = GoogleMap.MAP_TYPE_HYBRID
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(
            this, R.raw.map_style))
        map.isIndoorEnabled = false
        map.isBuildingsEnabled = false
        map.setMinZoomPreference(10f)
        map.setMaxZoomPreference(40f)
        Handler(Looper.getMainLooper()).postDelayed({
            map.setPadding(0, binding.locationPicker.height - binding.btnSelect.top, 0,
                (binding.locationPicker.height - (binding.btnSelect.top * 0.99)).toInt())
        }, 0)
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        } else snackBar(binding.root, "Location permission denied")

        val mapView = (supportFragmentManager.findFragmentById(R.id.map_picker) as SupportMapFragment).view
        mapView?.findViewWithTag<View>("GoogleMapMyLocationButton")?.let { button ->
            val params = button.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
            button.layoutParams = params
        }

        binding.btnType.setOnDebouncedClickListener {
            map.mapType = when (map.mapType) {
                GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
                GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_HYBRID
                GoogleMap.MAP_TYPE_HYBRID -> GoogleMap.MAP_TYPE_TERRAIN
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
        }

        getLocation(this) { latLng ->
            if (latLng != null) {
                map.moveCamera(CameraUpdateFactory.newLatLng(latLng))
                current = latLng.toLatLngString()
            }
        }
        map.setOnMyLocationButtonClickListener {
            map.moveCamera(CameraUpdateFactory.zoomTo(18f))
            false
        }
    }

    private fun initialize() {
        remoteConfig = Firebase.remoteConfig
        searchBar = binding.root.findViewById(R.id.search_bar)
        predictions = arrayListOf()
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_picker) as SupportMapFragment
        mapFragment.getMapAsync(this)
        session = ViewModelProvider(applicationContext as TodaGo)[SessionViewModel::class.java]
        fetchApi {
            if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(applicationContext, session.key.value!!)
            places = Places.createClient(this)
            setupButtons()
        }
    }

    private fun fetchApi(onComplete: () -> Unit) {
        setupProgress()
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) session.setKey(remoteConfig.getString("maps_api_key"))
            endProgress()
            onComplete()
        }
    }

    private fun setupButtons() {
        setupSearch()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelect.setOnClickListener {
            val intent = Intent()
            intent.putExtra("LATITUDE", map.cameraPosition.target.latitude)
            intent.putExtra("LONGITUDE", map.cameraPosition.target.longitude)
            intent.putExtra("CURRENT", current)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    private fun setupSearch() {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        searchBar.setAdapter(adapter)
        searchBar.setDropDownBackgroundResource(R.drawable.input_bg)
        searchBar.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
            if (focused) searchBar.setText(null)
        }
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(text: Editable?) {}
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                if (!text.isNullOrEmpty()) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(text.toString())
                        .setCountries("PH")
                        .build()
                    places.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            predictions.clear()
                            predictions.addAll(response.autocompletePredictions)
                            adapter.clear()
                            adapter.addAll(response.autocompletePredictions.map { it.getFullText(null).toString() })
                            adapter.notifyDataSetChanged()
                        }
                }
            }
        })
        searchBar.setOnItemClickListener { _, _, position, _ ->
            clear()
            val prediction = predictions[position]
            val placeId = prediction.placeId
            val placeFields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
            val request = FetchPlaceRequest.newInstance(placeId, placeFields)
            places.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(place.location!!, 18f))
                }
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }

    private fun setupProgress() {
        binding.loading.message.text = "Loading data"
        binding.loading.container.visibility = View.VISIBLE
        onBackPressedDispatcher.addCallback(this, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }
}