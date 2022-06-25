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
    private val precision = 1

    private var change = false
    private var refresh = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        //println("onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.settingLayout.keepScreenOn = settings.keepScreenOn

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
            if (!hasFocus) saveSettings()
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
            if (!hasFocus) saveSettings()
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
            if (!hasFocus) saveSettings()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // WindDir
        bind.settingWindDir.doOnTextChanged { text, _, _, _ ->
            val dWindDir = getDoubleOrNull(text.toString())
            if (!validWinDir(dWindDir)) showSettingsError(getString(R.string.txtInvalidWind))
            else if (settings.windDir != dWindDir) {
                settings.windDir = dWindDir!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
            }
        }
        bind.settingWindDir.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // WindSpeed
        bind.settingWindSpd.doOnTextChanged { text, _, _, _ ->
            val dWindSpd = getDoubleOrNull(text.toString())
            if (!validWinSpeed(dWindSpd)) showSettingsError(getString(R.string.txtInvalidWindSpeed))
            else if (settings.windSpd != dWindSpd) {
                settings.windSpd = dWindSpd!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
            }
        }
        bind.settingWindSpd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Fuel on board
        bind.settingFuel.doOnTextChanged { text, _, _, _ ->
            val dFob = getDoubleOrNull(text.toString())
            if (dFob != null) {
                if (dFob > 0.0) {
                    if (settings.fob != dFob) {
                        settings.fob = dFob
                        bind.settingsInfoBox.visibility = View.GONE
                        change = true
                    }
                } else showSettingsError(getString(R.string.txtInvalidFOB))
            }
        }
        bind.settingFuel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (settings.fob == null) bind.settingFuel.setText("")
                else bind.settingsInfoBox.visibility = View.GONE
                saveSettings()
            }
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
                    saveSettings()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Spinner - Speed units
        bind.spinnerUnitsSpd.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != settings.spdUnits) {
                        val oldUnits = settings.spdUnits
                        settings.spdUnits = position
                        convertSettingsSpdUnits(oldUnits, position)
                        change = true
                        saveSettings()
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
                    if (position != settings.distUnits) {
                        val oldUnits = settings.distUnits
                        settings.distUnits = position
                        convertSettingsDistUnits(oldUnits, position)
                        change = true
                        saveSettings()
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
                    if (position != settings.volUnits) {
                        val oldUnits = settings.volUnits
                        settings.volUnits = position
                        convertSettingsVolUnits(oldUnits, position)
                        change = true
                        saveSettings()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - GPS master
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
            saveSettings()
        }

        // Switch - Auto-detect Waypoint
        bind.settingAutoNext.setOnCheckedChangeListener { _, isChecked ->
            settings.autoNext = isChecked
            setWptDetectVisibility(settings.autoNext)
            change = true
            saveSettings()
            if (isAutoNextEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).detectFlightStageThread() }
        }

        // Switch - Trace recording
        bind.settingTrace.setOnCheckedChangeListener { _, isChecked ->
            settings.recordTrace = isChecked
            change = true
            saveSettings()
            if (isFlightTraceEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).traceThread() }
        }

        // Switch - Auto-next radius
        bind.spinnerNextRadius.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != settings.nextRadius) {
                        settings.nextRadius = position
                        change = true
                        saveSettings()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Switch - Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            settings.timeInUTC = isChecked
            change = true
            saveSettings()
        }

        // Switch - Keep screen ON
        bind.settingsScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            change = true
            saveSettings()
        }

        // Spinner - Map orientation
        bind.spinnerMapOrientation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != settings.mapOrientation) {
                        settings.mapOrientation = position
                        change = true
                        saveSettings()
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
                    if (position != settings.screenOrientation) {
                        settings.screenOrientation = position
                        change = true
                        (activity as MainActivity).setScreenOrientation()
                        saveSettings()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Disable edit during the flight
        if (timers.offblock != null) {
            bind.settingFlightPlanNameBox.isEnabled = false
            bind.spinnerAirplane.isEnabled = false
        }

        setupUI(view)
        restoreSettings()
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
        saveSettings()
    }

    private fun saveSettings() {
        if (refresh) return
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

        // Spinner - Speed units
        val unitsSpdList = ArrayList<String>()
        unitsSpdList.add("Knots")   // 0
        unitsSpdList.add("Mph")     // 1
        unitsSpdList.add("Kph")     // 2
        bind.spinnerUnitsSpd.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsSpdList)

        // Spinner - Speed units
        val unitsDistList = ArrayList<String>()
        unitsDistList.add("Nautical miles")    // 0
        unitsDistList.add("Statute miles")     // 1
        unitsDistList.add("Kilometers")        // 2
        bind.spinnerUnitsDist.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsDistList)

        // Spinner - Volume units
        val unitsFuelList = ArrayList<String>()
        unitsFuelList.add("US Gal")    // 0
        unitsFuelList.add("UK Gal")    // 1
        unitsFuelList.add("Liters")    // 2
        bind.spinnerUnitsVol.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsFuelList)

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

        // Spinner - Screen orientation
        val screenOrientationList = ArrayList<String>()
        screenOrientationList.add("Portrait")    // 0
        screenOrientationList.add("Landscape")   // 1
        screenOrientationList.add("Auto")        // 2
        bind.spinnerScreenOrientation.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, screenOrientationList)
    }

    private fun restoreSettings() {
        refresh = true

        bind.settingFlightPlanName.setText(settings.planName)
        bind.settingFrom.setText(settings.departure)
        bind.settingDestination.setText(settings.destination)

        // Airplane
        bind.settingFuel.setText(formatDouble(settings.fob, precision))

        val id = getAirplaneListPosition(settings.planeId)
        if (id > 0) {
            bind.spinnerAirplane.setSelection(id)
            bind.airplaneDetailsTas.text = formatDouble(settings.planeTas)
            bind.airplaneDetailsTank.text = formatDouble(settings.planeTank)
            bind.airplaneDetailsFph.text = formatDouble(settings.planeFph, 1)
            bind.airplaneDetailsBox.visibility = View.VISIBLE
            bind.settingsSelectAirplaneMsg.visibility = View.GONE
        } else {
            bind.airplaneDetailsBox.visibility = View.GONE
            bind.settingsSelectAirplaneMsg.visibility = View.VISIBLE
        }
        bind.airplaneSpdUnits.text = getUnitsSpd()
        bind.airplaneFuelUnits1.text = getUnitsVolume()
        bind.airplaneFuelUnits2.text = getUnitsVolume()

        // Flight conditions
        bind.settingWindDir.setText(formatDouble(settings.windDir, precision))
        bind.settingWindSpd.setText(formatDouble(settings.windSpd, precision))

        // Units
        bind.spinnerUnitsSpd.setSelection(settings.spdUnits)
        bind.spinnerUnitsDist.setSelection(settings.distUnits)
        bind.spinnerUnitsVol.setSelection(settings.volUnits)

        // GPS settings
        bind.settingGpsAssist.isChecked = settings.gpsAssist
        bind.spinnerMapOrientation.setSelection(settings.mapOrientation)
        bind.spinnerNextRadius.setSelection(settings.nextRadius)
        bind.settingAutoNext.isChecked = settings.autoNext
        bind.settingTrace.isChecked = settings.recordTrace

        // Misc
        bind.settingTimeUTC.isChecked = settings.timeInUTC
        bind.settingsScreenOn.isChecked = settings.keepScreenOn
        bind.spinnerScreenOrientation.setSelection(settings.screenOrientation)

        setGpsGroupVisibility(settings.gpsAssist)
        setWptDetectVisibility(settings.autoNext)

        refresh = false
    }

    private fun setGpsGroupVisibility(visible: Boolean) {
        bind.gpsGroup.isVisible = visible
    }

    private fun setWptDetectVisibility(visible: Boolean) {
        bind.settingWptDetection.isVisible = visible
    }

    private fun validWinDir(v: Double?): Boolean {
        return v != null && v in 0.0..360.0
    }

    private fun validWinSpeed(v: Double?): Boolean {
        return v != null && v >= 0.0 && v < settings.planeTas
    }

    private fun validTas(v: Double?): Boolean {
        return v != null && v > settings.windSpd
    }

    private fun getAirplaneSettings(i: Int = -1) {
        if (i < 0) {
            resetAirplaneSettings()
            return
        }
        Log.d("SettingsFragment", "getAirplaneSettings")

        settings.planeId = airplaneList[i].id
        val airplane = getAirplaneByID(settings.planeId)
        if (airplane != null) {
            settings.planeType = airplane.type
            settings.planeReg = airplane.reg
            settings.planeTas = airplane.tas
            settings.planeTank = airplane.tank
            settings.planeFph = airplane.fph
        } else resetAirplaneSettings()
    }
}

