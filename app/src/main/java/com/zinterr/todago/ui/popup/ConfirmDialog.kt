package com.zinterr.todago.ui.popup

import android.app.*
import android.os.*
import android.view.*
import androidx.fragment.app.DialogFragment
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable
import com.zinterr.todago.databinding.DialogConfirmBinding
import com.zinterr.todago.model.Global.setOnDebouncedClickListener

class ConfirmDialog(
    private val title: String,
    private val message: String,
    private val positive: String,
    private val negative: String? = null,
    private val onComplete: (Boolean) -> Unit
): DialogFragment() {

    private lateinit var binding: DialogConfirmBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogConfirmBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.title.text = title.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.message.text = message
        binding.btnPositive.text = positive
        if (negative != null) binding.btnNegative.apply {
            text = negative
            visibility = View.VISIBLE
        }

        binding.btnNegative.setOnClickListener {
            dialog.dismiss()
            onComplete(false)
        }

        binding.btnPositive.setOnDebouncedClickListener {
            dialog.dismiss()
            onComplete(true)
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }
}