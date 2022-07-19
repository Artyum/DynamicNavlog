package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentNavlogBinding
import java.util.*

class NavlogFragment : Fragment(R.layout.fragment_navlog), NavlogAdapter.OnItemClickInterface, NavlogAdapter.OnItemLongClickInterface {
    private var _binding: FragmentNavlogBinding? = null
    private val bind get() = _binding!!
    private val adapter = NavlogAdapter(navlogList, this, this)
    private var isNavlogCahnged: Boolean = false

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
        bind.navlogLayout.keepScreenOn = settings.keepScreenOn

        // Current & Incrementally switch
        bind.btnDisplayToggle.setOnClickListener {
            if (settings.tfDisplayToggle == C.TF_DISPLAY_CUR) {
                settings.tfDisplayToggle = C.TF_DISPLAY_REM
                Toast.makeText(view.context, R.string.txtDisplayRemaining, Toast.LENGTH_SHORT).show()
            } else {
                settings.tfDisplayToggle = C.TF_DISPLAY_CUR
                Toast.makeText(view.context, R.string.txtDisplayCurrent, Toast.LENGTH_SHORT).show()
            }
            calcNavlog(adapter)
        }

        val recyclerView = bind.navlogRecycler
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this.context)

        //calcNavlog(adapter)
        refreshBottomBar()

        if (isNavlogReady()) {
            recyclerView.scrollToPosition(getNavlogCurrentItemId())
            //optimizeLayout()
        }

        // Helper on end drag item
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
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
                isNavlogCahnged = true
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
                    if (isNavlogCahnged) {
                        calcNavlog(adapter)
                        saveState()
                        isNavlogCahnged = false
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
        val strDist = formatDouble(totals.dist) + " " + getUnitsDist()
        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = formatSecondsToTime(totals.time)
        val strFuel = formatDouble(totals.fuel) + " " + getUnitsVolume()
        bind.txtTotalFuel.text = strFuel

        if (navlogList.size == 0) bind.btnDisplayToggle.visibility = View.GONE else bind.btnDisplayToggle.visibility = View.VISIBLE
    }
}
