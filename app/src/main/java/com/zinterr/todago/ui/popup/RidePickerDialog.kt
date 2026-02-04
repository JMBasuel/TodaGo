package com.zinterr.todago.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.drawable.toDrawable
import com.google.android.gms.maps.model.LatLng
import com.zinterr.todago.R
import com.zinterr.todago.util.LocationPickerActivity
import com.zinterr.todago.databinding.DialogRidePickerBinding
import com.zinterr.todago.model.GeocodeResponse
import com.zinterr.todago.model.Global.setOnDebouncedClickListener
import com.zinterr.todago.model.Global.toLocalAddress
import com.zinterr.todago.network.RetrofitClient
import com.zinterr.todago.util.snackBar
import retrofit2.*

@SuppressLint("SetTextI18n")
class RidePickerDialog(
    private val apiKey: String,
    private val type: String? = null,
    private val onBookClick: (String, LatLng, String, LatLng, String, Int, Int, String?) -> Unit
) : DialogFragment() {
    private lateinit var binding: DialogRidePickerBinding
    private lateinit var dialog: AlertDialog
    private var destinationAddress: String? = null
    private var originAddress: String? = null
    private var destination: LatLng? = null
    private var textView: TextView? = null
    private var current: String? = null
    private var origin: LatLng? = null
    private var city: String? = null
    private var baggage: Int = 1
    private var count: Int = 1
    private val getLocation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) showData(result.data) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogRidePickerBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        setSliderListeners()
        setupAgreement()

        binding.title.apply {
            if (type != null) {
                text = "Book a Ride (Solo)"
                binding.message.text = "Your ride may be tagged as SPECIAL which will cost more"
            } else text = "Share a Ride (Pool)"
        }

        binding.locationPickerOrigin.setOnDebouncedClickListener {
            getAddress(binding.locationOrigin)
        }

        binding.locationPickerDestination.setOnDebouncedClickListener {
            getAddress(binding.locationDestination)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnBook.setOnDebouncedClickListener {
            bookRide()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun bookRide() {
        if (origin != null && destination != null) {
            onBookClick(city!!, origin!!, originAddress!!, destination!!, destinationAddress!!, count, baggage, current)
            dialog.dismiss()
        } else snackBar(view, "INCOMPLETE DETAILS")
    }

    private fun getAddress(view: TextView) {
        textView = view
        val intent = Intent(requireContext(), LocationPickerActivity::class.java)
        getLocation.launch(intent)
    }

    private fun showData(data: Intent?) {
        val latitude = data?.getDoubleExtra("LATITUDE", 0.0)
        val longitude = data?.getDoubleExtra("LONGITUDE", 0.0)
        val location = data?.getStringExtra("CURRENT")
        current = location
        if (textView == binding.locationOrigin) origin = LatLng(latitude!!, longitude!!)
        if (textView == binding.locationDestination) destination = LatLng(latitude!!, longitude!!)
        RetrofitClient.geocodeService.reverseGeocode("$latitude,$longitude", apiKey)
            .enqueue(object : Callback<GeocodeResponse> {
                override fun onResponse(call: Call<GeocodeResponse?>, response: Response<GeocodeResponse?>) {
                    if (response.isSuccessful) {
                        val address = response.body()?.toLocalAddress()
                        if (textView == binding.locationOrigin) city = address?.city
                        val locationAddress = (if (address?.extra != null) "${address.extra}, " else "") +
                                (if (address?.locality != null) "${address.locality}, " else "") +
                                (if (address?.city != null) "${address.city}, " else "") +
                                (address?.province ?: "")
                        if (textView == binding.locationOrigin) originAddress = locationAddress
                        if (textView == binding.locationDestination) destinationAddress = locationAddress
                        textView?.text = locationAddress
                    } else snackBar(view, "Error: ${response.errorBody()}")
                }
                override fun onFailure(call: Call<GeocodeResponse?>, t: Throwable) {}
            })
    }

    private fun setSliderListeners() {
        val message = "Your ride may be tagged as SPECIAL which will cost more"
        binding.passengerSlider.addOnChangeListener { _, value, _ ->
            binding.passengerCount.text = "Passenger/s: ${value.toInt()}"
            count = value.toInt()
            type?.let {
                if (count > 2) binding.message.text = ""
                else binding.message.text = message
            }
        }
        if (type == null) binding.baggageSlider.valueTo = 2F
        binding.baggageSlider.addOnChangeListener { _, value, _ ->
            baggage = value.toInt()
            binding.baggageClass.text = "Baggage: " + when (value.toInt()) {
                1 -> "Handcarry"
                2 -> "Small"
                else -> "Large"
            }
        }
    }

    private fun setupAgreement() {
        val text = "By proceeding, I agree to TodaGo's Terms and Privacy Policies"
        val ss = SpannableString(text)
        val startT = text.indexOf("Terms")
        val endT = startT + "Terms".length
        val startP = text.indexOf("Privacy")
        val endP = text.length
        ss.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_dark)),
            startT, endT, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(StyleSpan(Typeface.BOLD_ITALIC), startT, endT, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_dark)),
            startP, endP, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(StyleSpan(Typeface.BOLD_ITALIC), startP, endP, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.terms.text = ss
        binding.terms.highlightColor = Color.TRANSPARENT
    }
}