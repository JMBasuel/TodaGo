package com.zinterr.todago.ui.home

import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayout
import com.zinterr.todago.R
import com.zinterr.todago.adapter.PageAdapter
import com.zinterr.todago.databinding.HomeBinding
import com.zinterr.todago.model.Global
import com.zinterr.todago.ui.history.History
import com.zinterr.todago.ui.profile.Profile
import com.zinterr.todago.util.*
import com.zinterr.todago.viewmodel.CurrentViewViewModel
import kotlin.getValue

class Home : Fragment() {

    private lateinit var binding: HomeBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private var currentView: View? = null
    private var navBarBottom = 0
    private var press = 0
    private var prev = 0L
    private val callbackTrue = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } }
    private val callbackFalse = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HomeBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.home) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val params = binding.bottomNavigation.layoutParams as ViewGroup.LayoutParams
            params.height = 170 + navBars.bottom
            navBarBottom = navBars.bottom
            binding.bottomNavigation.layoutParams = params
            insets
        }
        binding.vpViews.adapter = PageAdapter(requireActivity(), listOf(Commuter(), History(), Profile()))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 2
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavigation()
        currentViewViewModel.currentView.observe(viewLifecycleOwner) { view ->
            currentView = view
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackTrue)
    }

    override fun onPause() {
        super.onPause()
        callbackTrue.remove()
    }

    private fun setupNavigation() {
        val icons = listOf(
            R.drawable.home,
            R.drawable.history,
            R.drawable.profile)
        val titles = listOf(
            getString(R.string.home),
            getString(R.string.history),
            getString(R.string.profile))
        for (i in icons.indices) {
            val tab = binding.bottomNavigation.newTab()
            binding.bottomNavigation.addTab(tab)
            val view = LayoutInflater.from(requireContext()).inflate(R.layout.tab_item, binding.root, false)
            view.setPadding(0, 0, 0, navBarBottom + 50)
            val icon = view.findViewById<ImageView>(R.id.tab_icon)
            val text = view.findViewById<TextView>(R.id.tab_text)
            icon.setImageResource(icons[i])
            text.text = titles[i]
            if (i == 0) text.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_dark))
            tab.customView = view
            tab.view.setOnLongClickListener { true }
        }
        binding.bottomNavigation.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                Global.navPosition = tab.position
                binding.vpViews.setCurrentItem(tab.position, false)
                val icon = tab.customView?.findViewById<ImageView>(R.id.tab_icon)
                val text = tab.customView?.findViewById<TextView>(R.id.tab_text)
                icon?.setColorFilter(ContextCompat.getColor(requireContext(), R.color.blue_dark))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_dark))
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                val icon = tab?.customView?.findViewById<ImageView>(R.id.tab_icon)
                val text = tab?.customView?.findViewById<TextView>(R.id.tab_text)
                icon?.setColorFilter(ContextCompat.getColor(requireContext(), R.color.gray))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    fun addCallbackFalse() {
        try { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
        } catch (_: Exception) {}
    }

    fun addCallbackTrue() {
        try { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackTrue)
        } catch (_: Exception) {}
    }

    private fun back() {
        val current = System.currentTimeMillis()
        if (current - prev > 2000) {
            press = 1
            currentView?.let { snackBar(currentView, "Press back again to exit") }
            Handler(Looper.getMainLooper()).postDelayed({
                cancelSnackBar()
            }, 2000)
        } else {
            press++
            if (press == 2) {
                cancelSnackBar()
                requireActivity().finish()
            }
        }
        prev = current
    }
}