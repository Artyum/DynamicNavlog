package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentNavlogBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

class NavlogFragment : Fragment(R.layout.fragment_navlog), NavlogAdapter.OnItemClickInterface, NavlogAdapter.OnItemLongClickInterface {
    private var _binding: FragmentNavlogBinding? = null
    private val bind get() = _binding!!
    private val adapter = NavlogAdapter(State.navlogList, this, this)
    private var isNavlogChanged: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavlogBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.navlogLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Current & Incrementally switch
        bind.btnDisplayToggle.setOnClickListener {
            if (State.settings.tfDisplayToggle == C.TF_DISPLAY_CUR) {
                State.settings.tfDisplayToggle = C.TF_DISPLAY_REM
                Toast.makeText(view.context, R.string.txtDisplayIncremental, Toast.LENGTH_SHORT).show()
            } else {
                State.settings.tfDisplayToggle = C.TF_DISPLAY_CUR
                Toast.makeText(view.context, R.string.txtDisplayCurrent, Toast.LENGTH_SHORT).show()
            }
            NavLogUtils.calcNavlog(adapter)
        }

        val recyclerView = bind.navlogRecycler
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.context)

        refreshBottomBar()
        if (NavLogUtils.isNavlogReady()) recyclerView.scrollToPosition(NavLogUtils.getNavlogCurrentItemId())

        // Helper on end drag item
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        lifecycleScope.launch { updateNavlogPageThread() }
    }

    // Moving/drag&drop items in RecycleView
    private var simpleCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP.or(ItemTouchHelper.DOWN), 0) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val startPosition = viewHolder.absoluteAdapterPosition
            val endPosition = target.absoluteAdapterPosition
            val i = NavLogUtils.getNavlogCurrentItemId()

            if (startPosition > i && endPosition > i && startPosition != endPosition) {
                Collections.swap(State.navlogList, startPosition, endPosition)
                recyclerView.adapter?.notifyItemMoved(startPosition, endPosition)
                isNavlogChanged = true
                return true
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        // OnDrop in RecycleView
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            when (actionState) {
                ItemTouchHelper.ACTION_STATE_IDLE -> {
                    if (isNavlogChanged) {
                        NavLogUtils.calcNavlog(adapter)
                        FileUtils.saveState()
                        isNavlogChanged = false
                    }
                }
            }
        }
    }

    // Click on RecycleView Item
    override fun onItemClick(position: Int) {
        val dialog = NavlogDialogFragment(position, adapter)
        dialog.show(parentFragmentManager, "NavlogDialogFragment")
    }

    override fun onItemLongClick(position: Int) {}

    private fun refreshBottomBar() {
        val p1 = if (State.totals.dist < C.DIST_THRESHOLD) 1 else 0
        val p2 = if (State.totals.fuel < C.VOL_THRESHOLD) 1 else 0
        val strDist = Utils.formatDouble(Units.toUserUnitsDis(State.totals.dist), p1) + " " + Units.getUnitsDis()
        val strFuel = Utils.formatDouble(Units.toUserUnitsVol(State.totals.fuel), p2) + " " + Units.getUnitsVol()
        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = TimeUtils.formatSecondsToTime(State.totals.time)
        bind.txtTotalFuel.text = strFuel

        if (State.navlogList.size == 0) bind.btnDisplayToggle.visibility = View.GONE else bind.btnDisplayToggle.visibility = View.VISIBLE
    }

    private suspend fun updateNavlogPageThread() {
        while (_binding != null) {
            if (Vars.globalRefresh) {
                Vars.globalRefresh = false
                adapter.notifyDataSetChanged()
            }
            delay(1000)
        }
    }
}
