package com.zinterr.todago.util

import com.zinterr.todago.viewholder.HistoryViewHolder

interface HistoryClickListener {
    fun getViewHolder(id: String): List<HistoryViewHolder>
}