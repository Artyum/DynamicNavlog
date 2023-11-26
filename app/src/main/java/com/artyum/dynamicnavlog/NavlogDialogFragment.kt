package com.artyum.dynamicnavlog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
            val dest = Utils.clearString(bind.dialogDest.text.toString())
            val remarks = Utils.clearString(bind.dialogRemarks.text.toString())
            val tt = Utils.clearString(bind.dialogTt.text.toString())
            val declination = Utils.clearString(bind.dialogDeclination.text.toString())
            val mt = Utils.clearString(bind.dialogMt.text.toString())
            val dist = Utils.clearString(bind.dialogDist.text.toString().trim())
            val lat = Utils.clearString(bind.dialogLat.text.toString().trim())
            val lng = Utils.clearString(bind.dialogLng.text.toString().trim())

            if (dest != "" && mt != "" && dist != "") {
                val dMagneticTrack = Utils.getDoubleOrNull(mt)
                val dDist = Convert.fromUserUnitsDis(Utils.getDoubleOrNull(dist))
                var dTrueTrack = Utils.getDoubleOrNull(tt)
                val dDeclination = Utils.getDoubleOrNull(declination)
                val dLat = Utils.getDoubleOrNull(lat)
                val dLng = Utils.getDoubleOrNull(lng)
                var chk = true

                if (dMagneticTrack == null || dMagneticTrack < 0.0 || dMagneticTrack > 360.0) {
                    Toast.makeText(context, getString(R.string.txtInvalidCourse), Toast.LENGTH_SHORT).show()
                    chk = false
                }

                if (dDist == null || dDist <= 0.0) {
                    Toast.makeText(context, getString(R.string.txtInvalidDistance), Toast.LENGTH_SHORT).show()
                    chk = false
                }

                if (dTrueTrack == null && dDeclination != null && dMagneticTrack != null) dTrueTrack = GPSUtils.normalizeBearing(dMagneticTrack - dDeclination)

                var pos: LatLng? = null
                if (dLat != null && dLng != null) pos = LatLng(dLat, dLng)

                if (chk) {
                    State.navlogList[item].dest = dest.uppercase()
                    State.navlogList[item].tt = dTrueTrack
                    State.navlogList[item].d = dDeclination
                    State.navlogList[item].mt = dMagneticTrack!!
                    State.navlogList[item].dist = dDist!!
                    State.navlogList[item].remarks = remarks
                    State.navlogList[item].pos = pos
                    State.navlogList[item].active = bind.dialogCheckboxActive.isChecked

                    adapter?.notifyItemChanged(item)
                    NavLogUtils.calcNavlog(adapter)
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
            if (item < State.navlogList.size) {
                State.navlogList.removeAt(item)
                adapter?.notifyItemRemoved(item)
                NavLogUtils.calcNavlog(adapter)
                setFragmentResult("requestKey", bundleOf("action" to "refresh"))
                dismiss()
            }
        }

        // Fill values
        if (item < State.navlogList.size) {
            val prev = (NavLogUtils.getNavlogPrevItemId(item))

            // Dest
            bind.dialogDest.setText(State.navlogList[item].dest.uppercase())

            // True track
            bind.dialogTt.setText(Utils.formatDouble(State.navlogList[item].tt, 1))

            // Declination
            if (State.navlogList[item].d != null) {
                bind.dialogDeclination.setText(Utils.formatDouble(State.navlogList[item].d, 1))
            } else {
                if (item > 0 && prev >= 0 && State.navlogList[prev].d != null) {
                    val declination = State.navlogList[prev].d!!
                    bind.dialogDeclination.setText(Utils.formatDouble(declination, 1))
                    if (State.navlogList[item].mt != null && State.navlogList[item].tt == null) {
                        bind.dialogTt.setText(Utils.formatDouble(State.navlogList[item].mt!! - declination, 1))
                    }
                }
            }

            // Magnetic track
            if (State.navlogList[item].mt != null) bind.dialogMt.setText(Utils.formatDouble(State.navlogList[item].mt, 1))

            // Distance
            bind.dialogDist.setText(Utils.formatDouble(Convert.toUserUnitsDis(State.navlogList[item].dist), 1))
            bind.boxDistance.hint = getString(R.string.txtDistance) + " (" + Convert.getUnitsDis() + ")"

            // Latitude
            if (State.navlogList[item].pos != null) {
                bind.dialogLat.setText(Utils.formatDouble(State.navlogList[item].pos?.latitude, C.POS_PRECISION))
                bind.dialogLng.setText(Utils.formatDouble(State.navlogList[item].pos?.longitude, C.POS_PRECISION))
            } else {
                bind.dialogLat.setText("")
                bind.dialogLng.setText("")
            }

            // Remarks
            bind.dialogRemarks.setText(State.navlogList[item].remarks)

            // Active
            bind.dialogCheckboxActive.isChecked = State.navlogList[item].active

            // Current
            if (State.navlogList[item].current) {
                bind.dialogCheckboxActive.isEnabled = false
                bind.btnDialogRemove.visibility = View.GONE
            }

            // Hide options for "Current"
            if (item < NavLogUtils.getNavlogCurrentItemId()) {
                bind.dialogCheckboxActive.isEnabled = false
                if (Utils.isFlightInProgress()) {
                    bind.btnDialogSubmit.visibility = View.GONE
                    bind.btnDialogRemove.visibility = View.GONE
                }
            }
        } else dismiss()

        //Focus on Destination
        bind.dialogDest.requestFocus()

        // Open keyboard
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private fun calcTt() {
        cmt = false
        val mt = Utils.getDoubleOrNull(bind.dialogMt.text.toString())
        val d = Utils.getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (mt != null && d != null && mt in 0.0..360.0) {
            val tt = GPSUtils.normalizeBearing(mt - d)
            bind.dialogTt.setText(Utils.formatDouble(tt, 1))
        } else bind.dialogTt.setText("")
        cmt = true
    }

    private fun calcMt() {
        ctt = false
        val tt = Utils.getDoubleOrNull(bind.dialogTt.text.toString())
        val d = Utils.getDoubleOrNull(bind.dialogDeclination.text.toString())
        if (tt != null && d != null && tt in 0.0..360.0) {
            val mt = GPSUtils.normalizeBearing(tt + d)
            bind.dialogMt.setText(Utils.formatDouble(mt, 1))
        } else bind.dialogMt.setText("")
        ctt = true
    }

    private fun calcPos() {
        val tt = Utils.getDoubleOrNull(bind.dialogTt.text.toString())
        val dist = Convert.fromUserUnitsDis(Utils.getDoubleOrNull(bind.dialogDist.text.toString()))
        val prevPos = NavLogUtils.getPrevCoords(item)
        if (tt != null && dist != null && prevPos != null) {
            val newPos = GPSUtils.calcDestinationPos(from = prevPos, bearing = tt, distance = Convert.nm2m(dist))
            bind.dialogLat.setText(Utils.formatDouble(newPos.latitude, C.POS_PRECISION))
            bind.dialogLng.setText(Utils.formatDouble(newPos.longitude, C.POS_PRECISION))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (item < State.navlogList.size) {
            if (!NavLogUtils.isNavlogItemValid(item)) {
                State.navlogList.removeAt(item)
                adapter?.notifyItemRemoved(item)
            }
        }
        super.onDismiss(dialog)
    }
}