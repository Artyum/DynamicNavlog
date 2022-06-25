package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentAirplanelistBinding

class AirplaneListFragment : Fragment(R.layout.fragment_airplanelist), AirplaneListAdapter.OnItemClickInterface {
    private var _binding: FragmentAirplanelistBinding? = null
    private val bind get() = _binding!!
    private var list = ArrayList<Airplane>()
    private var adapter = AirplaneListAdapter(list, this)

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
        bind.airplaneListLayout.keepScreenOn = settings.keepScreenOn

        bind.addAirplane.setOnClickListener {
            editAirplaneID = null
            findNavController().navigate(AirplaneListFragmentDirections.actionAirplaneListFragmentToAirplaneFragment())
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
            val id = list[pos].id
            deleteAirplane(id)
            refreshList()
        }
    }

    override fun onItemClick(position: Int) {
        editAirplaneID = airplaneList[position].id
        findNavController().navigate(AirplaneListFragmentDirections.actionAirplaneListFragmentToAirplaneFragment())
    }

    private fun refreshList() {
        list.clear()
        val search = bind.searchInput.text.toString().trim()
        if (search != "") {
            for (i in airplaneList.indices) {
                if (airplaneList[i].type.indexOf(search, ignoreCase = true) != -1 ||
                    airplaneList[i].reg.indexOf(search, ignoreCase = true) != -1
                ) list.add(airplaneList[i])
            }
        } else {
            list.addAll(airplaneList)
        }
        adapter.notifyDataSetChanged()
    }

    private fun deleteAirplane(id: String) {
        for (i in airplaneList.size - 1 downTo 0) {
            if (airplaneList[i].id == id) airplaneList.removeAt(i)
        }
        saveAirplaneList()
    }
}