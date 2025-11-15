package com.zinterr.todago.ui.login

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.text.*
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.*
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.firebase.storage.*
import com.google.gson.Gson
import com.zinterr.todago.*
import com.zinterr.todago.databinding.SignupBinding
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.toLocalAddress
import com.zinterr.todago.network.RetrofitClient
import com.zinterr.todago.util.*
import org.json.*
import retrofit2.*
import java.util.UUID

// ADD DISCOUNT REQUEST OPTION
@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class Signup : Fragment() {

    private lateinit var binding: SignupBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var auth: FirebaseAuth
    private var verified: Boolean = false
    private var location: String? = null
    private var gender: String? = null
    private var latLng: LatLng? = null
    private var fName: String? = null
    private var lName: String? = null
    private var phone: String? = null
    private var email: String? = null
    private var pass: String? = null
    private val scanQR = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) verify(result.data) }
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) { if (Global.key == null) fetchAPI { showData(result.data)
        } else showData(result.data) } }
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else requireCamera() }
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SignupBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.signUp) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = binding.btnSign.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = 40 + navBars.bottom
            binding.btnSign.layoutParams = params
            insets
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailListener()
        passwordListener()
        phoneListener()
        fetchAPI()

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.scroller.setOnDebouncedClickListener {
            clear()
        }

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.locationPicker.setOnDebouncedClickListener {
            getAddress()
        }

        binding.btnScanID.setOnClickListener {
            scanID()
        }

        binding.btnSign.setOnDebouncedClickListener {
            signUp()
        }
    }

    private fun initialize() {
        dbRef = Firebase.database.reference
        remoteConfig = Firebase.remoteConfig
        stRef = Firebase.storage.reference
        auth = Firebase.auth
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                Global.key = remoteConfig.getString("maps_api_key")
                Global.fee = remoteConfig.getDouble("service_fee")
            }
            endProgress()
            onComplete?.invoke()
        }
    }

    private fun signUp() {
        getDetails()
        if (isCompleteDetails()) {
            setupProgress("Signing up")
            auth.createUserWithEmailAndPassword(email!!, pass!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result.user?.uid
                        val account = Account(uid, "$fName $lName", gender, phone,
                            location, email, if (verified) 2.5F else 0F, if (verified) 1 else 0, verified, false)
                        dbRef.child("Account/TodaGo/$uid").setValue(account)
                            .addOnSuccessListener {
                                Global.account = account
                                Global.deviceID = getDeviceID()
                                dbRef.child("Account/TodaGo/${account.uid}/sessionID").setValue(Global.deviceID)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            endProgress()
                                            val intent = Intent(requireContext(), MainActivity::class.java)
                                            startActivity(intent)
                                            requireActivity().finish()
                                        }
                                    }
                            }
                    } else {
                        endProgress()
                        snackBar(view, "Error: ${task.exception?.message}")
                    }
                }
        } else snackBar(view, "Please fulfill all requirements")
    }

    private fun getDeviceID(): String {
        val prefs = requireContext().getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit { putString("device_id", deviceId) }
        }
        return deviceId
    }

    private fun getDetails() {
        location = if (latLng != null) "${latLng!!.latitude},${latLng!!.longitude}" else null
        gender = binding.gender.findViewById<RadioButton>(binding.gender.checkedRadioButtonId)?.text.toString()
        fName = binding.first.text.toString()
        lName = binding.last.text.toString()
        phone = binding.phone.text.toString()
        email = binding.email.text.toString()
        pass = binding.password.text.toString()
    }

    private fun isCompleteDetails(): Boolean = location != null && gender != null &&
            fName != null && lName != null && binding.phone.error == null && binding.email.error == null &&
            binding.password.error == null

    private fun getAddress() {
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        if (Global.key == null) fetchAPI {
            getLocation.launch(intent)
        } else getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        setupProgress("")
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        latLng = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", Global.key!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()
                        binding.location.text = (if (address?.locality != null) "${address.locality}, " else "") +
                                (if (address?.city != null) "${address.city}, " else "") +
                                (address?.province ?: "")
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
            })
    }

    private fun scanID() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) scan()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }

    private fun scan() {
        val intent = Intent(requireContext(), QrScannerActivity::class.java)
        scanQR.launch(intent)
    }

    private fun verify(data: Intent?) {
        try {
            val result = JSONObject(data?.getStringExtra("RESULT") ?: "")
            val subject = Gson().fromJson(result.getString("subject"), Subject::class.java)
            val first = subject.fName
            val last = subject.lName
            val sex = subject.sex
            binding.first.apply {
                if (!first.isNullOrEmpty()) {
                    setText(first.uppercase())
                    isEnabled = false
                }
            }
            binding.last.apply {
                if (!last.isNullOrEmpty()) {
                    setText(last.uppercase())
                    isEnabled = false
                }
            }
            binding.gender.apply {
                if (!sex.isNullOrEmpty()) when (sex.uppercase()) {
                    "MALE" -> binding.male.apply {
                        isChecked = true
                        isEnabled = false
                        binding.female.isEnabled = false
                    }
                    "FEMALE" -> binding.female.apply {
                        isChecked = true
                        isEnabled = false
                        binding.male.isEnabled = false
                    }
                }
            }
            if (first != null && last != null && sex != null) {
                verified = true
                binding.btnScanID.isEnabled = false
            }
        } catch (_: JSONException) {
            snackBar(view, "Invalid QR. Please scan you National ID")
        }
    }

    private fun requireCamera() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Access to your camera is required for this feature")
            .setPositiveButton("Understood") { dialog, _ ->
                requestPermission.launch(Manifest.permission.CAMERA)
                dialog.dismiss()
            }
            .show()
    }

    private fun emailListener() {
        binding.email.setOnFocusChangeListener { _, focused ->
            val email = binding.email.text.toString()
            if (!focused) {
                if (email.isNotEmpty()) {
                    binding.email.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
                        binding.email.error = "Invalid email address"
                } else binding.email.error = "Required"
            } else binding.email.error = "Required"
        }
    }

    private fun passwordListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val newInput = "${binding.password.text.trim()}"
            if (!focused) {
                binding.scroller.visibility = View.GONE
                if (newInput.isNotEmpty()) {
                    binding.password.error = null
                    if (!Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).{8,}$")
                            .matches(newInput)) binding.password.error = "Requirements not met"
                } else binding.password.error = "Required"
            } else binding.scroller.visibility = View.VISIBLE
        }
        binding.password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (Regex("^(?=.*[A-Z]).+$").matches(s!!)) binding.uppercase.visibility = View.GONE
                else binding.uppercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[a-z]).+$").matches(s)) binding.lowercase.visibility = View.GONE
                else binding.lowercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).+$").matches(s)) binding.symbol.visibility = View.GONE
                else binding.symbol.visibility = View.VISIBLE
                if (Regex("^(?=.*\\d).+$").matches(s)) binding.digit.visibility = View.GONE
                else binding.digit.visibility = View.VISIBLE
                if (Regex("^.{8,}$").matches(s)) binding.length.visibility = View.GONE
                else binding.length.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun phoneListener() {
        binding.phone.setOnFocusChangeListener { _, focused ->
            val phoneText = binding.phone.text.toString()
            if (!focused) {
                if (phoneText.isNotEmpty()) {
                    binding.phone.error = null
                    if (!Patterns.PHONE.matcher(phoneText).matches())
                        binding.phone.error = "Invalid phone number"
                } else binding.phone.error = "Required"
            } else binding.phone.error = "Required"
        }
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}