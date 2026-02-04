package com.zinterr.todago.ui.profile

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.zinterr.todago.R
import com.zinterr.todago.LoginActivity
import com.zinterr.todago.databinding.ProfileBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.remoteconfig.*
import com.google.gson.Gson
import com.zinterr.todago.TodaGo
import com.zinterr.todago.model.Global.toLocalAddress
import com.zinterr.todago.network.RetrofitClient
import com.zinterr.todago.ui.home.Home
import com.zinterr.todago.ui.popup.*
import com.zinterr.todago.util.*
import com.zinterr.todago.viewmodel.*
import org.json.*
import retrofit2.*

// ADD SAVED LOCATIONS
// ADD DISCOUNT UPDATE OPTION
@SuppressLint("SetTextI18n")
class Profile : Fragment() {

    private lateinit var binding: ProfileBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private val viewModel: VerifiedViewModel by activityViewModels()
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var passwordDialog: PasswordDialog? = null
    private lateinit var rideViewModel: RideViewModel
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private var phoneDialog: PhoneDialog? = null
    private lateinit var stRef: StorageReference
    private var emailDialog: EmailDialog? = null
    private var infoDialog: InfoDialog? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var account: Account
    private val scanQR = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) verify(result.data) }
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else requireCamera() }
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) saveData(result.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchAPI {
            setupData()
        }

        rideViewModel.ride.observe(viewLifecycleOwner) { ride ->
            observeRide(ride)
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        binding.btnEditEmail.setOnDebouncedClickListener {
            editEmail()
        }

        binding.btnVerifyEmail.setOnDebouncedClickListener {
            verifyEmail()
        }

        binding.btnEditPhone.setOnDebouncedClickListener {
            editPhone()
        }

        binding.btnEditLocation.setOnDebouncedClickListener {
            editLocation()
        }

        binding.btnEditAccount.setOnClickListener {
            editAccount()
        }

        binding.btnPassword.setOnDebouncedClickListener {
            changePassword()
        }

        binding.btnDelete.setOnDebouncedClickListener {
            deleteAccount()
        }

        binding.btnLogout.setOnDebouncedClickListener {
            signOut()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            setupData()
        }
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (emailDialog != null) if (emailDialog!!.isAdded) emailDialog!!.dismiss()
        if (passwordDialog != null) if (passwordDialog!!.isAdded) passwordDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (infoDialog != null) if (infoDialog!!.isAdded) infoDialog!!.dismiss()
        if (phoneDialog != null) if (phoneDialog!!.isAdded) phoneDialog!!.dismiss()
    }

    private fun initialize() {
        session = ViewModelProvider((requireActivity().application as TodaGo),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGo)))[SessionViewModel::class.java]
        account = session.account.value!!
        auth = Firebase.auth
        rideViewModel = ViewModelProvider((requireActivity().application as TodaGo),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGo)))[RideViewModel::class.java]
        remoteConfig = Firebase.remoteConfig
        dbRef = Firebase.database.reference
        stRef = Firebase.storage.reference
    }

    private fun fetchAPI(onComplete: (() -> Unit)? = null) {
        setupProgress("Loading data")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                session.setKey(remoteConfig.getString("maps_api_key"))
                session.setFee(remoteConfig.getDouble("service_fee"))
            }
            onComplete?.invoke()
        }
    }

    private fun setupData() {
        if (account.gender.equals("Male")) binding.userImage.setImageResource(R.drawable.head_male)
        else binding.userImage.setImageResource(R.drawable.head_female)
        binding.name.text = account.name
        binding.verified.setImageResource(if (account.verified!!) R.drawable.verified else R.drawable.unverified)
        if (account.verified == false) binding.name.setOnDebouncedClickListener { verifyAccount() }
        binding.stars.rating = account.rating!!/account.rates!!.toFloat()
        binding.rating.text = "${if ((account.rating!!/account.rates!!.toFloat()).isNaN()) 0F else "%.2f".format(account.rating!!/account.rates!!.toFloat())} (${account.rates})"
        auth.currentUser!!.reload()
            .addOnCompleteListener {
                auth.currentUser!!.email.let {
                    if (it != account.email) {
                        session.account.value?.let { acc ->
                            acc.email = it
                            session.setAccount(acc)
                        }
                        account.email == it
                        dbRef.child("Account/TodaGo/${account.uid}/email").setValue(it)
                    }
                }
                auth.currentUser!!.isEmailVerified.let {
                    if (it != account.emailVerified) {
                        dbRef.child("Account/TodaGo/${account.uid}")
                            .runTransaction(object : Transaction.Handler {
                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    val rating = currentData.child("rating").getValue(Float::class.java)
                                    val rates = currentData.child("rates").getValue(Int::class.java)
                                    if (rating != null && rates != null) {
                                        if (rates < 2) {
                                            currentData.child("rating").value = if (rates == 1) rating + 5F else 2.5F
                                            currentData.child("rates").value = rates + 1
                                        }
                                    }
                                    currentData.child("emailVerified").value = it
                                    return Transaction.success(currentData)
                                }
                                override fun onComplete(error: DatabaseError?,
                                    committed: Boolean, currentData: DataSnapshot?
                                ) {
                                    if (committed) {
                                        account.apply {
                                            if (rates!! < 2) {
                                                rating = if (rates == 1) rating!! + 5F else 2.5F
                                                rates = rates!! + 1
                                            }
                                            emailVerified = true
                                        }
                                        session.account.value?.let { acc ->
                                            acc.apply {
                                                if ((rates ?: 0) < 2) {
                                                    rating = if ((rates ?: 0) == 1) (rating ?: 0f) + 5f else 2.5f
                                                    rates = (rates ?: 0) + 1
                                                }
                                                emailVerified = true
                                            }
                                            session.setAccount(acc)
                                        }
                                        binding.stars.rating = account.rating!!/account.rates!!.toFloat()
                                        binding.rating.text = "${if ((account.rating!!/account.rates!!.toFloat()).isNaN()) 0F else account.rating!!/account.rates!!.toFloat()} (${account.rates})"
                                        binding.btnVerifyEmail.visibility = if (account.emailVerified == true) View.GONE else View.VISIBLE
                                        binding.emailVerified.text = if (account.emailVerified == true) "Verified" else "Unverified"
                                        viewModel.setVerified(true)
                                    }
                                }
                            })
                    }
                }
            }
        binding.emailVerified.text = if (auth.currentUser!!.isEmailVerified) "Verified" else "Unverified"
        binding.btnVerifyEmail.visibility = if (auth.currentUser!!.isEmailVerified) View.GONE else View.VISIBLE
        binding.email.text = account.email
        binding.phone.text = account.phone
        binding.swipeRefresh.isRefreshing = false
        setupLocation()
    }

    private fun observeRide(ride: Ride?) {
        binding.btnEditAccount.isEnabled = ride == null
        binding.btnLogout.isEnabled = ride == null
        binding.btnDelete.isEnabled = ride == null
    }

    private fun verifyAccount() {
        confirmDialog = ConfirmDialog("Verify account",
            "Do you want to verify your account? This will require you to scan your National ID.",
            "Verify", "Cancel") { confirm ->
            if (confirm) scanID()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
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
            if (first != null && last != null) {
                setupProgress("Verifying your account")
                dbRef.child("Account/TodaGo/${account.uid}")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val rating = currentData.child("rating").getValue(Float::class.java)
                            val rates = currentData.child("rates").getValue(Int::class.java)
                            if (rating != null && rates != null) {
                                if (rates < 2) {
                                    currentData.child("rating").value = if (rates == 1) rating + 5F else 2.5F
                                    currentData.child("rates").value = rates + 1
                                }
                            } else return Transaction.abort()
                            currentData.child("name").value = "$first $last"
                            currentData.child("verified").value = true
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?,
                            committed: Boolean, currentData: DataSnapshot?
                        ) {
                            if (error != null) snackBar(view, "Error: ${error.message}")
                            else if (committed) {
                                session.account.value?.let { acc ->
                                    acc.apply {
                                        name = "$first $last"
                                        if ((rates ?: 0) < 2) {
                                            rating = if ((rates ?: 0) == 1) (rating ?: 0f) + 5f else 2.5f
                                            rates = (rates ?: 0) + 1
                                        }
                                        verified = true
                                    }
                                    session.setAccount(acc)
                                }
                                account.apply {
                                    name = "$first $last"
                                    if (rates!! < 2) {
                                        rating = if (rates == 1) rating!! + 5F else 2.5F
                                        rates = rates!! + 1
                                    }
                                    verified = true
                                }
                                setupData()
                            }
                            editAccount()
                            endProgress()
                        }
                    })
            } else snackBar(view, "Error: Something went wrong")
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

    private fun setupLocation() {
        RetrofitClient.geocodeService.reverseGeocode("${account.location}", session.key.value!!)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()!!
                        binding.location.text = (if (address.extra != null) "${address.extra}, " else "") +
                                (if (address.locality != null) "${address.locality}, " else "") +
                                (if (address.city != null) "${address.city}, " else "") +
                                (address.province ?: "")
                    } else snackBar(view, "Error: ${response.errorBody()}")
                    endProgress()
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {
                    endProgress()
                    snackBar(view, "Error: ${t.message}")
                }
            })
    }

    private fun editEmail() {
        emailDialog = EmailDialog {
            infoDialog = InfoDialog("Email verification links has been sent to your " +
                    "current and new email address. Please click the links to confirm your email " +
                    "change. If you can't find it in your Inbox, kindly check your Spam folder.")
            infoDialog!!.show(childFragmentManager, "InfoDialog")
            editAccount()
        }
        emailDialog!!.show(childFragmentManager, "EmailDialog")
    }

    private fun verifyEmail() {
        setupProgress("Sending verification link")
        auth.currentUser!!.sendEmailVerification()
            .addOnSuccessListener {
                infoDialog = InfoDialog("Email verification link has been sent to your " +
                        "email address. Please click the link to confirm your email change. If you " +
                        "can't find it in your Inbox, kindly check your Spam folder.")
                infoDialog!!.show(childFragmentManager, "InfoDialog")
                endProgress()
            }
            .addOnFailureListener {
                endProgress()
                snackBar(view, "Error: ${it.message}")
            }
    }

    private fun editPhone() {
        phoneDialog = PhoneDialog { phone ->
            snackBar(view, "Phone number has been changed")
            session.account.value?.let { acc ->
                acc.phone = phone
                session.setAccount(acc)
            }
            account.phone = phone
            binding.phone.text = account.phone
            editAccount()
        }
        phoneDialog!!.show(childFragmentManager, "PhoneDialog")
    }

    private fun editLocation() {
        confirmDialog = ConfirmDialog("Change location",
            "Are you sure you want to change your address location?",
            "Change", "Cancel") { confirm ->
            if (confirm) {
                val intent = Intent(requireContext(), LocationPickerActivity::class.java)
                getLocation.launch(intent)
            }
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun saveData(data: Intent?) {
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        dbRef.child("Account/TodaGo/${account.uid}/location").setValue("$latitude,$longitude")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    session.account.value?.let { acc ->
                        acc.location = "$latitude,$longitude"
                        session.setAccount(acc)
                    }
                    account.location = "$latitude,$longitude"
                    if (session.key.value == null) fetchAPI { setupLocation() }
                    else setupLocation()
                    editAccount()
                } else snackBar(view, "Error: ${task.exception!!.message}")
            }
    }

    private fun editAccount() {
        binding.btnEditEmail.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditPhone.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditLocation.apply {
            visibility = if (isGone) View.VISIBLE
            else View.GONE
        }
        binding.btnEditAccount.apply {
            val btnTxt = "Edit Account Details"
            text = if (text.equals(btnTxt)) "Cancel Edit" else btnTxt
        }
    }

    private fun changePassword() {
        passwordDialog = PasswordDialog(account.email!!) {
            snackBar(view, "Password has been changed")
        }
        passwordDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun delete() {
        auth.currentUser!!.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
    }

    private fun signOut() {
        confirmDialog = ConfirmDialog("Log out",
            "Are you sure you want to log out?",
            "Log out", "Cancel") { confirm ->
            if (confirm) {
                auth.signOut()
                session.setAccount(null)
                session.setFee(null)
                session.setKey(null)
                session.setDeviceID(null)
                session.setCity(null)
                val intent = Intent(requireContext(), LoginActivity::class.java)
                startActivity(intent)
                requireActivity().finish()
            }
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun deleteAccount() {
        confirmDialog = ConfirmDialog("Delete account",
            "Are you sure you want to delete your account?",
            "Delete Account", "Cancel") { confirm ->
            if (confirm) delete()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
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