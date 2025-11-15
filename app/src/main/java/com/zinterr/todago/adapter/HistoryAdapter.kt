package com.zinterr.todago.adapter

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.zinterr.todago.databinding.ItemHistoryBinding
import com.zinterr.todago.model.History
import com.zinterr.todago.util.HistoryClickListener
import com.zinterr.todago.viewholder.HistoryViewHolder

class HistoryAdapter(
    private val history: ArrayList<History>,
    private val clickListener: HistoryClickListener
) :
    RecyclerView.Adapter<HistoryViewHolder>()
{
    private val viewHolders = mutableListOf<HistoryViewHolder?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemHistoryBinding.inflate(from, parent, false)
        val viewHolder = HistoryViewHolder(binding, clickListener)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val current = history[position]
        val previous = history.getOrNull(position - 1)
        val showDate = previous == null || previous.dateTime?.substringBefore(' ') != current.dateTime?.substringBefore(' ')
        holder.bindHistory(history[position], showDate)
    }

    override fun getItemCount(): Int = history.size

    fun getHistoryViewHolders(id: String): List<HistoryViewHolder> {
        val validViewHolders = mutableListOf<HistoryViewHolder>()
        for (viewHolder in viewHolders) if (viewHolder != null && viewHolder.id == id)
            validViewHolders.add(viewHolder)
        return validViewHolders
    }
}