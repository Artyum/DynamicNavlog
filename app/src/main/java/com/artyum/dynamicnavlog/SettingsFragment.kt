package com.artyum.dynamicnavlog

import android.content.pm.ActivityInfo
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
        //println("onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        bind.settingLayout.keepScreenOn = settings.keepScreenOn

        setupUI(view)
        restoreSettings()

        // Disable Flight Plan Name edit
        if (timers.offblock != null) {
            //bind.settingFlightPlanName.isEnabled = false
            bind.settingFlightPlanNameBox.isEnabled = false
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // Flight plan name
        bind.settingFlightPlanName.doOnTextChanged { text, _, _, _ ->
            val tmp = clearFlightPlanName(text.toString())
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

        // Type of plane
        bind.settingPlaneType.doOnTextChanged { text, _, _, _ ->
            val tmp = clearString(text.toString())
            if (settings.planeType != tmp) {
                //println("planeType")
                settings.planeType = tmp
                change = true
            }
        }
        bind.settingPlaneType.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Registration
        bind.settingRegistration.doOnTextChanged { text, _, _, _ ->
            val tmp = clearString(text.toString())
            if (settings.registration != tmp) {
                //println("registration")
                settings.registration = clearString(text.toString())
                change = true
            }
        }
        bind.settingRegistration.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // WindDir
        bind.settingWindDir.doOnTextChanged { text, _, _, _ ->
            val dWindDir = getDoubleOrNull(text.toString())
            if (!validWinDir(dWindDir)) showSettingsError(getString(R.string.txtInvalidWind))
            else if (settings.windDir != dWindDir) {
                //println("windDir")
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
                //println("windSpd")
                settings.windSpd = dWindSpd!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
            }
        }
        bind.settingWindSpd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // TAS
        bind.settingTas.doOnTextChanged { text, _, _, _ ->
            val dTas = getDoubleOrNull(text.toString())
            if (!validTas(dTas)) showSettingsError(getString(R.string.txtInvalidTAS))
            else if (settings.tas != dTas) {
                //println("tas")
                settings.tas = dTas!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
            }
        }
        bind.settingTas.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Fuel per hour
        bind.settingFph.doOnTextChanged { text, _, _, _ ->
            val dFph = getDoubleOrNull(text.toString())
            if (dFph != null) {
                if (dFph > 0.0) {
                    if (settings.fph != dFph) {
                        //println("fph")
                        settings.fph = dFph
                        bind.settingsInfoBox.visibility = View.GONE
                        change = true
                    }
                } else showSettingsError(getString(R.string.txtInvalidFPH))
            }
        }
        bind.settingFph.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (settings.fph == null) bind.settingFph.setText("")
                else bind.settingsInfoBox.visibility = View.GONE
                saveSettings()
            }
        }

        // Fuel on board
        bind.settingFuel.doOnTextChanged { text, _, _, _ ->
            val dFob = getDoubleOrNull(text.toString())
            if (dFob != null) {
                if (dFob > 0.0) {
                    if (settings.fuelOnBoard != dFob) {
                        //println("fuelOnBoard")
                        settings.fuelOnBoard = dFob
                        bind.settingsInfoBox.visibility = View.GONE
                        change = true
                    }
                } else showSettingsError(getString(R.string.txtInvalidFOB))
            }
        }
        bind.settingFuel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (settings.fuelOnBoard == null) bind.settingFuel.setText("")
                else bind.settingsInfoBox.visibility = View.GONE
                saveSettings()
            }
        }

        // Tank capacity
        bind.settingTank.doOnTextChanged { text, _, _, _ ->
            val dTc = getDoubleOrNull(text.toString())
            if (dTc != null) {
                if (dTc > 0.0) {
                    if (settings.tankCapacity != dTc) {
                        //println("tankCapacity")
                        settings.tankCapacity = dTc
                        bind.settingsInfoBox.visibility = View.GONE
                        change = true
                    }
                } else showSettingsError(getString(R.string.txtInvalidTC))
            }
        }
        bind.settingTank.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                if (settings.tankCapacity == null) bind.settingTank.setText("")
                else bind.settingsInfoBox.visibility = View.GONE
                saveSettings()
            }
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // Units spinner
        bind.spinnerUnits.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    //val sel = parent?.getItemAtPosition(position).toString() //settings.primaryUnits = position
                    if (position != settings.units) {
                        convertSettingsUnits(settings.units, position)
                        settings.units = position
                        change = true
                        saveSettings()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    return
                }
            }

        // Map orientation spinner
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

        // GPS master switch
        bind.settingGpsAssist.setOnCheckedChangeListener { _, isChecked ->
            val a = activity as MainActivity
            if (navlogList.size == 0 || isNavlogGpsReady()) {
                settings.gpsAssist = isChecked
                if (settings.gpsAssist) {
                    setGpsGroup(true)
                    a.locationSubscribe()
                } else {
                    setGpsGroup(false)
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

        // Auto next Waypoint
        bind.settingAutoNext.setOnCheckedChangeListener { _, isChecked ->
            settings.autoNext = isChecked
            change = true
            saveSettings()
            if (isAutoNextEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).autoNextThread() }
        }

        // Trace recording
        bind.settingTrace.setOnCheckedChangeListener { _, isChecked ->
            settings.recordTrace = isChecked
            change = true
            saveSettings()
            if (isFlightTraceEnabled()) CoroutineScope(CoroutineName("gpsCoroutine")).launch { (activity as MainActivity).traceThread() }
        }

        // Auto-next radius
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

        // Time UTC
        bind.settingTimeUTC.setOnCheckedChangeListener { _, isChecked ->
            settings.timeInUTC = isChecked
            change = true
            saveSettings()
        }

        // Keep screen ON
        bind.settingsScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            change = true
            saveSettings()
        }

        // Screen orientation spinner
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
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
        //println(settings)
        saveSettings()
    }

    private fun saveSettings() {
        if (refresh) return
        if (!change) return
        change = false
        Log.d(tag, "saveSettings")
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
        // Units spinner
        val unitsTypeList = ArrayList<String>()
        unitsTypeList.add("nm / kt")       // 0
        unitsTypeList.add("sm / mph")      // 1
        unitsTypeList.add("km / kph")      // 2
        bind.spinnerUnits.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, unitsTypeList)

        // Map orientation spinner
        val mapOrientationList = ArrayList<String>()
        mapOrientationList.add("North Up")     // 0
        mapOrientationList.add("Track Up")     // 1
        mapOrientationList.add("Bearing Up")   // 2
        bind.spinnerMapOrientation.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, mapOrientationList)

        // Auto-next radius
        val nextRadiusOptions = ArrayList<String>()
        for (i in nextRadiusList.indices) nextRadiusOptions.add(getNextRadiusUnits(i))
        bind.spinnerNextRadius.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, nextRadiusOptions)

        // Screen orientation spinner
        val screenOrientationList = ArrayList<String>()
        screenOrientationList.add("Portrait")    // 0
        screenOrientationList.add("Landscape")   // 1
        screenOrientationList.add("Auto")        // 2
        bind.spinnerScreenOrientation.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, screenOrientationList)
    }

    private fun restoreSettings() {
        refresh = true

        //Log.d(tag, "restoreSettings")
        //println(settings)

        bind.settingFlightPlanName.setText(settings.planName)
        bind.settingFrom.setText(settings.departure)
        bind.settingDestination.setText(settings.destination)
        bind.settingPlaneType.setText(settings.planeType)
        bind.settingRegistration.setText(settings.registration)

        // Flight conditions
        bind.settingWindDir.setText(formatDouble(settings.windDir, precision))
        bind.settingWindSpd.setText(formatDouble(settings.windSpd, precision))
        bind.settingTas.setText(formatDouble(settings.tas, precision))

        bind.settingFph.setText(formatDouble(settings.fph, precision))
        bind.settingFuel.setText(formatDouble(settings.fuelOnBoard, precision))
        bind.settingTank.setText(formatDouble(settings.tankCapacity, precision))

        // Settings
        bind.spinnerUnits.setSelection(settings.units)
        bind.settingGpsAssist.isChecked = settings.gpsAssist

        // GPS settings
        bind.spinnerMapOrientation.setSelection(settings.mapOrientation)
        bind.spinnerNextRadius.setSelection(settings.nextRadius)
        bind.settingAutoNext.isChecked = settings.autoNext
        bind.settingTrace.isChecked = settings.recordTrace

        // Misc
        bind.settingTimeUTC.isChecked = settings.timeInUTC
        bind.settingsScreenOn.isChecked = settings.keepScreenOn
        bind.spinnerScreenOrientation.setSelection(settings.screenOrientation)

        setGpsGroup(settings.gpsAssist)

        refresh = false
    }

    private fun setGpsGroup(isEnabled: Boolean) {
        bind.gpsGroup.isVisible = isEnabled
        //bind.settingAutoNext.isEnabled = isEnabled
        //bind.settingTrace.isEnabled = isEnabled
        //bind.spinnerMapOrientation.isEnabled = isEnabled
        //bind.spinnerNextRadius.isEnabled = isEnabled
    }

    private fun validWinDir(v: Double?): Boolean {
        return v != null && v in 0.0..360.0
    }

    private fun validWinSpeed(v: Double?): Boolean {
        return v != null && v >= 0.0 && v < settings.tas
    }

    private fun validTas(v: Double?): Boolean {
        return v != null && v > settings.windSpd
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
    settings.planName = ""
    settings.departure = ""
    settings.destination = ""
    settings.planeType = ""
    settings.registration = ""

    settings.windDir = 0.0
    settings.windSpd = 0.0
    settings.tas = 0.0

    settings.fph = null
    settings.fuelOnBoard = null
    settings.tankCapacity = null

    settings.units = 0
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
    settings.screenOrientation = C.SCREEN_PORTRAIT
}