fun isAutoNextEnabled(): Boolean {
    return settings.gpsAssist && settings.autoNext
}

fun isFlightTraceEnabled(): Boolean {
    return settings.gpsAssist && settings.recordTrace
}

fun isMapFollow(): Boolean {
    return settings.gpsAssist && settings.mapFollow
}

fun resetSettings() {
    settings.id = generateStringId()

    settings.planName = ""
    settings.departure = ""
    settings.destination = ""

    settings.windDir = 0.0
    settings.windSpd = 0.0

    resetAirplaneSettings()
    settings.fob = null

    settings.spdUnits = 0
    settings.distUnits = 0
    settings.volUnits = 0

    settings.timeInUTC = false
    settings.keepScreenOn = true

    settings.gpsAssist = true
    settings.autoNext = true
    settings.mapFollow = false
    settings.recordTrace = true

    settings.takeoffCoords = null
    settings.mapType = GoogleMap.MAP_TYPE_NORMAL
    settings.mapOrientation = C.MAP_ORIENTATION_NORTH
    settings.tfDisplayToggle = C.TF_DISPLAY_REM
    settings.nextRadius = C.DEFAULT_NEXT_RADIUS
    settings.screenOrientation = C.SCREEN_SENSOR
}

fun resetAirplaneSettings() {
    settings.planeId = ""
    settings.planeType = ""
    settings.planeReg = ""
    settings.planeTas = 0.0
    settings.planeFph = 0.0
    settings.planeTank = 0.0
}