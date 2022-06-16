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
import kotlin.math.cos
import kotlin.math.sin

class CalcWindFragment : Fragment(layout.fragment_calc_wind) {
    private var _binding: FragmentCalcWindBinding? = null
    private val bind get() = _binding!!

    val state = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentCalcWindBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.calcWindLayout.keepScreenOn = settings.keepScreenOn

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            calculate(it)
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            allClear()
            paintWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
        }

        restoreState()
        paintWindCircle(bind.imgView, resources, course = 0.0, windDir = 180.0, hdg = 0.0, speedRatio = 1.0)
    }

    private fun saveState() {
        state.clear()
        state.add(bind.edtCourse.text.toString())
        state.add(bind.edtDistance.text.toString())
        state.add(bind.edtWindDir.text.toString())
        state.add(bind.edtWindSpd.text.toString())
        state.add(bind.edtTas.text.toString())
        state.add(bind.edtFph.text.toString())
    }

    private fun restoreState() {
        if (state.size > 0) {
            bind.edtCourse.setText(state[0])
            bind.edtDistance.setText(state[1])
            if (state[2] != "") bind.edtWindDir.setText(state[2]) else bind.edtWindDir.setText(formatDouble(settings.windDir))
            if (state[3] != "") bind.edtWindSpd.setText(state[3]) else bind.edtWindSpd.setText(formatDouble(settings.windSpd))
            if (state[3] != "") bind.edtTas.setText(state[4]) else bind.edtTas.setText(formatDouble(settings.tas))
            if (state[4] != "") bind.edtFph.setText(state[5]) else bind.edtFph.setText(formatDouble(settings.fph))
        } else {
            bind.edtWindDir.setText(formatDouble(settings.windDir))
            bind.edtWindSpd.setText(formatDouble(settings.windSpd))
            bind.edtTas.setText(formatDouble(settings.tas))
            bind.edtFph.setText(formatDouble(settings.fph))
        }
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

        saveState()
    }

    private fun calculate(view: View) {
        saveState()

        val dCourse = getDoubleOrNull(bind.edtCourse.text.toString())
        val dDist = getDoubleOrNull(bind.edtDistance.text.toString())
        val dWindDir = getDoubleOrNull(bind.edtWindDir.text.toString())
        val dWindSpd = getDoubleOrNull(bind.edtWindSpd.text.toString())
        val dTas = getDoubleOrNull(bind.edtTas.text.toString())
        val dFph = getDoubleOrNull(bind.edtFph.text.toString())

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
            bind.outGs.setText(formatDouble(gs))
            bind.outFlightTime.setText(formatSecondsToTime(timeSec))
            bind.outFuel.setText(formatDouble(fuel))

            // Headwind / Crosswind
            val angle = deg2rad(dCourse - dWindDir + 360.0)
            val hw = dWindSpd * cos(angle)
            val cw = dWindSpd * sin(angle)
            if (hw >= 0) {
                bind.labelHeadwind.hint = getString(string.txtHeadwind)
                bind.outHeadwind.setText(formatDouble(hw))
            } else {
                bind.labelHeadwind.hint = getString(string.txtTailwind)
                bind.outHeadwind.setText(formatDouble(-hw))
            }
            if (cw >= 0) {
                bind.labelCrosswind.hint = getString(string.txtLeftxwind)
                bind.outCrosswind.setText(formatDouble(cw))
            } else {
                bind.labelCrosswind.hint = getString(string.txtRightxwind)
                bind.outCrosswind.setText(formatDouble(-cw))
            }

            // Image
            paintWindCircle(bind.imgView, resources, course = dCourse, windDir = dWindDir, hdg = hdg, speedRatio = gs / dTas)

        } else {
            Toast.makeText(view.context, getString(string.txtInvalidFlightParams), Toast.LENGTH_SHORT).show()
        }
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
