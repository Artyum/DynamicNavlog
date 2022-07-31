package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentOptionsBinding
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

class OptionsFragment : Fragment(R.layout.fragment_options) {
    private val TAG = "PreferencesFragment"
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.optionsLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).hideButtons()

        // Switch - GPS assist
        bind.settingGpsAssist.setOnCheckedChangeListener { _, isChecked ->
            options.gpsAssist = isChecked
            if (options.gpsAssist) {
                (activity as MainActivity).locationSubscribe()
            } else {
                runBlocking { gpsMutex.withLock { gpsData.isValid = false } }
                (activity as MainActivity).locationUnsubscribe()
            }
            save = true
            saveForm()
        }

        // Switch - Auto-detect waypoint
        bind.settingAutoNext.setOnCheckedChangeListener { _, isChecked ->
            options.autoNext = isChecked
            save = true
            saveForm()
            if (isAutoNextEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).detectFlightStageThread() }
        }

        // Switch - Auto-next radius
        bind.spinnerNextRadius.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.nextRadius) {
                        options.nextRadius = position
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
            var dToSpd = getDoubleOrNull(text.toString())
            if (dToSpd != null) {
                if (dToSpd < C.AUTO_TAKEOFF_MIN_SPEED_KT) dToSpd = C.AUTO_TAKEOFF_MIN_SPEED_KT
                options.autoTakeoffSpd = kt2mps(dToSpd)
                save = true
            }
        }
        bind.takeoffSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

        // Landing detect speed
        bind.landingSpd.doOnTextChanged { text, _, _, _ ->
            var dLndSpd = getDoubleOrNull(text.toString())
            if (dLndSpd != null) {
                if (dLndSpd < C.AUTO_LANDING_MIN_SPEED_KT) dLndSpd = C.AUTO_LANDING_MIN_SPEED_KT
                options.autoLandingSpd = kt2mps(dLndSpd)
                save = true
            }
        }
        bind.landingSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

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

        // Spinner - Map orientation
        bind.spinnerMapOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != options.mapOrientation) {
                        options.mapOrientation = position
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
            options.displayTrace = isChecked
            save = true
            saveForm()
        }

        // Switch - Display wind arrow
        bind.settingWindArrow.setOnCheckedChangeListener { _, isChecked ->
            options.drawWindArrow = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radials
        bind.settingRadials.setOnCheckedChangeListener { _, isChecked ->
            options.drawRadials = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radial markers
        bind.settingRadialsMarkers.setOnCheckedChangeListener { _, isChecked ->
            options.drawRadialsMarkers = isChecked
            save = true
            saveForm()
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

        // Switch - Keep the screen ON
        bind.settingsScreenOn.setOnCheckedChangeListener { _, isChecked ->
            options.keepScreenOn = isChecked
            save = true
            saveForm()
        }

        // Switch - Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            options.timeInUTC = isChecked
            save = true
            saveForm()
        }

        // Switch - Time UTC
        bind.settingHints.setOnCheckedChangeListener { _, isChecked ->
            options.showHints = isChecked
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
        if (restore) return
        if (!save) return
        save = false
        saveOptions()
        restoreOptions()
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

        // Spinner - Map orientation
        val mapOrientationList = ArrayList<String>()
        mapOrientationList.add("North Up")     // 0
        mapOrientationList.add("Track Up")     // 1
        mapOrientationList.add("Bearing Up")   // 2
        bind.spinnerMapOrientation.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, mapOrientationList)

        // Switch - Auto-next radius
        val nextRadiusOptions = ArrayList<String>()
        for (i in nextRadiusList.indices) nextRadiusOptions.add(getNextRadiusUnits(i))
        bind.spinnerNextRadius.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, nextRadiusOptions)
    }

    private fun restoreOptions() {
        restore = true

        // Navigation options
        bind.settingGpsAssist.isChecked = options.gpsAssist
        bind.settingAutoNext.isChecked = options.autoNext
        bind.spinnerNextRadius.setSelection(options.nextRadius)
        bind.takeoffSpd.setText(formatDouble(mps2kt(options.autoTakeoffSpd)))
        bind.landingSpd.setText(formatDouble(mps2kt(options.autoLandingSpd)))

        // Display units
        bind.spinnerUnitsSpd.setSelection(options.spdUnits)
        bind.spinnerUnitsDist.setSelection(options.distUnits)
        bind.spinnerUnitsVol.setSelection(options.volUnits)

        // Map options
        bind.spinnerMapOrientation.setSelection(options.mapOrientation)
        bind.settingTrace.isChecked = options.displayTrace
        bind.settingWindArrow.isChecked = options.drawWindArrow
        bind.settingRadials.isChecked = options.drawRadials
        bind.settingRadialsMarkers.isChecked = options.drawRadialsMarkers

        // Options
        bind.spinnerScreenOrientation.setSelection(options.screenOrientation)
        bind.settingsScreenOn.isChecked = options.keepScreenOn
        bind.settingTimeUTC.isChecked = options.timeInUTC
        bind.settingHints.isChecked = options.showHints

        // Enable / disable options
        bind.settingAutoNext.isEnabled = options.gpsAssist
        bind.spinnerNextRadius.isEnabled = !(!options.gpsAssist || !options.autoNext)
        bind.takeOffBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.lndBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.settingRadialsMarkers.isEnabled = options.drawRadials

        restore = false
    }
}
