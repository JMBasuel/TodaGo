package com.zinterr.todago.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todago.databinding.ItemPassengerBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.viewholder.PassengerViewHolder

class PassengerAdapter(
    private val passengers: ArrayList<Commuter>,
    private val account: Account
) :
    RecyclerView.Adapter<PassengerViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PassengerViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemPassengerBinding.inflate(from, parent, false)
        return PassengerViewHolder(binding, account)
    }
    override fun onBindViewHolder(holder: PassengerViewHolder, position: Int) {
        holder.bindPassenger(passengers[position])
    }
    override fun getItemCount(): Int = passengers.size
}