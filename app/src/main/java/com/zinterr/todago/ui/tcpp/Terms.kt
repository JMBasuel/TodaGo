package com.zinterr.todago.ui.tcpp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import com.zinterr.todago.databinding.TermsBinding

class Terms : Fragment() {

    private lateinit var binding: TermsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TermsBinding.inflate(inflater, container, false)
        return binding.root
    }
}