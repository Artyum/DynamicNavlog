package com.artyum.dynamicnavlog

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.artyum.dynamicnavlog.databinding.FragmentNavlogDialogBinding
import com.google.android.gms.maps.model.LatLng

class NavlogDialogFragment(private val item: Int, private val adapter: NavlogAdapter? = null) : DialogFragment() {
    private var _binding: FragmentNavlogDialogBinding? = null
    private val bind get() = _binding!!

    private var ctt = true
    private var cmt = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavlogDialogBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bind.dialogTt.doAfterTextChanged {
            if (cmt) {
                calcMt()
                calcPos()
            }
        }

        bind.dialogDeclination.doAfterTextChanged {
            if (cmt) {
                calcMt()
                calcPos()
            }
        }

        bind.dialogMt.doAfterTextChanged {
            if (ctt) {
                calcTt()
                calcPos()
            }
        }

        bind.dialogDist.doAfterTextChanged {
            calcPos()
        }

        // Button Apply
        bind.btnDialogSubmit.setOnClickListener {
            val dest = clearString(bind.dialogDest.text.toString())
            val remarks = clearString(bind.dialogRemarks.text.toString())
            val tt = clearString(bind.dialogTt.text.toString())
            val declination = clearString(bind.dialogDeclination.text.toString())
            val mt = clearString(bind.dialogMt.text.toString())
            val dist = clearString(bind.dialogDist.text.toString().trim())
            val lat = clearString(bind.dialogLat.text.toString().trim())
            val lng = clearString(bind.dialogLng.text.toString().trim())

            if (dest != "" && mt != "" && dist != "") {
                val dMagneticTrack = getDoubleOrNull(mt)
                val dDist = fromUserUnitsDis(getDoubleOrNull(dist))
                var dTrueTrack = getDoubleOrNull(tt)
                val dDeclination = getDoubleOrNull(declination)
                val dLat = getDoubleOrNull(lat)
                val dLng = getDoubleOrNull(lng)
                var chk = true

                if (dMagneticTrack == null || dMagneticTrack < 0.0 || dMagneticTrack > 360.0) {
                    Toast.makeText(context, getString(R.string.txtInvalidCourse), Toast.LENGTH_SHORT).show()
                    chk = false
                }

                if (dDist == null || dDist <= 0.0) {
                    Toast.makeText(context, getString(R.string.txtInvalidDistance), Toast.LENGTH_SHORT).show()
                    chk = false
                }

                if (dTrueTrack == null && dDeclination != null && dMagneticTrack != null) dTrueTrack = normalizeBearing(dMagneticTrack - dDeclination)

                var pos: LatLng? = null
                if (dLat != null && dLng != null) pos = LatLng(dLat, dLng)

                if (chk) {
                    navlogList[item].dest = dest.uppercase()
                    navlogList[item].tt = dTrueTrack
                    navlogList[item].d = dDeclination
                    navlogList[item].mt = dMagneticTrack!!
                    navlogList[item].dist = dDist!!
                    navlogList[item].remarks = remarks
                    navlogList[item].pos = pos
                    navlogList[item].active = bind.dialogCheckboxActive.isChecked

                    adapter?.notifyItemChanged(item)
                    calcNavlog(adapter)
                    setFragmentResult("requestKey", bundleOf("action" to "refresh"))
                    dismiss()
                }
            } else Toast.makeText(context, getString(R.string.txtInvalidDialogCheck), Toast.LENGTH_SHORT).show()
        }

        // Button Cancel
        bind.btnDialogCancel.setOnClickListener {
            dismiss()
        }

        // Button Del
        bind.btnDialogRemove.setOnClickListener {
            if (item < navlogList.size) {
                navlogList.removeAt(item)
                adapter?.notifyItemRemoved(item)
                calcNavlog(adapter)
                setFragmentResult("requestKey", bundleOf("action" to "refresh"))
                dismiss()
            }
        }

