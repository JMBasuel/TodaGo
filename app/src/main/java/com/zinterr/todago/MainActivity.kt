package com.zinterr.todago

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.zinterr.todago.databinding.ActivityMainBinding
import com.zinterr.todago.util.snackBar
import com.zinterr.todago.viewmodel.SessionViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: SessionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = ViewModelProvider((application as TodaGo),
            ViewModelProvider.AndroidViewModelFactory
                .getInstance((application as TodaGo)))[SessionViewModel::class.java]
        checkSession()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.myNavHostFragmentMain)
        return navController.navigateUp()
    }

    private fun checkSession() {
        Firebase.database.reference.child("Account/TodaGo/${session.account.value?.uid}/sessionID")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val sessionID = snapshot.getValue(String::class.java)!!
                        if (sessionID != session.deviceID.value) {
                            snackBar(binding.root, "Your account has been logged in on another device. You have been signed out.")
                            Firebase.auth.signOut()
                            val intent = Intent(this@MainActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}