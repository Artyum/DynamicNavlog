package com.artyum.dynamicnavlog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentOptionsBinding

class OptionsFragment : Fragment(R.layout.fragment_options) {
    private val TAG = "PreferencesFragment"
    private var _binding: FragmentOptionsBinding? = null
    private val bind get() = _binding!!

    private var save = false
    private var refresh = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.optionsLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).hideButtons()

        // Spinner - Speed units
        bind.spinnerUnitsSpd.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.spdUnits) {
                        options.spdUnits = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Spinner - Distance units
        bind.spinnerUnitsDist.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.distUnits) {
                        options.distUnits = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Spinner - Volume units
        bind.spinnerUnitsVol.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.volUnits) {
                        options.volUnits = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Spinner - Screen orientation
        bind.spinnerScreenOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.screenOrientation) {
                        options.screenOrientation = position
                        save = true
                        (activity as MainActivity).setScreenOrientation()
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            options.timeInUTC = isChecked
            save = true
            saveForm()
        }

        // Switch - Keep screen ON
        bind.settingsScreenOn.setOnCheckedChangeListener { _, isChecked ->
            options.keepScreenOn = isChecked
            save = true
            saveForm()
        }

        setupUI(view)
        restoreOptions()
    }

    override fun onStop() {
        super.onStop()
        saveForm()
    }

    private fun saveForm() {
        if (refresh) return
        if (!save) return
        save = false
        saveOptions()
    }

    private fun setupUI(view: View) {
        // Spinner - Speed units
        val unitsSpdList = ArrayList<String>()
        unitsSpdList.add("Knots (kt)")            // 0
        unitsSpdList.add("Miles/h (mph)")         // 1
        unitsSpdList.add("Kilometers/h (kph)")    // 2
        bind.spinnerUnitsSpd.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsSpdList)

        // Spinner - Speed units
        val unitsDistList = ArrayList<String>()
        unitsDistList.add("Nautical miles (nm)")  // 0
        unitsDistList.add("Statute miles (sm)")   // 1
        unitsDistList.add("Kilometers (km)")      // 2
        bind.spinnerUnitsDist.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsDistList)

        // Spinner - Volume units
        val unitsFuelList = ArrayList<String>()
        unitsFuelList.add("US Gal (gal)")         // 0
        unitsFuelList.add("UK Gal (gal)")         // 1
        unitsFuelList.add("Liters (l)")           // 2
        bind.spinnerUnitsVol.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsFuelList)

        // Spinner - Screen orientation
        val screenOrientationList = ArrayList<String>()
        screenOrientationList.add("Portrait")     // 0
        screenOrientationList.add("Landscape")    // 1
        screenOrientationList.add("Auto")         // 2
        bind.spinnerScreenOrientation.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, screenOrientationList)
    }

    private fun restoreOptions() {
        // Options
        bind.spinnerScreenOrientation.setSelection(options.screenOrientation)
        bind.settingTimeUTC.isChecked = options.timeInUTC
        bind.settingsScreenOn.isChecked = options.keepScreenOn

        // Units
        bind.spinnerUnitsSpd.setSelection(options.spdUnits)
        bind.spinnerUnitsDist.setSelection(options.distUnits)
        bind.spinnerUnitsVol.setSelection(options.volUnits)
    }
}

fun resetOptions() {
    options.spdUnits = 0
    options.distUnits = 0
    options.volUnits = 0
    options.screenOrientation = C.SCREEN_SENSOR
    options.timeInUTC = false
    options.keepScreenOn = false
}