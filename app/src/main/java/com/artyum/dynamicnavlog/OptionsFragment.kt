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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.optionsLayout.keepScreenOn = G.vm.options.value!!.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Switch - GPS assist
        bind.settingGpsAssist.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.gpsAssist = isChecked
            if (G.vm.options.value!!.gpsAssist) {
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
            G.vm.options.value!!.autoNext = isChecked
            save = true
            saveForm()
        }

        // Switch - Auto-next radius
        bind.spinnerNextRadius.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != G.vm.options.value!!.nextRadiusIndex) {
                        G.vm.options.value!!.nextRadiusIndex = position
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
                dToSpd = fromUserUnitsSpd(dToSpd)!!
                if (dToSpd < C.AUTO_TAKEOFF_MIN_SPEED_KT) dToSpd = C.AUTO_TAKEOFF_MIN_SPEED_KT
                G.vm.options.value!!.autoTakeoffSpd = kt2mps(dToSpd)
                save = true
            }
        }
        bind.takeoffSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

        // Landing detect speed
        bind.landingSpd.doOnTextChanged { text, _, _, _ ->
            var dLndSpd = getDoubleOrNull(text.toString())
            if (dLndSpd != null) {
                dLndSpd = fromUserUnitsSpd(dLndSpd)!!
                if (dLndSpd < C.AUTO_LANDING_MIN_SPEED_KT) dLndSpd = C.AUTO_LANDING_MIN_SPEED_KT
                G.vm.options.value!!.autoLandingSpd = kt2mps(dLndSpd)
                save = true
            }
        }
        bind.landingSpd.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveForm() }

        // Spinner - Speed units
        bind.spinnerUnitsSpd.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != G.vm.options.value!!.spdUnits) {
                        G.vm.options.value!!.spdUnits = position
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
                    if (position != G.vm.options.value!!.distUnits) {
                        G.vm.options.value!!.distUnits = position
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
                    if (position != G.vm.options.value!!.volUnits) {
                        G.vm.options.value!!.volUnits = position
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
                    if (position != G.vm.options.value!!.mapOrientation) {
                        G.vm.options.value!!.mapOrientation = position
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
            G.vm.options.value!!.displayTrace = isChecked
            save = true
            saveForm()
        }

        // Switch - Display wind arrow
        bind.settingWindArrow.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.drawWindArrow = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radials
        bind.settingRadials.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.drawRadials = isChecked
            save = true
            saveForm()
        }

        // Switch - Display radial markers
        bind.settingRadialsMarkers.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.drawRadialsMarkers = isChecked
            save = true
            saveForm()
        }

        // Spinner - Screen orientation
        bind.spinnerScreenOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != G.vm.options.value!!.screenOrientation) {
                        G.vm.options.value!!.screenOrientation = position
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
            G.vm.options.value!!.keepScreenOn = isChecked
            save = true
            saveForm()
        }

        // Switch - Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.timeInUTC = isChecked
            save = true
            saveForm()
        }

        // Switch - Disable edit after Off-Block
        bind.settingBlockPlanEdit.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.blockPlanEdit = isChecked
            save = true
            saveForm()
        }

        // Switch - Show hints
        bind.settingHints.setOnCheckedChangeListener { _, isChecked ->
            G.vm.options.value!!.showHints = isChecked
            save = true
            saveForm()
        }

        setupUI()
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
        for (i in nextRadiusList.indices) {
            val r = formatDouble(toUserUnitsDis(nextRadiusList[i]), 1) + " " + getUnitsDis()
            nextRadiusOptions.add(r)
        }
        bind.spinnerNextRadius.adapter = ArrayAdapter(this.requireContext(), R.layout.support_simple_spinner_dropdown_item, nextRadiusOptions)
    }

    private fun restoreOptions() {
        restore = true

        // Navigation options
        bind.settingGpsAssist.isChecked = G.vm.options.value!!.gpsAssist
        bind.settingAutoNext.isChecked = G.vm.options.value!!.autoNext
        bind.spinnerNextRadius.setSelection(G.vm.options.value!!.nextRadiusIndex)

        bind.takeoffSpd.setText(formatDouble(toUserUnitsSpd(mps2kt(G.vm.options.value!!.autoTakeoffSpd))))
        bind.landingSpd.setText(formatDouble(toUserUnitsSpd(mps2kt(G.vm.options.value!!.autoLandingSpd))))

        // Display units
        bind.spinnerUnitsSpd.setSelection(G.vm.options.value!!.spdUnits)
        bind.spinnerUnitsDist.setSelection(G.vm.options.value!!.distUnits)
        bind.spinnerUnitsVol.setSelection(G.vm.options.value!!.volUnits)

        // Map options
        bind.spinnerMapOrientation.setSelection(G.vm.options.value!!.mapOrientation)
        bind.settingTrace.isChecked = G.vm.options.value!!.displayTrace
        bind.settingWindArrow.isChecked = G.vm.options.value!!.drawWindArrow
        bind.settingRadials.isChecked = G.vm.options.value!!.drawRadials
        bind.settingRadialsMarkers.isChecked = G.vm.options.value!!.drawRadialsMarkers

        // Options
        bind.spinnerScreenOrientation.setSelection(G.vm.options.value!!.screenOrientation)
        bind.settingsScreenOn.isChecked = G.vm.options.value!!.keepScreenOn
        bind.settingTimeUTC.isChecked = G.vm.options.value!!.timeInUTC
        bind.settingBlockPlanEdit.isChecked = G.vm.options.value!!.blockPlanEdit
        bind.settingHints.isChecked = G.vm.options.value!!.showHints

        // Enable / disable options
        bind.settingAutoNext.isEnabled = G.vm.options.value!!.gpsAssist
        bind.spinnerNextRadius.isEnabled = !(!G.vm.options.value!!.gpsAssist || !G.vm.options.value!!.autoNext)
        bind.settingRadialsMarkers.isEnabled = G.vm.options.value!!.drawRadials

        bind.takeoffBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.landingBox.isEnabled = bind.spinnerNextRadius.isEnabled
        bind.takeoffBox.hint = getString(R.string.txtTakeoffSpeed) + " (" + getUnitsSpd() + ")"
        bind.landingBox.hint = getString(R.string.txtLandingSpeed) + " (" + getUnitsSpd() + ")"


        restore = false
    }
}
