package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentCalcFuelBinding
import kotlin.math.roundToLong

class CalcFuelFragment : Fragment(R.layout.fragment_calc_fuel) {
    private var _binding: FragmentCalcFuelBinding? = null
    private val bind get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcFuelBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.fuelLayout.keepScreenOn = G.vm.options.value!!.keepScreenOn
        (activity as MainActivity).displayButtons()

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            fuelCalculate()
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            bind.edtFuelStart.setText("")
            bind.edtFuelEnd.setText("")
            bind.edtFuelTime.setText("")
            bind.edtFuelFph.setText("")
        }
    }

    private fun fuelCalculate() {
        val fuelStart = getDoubleOrNull(bind.edtFuelStart.text.toString())
        var fuelEnd = getDoubleOrNull(bind.edtFuelEnd.text.toString())
        val fuelFPH = getDoubleOrNull(bind.edtFuelFph.text.toString())
        val fuelTime: Double?

        val txtTime = bind.edtFuelTime.text.toString()
        fuelTime = strTime2Sec(txtTime)
        bind.edtFuelTime.setText(formatSecondsToTime(fuelTime?.roundToLong(), true))

        if (fuelEnd == null) {
            bind.edtFuelEnd.setText("0")
            fuelEnd = 0.0
        }

        // Fph
        if (fuelStart != null && fuelTime != null && fuelFPH == null) {
            val tmp = (fuelStart - fuelEnd) / fuelTime * 3600.0
            bind.edtFuelFph.setText(formatDouble(tmp, 1))
        }

        // Time
        if (fuelStart != null && fuelTime == null && fuelFPH != null) {
            val tmp = (fuelStart - fuelEnd) / fuelFPH * 3600.0
            bind.edtFuelTime.setText(formatSecondsToTime(tmp.roundToLong(), true))
        }

        // Fuel after flight
        if (fuelStart != null && fuelTime != null && fuelFPH != null) {
            val tmp = fuelStart - (fuelTime / 3600.0 * fuelFPH)
            bind.edtFuelEnd.setText(formatDouble(tmp, 1))
        }

        // Fuel for start
        if (fuelStart == null && fuelTime != null && fuelFPH != null) {
            val tmp = fuelEnd + (fuelTime / 3600.0 * fuelFPH)
            bind.edtFuelStart.setText(formatDouble(tmp, 1))
        }
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}