package com.artyum.dynamicnavlog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.artyum.dynamicnavlog.databinding.FragmentAirplaneBinding

class AirplaneFragment : Fragment() {
    private var _binding: FragmentAirplaneBinding? = null
    private val bind get() = _binding!!

    private var airplane = Airplane()
    private var spdUnits = 0
    private var volUnits = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAirplaneBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.airplaneLayout.keepScreenOn = settings.keepScreenOn

        // Speed units
        bind.spinnerSpeedUnits.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    spdUnits = position
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Tank units
        bind.spinnerVolUnits.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    volUnits = position
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        bind.btnApply.setOnClickListener {
            hideErrorBox()
            val type = clearString(bind.airplaneType.text.toString())
            val reg = clearString(bind.airplaneReg.text.toString())
            val rmk = clearString(bind.airplaneRmk.text.toString())
            val tas = bind.airplaneTas.text.toString().toDoubleOrNull()
            val tank = bind.airplaneTank.text.toString().toDoubleOrNull()
            val fph = bind.airplaneFph.text.toString().toDoubleOrNull()

            // Validation
            var ok = true
            if (type.isEmpty()) {
                bind.errType.visibility = View.VISIBLE
                ok = false
            }

            if (reg.isEmpty()) {
                bind.errReg.visibility = View.VISIBLE
                ok = false
            }

            if (tas == null || tas <= 0.0) {
                bind.errTas.visibility = View.VISIBLE
                ok = false
            }

            if (tank == null || tank <= 0.0) {
                bind.errTank.visibility = View.VISIBLE
                ok = false
            }

            if (fph == null || fph <= 0.0) {
                bind.errFph.visibility = View.VISIBLE
                ok = false
            }

            if (ok) {
                airplane.type = type
                airplane.reg = reg
                airplane.rmk = rmk
                airplane.tas = tas!!
                airplane.tank = tank!!
                airplane.fph = fph!!
                airplane.spdUnits = spdUnits
                airplane.volUnits = volUnits
                addAirplane()
                findNavController().popBackStack()
            }
        }

        setupUI(view)
        loadAirplane()
        restoreSettings()
    }

    private fun hideErrorBox() {
        bind.errType.visibility = View.GONE
        bind.errReg.visibility = View.GONE
        bind.errTas.visibility = View.GONE
        bind.errTank.visibility = View.GONE
        bind.errFph.visibility = View.GONE
    }

    private fun setupUI(view: View) {
        // Units
        val unitsTypeList = ArrayList<String>()
        unitsTypeList.add("kt")      // 0
        unitsTypeList.add("mph")     // 1
        unitsTypeList.add("kph")     // 2
        bind.spinnerSpeedUnits.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsTypeList)
        bind.spinnerSpeedUnits.setSelection(spdUnits)

        // Volume
        val volumeTypeList = ArrayList<String>()
        volumeTypeList.add("us. gal")    // 0
        volumeTypeList.add("imp. gal")   // 1
        volumeTypeList.add("liters")     // 2
        bind.spinnerVolUnits.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, volumeTypeList)
        bind.spinnerVolUnits.setSelection(volUnits)
    }

    private fun loadAirplane() {
        if (!editAirplaneID.isNullOrEmpty()) {
            for (i in airplaneList.indices) {
                if (airplaneList[i].id == editAirplaneID) {
                    airplane = airplaneList[i]
                    return
                }
            }
        }
        airplane.id = generateStringId()
    }

    private fun restoreSettings() {
        bind.airplaneType.setText(airplane.type)
        bind.airplaneReg.setText(airplane.reg)
        bind.airplaneRmk.setText(airplane.rmk)

        bind.spinnerSpeedUnits.setSelection(airplane.spdUnits)
        bind.spinnerVolUnits.setSelection(airplane.volUnits)

        val tas = airplane.tas
        val tank = airplane.tank
        val fph = airplane.fph

        if (tas > 0.0) bind.airplaneTas.setText(formatDouble(tas))
        if (tank > 0.0) bind.airplaneTank.setText(formatDouble(tank))
        if (fph > 0.0) bind.airplaneFph.setText(formatDouble(fph))
    }

    private fun addAirplane() {
        for (i in airplaneList.size - 1 downTo 0) {
            if (airplaneList[i].id == airplane.id) airplaneList.removeAt(i)
        }
        airplaneList.add(airplane)
        airplaneList.sortBy { it.reg }
        saveAirplaneList()
    }
}

fun getAirplaneByID(id: String): Airplane? {
    if (id == "") return null
    for (i in airplaneList.indices) {
        if (airplaneList[i].id == id) {
            Log.d("AirplaneFragment", "getAirplaneByID")
            // Convert airplane units to flight plan units

            val airplane = airplaneList[i].copy()

            // Convert all to kt and litres
            if (airplane.spdUnits == C.SPD_MPH) airplane.tas = mph2kt(airplane.tas)
            if (airplane.spdUnits == C.SPD_KPH) airplane.tas = kph2kt(airplane.tas)
            if (airplane.volUnits == C.VOL_USGAL) {
                airplane.tank = usgal2l(airplane.tank)
                airplane.fph = usgal2l(airplane.fph)
            }
            if (airplane.volUnits == C.VOL_UKGAL) {
                airplane.tank = ukgal2l(airplane.tank)
                airplane.fph = ukgal2l(airplane.fph)
            }

            //Convert from kt and litres to plan units
            if (settings.spdUnits == C.SPD_MPH) airplane.tas = kt2mph(airplane.tas)
            if (settings.spdUnits == C.SPD_KPH) airplane.tas = kt2kph(airplane.tas)
            if (settings.volUnits == C.VOL_USGAL) {
                airplane.tank = l2usgal(airplane.tank)
                airplane.fph = l2usgal(airplane.fph)
            }
            if (settings.volUnits == C.VOL_UKGAL) {
                airplane.tank = l2ukgal(airplane.tank)
                airplane.fph = l2ukgal(airplane.fph)
            }

            return airplane
        }
    }
    return null
}

fun getAirplaneListPosition(id: String): Int {
    if (id == "") return 0
    for (i in airplaneList.indices) {
        if (airplaneList[i].id == id) return i + 1
    }
    return 0
}
