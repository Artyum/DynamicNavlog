package com.artyum.dynamicnavlog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentSettingsBinding
import com.google.android.gms.maps.GoogleMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

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
        (activity as MainActivity).hideButtons()

        // Flight plan name
        bind.settingFlightPlanName.doOnTextChanged { text, _, _, _ ->
            val tmp = text.toString().trim()
            if (settings.planName != tmp) {
                //println("planName")
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
                //println("departure")
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
                //println("destination")
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
                refreshSpareFuelBox()
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
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - GPS assist master
        bind.settingGpsAssist.setOnCheckedChangeListener { _, isChecked ->
            val a = activity as MainActivity
            if (navlogList.size == 0 || isNavlogGpsReady()) {
                settings.gpsAssist = isChecked
                if (settings.gpsAssist) {
                    setGpsGroupVisibility(true)
                    a.locationSubscribe()
                } else {
                    setGpsGroupVisibility(false)
                    runBlocking { gpsMutex.withLock { gpsData.isValid = false } }
                    a.locationUnsubscribe()
                }
            } else {
                settings.gpsAssist = false
                Toast.makeText(view.context, R.string.txtNotAllTrueTrackSet, Toast.LENGTH_LONG).show()
            }
            change = true
            saveForm()
        }

        // Switch - Auto-detect Waypoint
        bind.settingAutoNext.setOnCheckedChangeListener { _, isChecked ->
            settings.autoNext = isChecked
            setWptDetectVisibility(settings.autoNext)
            change = true
            saveForm()
            if (isAutoNextEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).detectFlightStageThread() }
        }

        // Switch - Trace recording
        bind.settingTrace.setOnCheckedChangeListener { _, isChecked ->
            settings.displayTrace = isChecked
            change = true
            saveForm()
        }

        // Switch - Display wind arrow
        bind.settingWindArrow.setOnCheckedChangeListener { _, isChecked ->
            settings.drawWindArrow = isChecked
            change = true
            saveForm()
        }

        // Switch - Auto-next radius
        bind.spinnerNextRadius.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != settings.nextRadius) {
                        settings.nextRadius = position
                        change = true
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
                    if (position != settings.mapOrientation) {
                        settings.mapOrientation = position
                        change = true
                        saveForm()
                    }
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
            bind.airplaneDetailsTas.text = formatDouble(toUserUnitsSpd(airplane.tas))
            bind.airplaneDetailsTank.text = formatDouble(toUserUnitsVol(airplane.tank))
            bind.airplaneDetailsFph.text = formatDouble(toUserUnitsVol(airplane.fph), 1)
            bind.airplaneDetailsBox.visibility = View.VISIBLE
            bind.settingsSelectAirplaneMsg.visibility = View.GONE
        } else {
            bind.airplaneDetailsBox.visibility = View.GONE
            bind.settingsSelectAirplaneMsg.visibility = View.VISIBLE
        }
        bind.airplaneSpdUnits.text = getUnitsSpd()
        bind.airplaneFuelUnits1.text = getUnitsVol()
        bind.airplaneFuelUnits2.text = getUnitsVol()

        // Wind conditions
        bind.settingWindDir.setText(formatDouble(settings.windDir, 1))
        bind.settingWindSpd.setText(formatDouble(toUserUnitsSpd(settings.windSpd), 1))
        bind.hintWindSpd.hint = getString(R.string.txtWindSpeed) + " (" + getUnitsSpd() + ")"

        // GPS settings
        bind.settingGpsAssist.isChecked = settings.gpsAssist
        bind.spinnerMapOrientation.setSelection(settings.mapOrientation)
        bind.spinnerNextRadius.setSelection(settings.nextRadius)
        bind.settingAutoNext.isChecked = settings.autoNext
        bind.settingTrace.isChecked = settings.displayTrace
        bind.settingWindArrow.isChecked = settings.drawWindArrow

        setGpsGroupVisibility(settings.gpsAssist)
        setWptDetectVisibility(settings.autoNext)
        refreshSpareFuelBox()

        restore = false
    }

    private fun refreshSpareFuelBox() {
        if (settings.airplaneId != "" && airplane.fph > 0.0) {
            val spareFuel = settings.fob - totals.fuel
            val h = spareFuel / airplane.fph
            val extraDist = airplane.tas * h

            //  Extra fuel
            bind.spareFuel.text = formatDouble(toUserUnitsVol(totals.fuel)) + "/" + formatDouble(toUserUnitsVol(spareFuel))
            bind.spareFuelUnits.text = getUnitsVol()

            // Extra distance
            bind.extraDistance.text = formatDouble(toUserUnitsDis(extraDist))
            bind.extraDistanceUnits.text = getUnitsDis()

            // Extra time
            bind.additionalTime.text = formatSecondsToTime((h * 3600.0).toLong())
        } else {
            bind.spareFuel.text = ""
            bind.extraDistance.text = ""
            bind.additionalTime.text = "-"
            bind.spareFuelUnits.text = "-"
            bind.extraDistanceUnits.text = "-"
        }
    }

    private fun setGpsGroupVisibility(visible: Boolean) {
        bind.gpsGroup.isVisible = visible
    }

    private fun setWptDetectVisibility(visible: Boolean) {
        bind.settingWptDetection.isVisible = visible
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
    return settings.gpsAssist && settings.autoNext
}

fun isDisplayFlightTrace(): Boolean {
    return settings.gpsAssist && settings.displayTrace
}

fun isMapFollow(): Boolean {
    return settings.gpsAssist && settings.mapFollow
}

fun resetSettings() {
    settings = Settings()
    settings.planId = generateStringId()
    resetAirplaneSettings()
    loadOptions()
}
