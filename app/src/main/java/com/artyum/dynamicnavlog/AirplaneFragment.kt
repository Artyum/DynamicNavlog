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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.artyum.dynamicnavlog.databinding.FragmentAirplaneBinding

class AirplaneFragment : Fragment() {
    private var _binding: FragmentAirplaneBinding? = null
    private val bind get() = _binding!!
    private lateinit var vm: GlobalViewModel

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
        vm = ViewModelProvider(requireActivity())[GlobalViewModel::class.java]
        bind.airplaneLayout.keepScreenOn = vm.options.value!!.keepScreenOn
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
            vm.airplane.value!!.type = type
            vm.airplane.value!!.reg = reg
            vm.airplane.value!!.rmk = rmk
            vm.airplane.value!!.tas = tas!!
            vm.airplane.value!!.tank = tank!!
            vm.airplane.value!!.fph = fph!!
            vm.airplane.value!!.spdUnits = spdUnits
            vm.airplane.value!!.volUnits = volUnits
            addAirplane()

            // Refresh airplane settings
            getAirplaneByID(vm.settings.value!!.airplaneId)
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
                    vm.airplane.value = airplaneList[i].copy()
                    return
                }
            }
        } else {
            vm.airplane.value = Airplane()
            vm.airplane.value!!.id = generateStringId()
        }
    }

    private fun restoreSettings() {
        val airplane = vm.airplane.value!!
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
        for (i in airplaneList.indices) {
            if (airplaneList[i].id == vm.airplane.value!!.id) {
                airplaneList.removeAt(i)
                break
            }
        }
        airplaneList.add(vm.airplane.value!!)
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