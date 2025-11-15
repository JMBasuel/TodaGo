package com.zinterr.todago

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.zinterr.todago.databinding.ActivityMainBinding
import com.zinterr.todago.model.Global
import com.zinterr.todago.util.snackBar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkSession()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.myNavHostFragmentMain)
        return navController.navigateUp()
    }

    private fun checkSession() {
        Firebase.database.reference.child("Account/TodaGo/${Global.account?.uid}/sessionID")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val sessionID = snapshot.getValue(String::class.java)!!
                        if (sessionID != Global.deviceID) {
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