package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artyum.dynamicnavlog.databinding.FragmentNavlogBinding
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.*

class NavlogFragment : Fragment(R.layout.fragment_navlog), NavlogAdapter.OnItemClickInterface, NavlogAdapter.OnItemLongClickInterface {
    private var _binding: FragmentNavlogBinding? = null
    private val bind get() = _binding!!
    private val adapter = NavlogAdapter(navlogList, this, this)
    private var isNavlogCahnged: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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

        // Insert Button
        bind.btnNavlogInsert.setOnClickListener {
            clearNavlogInvalidItems(adapter)

            if (settings.gpsAssist && settings.takeoffCoords == null) {
                Toast.makeText(view.context, R.string.txtNoTakeoffPoint, Toast.LENGTH_LONG).show()
            } else {
                if (!isAppPurchased && navlogList.size >= C.FREE_WPT_NUMBER_LIMIT) {
                    Toast.makeText(view.context, R.string.txtTooManyItems, Toast.LENGTH_LONG).show()
                } else {
                    val newItem = NavlogItem(dest = "", magneticTrack = null, distance = null)
                    navlogList.add(newItem)
                    adapter.notifyItemInserted(navlogList.lastIndex)

                    val recyclerView = bind.navlogRecycler
                    recyclerView.scrollToPosition(navlogList.lastIndex)
                    onItemClick(navlogList.lastIndex)
                }
            }
        }

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
        refreshPageItems()

        if (isNavlogReady()) {
            recyclerView.scrollToPosition(getNavlogCurrentItemId())
            //optimizeLayout()
        }

        // Helper on end drag item
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    // Listener for data exchange from Dialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("requestKey") { _, _ ->
            //val result = bundle.getString("action")
            saveState()
            refreshPageItems()
        }
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

    private fun refreshPageItems() {
        val t = formatDouble(totals.dist) + " " + getDistUnitsLong()
        bind.txtTotalDist.text = t
        bind.txtTotalTime.text = formatSecondsToTime(totals.time)
        bind.txtTotalFuel.text = formatDouble(totals.fuel)

        if (navlogList.size == 0) bind.btnDisplayToggle.visibility = View.GONE else bind.btnDisplayToggle.visibility = View.VISIBLE
    }

//    inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
//        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
//            override fun onGlobalLayout() {
//                if (measuredWidth > 0 && measuredHeight > 0) {
//                    viewTreeObserver.removeOnGlobalLayoutListener(this)
//                    f()
//                }
//            }
//        })
//    }

    //        private fun optimizeLayout() {
//        var maxWidth = 100
//        val recyclerView = navlogRecycler
//
//        //recyclerView.afterMeasured {
//        recyclerView.doOnLayout {
//            // Get width
//            for (i in 0 until recyclerView.childCount) {
//                val v = recyclerView.layoutManager?.findViewByPosition(i)
//                val tv = v?.findViewById<TextView>(R.id.txtNavlogDest)
//                if (tv != null) {
//                    tv.measure(0, 0)
//                    if (tv.measuredWidth > maxWidth) maxWidth = tv.measuredWidth
//                    println("Item " + i.toString() + ": " + tv.measuredWidth)
//                }
//            }
//            // Set width
//            for (i in 0 until recyclerView.childCount) {
//                val v = recyclerView.layoutManager?.findViewByPosition(i)
//                val tv = v?.findViewById<TextView>(R.id.txtNavlogDest)
//                if (tv != null) {
//                    tv.width = maxWidth
//                    //tv.post { tv.width = maxWidth }
//                    println("Set item $i")
//                }
//            }
//            //txtNavlogDestHeader.doOnLayout { txtNavlogDestHeader.width = maxWidth }
//            txtNavlogDestHeader.width = maxWidth
//            //txtNavlogDestHeader.post { txtNavlogDestHeader.width = maxWidth }
//        }
//    }

//    private fun getMaxWidth() {
//        var maxWidth = 100
//        for (i in navlogList.indices) {
//            tempTv.text = navlogList[i].dest
//            tempTv.measure(0, 0)
//            if (tempTv.measuredWidth > maxWidth) maxWidth = tempTv.measuredWidth
//        }
//        maxDestWidth = maxWidth + 50
//        //println(maxDestWidth)
//    }
}
