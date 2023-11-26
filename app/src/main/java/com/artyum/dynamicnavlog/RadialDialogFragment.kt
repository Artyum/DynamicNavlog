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
            val angle = Utils.getDoubleOrNull(bind.dialogRadial.text.toString())
            val dist = Utils.getDoubleOrNull(bind.dialogDistance.text.toString())
            val lat1 = Utils.getDoubleOrNull(bind.dialogLat1.text.toString())
            val lng1 = Utils.getDoubleOrNull(bind.dialogLng1.text.toString())
            val lat2 = Utils.getDoubleOrNull(bind.dialogLat2.text.toString())
            val lng2 = Utils.getDoubleOrNull(bind.dialogLng2.text.toString())
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
                    State.radialList[item].angle = angle!!
                    State.radialList[item].dist = Convert.fromUserUnitsDis(dist!!)!!
                    State.radialList[item].pos1 = LatLng(lat1, lng1)
                    State.radialList[item].pos2 = LatLng(lat2, lng2)
                } else {
                    val radial = RadialData(angle = angle!!, dist = dist!!, pos1 = LatLng(lat1, lng1), pos2 = LatLng(lat2, lng2))
                    State.radialList.add(radial)
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
            if (item != null && State.radialList.size > 0 && item >= 0 && item < State.radialList.size) {
                State.radialList.removeAt(item)
                FileUtils.saveState()
                setFragmentResult("requestKey", bundleOf("action" to "refresh"))
            }
            dismiss()
        }

        // Display form
        bind.labelDistance.hint = getString(R.string.txtDistance) + " (" + Convert.getUnitsDis() + ")"
        if (item != null) {
            // Edit radial
            bind.dialogRadial.setText(Utils.formatDouble(State.radialList[item].angle, 0))
            bind.dialogDistance.setText(Utils.formatDouble(Convert.toUserUnitsDis(State.radialList[item].dist), 2))
            bind.dialogLat1.setText(Utils.formatDouble(State.radialList[item].pos1.latitude, C.POS_PRECISION))
            bind.dialogLng1.setText(Utils.formatDouble(State.radialList[item].pos1.longitude, C.POS_PRECISION))
            bind.dialogLat2.setText(Utils.formatDouble(State.radialList[item].pos2.latitude, C.POS_PRECISION))
            bind.dialogLng2.setText(Utils.formatDouble(State.radialList[item].pos2.longitude, C.POS_PRECISION))
        } else if (pos1 != null && pos2 != null) {
            // New radial
            // Calculate Radial and distance
            val radial = GPSUtils.calcBearing(pos1, pos2)
            val dist = Convert.m2nm(GPSUtils.calcDistance(pos1, pos2))

            bind.dialogRadial.setText(Utils.formatDouble(radial))
            bind.dialogDistance.setText(Utils.formatDouble(Convert.toUserUnitsDis(dist), 2))
            bind.dialogLat1.setText(Utils.formatDouble(pos1.latitude, C.POS_PRECISION))
            bind.dialogLng1.setText(Utils.formatDouble(pos1.longitude, C.POS_PRECISION))
            bind.dialogLat2.setText(Utils.formatDouble(pos2.latitude, C.POS_PRECISION))
            bind.dialogLng2.setText(Utils.formatDouble(pos2.longitude, C.POS_PRECISION))

            bind.btnDialogRemove.visibility = View.GONE
        }
    }

    private fun calculateEndPoint() {
        var angle = Utils.getDoubleOrNull(bind.dialogRadial.text.toString())
        var dist = Utils.getDoubleOrNull(bind.dialogDistance.text.toString())
        val lat1 = Utils.getDoubleOrNull(bind.dialogLat1.text.toString())
        val lng1 = Utils.getDoubleOrNull(bind.dialogLng1.text.toString())
        if (angle != null && dist != null && lat1 != null && lng1 != null) {
            dist = Convert.nm2m(Convert.fromUserUnitsDis(dist)!!)
            angle = GPSUtils.normalizeBearing(angle - GPSUtils.getDeclination(LatLng(lat1, lng1)))
            val pos2 = GPSUtils.calcDestinationPos(LatLng(lat1, lng1), angle, dist)
            bind.dialogLat2.setText(Utils.formatDouble(pos2.latitude, C.POS_PRECISION))
            bind.dialogLng2.setText(Utils.formatDouble(pos2.longitude, C.POS_PRECISION))
        }
    }
}
