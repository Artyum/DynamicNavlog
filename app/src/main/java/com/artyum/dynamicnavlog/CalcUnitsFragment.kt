package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentCalcUnitsBinding

class CalcUnitsFragment : Fragment(R.layout.fragment_calc_units) {
    private var _binding: FragmentCalcUnitsBinding? = null
    private val bind get() = _binding!!
    private val unitsTypeList = ArrayList<String>()
    private val unitsList = ArrayList<String>()
    private var unitsType: Int = 0
    private var unitsBase: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcUnitsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.unitsLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        val con = view.context

        unitsTypeList.clear()
        unitsTypeList.add("Distance")       // 0
        unitsTypeList.add("Speed")          // 1
        unitsTypeList.add("Temperature")    // 2
        unitsTypeList.add("Pressure")       // 3
        unitsTypeList.add("Weight & Mass")  // 4
        unitsTypeList.add("Volume")         // 5

        bind.spinnerUnitsType.adapter = ArrayAdapter(con, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, unitsTypeList)
        bind.spinnerUnitsType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                //val sel = parent?.getItemAtPosition(position).toString()
                unitsType = position
                unitsList.clear()

                when (position) {
                    0 -> {
                        unitsList.add("nm")  // 0
                        unitsList.add("sm")  // 1
                        unitsList.add("km")  // 2
                        unitsList.add("m")   // 3
                        unitsList.add("ft")  // 4
                    }

                    1 -> {
                        unitsList.add("kt")      // 0
                        unitsList.add("mph")     // 1
                        unitsList.add("kph")     // 2
                        unitsList.add("m/s")     // 3
                        unitsList.add("ft/min")  // 4
                    }

                    2 -> {
                        unitsList.add("\u2109")  // 0 F
                        unitsList.add("\u2103")  // 1 C
                        unitsList.add("\u212A")  // 2 K
                    }

                    3 -> {
                        unitsList.add("inhg")  // 0
                        unitsList.add("mmhg")  // 1
                        unitsList.add("hpa")   // 2
                        unitsList.add("atm")   // 3
                    }

                    4 -> {
                        unitsList.add("kg")  // 0
                        unitsList.add("lb")  // 1
                        unitsList.add("oz")  // 2
                    }

                    5 -> {
                        unitsList.add("Liters")        // 0
                        unitsList.add("US gallons")    // 1
                        unitsList.add("UK gallons")  // 2
                    }
                }
                bind.spinnerUnits.adapter = ArrayAdapter(con, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, unitsList)
                bind.spinnerUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        unitsBase = position
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        return
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }
        }

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()

            bind.outputFrame.text = ""
            var value = Utils.getDoubleOrNull(bind.edtInputVal.text.toString())

            if (value == null) {
                Toast.makeText(view.context, getString(R.string.txtEnterValue), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var msg = ""
            val p = 4
            val pad = 7

            // Distance
            if (unitsType == 0) {
                if (unitsBase == 1) value = Units.sm2nm(value)   //sm -> nm
                if (unitsBase == 2) value = Units.km2nm(value)   //km -> nm
                if (unitsBase == 3) value = Units.m2nm(value)    //m -> nm
                if (unitsBase == 4) value = Units.ft2nm(value)   //ft -> nm

                msg += Utils.formatDouble(value, p) + " nm".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.nm2sm(value), p) + " sm".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.nm2km(value), p) + " km".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.nm2m(value), p) + " m".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.nm2ft(value), p) + " ft".padEnd(pad, ' ')
            }

            // Speed
            if (unitsType == 1) {
                if (unitsBase == 1) value = Units.mph2kt(value)   //mph -> kt
                if (unitsBase == 2) value = Units.kph2kt(value)   //kph -> kt
                if (unitsBase == 3) value = Units.mps2kt(value)   //m/s -> kt
                if (unitsBase == 4) value = Units.fpm2kt(value)   //ft/min -> kt

                msg += Utils.formatDouble(value, p) + " kt".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kt2mph(value), p) + " mph".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kt2kph(value), p) + " kph".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kt2mps(value), p) + " m/s".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kt2fpm(value), p) + " ft/min".padEnd(pad, ' ')
            }

            // Temperature
            if (unitsType == 2) {
                if (unitsBase == 0) value = Units.f2c(value)   //F -> C
                if (unitsBase == 2) value = Units.k2c(value)   //K -> C

                msg += Utils.formatDouble(Units.c2f(value), p) + " \u2109".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(value, p) + " \u2103".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.c2k(value), p) + " \u212A".padEnd(pad, ' ')
            }

            // Pressure
            if (unitsType == 3) {
                if (unitsBase == 1) value = Units.mmhg2inhg(value)   //mmhg -> inhg
                if (unitsBase == 2) value = Units.hpa2inhg(value)    //hpa -> inhg
                if (unitsBase == 3) value = Units.atm2inhg(value)    //atm -> inhg

                msg += Utils.formatDouble(value, p) + " inhg".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.inhg2mmhg(value), p) + " mmhg".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.inhg2hpa(value), p) + " hpa".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.inhg2atm(value), 3) + " atm".padEnd(pad, ' ')
            }

            // Weight & Mass
            if (unitsType == 4) {
                if (unitsBase == 1) value = Units.lb2kg(value)    //lb -> kg
                if (unitsBase == 2) value = Units.oz2kg(value)    //oz -> kg

                msg += Utils.formatDouble(value, p) + " kg".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kg2lb(value), p) + " lb".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.kg2oz(value), p) + " oz".padEnd(pad, ' ')
            }

            // Volume
            if (unitsType == 5) {
                if (unitsBase == 1) value = Units.usgal2l(value)   //US gal -> l
                if (unitsBase == 2) value = Units.ukgal2l(value)   //UK gal -> l

                msg += Utils.formatDouble(value, p) + " l".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.l2usgal(value), p) + " US gal".padEnd(pad, ' ') + "\n"
                msg += Utils.formatDouble(Units.l2ukgal(value), p) + " UK gal".padEnd(pad, ' ')
            }

            bind.outputFrame.text = msg
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            bind.edtInputVal.setText("")
            bind.outputFrame.text = ""
        }
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}