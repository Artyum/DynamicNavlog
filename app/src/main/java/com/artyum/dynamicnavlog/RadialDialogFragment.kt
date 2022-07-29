package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.artyum.dynamicnavlog.databinding.FragmentRadialDialogBinding
import com.google.android.gms.maps.model.LatLng

class RadialDialogFragment(private val pos: LatLng, private val item: Int? = null) : DialogFragment() {
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
        // Button Apply
        bind.btnDialogSubmit.setOnClickListener {
            val angle = bind.dialogRadial.text.toString().toDoubleOrNull()
            val dist = bind.dialogDistance.text.toString().toDoubleOrNull()
            val lat = bind.dialogLat.text.toString().toDoubleOrNull()
            val lng = bind.dialogLng.text.toString().toDoubleOrNull()
            var chk = true

            if (angle == null || angle < 0.0 || angle > 360.0) {
                Toast.makeText(context, getString(R.string.txtInvalidRadial), Toast.LENGTH_SHORT).show()
                chk = false
            }

            if (dist == null || dist <= 0) {
                Toast.makeText(context, getString(R.string.txtInvalidRadialDist), Toast.LENGTH_SHORT).show()
                chk = false
            }

            if (chk && lat != null && lng != null) {
                if (item != null) {
                    radialList[item].angle = angle!!
                    radialList[item].dist = fromUserUnitsDis(dist!!)!!
                    radialList[item].pos = LatLng(lat, lng)
                } else {
                    val radial = Radial(angle!!, dist!!, LatLng(lat, lng))
                    radialList.add(radial)
                }

                refreshDisplay = true
                saveState()
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
                refreshDisplay = true
                saveState()
            }
            dismiss()
        }

        // Display form
        bind.labelDistance.hint = getString(R.string.txtDistance) + " (" + getUnitsDis() + ")"
        if (item != null) {
            // Edit radial
            bind.dialogRadial.setText(formatDouble(radialList[item].angle, 0))
            bind.dialogDistance.setText(formatDouble(toUserUnitsDis(radialList[item].dist), 2))
            bind.dialogLat.setText(formatDouble(radialList[item].pos.latitude, C.COORDS_PRECISION))
            bind.dialogLng.setText(formatDouble(radialList[item].pos.longitude, C.COORDS_PRECISION))
        } else {
            // New radial
            bind.dialogLat.setText(formatDouble(pos.latitude, C.COORDS_PRECISION))
            bind.dialogLng.setText(formatDouble(pos.longitude, C.COORDS_PRECISION))

            bind.btnDialogRemove.visibility = View.GONE
        }
    }
}
