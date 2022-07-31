package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.artyum.dynamicnavlog.databinding.FragmentRadialDialogBinding
import com.google.android.gms.maps.model.LatLng

class RadialDialogFragment(private val pos1: LatLng? = null, private val pos2: LatLng? = null, private val item: Int? = null) : DialogFragment() {
    private val TAG = "RadialDialogFragment"
    private var _binding: FragmentRadialDialogBinding? = null
    private val bind get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRadialDialogBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bind.dialogRadial.doAfterTextChanged { calculateEndPoint() }
        bind.dialogDistance.doAfterTextChanged { calculateEndPoint() }
        bind.dialogLat1.doAfterTextChanged { calculateEndPoint() }
        bind.dialogLng1.doAfterTextChanged { calculateEndPoint() }

        // Button Apply
        bind.btnDialogSubmit.setOnClickListener {
            var angle = getDoubleOrNull(bind.dialogRadial.text.toString())
            val dist = getDoubleOrNull(bind.dialogDistance.text.toString())
            val lat1 = getDoubleOrNull(bind.dialogLat1.text.toString())
            val lng1 = getDoubleOrNull(bind.dialogLng1.text.toString())
            val lat2 = getDoubleOrNull(bind.dialogLat2.text.toString())
            val lng2 = getDoubleOrNull(bind.dialogLng2.text.toString())
            var chk = true

            if (angle == null || angle < 0.0 || angle > 360.0) {
                Toast.makeText(context, getString(R.string.txtInvalidRadial), Toast.LENGTH_SHORT).show()
                chk = false
            }

            if (dist == null || dist <= 0) {
                Toast.makeText(context, getString(R.string.txtInvalidRadialDist), Toast.LENGTH_SHORT).show()
                chk = false
            }

            if (chk && lat1 != null && lng1 != null && lat2 != null && lng2 != null) {
                if (item != null) {
                    radialList[item].angle = angle!!
                    radialList[item].dist = fromUserUnitsDis(dist!!)!!
                    radialList[item].pos1 = LatLng(lat1, lng1)
                    radialList[item].pos2 = LatLng(lat2, lng2)
                } else {
                    val radial = Radial(angle = angle!!, dist = dist!!, pos1 = LatLng(lat1, lng1), pos2 = LatLng(lat2, lng2))
                    radialList.add(radial)
                }

                setFragmentResult("requestKey", bundleOf("action" to "refresh"))
                dismiss()
            }
        }

        // Button Cancel
        bind.btnDialogCancel.setOnClickListener {
            dismiss()
        }

        // Button delete
        bind.btnDialogRemove.setOnClickListener {
            if (item != null && radialList.size > 0 && item >= 0 && item < radialList.size) {
                radialList.removeAt(item)
                saveState()
                setFragmentResult("requestKey", bundleOf("action" to "refresh"))
            }
            dismiss()
        }

        // Display form
        bind.labelDistance.hint = getString(R.string.txtDistance) + " (" + getUnitsDis() + ")"
        if (item != null) {
            // Edit radial
            bind.dialogRadial.setText(formatDouble(radialList[item].angle, 0))
            bind.dialogDistance.setText(formatDouble(toUserUnitsDis(radialList[item].dist), 2))
            bind.dialogLat1.setText(formatDouble(radialList[item].pos1.latitude, C.COORDS_PRECISION))
            bind.dialogLng1.setText(formatDouble(radialList[item].pos1.longitude, C.COORDS_PRECISION))
            bind.dialogLat2.setText(formatDouble(radialList[item].pos2.latitude, C.COORDS_PRECISION))
            bind.dialogLng2.setText(formatDouble(radialList[item].pos2.longitude, C.COORDS_PRECISION))
        } else if (pos1 != null && pos2 != null) {
            // New radial
            // Calculate Radial and distance
            val radial = calcBearing(pos1, pos2)
            val dist = m2nm(calcDistance(pos1, pos2))

            bind.dialogRadial.setText(formatDouble(radial))
            bind.dialogDistance.setText(formatDouble(toUserUnitsDis(dist), 2))
            bind.dialogLat1.setText(formatDouble(pos1.latitude, C.COORDS_PRECISION))
            bind.dialogLng1.setText(formatDouble(pos1.longitude, C.COORDS_PRECISION))
            bind.dialogLat2.setText(formatDouble(pos2.latitude, C.COORDS_PRECISION))
            bind.dialogLng2.setText(formatDouble(pos2.longitude, C.COORDS_PRECISION))

            bind.btnDialogRemove.visibility = View.GONE
        }
    }

    private fun calculateEndPoint() {
        var angle = getDoubleOrNull(bind.dialogRadial.text.toString())
        var dist = getDoubleOrNull(bind.dialogDistance.text.toString())
        val lat1 = getDoubleOrNull(bind.dialogLat1.text.toString())
        val lng1 = getDoubleOrNull(bind.dialogLng1.text.toString())
        if (angle != null && dist != null && lat1 != null && lng1 != null) {
            dist = nm2m(fromUserUnitsDis(dist)!!)
            angle = normalizeBearing(angle - getDeclination(LatLng(lat1, lng1)))
            val pos2 = calcDestinationPos(LatLng(lat1, lng1), angle, dist)
            bind.dialogLat2.setText(formatDouble(pos2.latitude, C.COORDS_PRECISION))
            bind.dialogLng2.setText(formatDouble(pos2.longitude, C.COORDS_PRECISION))
        }
    }
}

fun recalculateRadial(i: Int) {
    if (i < 0 || i > radialList.size - 1) return
    radialList[i].angle = normalizeBearing(calcBearing(radialList[i].pos1, radialList[i].pos2) + getDeclination(radialList[i].pos1))
    radialList[i].dist = m2nm(calcDistance(radialList[i].pos1, radialList[i].pos2))
}