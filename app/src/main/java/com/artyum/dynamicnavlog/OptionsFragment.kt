package com.artyum.dynamicnavlog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentOptionsBinding
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

class OptionsFragment : Fragment(R.layout.fragment_options) {
    private var _binding: FragmentOptionsBinding? = null
    private val bind get() = _binding!!
    private var save = false
    private var restore = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Do not save fragment sate to prevent restoring previous data on entering the fragment
        //super.onSaveInstanceState(outState)
        Log.d("OptionsFragment", "onSaveInstanceState")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.optionsLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        setupUI()
        restoreOptions()

        // Switch - GPS assist
        bind.settingGpsAssist.setOnCheckedChangeListener { _, isChecked ->
            State.options.gpsAssist = isChecked
            if (State.options.gpsAssist) {
                LocationManager.locationSubscribe()
            } else {
                runBlocking { Vars.gpsMutex.withLock { Vars.gpsData.isValid = false } }
                LocationManager.locationUnsubscribe()
            }
            save = true
            saveForm()
        }

        // Switch - Auto-detect waypoint
        bind.settingAutoNext.setOnCheckedChangeListener { _, isChecked ->
            State.options.autoNext = isChecked
            save = true
            saveForm()
        }

        // Switch - Auto-next radius
        bind.spinnerNextRadius.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != State.options.nextRadiusIndex) {
                        State.options.nextRadiusIndex = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Takeoff detect speed
        bind.takeoffSpd.doOnTextChanged { text, _, _, _ ->
            var dToSpd = Utils.getDoubleOrNull(text.toString())
            if (dToSpd != null) {
                dToSpd = Convert.fromUserUnitsSpd(dToSpd)!!
                if (dToSpd < C.AUTO_TAKEOFF_MIN_SPEED_KT) dToSpd = C.AUTO_TAKEOFF_MIN_SPEED_KT
                State.options.autoTakeoffSpd = Convert.kt2mps(dToSpd)
                save = true
            }
        }
        bind.takeoffSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

        // Landing detect speed
        bind.landingSpd.doOnTextChanged { text, _, _, _ ->
            var dLndSpd = Utils.getDoubleOrNull(text.toString())
            if (dLndSpd != null) {
                dLndSpd = Convert.fromUserUnitsSpd(dLndSpd)!!
                if (dLndSpd < C.AUTO_LANDING_MIN_SPEED_KT) dLndSpd = C.AUTO_LANDING_MIN_SPEED_KT
                State.options.autoLandingSpd = Convert.kt2mps(dLndSpd)
                save = true
            }
        }
        bind.landingSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

        // Spinner - Speed units
        bind.spinnerUnitsSpd.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != State.options.spdUnits) {
                        State.options.spdUnits = position
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
                    if (position != State.options.distUnits) {
                        State.options.distUnits = position
                        save = true
                        setupUI()
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
                    if (position != State.options.volUnits) {
                        State.options.volUnits = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Spinner - Map orientation
        bind.spinnerMapOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != State.options.mapOrientation) {
                        State.options.mapOrientation = position
                        save = true
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - Display trace
        bind.settingTrace.setOnCheckedChangeListener { _, isChecked ->
            State.options.displayTrace = isChecked
            save = true
            saveForm()
        }

        // Switch - Display wind arrow
        bind.settingWindArrow.setOnCheckedChangeListener { _, isChecked ->
            State.options.drawWindArrow = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radials
        bind.settingRadials.setOnCheckedChangeListener { _, isChecked ->
            State.options.drawRadials = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radial markers
        bind.settingRadialsMarkers.setOnCheckedChangeListener { _, isChecked ->
            State.options.drawRadialsMarkers = isChecked
            save = true
            saveForm()
        }

        // Spinner - Screen orientation
        bind.spinnerScreenOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != State.options.screenOrientation) {
                        State.options.screenOrientation = position
                        save = true
                        (activity as MainActivity).setScreenOrientation()
                        saveForm()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - Keep the screen ON
        bind.settingsScreenOn.setOnCheckedChangeListener { _, isChecked ->
            State.options.keepScreenOn = isChecked
            save = true
            saveForm()
        }

        // Switch - Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            State.options.timeInUTC = isChecked
            save = true
            saveForm()
        }

        // Switch - Disable edit after Off-Block
        bind.settingBlockPlanEdit.setOnCheckedChangeListener { _, isChecked ->
            State.options.blockPlanEdit = isChecked
            save = true
            saveForm()
        }

        // Switch - Show hints
        bind.settingHints.setOnCheckedChangeListener { _, isChecked ->
            State.options.showHints = isChecked
            save = true
            saveForm()
        }
    }

    override fun onStop() {
        super.onStop()
        saveForm()
    }

    private fun saveForm() {
        if (restore) return
        if (!save) return
        save = false
        FileUtils.saveOptions()
        restoreOptions()
    }

    private fun setupUI() {
        // Spinner - Speed units
        val unitsSpdList = ArrayList<String>()
        unitsSpdList.add("Knots (kt)")            // 0
        unitsSpdList.add("Miles/h (mph)")         // 1
        unitsSpdList.add("Kilometers/h (kph)")    // 2
        bind.spinnerUnitsSpd.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, unitsSpdList)

        // Spinner - Speed units
        val unitsDistList = ArrayList<String>()
        unitsDistList.add("Nautical miles (nm)")  // 0
        unitsDistList.add("Statute miles (sm)")   // 1
        unitsDistList.add("Kilometers (km)")      // 2
        bind.spinnerUnitsDist.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, unitsDistList)

        // Spinner - Volume units
        val unitsFuelList = ArrayList<String>()
        unitsFuelList.add("US Gal (gal)")         // 0
        unitsFuelList.add("UK Gal (gal)")         // 1
        unitsFuelList.add("Liters (l)")           // 2
        bind.spinnerUnitsVol.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, unitsFuelList)

        // Spinner - Screen orientation
        val screenOrientationList = ArrayList<String>()
        screenOrientationList.add("Portrait")     // 0
        screenOrientationList.add("Landscape")    // 1
        screenOrientationList.add("Auto")         // 2
        bind.spinnerScreenOrientation.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, screenOrientationList)

        // Spinner - Map orientation
        val mapOrientationList = ArrayList<String>()
        mapOrientationList.add("North Up")     // 0
        mapOrientationList.add("Track Up")     // 1
        mapOrientationList.add("Bearing Up")   // 2
        bind.spinnerMapOrientation.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, mapOrientationList)

        // Switch - Auto-next radius
        val nextRadiusOptions = ArrayList<String>()
        for (i in C.nextRadiusList.indices) {
            val r = Utils.formatDouble(Convert.toUserUnitsDis(C.nextRadiusList[i]), 1) + " " + Convert.getUnitsDis()
            nextRadiusOptions.add(r)
        }
        bind.spinnerNextRadius.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, nextRadiusOptions)
    }

    private fun restoreOptions() {
        restore = true

        // Navigation options
        bind.settingGpsAssist.isChecked = State.options.gpsAssist
        bind.settingAutoNext.isChecked = State.options.autoNext
        bind.spinnerNextRadius.setSelection(State.options.nextRadiusIndex)

        bind.takeoffSpd.setText(Utils.formatDouble(Convert.toUserUnitsSpd(Convert.mps2kt(State.options.autoTakeoffSpd))))
        bind.landingSpd.setText(Utils.formatDouble(Convert.toUserUnitsSpd(Convert.mps2kt(State.options.autoLandingSpd))))

        // Display units
        bind.spinnerUnitsSpd.setSelection(State.options.spdUnits)
        bind.spinnerUnitsDist.setSelection(State.options.distUnits)
        bind.spinnerUnitsVol.setSelection(State.options.volUnits)

        // Map options
        bind.spinnerMapOrientation.setSelection(State.options.mapOrientation)
        bind.settingTrace.isChecked = State.options.displayTrace
        bind.settingWindArrow.isChecked = State.options.drawWindArrow
        bind.settingRadials.isChecked = State.options.drawRadials
        bind.settingRadialsMarkers.isChecked = State.options.drawRadialsMarkers

        // Options
        bind.spinnerScreenOrientation.setSelection(State.options.screenOrientation)
        bind.settingsScreenOn.isChecked = State.options.keepScreenOn
        bind.settingTimeUTC.isChecked = State.options.timeInUTC
        bind.settingBlockPlanEdit.isChecked = State.options.blockPlanEdit
        bind.settingHints.isChecked = State.options.showHints

        // Enable / disable options
        bind.settingAutoNext.isEnabled = State.options.gpsAssist
        bind.spinnerNextRadius.isEnabled = !(!State.options.gpsAssist || !State.options.autoNext)
        bind.settingRadialsMarkers.isEnabled = State.options.drawRadials

        bind.takeoffBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.landingBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.takeoffBox.hint = getString(R.string.txtTakeoffSpeed) + " (" + Convert.getUnitsSpd() + ")"
        bind.landingBox.hint = getString(R.string.txtLandingSpeed) + " (" + Convert.getUnitsSpd() + ")"

        restore = false
    }
}
