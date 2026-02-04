package com.zinterr.todago.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.util.Patterns
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.*
import androidx.core.graphics.drawable.toDrawable
import com.google.firebase.Firebase
import com.zinterr.todago.R
import com.zinterr.todago.databinding.DialogEmailBinding
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.util.snackBar

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class EmailDialog(
    private val onComplete: () -> Unit
): DialogFragment() {

    private lateinit var binding: DialogEmailBinding
    private lateinit var dialog: AlertDialog
    private lateinit var auth: FirebaseAuth

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogEmailBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        auth = Firebase.auth

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.loading.progress.setIndicatorColor(
            ContextCompat.getColor(requireContext(), R.color.blue_dark),
            ContextCompat.getColor(requireContext(), R.color.red))

        emailListener()
        passwordListener()
        inputListener()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnChange.setOnDebouncedClickListener {
            clear()
            change()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun change() {
        if (isInputValid()) {
            val input = "${binding.newInput.text.trim()}"
            authenticate {
                updateEmail(input)
            }
        }
    }

    private fun authenticate(onFinish: () -> Unit) {
        setupProgress("Authenticating")
        val credential = EmailAuthProvider.getCredential("${binding.email.text.trim()}", "${binding.password.text.trim()}")
        auth.currentUser!!.reauthenticate(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onFinish()
                else {
                    binding.loading.container.visibility = View.GONE
                    snackBar(binding.root, "Incorrect credentials")
                }
            }
    }

    private fun updateEmail(input: String) {
        setupProgress("Updating email")
        auth.currentUser!!.verifyBeforeUpdateEmail (input)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                onComplete()
                dialog.dismiss()
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    private fun isInputValid(): Boolean = binding.email.error == null &&
            binding.password.error == null && binding.newInput.error == null

    private fun emailListener() {
        binding.email.setOnFocusChangeListener { _, focused ->
            val emailText = binding.email.text.toString()
            if (!focused) {
                if (emailText.isNotEmpty()) {
                    binding.email.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches())
                        binding.email.error = "Invalid email address"
                } else binding.email.error = "Required"
            } else binding.email.error = "Required"
        }
    }

    private fun passwordListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val password = "${binding.password.text.trim()}"
            if (!focused) {
                if (password.isNotEmpty()) {
                    binding.password.error = null
                    if (password.length < 8) binding.password.error =
                        "Minimum of 8 characters"
                } else binding.password.error = "Required"
            } else binding.password.error = "Required"
        }
    }

    private fun inputListener() {
        binding.newInput.setOnFocusChangeListener { _, focused ->
            val newInput = "${binding.newInput.text.trim()}"
            if (!focused) {
                if (newInput.isNotEmpty()) {
                    binding.newInput.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(newInput).matches())
                        binding.newInput.error = "Invalid email address"
                } else binding.newInput.error = "Required"
            }
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