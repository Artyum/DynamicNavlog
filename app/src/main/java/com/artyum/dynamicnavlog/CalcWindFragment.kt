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
        bind.calcWindLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).displayButtons()

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            calculate(it)
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            allClear()
            generateWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
        }

        setupUI()
        generateWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
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
        val dCourse = getDoubleOrNull(bind.edtCourse.text.toString())
        val dDist = fromUserUnitsDis(getDoubleOrNull(bind.edtDistance.text.toString()))
        val dWindDir = getDoubleOrNull(bind.edtWindDir.text.toString())
        val dWindSpd = fromUserUnitsSpd(getDoubleOrNull(bind.edtWindSpd.text.toString()))
        val dTas = fromUserUnitsSpd(getDoubleOrNull(bind.edtTas.text.toString()))
        val dFph = fromUserUnitsVol(getDoubleOrNull(bind.edtFph.text.toString()))

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
            val (wca, hdg, gs, timeSec, fuel) = flightCalculator(course = dCourse, windDir = dWindDir, windSpd = dWindSpd, tas = dTas, dist = dDist, fph = dFph)

            bind.outWca.setText(formatDouble(wca))
            bind.outHdg.setText(formatDouble(hdg))
            bind.outGs.setText(formatDouble(toUserUnitsSpd(gs)))
            bind.outFlightTime.setText(formatSecondsToTime(timeSec))
            bind.outFuel.setText(formatDouble(toUserUnitsVol(fuel)))

            // Headwind / Crosswind
            val angle = deg2rad(dCourse - dWindDir + 360.0)
            val hw = dWindSpd * cos(angle)
            val cw = dWindSpd * sin(angle)

            if (hw >= 0) bind.labelHeadwind.hint = getString(string.txtHeadwind) + " (" + getUnitsSpd() + ")"
            else bind.labelHeadwind.hint = getString(string.txtTailwind) + " (" + getUnitsSpd() + ")"
            bind.outHeadwind.setText(formatDouble(toUserUnitsSpd(abs(hw))))

            if (cw >= 0) bind.labelCrosswind.hint = getString(string.txtLeftxwind) + " (" + getUnitsSpd() + ")"
            else bind.labelCrosswind.hint = getString(string.txtRightxwind) + " (" + getUnitsSpd() + ")"
            bind.outCrosswind.setText(formatDouble(toUserUnitsSpd(abs(cw))))

            // Image
            generateWindCircle(bind.imgView, resources, course = dCourse, windDir = dWindDir, hdg = hdg, speedRatio = gs / dTas)

        } else {
            Toast.makeText(view.context, getString(string.txtInvalidFlightParams), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        bind.boxTas.hint = getString(string.txtTAS) + " (" + getUnitsSpd() + ")"
        bind.boxGs.hint = getString(string.txtGs) + " (" + getUnitsSpd() + ")"
        bind.boxWindSpd.hint = getString(string.txtWindSpeed) + " (" + getUnitsSpd() + ")"
        bind.boxDist.hint = getString(string.txtDistance) + " (" + getUnitsDis() + ")"
        bind.boxFuel1.hint = getString(string.txtFuelPerHour) + " (" + getUnitsVol() + ")"
        bind.boxFuel2.hint = getString(string.txtFuelRequired) + " (" + getUnitsVol() + ")"
        bind.labelHeadwind.hint = getString(string.txtHeadwind) + " (" + getUnitsSpd() + ")"
        bind.labelCrosswind.hint = getString(string.txtCrosswind) + " (" + getUnitsSpd() + ")"
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
