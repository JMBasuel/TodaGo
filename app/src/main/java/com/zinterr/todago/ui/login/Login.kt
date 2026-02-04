package com.zinterr.todago.ui.login

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.*
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.*
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.zinterr.todago.R
import com.zinterr.todago.MainActivity
import com.zinterr.todago.TodaGo
import com.zinterr.todago.databinding.LoginBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.checkGPS
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.isNotificationDisabled
import com.zinterr.todago.ui.popup.ConfirmDialog
import com.zinterr.todago.util.snackBar
import com.zinterr.todago.viewmodel.SessionViewModel
import java.util.UUID

@SuppressLint("ClickableViewAccessibility")
class Login : Fragment() {

    private lateinit var binding: LoginBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var session: SessionViewModel
    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var isAppValid: Boolean? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) checkGPS(requireContext(), locationSettingsLauncher) { checkLogin() }
            else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else requireNotifications()
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkGPS(requireContext(), locationSettingsLauncher) { checkLogin() }
        else requireLocationService()
    }
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) checkLogin()
        else requireGPS()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    clear()
                }
            }
            true
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        emailListener()
        passwordListener()

        binding.btnForgot.setOnClickListener {
            findNavController().navigate(LoginDirections.actionLoginToForgot())
        }

        binding.btnLogin.setOnDebouncedClickListener {
            clear()
            login()
        }

        binding.btnSignup.setOnClickListener {
            findNavController().navigate(LoginDirections.actionLoginToSignupCommuter())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onStart() {
        super.onStart()
        checkAppValidity {
            if (isAppValid == true) {
                getApp {
                    if (Global.app!!.todaGoVersion!! != Global.VERSION && !Global.VERSION.endsWith("DEBUG")) showRequireUpdate()
                    else checkPermissions()
                }
            } else AlertDialog.Builder(requireContext())
                .setTitle("APP DISCONTINUED")
                .setMessage("Sorry. This application has been discontinued by the administrator.")
                .setPositiveButton("Understood") { _, _ ->
                    requireActivity().finish()
                }
                .show()
        }
    }

    private fun initialize() {
        dbRef = Firebase.database.reference
        auth = Firebase.auth
        remoteConfig = Firebase.remoteConfig
        session = ViewModelProvider((requireActivity().application as TodaGo),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((requireActivity().application as TodaGo)))[SessionViewModel::class.java]
    }

    private fun login() {
        checkAppValidity {
            if (isAppValid == true) {
                val isEmail = binding.emailContainer.helperText == null &&
                        binding.emailContainer.error == null
                val isPass = binding.passwordContainer.helperText == null &&
                        binding.passwordContainer.error == null
                val email = binding.emailEditText.text.toString()
                val pass = binding.passwordEditText.text.toString()
                if (isEmail && isPass) {
                    setupProgress("Logging in")
                    auth.signInWithEmailAndPassword(email, pass)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                getAccount(task.result.user?.uid!!) { account ->
                                    if (account != null) {
                                        session.setAccount(account)
                                        session.setDeviceID(getDeviceID())
                                        dbRef.child("Account/TodaGo/${account.uid}/sessionID").setValue(session.deviceID.value)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    binding.loading.container.visibility = View.GONE
                                                    val intent = Intent(requireContext(), MainActivity::class.java)
                                                    startActivity(intent)
                                                    requireActivity().finish()
                                                }
                                            }
                                    } else {
                                        auth.signOut()
                                        binding.loading.container.visibility = View.GONE
                                        snackBar(view, "Something went wrong")
                                    }
                                }
                            } else {
                                binding.loading.container.visibility = View.GONE
                                snackBar(view, "Incorrect credentials")
                            }
                        }
                } else if (isEmail) {
                    if (binding.passwordContainer.error != "Incorrect password")
                        binding.passwordContainer.error = "Minimum of 8 characters"
                } else if (isPass) {
                    binding.emailContainer.error = "Invalid email address"
                } else {
                    if (binding.passwordContainer.error != "Incorrect password")
                        binding.passwordContainer.error = "Minimum of 8 characters"
                    binding.emailContainer.error = "Invalid email address"
                }
            } else AlertDialog.Builder(requireContext())
                .setTitle("APP DISCONTINUED")
                .setMessage("Sorry. This application has been discontinued by the administrator.")
                .setPositiveButton("Understood") { _, _ ->
                    requireActivity().finish()
                }
                .show()
        }

    }

    private fun getAccount(uid: String, onComplete: (Account?) -> Unit) {
        setupProgress("Fetching data")
        dbRef.child("Account/TodaGo/$uid").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val account = snapshot.getValue(Account::class.java)!!
                        onComplete(account)
                    } else onComplete(null)
                }
                override fun onCancelled(error: DatabaseError) {
                    binding.loading.container.visibility = View.GONE
                    snackBar(view, "Error encountered")
                }
            })
    }

    private fun checkAppValidity(onComplete: () -> Unit) {
        setupProgress("")
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) isAppValid = remoteConfig.getBoolean("app_state_active")
            binding.loading.container.visibility = View.GONE
            onComplete()
        }
    }

    private fun getApp(onComplete: () -> Unit) {
        dbRef.child("App").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val app = snapshot.getValue(App::class.java)
                        if (app != null) Global.app = app
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    binding.loading.container.visibility = View.GONE
                }
            })
    }

    private fun showRequireUpdate() {
        var changes = ""
        Global.app!!.todaGoLog!!.split(".").forEach { changes += "\n\t-- $it" }
        confirmDialog = ConfirmDialog("UPDATE ${if (Global.app!!.todaGoRequired == true) "REQUIRED" else "AVAILABLE"}",
            "Your app is not the latest (official) version. Update to version ${Global.app!!.todaGoVersion}.\n\nWhat's new in " +
                "${Global.app!!.todaGoVersion}:$changes", "UPDATE", if (Global.app!!.todaGoRequired == true) null else "Later") { confirmed ->
            if (confirmed) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Global.app!!.todaGoLink?.toUri()
                startActivity(Intent.createChooser(intent, "Open with"))
            } else checkPermissions()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun checkPermissions() {
        if (isNotificationDisabled(requireContext()))
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) checkGPS(requireContext(), locationSettingsLauncher) { checkLogin() }
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun checkLogin() {
        if (auth.currentUser != null) {
            setupProgress("Logging in")
            getAccount(auth.currentUser?.uid!!) { account ->
                session.setAccount(account!!)
                session.setDeviceID(getDeviceID())
                dbRef.child("Account/TodaGo/${account.uid}/sessionID").setValue(session.deviceID.value)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            binding.loading.container.visibility = View.GONE
                            val intent = Intent(requireContext(), MainActivity::class.java)
                            startActivity(intent)
                            requireActivity().finish()
                        }
                    }
            }
        }
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

    private fun requireLocationService() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Access to your location service is required for this application to work")
            .setPositiveButton("Understood") { dialog, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                dialog.dismiss()
            }
            .show()
    }

    private fun requireGPS() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Enabling your location service is required for this application to work")
            .setPositiveButton("Understood") { dialog, _ ->
                checkGPS(requireContext(), locationSettingsLauncher) { checkLogin() }
                dialog.dismiss()
            }
            .show()
    }

    private fun requireNotifications() {
        AlertDialog.Builder(requireContext())
            .setTitle("REQUIRED")
            .setMessage("Enabling notifications is required for this application to work")
            .setPositiveButton("Understood") { dialog, _ ->
                if (isNotificationDisabled(requireContext()))
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                dialog.dismiss()
            }
            .show()
    }

    private fun emailListener() {
        binding.emailEditText.setOnFocusChangeListener { _, focused ->
            val emailText = binding.emailEditText.text.toString()
            if (!focused) {
                if (emailText.isNotEmpty()) {
                    binding.emailContainer.helperText = null
                    binding.emailContainer.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches())
                        binding.emailContainer.error = "Invalid email address"
                } else binding.emailContainer.helperText = "Required"
            } else binding.emailContainer.helperText = "Required"
        }
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s!!.endsWith(".com") && before < count)
                    binding.passwordContainer.requestFocus()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.emailContainer.setEndIconOnClickListener {
            binding.emailContainer.requestFocus()
            binding.emailEditText.text = null
        }
    }

    private fun passwordListener() {
        binding.passwordEditText.setOnFocusChangeListener { _, focused ->
            val passwordText = binding.passwordEditText.text.toString()
            if (!focused) {
                if (passwordText.isNotEmpty()) {
                    binding.passwordContainer.helperText = null
                    binding.passwordContainer.error = null
                    if (passwordText.length < 8) binding.passwordContainer.error =
                        "Minimum of 8 characters"
                } else binding.passwordContainer.helperText = "Required"
            } else binding.passwordContainer.helperText = "Required"
        }
    }

    private fun setupProgress(message: String) {
        binding.loading.message.apply {
            visibility = if (message.isEmpty()) View.GONE
            else View.VISIBLE
            text = message
        }
        binding.loading.container.visibility = View.VISIBLE
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}