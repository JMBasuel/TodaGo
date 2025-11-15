package com.zinterr.todago.viewholder

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todago.databinding.ItemPassengerBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.R

@SuppressLint("SetTextI18n")
class PassengerViewHolder(
    private var binding: ItemPassengerBinding,
    private val account: Account
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindPassenger(commuter: Commuter) {
        if (commuter.uid == account.uid) {
            if (account.gender.equals("Female")) binding.passengerProfile.setImageResource(R.drawable.head_female)
            else binding.passengerProfile.setImageResource(R.drawable.head_male)
        } else binding.passengerProfile.setImageResource(R.drawable.commuter)
        binding.passengerName.text = commuter.name
        binding.passengerStars.rating = commuter.rate!!.toFloat()
        binding.passengerRate.text = "${commuter.rate.toFloat()}"
        binding.passengerWeight.text = "${when (commuter.weight) {
            1 -> "Handcarry"
            2 -> "Small"
            else -> "Large"
        }} ● ${commuter.passenger}"
        binding.passengerStatus.text = when {
            commuter.status!!.contains("DRIVING") -> "On board"
            else -> "Waiting"
        }
    }
}