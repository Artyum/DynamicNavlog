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
import java.util.*

class NavlogFragment : Fragment(R.layout.fragment_navlog), NavlogAdapter.OnItemClickInterface, NavlogAdapter.OnItemLongClickInterface {
    private var _binding: FragmentNavlogBinding? = null
    private val bind get() = _binding!!
    private val adapter = NavlogAdapter(navlogList, this, this)
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
        bind.navlogLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Current & Incrementally switch
        bind.btnDisplayToggle.setOnClickListener {
            if (settings.tfDisplayToggle == C.TF_DISPLAY_CUR) {
                settings.tfDisplayToggle = C.TF_DISPLAY_REM
                Toast.makeText(view.context, R.string.txtDisplayIncremental, Toast.LENGTH_SHORT).show()
            } else {
                settings.tfDisplayToggle = C.TF_DISPLAY_CUR
                Toast.makeText(view.context, R.string.txtDisplayCurrent, Toast.LENGTH_SHORT).show()
            }
            calcNavlog(adapter)
        }

        val recyclerView = bind.navlogRecycler
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.context)

        refreshBottomBar()
        if (isNavlogReady()) recyclerView.scrollToPosition(getNavlogCurrentItemId())

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
            val i = getNavlogCurrentItemId()

            if (startPosition > i && endPosition > i && startPosition != endPosition) {
                Collections.swap(navlogList, startPosition, endPosition)
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
                        calcNavlog(adapter)
                        saveState()
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
        val p1 = if (totals.dist < C.DIST_THRESHOLD) 1 else 0
        val p2 = if (totals.fuel < C.VOL_THRESHOLD) 1 else 0
        val strDist = formatDouble(toUserUnitsDis(totals.dist), p1) + " " + getUnitsDis()
        val strFuel = formatDouble(toUserUnitsVol(totals.fuel), p2) + " " + getUnitsVol()
        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = formatSecondsToTime(totals.time)
        bind.txtTotalFuel.text = strFuel

        if (navlogList.size == 0) bind.btnDisplayToggle.visibility = View.GONE else bind.btnDisplayToggle.visibility = View.VISIBLE
    }

    private suspend fun updateNavlogPageThread() {
        while (true) {
            if (refreshDisplay) adapter.notifyDataSetChanged()
            delay(100)
        }
    }
}
