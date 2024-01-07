package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.R.layout
import com.artyum.dynamicnavlog.R.string
import com.artyum.dynamicnavlog.databinding.FragmentCalcWindBinding
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CalcWindFragment : Fragment(layout.fragment_calc_wind) {
    private var _binding: FragmentCalcWindBinding? = null
    private val bind get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcWindBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.calcWindLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            calculate(it)
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            allClear()
            Utils.generateWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
        }

        setupUI()
        Utils.generateWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
    }

    private fun allClear() {
        bind.edtCourse.setText("")
        bind.edtDistance.setText("")
        bind.edtWindDir.setText("")
        bind.edtWindSpd.setText("")
        bind.edtTas.setText("")
        bind.edtFph.setText("")
        bind.outWca.setText("")
        bind.outHdg.setText("")
        bind.outGs.setText("")
        bind.outFlightTime.setText("")
        bind.outHeadwind.setText("")
        bind.outCrosswind.setText("")
        bind.outFuel.setText("")
    }

    private fun calculate(view: View) {
        val dCourse = Utils.getDoubleOrNull(bind.edtCourse.text.toString())
        val dDist = Units.fromUserUnitsDis(Utils.getDoubleOrNull(bind.edtDistance.text.toString()))
        val dWindDir = Utils.getDoubleOrNull(bind.edtWindDir.text.toString())
        val dWindSpd = Units.fromUserUnitsSpd(Utils.getDoubleOrNull(bind.edtWindSpd.text.toString()))
        val dTas = Units.fromUserUnitsSpd(Utils.getDoubleOrNull(bind.edtTas.text.toString()))
        val dFph = Units.fromUserUnitsVol(Utils.getDoubleOrNull(bind.edtFph.text.toString()))

        if (dCourse != null && dWindDir != null && dWindSpd != null && dTas != null) {
            // Validate
            if (dCourse > 360.0 || dWindDir > 360.0) {
                Toast.makeText(view.context, getString(string.txtInvalidCourseAndWind), Toast.LENGTH_SHORT).show()
                return
            }
            if (dTas < dWindSpd) {
                Toast.makeText(view.context, getString(string.txtInvalidTAS), Toast.LENGTH_SHORT).show()
                return
            }

            // WCA / HDG / GS
            val (wca, hdg, gs, timeSec, fuel) = Utils.flightCalculator(course = dCourse, windDir = dWindDir, windSpd = dWindSpd, tas = dTas, dist = dDist, fph = dFph)

            bind.outWca.setText(Utils.formatDouble(wca))
            bind.outHdg.setText(Utils.formatDouble(hdg))
            bind.outGs.setText(Utils.formatDouble(Units.toUserUnitsSpd(gs)))
            bind.outFlightTime.setText(TimeUtils.formatSecondsToTime(timeSec))
            bind.outFuel.setText(Utils.formatDouble(Units.toUserUnitsVol(fuel)))

            // Headwind / Crosswind
            val angle = Units.deg2rad(dCourse - dWindDir + 360.0)
            val hw = dWindSpd * cos(angle)
            val cw = dWindSpd * sin(angle)

            if (hw >= 0) bind.labelHeadwind.hint = getString(string.txtHeadwind) + " (" + Units.getUnitsSpd() + ")"
            else bind.labelHeadwind.hint = getString(string.txtTailwind) + " (" + Units.getUnitsSpd() + ")"
            bind.outHeadwind.setText(Utils.formatDouble(Units.toUserUnitsSpd(abs(hw))))

            if (cw >= 0) bind.labelCrosswind.hint = getString(string.txtLeftxwind) + " (" + Units.getUnitsSpd() + ")"
            else bind.labelCrosswind.hint = getString(string.txtRightxwind) + " (" + Units.getUnitsSpd() + ")"
            bind.outCrosswind.setText(Utils.formatDouble(Units.toUserUnitsSpd(abs(cw))))

            // Image
            Utils.generateWindCircle(bind.imgView, resources, course = dCourse, windDir = dWindDir, hdg = hdg, speedRatio = gs / dTas)

        } else {
            Toast.makeText(view.context, getString(string.txtInvalidFlightParams), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        bind.boxTas.hint = getString(string.txtTAS) + " (" + Units.getUnitsSpd() + ")"
        bind.boxGs.hint = getString(string.txtGs) + " (" + Units.getUnitsSpd() + ")"
        bind.boxWindSpd.hint = getString(string.txtWindSpeed) + " (" + Units.getUnitsSpd() + ")"
        bind.boxDist.hint = getString(string.txtDistance) + " (" + Units.getUnitsDis() + ")"
        bind.boxFuel1.hint = getString(string.txtFuelPerHour) + " (" + Units.getUnitsVol() + ")"
        bind.boxFuel2.hint = getString(string.txtFuelRequired) + " (" + Units.getUnitsVol() + ")"
        bind.labelHeadwind.hint = getString(string.txtHeadwind) + " (" + Units.getUnitsSpd() + ")"
        bind.labelCrosswind.hint = getString(string.txtCrosswind) + " (" + Units.getUnitsSpd() + ")"
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
