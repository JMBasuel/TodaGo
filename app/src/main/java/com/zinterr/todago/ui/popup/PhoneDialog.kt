package com.zinterr.todago.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.util.Patterns
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.google.firebase.Firebase
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.zinterr.todago.R
import com.zinterr.todago.databinding.DialogPhoneBinding
import com.zinterr.todago.model.Global.hideKeyboard
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.util.snackBar

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class PhoneDialog(
    private val onComplete: (String) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogPhoneBinding
    private lateinit var dbRef: DatabaseReference
    private lateinit var dialog: AlertDialog
    private lateinit var auth: FirebaseAuth

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogPhoneBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        dbRef = Firebase.database.reference
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

        phoneListener()

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
            val input = "${binding.phone.text.trim()}"
            updatePhone(input)
        }
    }

    private fun updatePhone(phone: String) {
        setupProgress()
        dbRef.child("Account/TodaGo/${auth.currentUser!!.uid}/phone").setValue(phone)
            .addOnCompleteListener { task ->
                binding.loading.container.visibility = View.GONE
                if (task.isSuccessful) {
                    onComplete(phone)
                    dialog.dismiss()
                } else snackBar(binding.root, "Error: ${task.exception!!.message}")
            }
    }

    private fun isInputValid(): Boolean = binding.phone.error == null

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

    private fun setupProgress() {
        binding.loading.message.text = "Updating phone number"
        binding.loading.container.visibility = View.VISIBLE
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}