        // Fill values
        if (item < navlogList.size) {
            val prev = (getNavlogPrevItemId(item))

            // Dest
            bind.dialogDest.setText(navlogList[item].dest.uppercase())

            // True track
            bind.dialogTt.setText(formatDouble(navlogList[item].tt, 1))

            // Declination
            if (navlogList[item].d != null) {
                bind.dialogDeclination.setText(formatDouble(navlogList[item].d, 1))
            } else {
                if (item > 0 && prev >= 0 && navlogList[prev].d != null) {
                    val declination = navlogList[prev].d!!
                    bind.dialogDeclination.setText(formatDouble(declination, 1))
                    if (navlogList[item].mt != null && navlogList[item].tt == null) {
                        bind.dialogTt.setText(formatDouble(navlogList[item].mt!! - declination, 1))
                    }
                }
            }

            // Magnetic track
            if (navlogList[item].mt != null) bind.dialogMt.setText(formatDouble(navlogList[item].mt, 1))

            // Distance
            bind.dialogDist.setText(formatDouble(toUserUnitsDis(navlogList[item].dist), 1))
            bind.boxDistance.hint = getString(R.string.txtDistance) + " (" + getUnitsDis() + ")"

            // Latitude
            if (navlogList[item].pos != null) {
                bind.dialogLat.setText(formatDouble(navlogList[item].pos?.latitude, C.POS_PRECISION))
                bind.dialogLng.setText(formatDouble(navlogList[item].pos?.longitude, C.POS_PRECISION))
            } else {
                bind.dialogLat.setText("")
                bind.dialogLng.setText("")
            }

            // Remarks
            bind.dialogRemarks.setText(navlogList[item].remarks)

            // Active
            bind.dialogCheckboxActive.isChecked = navlogList[item].active

            // Current
            if (navlogList[item].current) {
                bind.dialogCheckboxActive.isEnabled = false
                bind.btnDialogRemove.visibility = View.GONE
            }

            // Hide options for "Current"
            if (item < getNavlogCurrentItemId()) {
                bind.dialogCheckboxActive.isEnabled = false
                if (isFlightInProgress()) {
                    bind.btnDialogSubmit.visibility = View.GONE
                    bind.btnDialogRemove.visibility = View.GONE
                }
            }
        } else dismiss()
    }

    private fun calcTt() {
        cmt = false
        val mt = getDoubleOrNull(bind.dialogMt.text.toString())
        val d = getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (mt != null && d != null && mt in 0.0..360.0) {
            val tt = normalizeBearing(mt - d)
            bind.dialogTt.setText(formatDouble(tt, 1))
        } else bind.dialogTt.setText("")
        cmt = true
    }

    private fun calcMt() {
        ctt = false
        val tt = getDoubleOrNull(bind.dialogTt.text.toString())
        val d = getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (tt != null && d != null && tt in 0.0..360.0) {
            val mt = normalizeBearing(tt + d)
            bind.dialogMt.setText(formatDouble(mt, 1))
        } else bind.dialogMt.setText("")
        ctt = true
    }

    private fun calcPos() {
        val tt = getDoubleOrNull(bind.dialogTt.text.toString())
        val dist = fromUserUnitsDis(getDoubleOrNull(bind.dialogDist.text.toString()))
        val prevPos = getPrevCoords(item)
        if (tt != null && dist != null && prevPos != null) {
            val newPos = calcDestinationPos(from = prevPos, bearing = tt, distance = nm2m(dist))
            bind.dialogLat.setText(formatDouble(newPos.latitude, C.POS_PRECISION))
            bind.dialogLng.setText(formatDouble(newPos.longitude, C.POS_PRECISION))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (item < navlogList.size) {
            if (!isNavlogItemValid(item)) {
                navlogList.removeAt(item)
                adapter?.notifyItemRemoved(item)
            }
        }
        super.onDismiss(dialog)
    }
}