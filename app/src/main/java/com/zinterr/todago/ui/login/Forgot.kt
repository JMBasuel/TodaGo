package com.zinterr.todago.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import androidx.core.graphics.toColorInt
import androidx.navigation.fragment.findNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.zinterr.todago.databinding.ForgotBinding
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.util.snackBar

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class Forgot : Fragment() {

    private lateinit var binding: ForgotBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ForgotBinding.inflate(inflater, container, false)
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
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRecover.setOnDebouncedClickListener {
            clear()
            recover()
        }
    }

    private fun initialize() {
        auth = Firebase.auth
    }

    private fun recover() {
        val email = "${binding.email.text.trim()}"
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setupProgress()
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    binding.loading.container.visibility = View.GONE
                    snackBar(view, "Password reset link has been sent")
                }
                .addOnFailureListener {
                    binding.loading.container.visibility = View.GONE
                    snackBar(binding.root, "Error: ${it.message}")
                }
        }
    }

    private fun setupProgress() {
        binding.loading.message.text = "Sending reset link"
        binding.loading.container.visibility = View.VISIBLE
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}