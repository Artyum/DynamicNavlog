package com.artyum.dynamicnavlog

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentPlanlistBinding

class PlanListFragment : Fragment(R.layout.fragment_planlist), PlanListAdapter.OnItemClickInterface {
    private var _binding: FragmentPlanlistBinding? = null
    private val bind get() = _binding!!
    private val adapter = PlanListAdapter(State.planList, this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlanlistBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.planListLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Search box
        bind.searchInput.doAfterTextChanged {
            val search = bind.searchInput.text.toString().trim()
            FileUtils.loadFlightPlanList(search)
            adapter.notifyDataSetChanged()
        }

        val recyclerView = bind.planListRecycler
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.context)

        // Helper on end drag item
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        FileUtils.loadFlightPlanList()
    }

    // Open flight plan
    override fun onItemClick(position: Int) {
        if (Utils.isEngineRunning()) {
            val builder = AlertDialog.Builder(this.context)
            builder.setMessage(R.string.txtWarningFlightInProgressDialog)
                .setCancelable(false)
                .setPositiveButton(R.string.txtYes) { _, _ ->
                    loadFLightPlan(position)
                }
                .setNegativeButton(R.string.txtNo) { dialog, _ ->
                    dialog.dismiss()
                }
            val alert = builder.create()
            alert.show()
        } else {
            loadFLightPlan(position)
        }
    }

    // Moving/drag&drop items in RecycleView
    private var simpleCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT.or(ItemTouchHelper.RIGHT)) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val pos = viewHolder.absoluteAdapterPosition
            val fileName = State.planList[pos].id

            // Delete json
            FileUtils.deleteFile(fileName)
            // Delete trk
            FileUtils.deleteFile(fileName.replace(C.JSON_EXTENSION, C.TRK_EXTENSION, ignoreCase = true))

            State.planList.removeAt(pos)
            adapter.notifyItemRemoved(pos)
        }
    }

    private fun loadFLightPlan(i: Int) {
        val fpn = State.planList[i].id

        if (fpn != State.settings.planId) {
            FileUtils.loadState(State.planList[i].id)
            NavLogUtils.calcNavlog()
        }

        (activity as MainActivity).resetFlight()
        findNavController().navigate(R.id.action_global_settingsFragment)
    }
}