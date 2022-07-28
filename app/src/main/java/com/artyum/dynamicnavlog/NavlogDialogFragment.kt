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
    private val TAG = "NavlogDialogFragment"
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
                calcCoords()
            }
        }

        bind.dialogDeclination.doAfterTextChanged {
            if (cmt) {
                calcMt()
                calcCoords()
            }
        }

        bind.dialogMt.doAfterTextChanged {
            if (ctt) {
                calcTt()
                calcCoords()
            }
        }

        bind.dialogDist.doAfterTextChanged {
            calcCoords()
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
                val dDist = fromUnitsDis(getDoubleOrNull(dist))
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

                if (dTrueTrack == null && dDeclination != null && dMagneticTrack != null) dTrueTrack = dMagneticTrack - dDeclination

                var coords: LatLng? = null
                if (dLat != null && dLng != null) coords = LatLng(dLat, dLng)

                if (chk) {
                    navlogList[item].dest = dest.uppercase()
                    navlogList[item].trueTrack = dTrueTrack
                    navlogList[item].declination = dDeclination
                    navlogList[item].magneticTrack = dMagneticTrack!!
                    navlogList[item].distance = dDist!!
                    navlogList[item].remarks = remarks
                    navlogList[item].coords = coords
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

        // Hide coordinates row when GPS support is disabled
        if (!settings.gpsAssist) bind.dialogCoordsRow.visibility = View.GONE

        // Fill values
        if (item < navlogList.size) {
            val prev = (getNavlogPrevItemId(item))

            // Dest
            bind.dialogDest.setText(navlogList[item].dest.uppercase())

            // True track
            bind.dialogTt.setText(formatDouble(navlogList[item].trueTrack, 1))

            // Declination
            if (navlogList[item].declination != null) {
                bind.dialogDeclination.setText(formatDouble(navlogList[item].declination, 1))
            } else {
                if (item > 0 && prev >= 0 && navlogList[prev].declination != null) {
                    val declination = navlogList[prev].declination!!
                    bind.dialogDeclination.setText(formatDouble(declination, 1))
                    if (navlogList[item].magneticTrack != null && navlogList[item].trueTrack == null) {
                        bind.dialogTt.setText(formatDouble(navlogList[item].magneticTrack!! - declination, 1))
                    }
                }
            }

            // Magnetic track
            if (navlogList[item].magneticTrack != null) bind.dialogMt.setText(formatDouble(navlogList[item].magneticTrack, 1))

            // Distance
            bind.dialogDist.setText(formatDouble(toUnitsDis(navlogList[item].distance), 1))
            bind.boxDistance.hint = getString(R.string.txtDistance) + " (" + getUnitsDis() + ")"

            // Latitude
            if (navlogList[item].coords != null) {
                bind.dialogLat.setText(formatDouble(navlogList[item].coords?.latitude, C.COORDS_PRECISION))
                bind.dialogLng.setText(formatDouble(navlogList[item].coords?.longitude, C.COORDS_PRECISION))
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
        Log.d(TAG, "calcTt")
        cmt = false
        val mt = getDoubleOrNull(bind.dialogMt.text.toString())
        val decl = getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (mt != null && decl != null && mt in 0.0..360.0) {
            val tt = normalizeBearing(mt - decl)
            bind.dialogTt.setText(formatDouble(tt, 1))
        } else bind.dialogTt.setText("")
        cmt = true
    }

    private fun calcMt() {
        Log.d(TAG, "calcMt")
        ctt = false
        val tt = getDoubleOrNull(bind.dialogTt.text.toString())
        val decl = getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (tt != null && decl != null && tt in 0.0..360.0) {
            val mt = normalizeBearing(tt + decl)
            bind.dialogMt.setText(formatDouble(mt, 1))
        } else bind.dialogMt.setText("")
        ctt = true
    }

    private fun calcCoords() {
        val tt = getDoubleOrNull(bind.dialogTt.text.toString())
        val dist = getDoubleOrNull(bind.dialogDist.text.toString())
        val prevCoords = getPrevCoords(item)
        if (tt != null && dist != null && prevCoords != null) {
            val newCoords = calcDestinationCoords(from = prevCoords, bearing = tt, distance = nm2m(dist))
            //val d = getDeclination(newCoords)
            //bind.dialogDeclination.setText(formatDouble(d, 1))
            bind.dialogLat.setText(formatDouble(newCoords.latitude, C.COORDS_PRECISION))
            bind.dialogLng.setText(formatDouble(newCoords.longitude, C.COORDS_PRECISION))
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