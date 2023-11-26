package com.artyum.dynamicnavlog

import android.os.Bundle
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
        bind.airplaneLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Speed units
        bind.spinnerSpeedUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                spdUnits = position
                onChange()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }
        }

        // Tank units
        bind.spinnerVolUnits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
        val tas = Utils.getDoubleOrNull(bind.airplaneTas.text.toString())
        val tank = Utils.getDoubleOrNull(bind.airplaneTank.text.toString())
        val fph = Utils.getDoubleOrNull(bind.airplaneFph.text.toString())

        if (tas != null && tank != null && fph != null) {
            val time = tank / fph   // time in h
            val range = tas * time
            val units = when (spdUnits) {
                0 -> "nm"
                1 -> "miles"
                2 -> "km"
                else -> ""
            }

            bind.perfRange.text = Utils.formatDouble(range)
            bind.perfRangeUnits.text = units
            bind.perfFlightTime.text = TimeUtils.formatSecondsToTime((time * 3600.0).toLong())

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
        val type = Utils.clearString(bind.airplaneType.text.toString())
        val reg = Utils.clearString(bind.airplaneReg.text.toString())
        val rmk = Utils.clearString(bind.airplaneRmk.text.toString())
        val tas = Utils.getDoubleOrNull(bind.airplaneTas.text.toString())
        val tank = Utils.getDoubleOrNull(bind.airplaneTank.text.toString())
        val fph = Utils.getDoubleOrNull(bind.airplaneFph.text.toString())

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
            State.airplane.type = type
            State.airplane.reg = reg
            State.airplane.rmk = rmk
            State.airplane.tas = tas!!
            State.airplane.tank = tank!!
            State.airplane.fph = fph!!
            State.airplane.spdUnits = spdUnits
            State.airplane.volUnits = volUnits

            addAirplane()

            // Refresh airplane settings
            AirplaneUtils.getAirplaneByID(State.settings.airplaneId)

            NavLogUtils.calcNavlog()

            // Go back to the list of airplanes
            findNavController().popBackStack()

            //Toast.makeText(context, getString(R.string.txtAirplaneSaved), Toast.LENGTH_SHORT).show()
            //(bind.btnApply as MaterialButton).background.setTint(bind.btnApply.context.getColor(R.color.gray))
        }
    }

    private fun loadAirplane() {
        if (editAirplaneId.isNotEmpty()) {
            // Search in airplane list
            for (i in State.airplaneList.indices) {
                if (State.airplaneList[i].id == editAirplaneId) {
                    State.airplane = State.airplaneList[i].copy()
                    return
                }
            }
        } else {
            State.airplane = AirplaneData()
            State.airplane.id = Utils.generateStringId()
        }
    }

    private fun restoreSettings() {
        bind.airplaneType.setText(State.airplane.type)
        bind.airplaneReg.setText(State.airplane.reg)
        bind.airplaneRmk.setText(State.airplane.rmk)

        bind.spinnerSpeedUnits.setSelection(State.airplane.spdUnits)
        bind.spinnerVolUnits.setSelection(State.airplane.volUnits)

        val tas = State.airplane.tas
        val tank = State.airplane.tank
        val fph = State.airplane.fph

        if (tas > 0.0) bind.airplaneTas.setText(Utils.formatDouble(tas))
        if (tank > 0.0) bind.airplaneTank.setText(Utils.formatDouble(tank))
        if (fph > 0.0) bind.airplaneFph.setText(Utils.formatDouble(fph))
    }

    private fun addAirplane() {
        for (i in State.airplaneList.indices) {
            if (State.airplaneList[i].id == State.airplane.id) {
                State.airplaneList.removeAt(i)
                break
            }
        }
        State.airplaneList.add(State.airplane)
        State.airplaneList.sortBy { it.reg }
        FileUtils.saveAirplaneList()
    }
}

object AirplaneUtils {
    fun getAirplaneByID(id: String) {
        if (id == "") {
            resetAirplaneSettings()
            return
        }

        for (i in State.airplaneList.indices) {
            if (State.airplaneList[i].id == id) {
                // Convert airplane units to flight plan units
                val a = State.airplaneList[i].copy()

                // Convert to kt and litres
                when (a.spdUnits) {
                    C.SPD_MPH -> a.tas = Convert.mph2kt(a.tas)
                    C.SPD_KPH -> a.tas = Convert.kph2kt(a.tas)
                }
                when (a.volUnits) {
                    C.VOL_USGAL -> {
                        a.tank = Convert.usgal2l(a.tank)
                        a.fph = Convert.usgal2l(a.fph)
                    }

                    C.VOL_UKGAL -> {
                        a.tank = Convert.ukgal2l(a.tank)
                        a.fph = Convert.ukgal2l(a.fph)
                    }
                }

                State.airplane = a.copy()
                State.settings.airplaneId = id
                if (State.settings.fob < 0.0) State.settings.fob = 0.0
                if (State.settings.fob > a.tank) State.settings.fob = a.tank

                return
            }
        }
        resetAirplaneSettings()
    }

    fun resetAirplaneSettings() {
        State.settings.airplaneId = ""
        State.airplane = AirplaneData()
    }
}