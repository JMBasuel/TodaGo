package com.zinterr.todago.viewholder

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todago.databinding.ItemHistoryBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.Global.timeTo12
import com.zinterr.todago.util.HistoryClickListener

@SuppressLint("SetTextI18n")
class HistoryViewHolder(
    private var binding: ItemHistoryBinding,
    private val clickListener: HistoryClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var id: String

    fun bindHistory(history: History, showDate: Boolean) {
        id = history.uid!!
        if (showDate)binding.historyDate.apply {
            text = history.dateTime?.substringBefore(' ')
            visibility = View.VISIBLE
        }
        binding.historyTime.text = timeTo12(history.dateTime?.substringAfter(' ')!!)
        binding.historyUID.text = history.uid
        binding.historyType.text = "${if (history.solo == true) "SOLO" else "POOL"} RIDE"
        binding.historyDriver.text = history.driver
        binding.historyPassenger.text = "${history.passenger}"
        binding.historyOrigin.text = history.start
        binding.historyDestination.text = history.end
        binding.historyPrice.text = "₱ ${history.price}.00"
        binding.cvHistory.setOnClickListener {
            if (Global.historyPrev == id) {
                binding.content.apply {
                    visibility = if (isVisible) View.GONE
                    else View.VISIBLE
                }
            } else {
                if (Global.historyPrev != null) resetCard(Global.historyPrev!!)
                binding.content.visibility = View.VISIBLE
                Global.historyPrev = id
            }
        }
    }

    private fun resetCard(id: String) {
        val previous = clickListener.getViewHolder(id)
        for (viewHolder in previous)
            viewHolder.binding.content.visibility = View.GONE
    }
}