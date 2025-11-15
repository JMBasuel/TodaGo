package com.zinterr.todago.util

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.Snackbar
import com.zinterr.todago.databinding.SnackbarBinding

private var snackBar: Snackbar? = null

@SuppressLint("RestrictedApi")
fun snackBar(anchor: View?, message: String) {
    val context = anchor?.context
    if (context is Activity && !context.isFinishing && !context.isDestroyed) {

        val rootView = context.findViewById<View>(android.R.id.content)

        val snackBar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)
        snackBar.view.setBackgroundColor(Color.TRANSPARENT)

        val params = snackBar.view.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER
        params.setMargins(50, 0, 50, 200)
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        snackBar.view.layoutParams = params

        val snackBarLayout = snackBar.view as Snackbar.SnackbarLayout
        snackBarLayout.setPadding(0, 0, 0, 0)

        val binding = SnackbarBinding.inflate(LayoutInflater.from(context))
        binding.toastMessage.text = message
        snackBarLayout.addView(binding.root)

        snackBar.view.elevation = 100f
        ViewCompat.setTranslationZ(snackBar.view, 100f)

        snackBar.show()
    }
}

fun cancelSnackBar() {
    snackBar?.dismiss()
}