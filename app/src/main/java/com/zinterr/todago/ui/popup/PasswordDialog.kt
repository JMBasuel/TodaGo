package com.zinterr.todago.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.text.*
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.*
import androidx.core.graphics.drawable.toDrawable
import com.google.firebase.Firebase
import com.zinterr.todago.R
import com.zinterr.todago.databinding.DialogPasswordBinding
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.util.snackBar

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class PasswordDialog(
    private val email: String,
    private val onComplete: () -> Unit
): DialogFragment() {

    private lateinit var binding: DialogPasswordBinding
    private lateinit var dialog: AlertDialog
    private lateinit var auth: FirebaseAuth

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPasswordBinding.inflate(layoutInflater)
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
                updatePassword(input)
            }
        }
    }

    private fun authenticate(onFinish: () -> Unit) {
        setupProgress("Authenticating")
        val credential = EmailAuthProvider.getCredential(email, "${binding.password.text.trim()}")
        auth.currentUser!!.reauthenticate(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onFinish()
                else {
                    binding.loading.container.visibility = View.GONE
                    snackBar(binding.root, "Incorrect password")
                }
            }
    }

    private fun updatePassword(input: String) {
        setupProgress("Updating password")
        auth.currentUser!!.updatePassword(input)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                auth.currentUser!!.reload()
                onComplete()
                dialog.dismiss()
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    private fun isInputValid(): Boolean = binding.password.error == null && binding.newInput.error == null

    private fun passwordListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val password = "${binding.password.text.trim()}"
            if (!focused) {
                if (password.isNotEmpty()) {
                    binding.password.error = null
                    if (password.length < 8) binding.password.error =
                        "Minimum of 8 characters"
                } else binding.password.error = "Required"
            }
        }
    }

    private fun inputListener() {
        binding.newInput.setOnFocusChangeListener { _, focused ->
            val newInput = "${binding.newInput.text.trim()}"
            if (!focused) {
                if (newInput.isNotEmpty()) {
                    binding.newInput.error = null
                    if (!Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).{8,}$")
                        .matches(newInput)) binding.newInput.error = "Requirements not met"
                } else binding.newInput.error = "Required"
            }
        }
        binding.newInput.addTextChangedListener(object : TextWatcher {
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