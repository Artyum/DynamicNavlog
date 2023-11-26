package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentAirplanelistBinding

class AirplaneListFragment : Fragment(R.layout.fragment_airplanelist), AirplaneListAdapter.OnItemClickInterface {
    private var _binding: FragmentAirplanelistBinding? = null
    private val bind get() = _binding!!
    private var adapterList = ArrayList<AirplaneData>()
    private var adapter = AirplaneListAdapter(adapterList, this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAirplanelistBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.airplaneListLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Add airplane
        bind.addAirplane.setOnClickListener {
            setFragmentResult("requestKey", bundleOf("airplaneId" to ""))
            findNavController().navigate(R.id.action_airplaneListFragment_to_airplaneFragment)
        }

        // Search box
        bind.searchInput.doAfterTextChanged {
            refreshList()
        }

        val recyclerView = bind.airplaneListRecycler
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.context)

        // Helper on end drag item
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        refreshList()
    }

    // Moving/drag&drop items in RecycleView
    private var simpleCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT.or(ItemTouchHelper.RIGHT)) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val pos = viewHolder.absoluteAdapterPosition
            val id = adapterList[pos].id
            deleteAirplane(id)
            adapterList.removeAt(pos)
            adapter.notifyItemRemoved(pos)
        }
    }

    override fun onItemClick(position: Int) {
        setFragmentResult("requestKey", bundleOf("airplaneId" to State.airplaneList[position].id))
        findNavController().navigate(R.id.action_airplaneListFragment_to_airplaneFragment)
    }

    private fun refreshList() {
        adapterList.clear()
        val search = bind.searchInput.text.toString().trim()
        if (search != "") {
            for (i in State.airplaneList.indices) {
                if (State.airplaneList[i].type.indexOf(search, ignoreCase = true) != -1 ||
                    State.airplaneList[i].reg.indexOf(search, ignoreCase = true) != -1
                ) adapterList.add(State.airplaneList[i])
            }
        } else {
            adapterList.addAll(State.airplaneList)
        }
        adapter.notifyDataSetChanged()
    }

    private fun deleteAirplane(id: String) {
        for (i in State.airplaneList.indices) {
            if (State.airplaneList[i].id == id) {
                State.airplaneList.removeAt(i)
                break
            }
        }
        FileUtils.saveAirplaneList()
    }
}