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
import com.artyum.dynamicnavlog.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val TAG = "SettingsFragment"
    private var _binding: FragmentSettingsBinding? = null
    private val bind get() = _binding!!

    private var change = false
    private var restore = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.settingLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // Flight plan name
        bind.settingFlightPlanName.doOnTextChanged { text, _, _, _ ->
            val tmp = text.toString().trim()
            if (settings.planName != tmp) {
                settings.planName = tmp
                change = true
            }
        }
        bind.settingFlightPlanName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // Departure
        bind.settingFrom.doOnTextChanged { text, _, _, _ ->
            val tmp = clearString(text.toString())
            if (settings.departure != tmp) {
                settings.departure = tmp
                change = true
            }
        }
        bind.settingFrom.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // Destination
        bind.settingDestination.doOnTextChanged { text, _, _, _ ->
            val tmp = clearString(text.toString())
            if (settings.destination != tmp) {
                settings.destination = tmp
                change = true
            }
        }
        bind.settingDestination.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // Wind direction
        bind.settingWindDir.doOnTextChanged { text, _, _, _ ->
            val dWindDir = getDoubleOrNull(text.toString())
            if (!isValidWindDir(dWindDir)) showSettingsError(getString(R.string.txtInvalidWind))
            else if (settings.windDir != dWindDir) {
                settings.windDir = dWindDir!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
                refreshSummaryBox()
            }
        }
        bind.settingWindDir.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // Wind speed
        bind.settingWindSpd.doOnTextChanged { text, _, _, _ ->
            val dWindSpd = fromUserUnitsSpd(getDoubleOrNull(text.toString()))
            if (!isValidWindSpeed(dWindSpd)) showSettingsError(getString(R.string.txtInvalidWindSpeed))
            else if (settings.windSpd != dWindSpd) {
                settings.windSpd = dWindSpd!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
                refreshSummaryBox()
            }
        }
        bind.settingWindSpd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // Fuel on board / Takeoff fuel
        bind.settingFuel.doOnTextChanged { text, _, _, _ ->
            var dFob = fromUserUnitsVol(getDoubleOrNull(text.toString()))
            if (dFob != null) {
                if (dFob < 0.0) dFob = 0.0
                if (dFob < totals.fuel) dFob = totals.fuel
                if (dFob > airplane.tank) dFob = airplane.tank
                settings.fob = dFob
                bind.settingsInfoBox.visibility = View.GONE
                change = true
                refreshSummaryBox()
            }
        }
        bind.settingFuel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveForm()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // Spinner - Airplane
        bind.spinnerAirplane.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position > 0) {
                        getAirplaneSettings(position - 1)
                    } else {
                        getAirplaneSettings(-1)
                    }
                    change = true
                    saveForm()
                    refreshSummaryBox()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        setupUI(view)
        restoreSettings()
    }

    override fun onStop() {
        super.onStop()
        saveForm()
    }

    private fun saveForm() {
        if (restore) return
        if (!change) return
        change = false
        Log.d(TAG, "saveSettings")
        calcNavlog()
        saveState()
        bind.settingsInfoBox.visibility = View.GONE
        restoreSettings()
    }

    private fun showSettingsError(msg: String) {
        bind.settingsInfoBox.text = msg
        bind.settingsInfoBox.visibility = View.VISIBLE
    }

    private fun setupUI(view: View) {
        // Spinner - Airplane
        val planeList = ArrayList<String>()
        planeList.add("Select an airplane")
        for (i in airplaneList.indices) {
            val plane = airplaneList[i].reg + "  (" + airplaneList[i].type + ")"
            planeList.add(plane)
        }
        bind.spinnerAirplane.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, planeList)
    }

    private fun restoreSettings() {
        restore = true

        bind.settingFlightPlanName.setText(settings.planName)
        bind.settingFrom.setText(settings.departure)
        bind.settingDestination.setText(settings.destination)

        // Takeoff fuel
        val p = if (settings.fob < C.VOL_THRESHOLD) 1 else 0
        bind.settingFuel.setText(formatDouble(toUserUnitsVol(settings.fob), p))
        bind.hintTakeoffFuel.hint = getString(R.string.txtTakeoffFuel) + " (" + getUnitsVol() + ")"

        // Airplane
        val id = getAirplaneListPosition(settings.airplaneId)
        if (id > 0) {
            bind.spinnerAirplane.setSelection(id)
            bind.airplaneTas.text = formatDouble(toUserUnitsSpd(airplane.tas))
            bind.airplaneTasUnits.text = getUnitsSpd()
            bind.airplaneTank.text = formatDouble(toUserUnitsVol(airplane.tank))
            bind.airplaneTankUnits.text = getUnitsVol()
            bind.airplaneFph.text = formatDouble(toUserUnitsVol(airplane.fph), 1)
            bind.airplaneFphUnits.text = getUnitsVol()
            bind.settingsSelectAirplaneMsg.visibility = View.GONE
        } else {
            bind.airplaneTas.text = "-"
            bind.airplaneTasUnits.text = ""
            bind.airplaneTank.text = "-"
            bind.airplaneTankUnits.text = ""
            bind.airplaneFph.text = "-"
            bind.airplaneFphUnits.text = ""
            bind.settingsSelectAirplaneMsg.visibility = View.VISIBLE
        }

        // Wind conditions
        bind.settingWindDir.setText(formatDouble(settings.windDir, 1))
        bind.settingWindSpd.setText(formatDouble(toUserUnitsSpd(settings.windSpd), 1))
        bind.hintWindSpd.hint = getString(R.string.txtWindSpeed) + " (" + getUnitsSpd() + ")"

        refreshSummaryBox()

        restore = false
    }

    private fun refreshSummaryBox() {
        if (settings.airplaneId != "" && airplane.fph > 0.0) {
            val txtTotDist = formatDouble(toUserUnitsDis(totals.dist))
            val txtTotTime = formatSecondsToTime(totals.time)
            val txtTotFuel = formatDouble(toUserUnitsVol(totals.fuel))

            val spareFuel = settings.fob - totals.fuel

            // Distance opposite to the wind
            val fData1 = flightCalculator(
                course = settings.windDir,
                windDir = settings.windDir,
                windSpd = settings.windSpd,
                tas = airplane.tas,
                fob = spareFuel,
                fph = airplane.fph
            )

            // Distance with the wind
            val fData2 = flightCalculator(
                course = normalizeBearing(settings.windDir + 180.0),
                windDir = settings.windDir,
                windSpd = settings.windSpd,
                tas = airplane.tas,
                fob = spareFuel,
                fph = airplane.fph
            )

            val txtSpareFuel = formatDouble(toUserUnitsVol(spareFuel))
            val txtExtraTime = formatSecondsToTime(fData1.time)
            val txtExtraDist = formatDouble(toUserUnitsDis(fData1.dist)) + "-" + formatDouble(toUserUnitsDis(fData2.dist))

            // Display

            // Total distance
            bind.totsDist.text = txtTotDist
            bind.totsDistUnits.text = getUnitsDis()
            bind.totFuel.text = txtTotFuel
            bind.totFuelUnits.text = getUnitsVol()

            //  Extra fuel
            bind.spareFuel.text = txtSpareFuel
            bind.spareFuelUnits.text = getUnitsVol()

            // Extra distance
            bind.extraDistance.text = txtExtraDist
            bind.extraDistanceUnits.text = getUnitsDis()

            // Plan flight time
            bind.totTime.text = txtTotTime

            // Extra time
            bind.additionalTime.text = txtExtraTime
        } else {
            // Clear
            bind.totsDist.text = "-"
            bind.totsDistUnits.text = ""
            bind.totTime.text = "-"
            bind.totFuel.text = "-"
            bind.totFuelUnits.text = ""
            bind.spareFuel.text = "-"
            bind.spareFuelUnits.text = ""
            bind.extraDistance.text = "-"
            bind.extraDistanceUnits.text = ""
            bind.additionalTime.text = "-"
        }
    }

    private fun isValidWindDir(v: Double?): Boolean {
        return v != null && v in 0.0..360.0
    }

    private fun isValidWindSpeed(v: Double?): Boolean {
        return v != null && v >= 0.0 && v < airplane.tas
    }

    private fun getAirplaneListPosition(id: String): Int {
        // Index 0 -> "Select an airplane"
        if (id == "") return 0
        for (i in airplaneList.indices) {
            if (airplaneList[i].id == id) return i + 1
        }
        return 0
    }

    private fun getAirplaneSettings(i: Int = -1) {
        if (i < 0) {
            resetAirplaneSettings()
        } else {
            getAirplaneByID(airplaneList[i].id)
        }
        restoreSettings()
    }
}

fun isAutoNextEnabled(): Boolean {
    return options.gpsAssist && options.autoNext
}

fun isMapFollow(): Boolean {
    return options.gpsAssist && settings.mapFollow
}

fun resetAllSettings() {
    settings = Settings()
    settings.planId = generateStringId()
    resetAirplaneSettings()
    loadOptions()
}
