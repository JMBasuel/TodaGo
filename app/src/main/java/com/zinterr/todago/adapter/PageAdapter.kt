package com.zinterr.todago.adapter

import androidx.fragment.app.*
import androidx.viewpager2.adapter.FragmentStateAdapter

class PageAdapter(
    fragmentActivity: FragmentActivity,
    private val fragments: List<Fragment>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}