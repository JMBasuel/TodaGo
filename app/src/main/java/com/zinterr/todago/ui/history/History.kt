package com.zinterr.todago.ui.history

import android.annotation.SuppressLint
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.zinterr.todago.adapter.HistoryAdapter
import com.zinterr.todago.databinding.HistoryBinding
import com.zinterr.todago.model.*
import com.zinterr.todago.model.History
import com.zinterr.todago.ui.home.Home
import com.zinterr.todago.util.*
import com.zinterr.todago.R
import com.zinterr.todago.viewholder.HistoryViewHolder
import com.zinterr.todago.viewmodel.CurrentViewViewModel
import kotlin.getValue

@SuppressLint("SetTextI18n")
class History : Fragment(), HistoryClickListener {

    private lateinit var binding: HistoryBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var dbRef: DatabaseReference
    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.rvHistory.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            fetchHistory()
        }
    }

    override fun onStart() {
        super.onStart()
        fetchHistory()
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    private fun initialize() {
        account = Global.account!!
        dbRef = Firebase.database.reference
    }

    private fun fetchHistory() {
        setupProgress()
        dbRef.child("Account/TodaGo/${account.uid}/History")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val histories = mutableListOf<History>()
                    if (snapshot.exists()) {
                        for (item in snapshot.children) {
                            val history = item.getValue(History::class.java)!!
                            histories.add(history)
                        }
                    }
                    if (!histories.isEmpty()) {
                        histories.sortByDescending { it.dateTime }
                        setupHistory(ArrayList(histories))
                    } else setupHistory()
                    endProgress()
                    binding.swipeRefresh.isRefreshing = false
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun setupHistory(history: ArrayList<History>? = null) {
        if (history != null) {
            historyAdapter = HistoryAdapter(history, this)
            binding.rvHistory.adapter = historyAdapter
        } else {
            binding.noHistory.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        }
    }

    override fun getViewHolder(id: String): List<HistoryViewHolder> {
        return historyAdapter.getHistoryViewHolders(id)
    }

    private fun setupProgress() {
        binding.loading.message.text = "Loading data"
        binding.loading.container.visibility = View.VISIBLE
        Home().addCallbackFalse()
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        Home().addCallbackTrue()
    }
}