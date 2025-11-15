package com.zinterr.todago.ui.popup

import android.app.*
import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.zinterr.todago.databinding.DialogInfoBinding

class InfoDialog(
    private val message: String,
    private val onComplete: (() -> Unit)? = null
) : DialogFragment() {

    private lateinit var binding: DialogInfoBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogInfoBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.message.text = message

        binding.btnOk.setOnClickListener {
            onComplete?.invoke()
            dialog.dismiss()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }
}