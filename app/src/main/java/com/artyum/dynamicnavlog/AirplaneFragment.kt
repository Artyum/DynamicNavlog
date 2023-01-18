package com.artyum.dynamicnavlog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.artyum.dynamicnavlog.databinding.FragmentAirplaneBinding

class AirplaneFragment : Fragment() {
    private var _binding: FragmentAirplaneBinding? = null
    private val bind get() = _binding!!

    private var spdUnits = 0
    private var volUnits = 0
    private var editAirplaneId: String = ""

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
        bind.airplaneLayout.keepScreenOn = G.vm.options.value!!.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Speed units
        bind.spinnerSpeedUnits.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    spdUnits = position
                    onChange()
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
                    onChange()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        setFragmentResultListener("requestKey") { _, bundle ->
            editAirplaneId = bundle.getString("airplaneId").toString()
            loadAirplane()
            restoreSettings()
        }

        bind.airplaneType.doAfterTextChanged { onChange() }
        bind.airplaneReg.doAfterTextChanged { onChange() }
        bind.airplaneRmk.doAfterTextChanged { onChange() }
        bind.airplaneTas.doAfterTextChanged { onChange() }
        bind.airplaneTank.doAfterTextChanged { onChange() }
        bind.airplaneFph.doAfterTextChanged { onChange() }

        // Save airplane
        bind.btnApply.setOnClickListener { saveAirplane() }

        setupUI(view)
    }

    private fun onChange() {
        calcPerformance()
        //(bind.btnApply as MaterialButton).background.setTint(bind.btnApply.context.getColor(R.color.red))
    }

    private fun calcPerformance() {
        val tas = getDoubleOrNull(bind.airplaneTas.text.toString())
        val tank = getDoubleOrNull(bind.airplaneTank.text.toString())
        val fph = getDoubleOrNull(bind.airplaneFph.text.toString())

        if (tas != null && tank != null && fph != null) {
            val time = tank / fph   // time in h
            val range = tas * time
            val units = when (spdUnits) {
                0 -> "nm"
                1 -> "miles"
                2 -> "km"
                else -> ""
            }

            bind.perfRange.text = formatDouble(range)
            bind.perfRangeUnits.text = units
            bind.perfFlightTime.text = formatSecondsToTime((time * 3600.0).toLong())

        } else {
            bind.perfRange.text = "-"
            bind.perfRangeUnits.text = ""
            bind.perfFlightTime.text = "-"
        }
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
        unitsTypeList.add("Knots")    // 0
        unitsTypeList.add("Mph")      // 1
        unitsTypeList.add("Kph")      // 2
        bind.spinnerSpeedUnits.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsTypeList)

        // Volume
        val volumeTypeList = ArrayList<String>()
        volumeTypeList.add("US Gal")     // 0
        volumeTypeList.add("Imp. Gal")   // 1
        volumeTypeList.add("Liters")     // 2
        bind.spinnerVolUnits.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, volumeTypeList)
    }

    private fun saveAirplane() {
        hideErrorBox()
        val type = clearString(bind.airplaneType.text.toString())
        val reg = clearString(bind.airplaneReg.text.toString())
        val rmk = clearString(bind.airplaneRmk.text.toString())
        val tas = getDoubleOrNull(bind.airplaneTas.text.toString())
        val tank = getDoubleOrNull(bind.airplaneTank.text.toString())
        val fph = getDoubleOrNull(bind.airplaneFph.text.toString())

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
            G.vm.airplane.value!!.type = type
            G.vm.airplane.value!!.reg = reg
            G.vm.airplane.value!!.rmk = rmk
            G.vm.airplane.value!!.tas = tas!!
            G.vm.airplane.value!!.tank = tank!!
            G.vm.airplane.value!!.fph = fph!!
            G.vm.airplane.value!!.spdUnits = spdUnits
            G.vm.airplane.value!!.volUnits = volUnits
            addAirplane()

            // Refresh airplane settings
            getAirplaneByID(G.vm.settings.value!!.airplaneId)
            calcNavlog()

            // Go back to the list of airplanes
            findNavController().popBackStack()

            //Toast.makeText(context, getString(R.string.txtAirplaneSaved), Toast.LENGTH_SHORT).show()
            //(bind.btnApply as MaterialButton).background.setTint(bind.btnApply.context.getColor(R.color.gray))
        }
    }

    private fun loadAirplane() {
        if (editAirplaneId.isNotEmpty()) {
            // Search in airplane list
            for (i in airplaneList.indices) {
                if (airplaneList[i].id == editAirplaneId) {
                    G.vm.airplane.value = airplaneList[i].copy()
                    return
                }
            }
        } else {
            G.vm.airplane.value = Airplane()
            G.vm.airplane.value!!.id = generateStringId()
        }
    }

    private fun restoreSettings() {
        bind.airplaneType.setText(G.vm.airplane.value!!.type)
        bind.airplaneReg.setText(G.vm.airplane.value!!.reg)
        bind.airplaneRmk.setText(G.vm.airplane.value!!.rmk)

        bind.spinnerSpeedUnits.setSelection(G.vm.airplane.value!!.spdUnits)
        bind.spinnerVolUnits.setSelection(G.vm.airplane.value!!.volUnits)

        val tas = G.vm.airplane.value!!.tas
        val tank = G.vm.airplane.value!!.tank
        val fph = G.vm.airplane.value!!.fph

        if (tas > 0.0) bind.airplaneTas.setText(formatDouble(tas))
        if (tank > 0.0) bind.airplaneTank.setText(formatDouble(tank))
        if (fph > 0.0) bind.airplaneFph.setText(formatDouble(fph))
    }

    private fun addAirplane() {
        for (i in airplaneList.indices) {
            if (airplaneList[i].id == G.vm.airplane.value!!.id) {
                airplaneList.removeAt(i)
                break
            }
        }
        airplaneList.add(G.vm.airplane.value!!)
        airplaneList.sortBy { it.reg }
        saveAirplaneList()
    }
}

fun getAirplaneByID(id: String) {
    if (id == "") {
        resetAirplaneSettings()
        return
    }

    for (i in airplaneList.indices) {
        if (airplaneList[i].id == id) {
            // Convert airplane units to flight plan units
            val a = airplaneList[i].copy()

            // Convert to kt and litres
            when (a.spdUnits) {
                C.SPD_MPH -> a.tas = mph2kt(a.tas)
                C.SPD_KPH -> a.tas = kph2kt(a.tas)
            }
            when (a.volUnits) {
                C.VOL_USGAL -> {
                    a.tank = usgal2l(a.tank)
                    a.fph = usgal2l(a.fph)
                }
                C.VOL_UKGAL -> {
                    a.tank = ukgal2l(a.tank)
                    a.fph = ukgal2l(a.fph)
                }
            }

            G.vm.airplane.value = a
            G.vm.settings.value!!.airplaneId = id
            return
        }
    }
    resetAirplaneSettings()
}

fun resetAirplaneSettings() {
    G.vm.settings.value!!.airplaneId = ""
    G.vm.airplane.value = Airplane()
}