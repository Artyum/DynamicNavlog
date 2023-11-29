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
    private var _binding: FragmentSettingsBinding? = null
    private val bind get() = _binding!!
    private var change = false
    private var restore = true
    private var c = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("SettingsFragment", "onCreate")
        super.onCreate(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d("SettingsFragment", "onCreateView")
        restore = true
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        Log.d("SettingsFragment", "onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("SettingsFragment", "onSaveInstanceState")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("SettingsFragment", "onViewCreated")
        super.onViewCreated(view, null)
        bind.settingLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()
        restore = true

        setupSpinner(view)
        restoreSettings()

        // Flight plan name
        bind.settingFlightPlanName.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingFlightPlanName")
            if (restore) return@doOnTextChanged
            val tmp = text.toString().trim()
            if (State.settings.planName != tmp) {
                Log.d("SettingsFragment", "new FlightPlanName")
                State.settings.planName = tmp
                change = true
            }
        }
        bind.settingFlightPlanName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Departure
        bind.settingFrom.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingFrom")
            if (restore) return@doOnTextChanged
            val tmp = Utils.clearString(text.toString())
            if (State.settings.departure != tmp) {
                Log.d("SettingsFragment", "new Departure")
                State.settings.departure = tmp
                change = true
            }
        }
        bind.settingFrom.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Destination
        bind.settingDestination.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingDestination")
            if (restore) return@doOnTextChanged
            val tmp = Utils.clearString(text.toString())
            if (State.settings.destination != tmp) {
                Log.d("SettingsFragment", "new Destination")
                State.settings.destination = tmp
                change = true
            }
        }
        bind.settingDestination.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

        // Wind direction
        bind.settingWindDir.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingWindDir")
            if (restore) return@doOnTextChanged
            val dWindDir = Utils.getDoubleOrNull(text.toString())
            if (!isValidWindDir(dWindDir)) showSettingsError(getString(R.string.txtInvalidWind))
            else if (State.settings.windDir != dWindDir) {
                Log.d("SettingsFragment", "new WindDir")
                State.settings.windDir = dWindDir!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
                refreshSummaryBox()
            }
        }
        bind.settingWindDir.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Wind speed
        bind.settingWindSpd.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingWindSpd")
            if (restore) return@doOnTextChanged
            val dWindSpd = Convert.fromUserUnitsSpd(Utils.getDoubleOrNull(text.toString()))
            if (!isValidWindSpeed(dWindSpd)) showSettingsError(getString(R.string.txtInvalidWindSpeed))
            else if (State.settings.windSpd != dWindSpd) {
                Log.d("SettingsFragment", "new WindSpd")
                State.settings.windSpd = dWindSpd!!
                bind.settingsInfoBox.visibility = View.GONE
                change = true
                refreshSummaryBox()
            }
        }
        bind.settingWindSpd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // Fuel on board / Takeoff fuel
        bind.settingFuel.doOnTextChanged { text, _, _, _ ->
            Log.d("SettingsFragment", "trigger settingFuel")
            if (restore) return@doOnTextChanged
            var dFob = Convert.fromUserUnitsVol(Utils.getDoubleOrNull(text.toString()))
            if (dFob != null) {
                if (dFob < 0.0) dFob = 0.0
                if (dFob < State.totals.fuel) dFob = State.totals.fuel
                if (dFob > State.airplane.tank) dFob = State.airplane.tank

                if (State.settings.fob != dFob) {
                    Log.d("SettingsFragment", "new Takeoff Fuel")
                    State.settings.fob = dFob
                    bind.settingsInfoBox.visibility = View.GONE
                    change = true
                    refreshSummaryBox()
                }

            }
        }
        bind.settingFuel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSettings()
        }

        // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //
        // Spinner - Airplane
        bind.spinnerAirplane.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Log.d("SettingsFragment", "Airplane onItemSelected")

                if (Vars.appInit) {
                    Vars.appInit = false
                    c++
                }

                c++

                if (c < 3) {
                    Log.d("SettingsFragment", "c=$c")
                    return
                }

                if (position > 0) {
                    getAirplaneSettings(position - 1)
                } else {
                    getAirplaneSettings(-1)
                }

                Log.d("SettingsFragment", "new Airplane selected (c=$c)")
                change = true
                saveSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }
        }
    }

    override fun onStop() {
        Log.d("SettingsFragment", "onStop")
        super.onStop()
        if (change) {
            change = false
            Log.d("SettingsFragment", "onStop Save")
            NavLogUtils.calcNavlog()
            FileUtils.saveState()
        }
        restore = true
    }

    private fun saveSettings() {
        if (restore) return
        if (!change) return
        change = false

        Log.d("SettingsFragment", "saveForm")

        NavLogUtils.calcNavlog()
        FileUtils.saveState()
        bind.settingsInfoBox.visibility = View.GONE
        //restoreSettings()
    }

    private fun showSettingsError(msg: String) {
        bind.settingsInfoBox.text = msg
        bind.settingsInfoBox.visibility = View.VISIBLE
    }

    private fun setupSpinner(view: View) {
        // Spinner - Airplane
        Log.d("SettingsFragment", "setupSpinner")

        val planeList = ArrayList<String>()
        planeList.add("Select an airplane")
        for (i in State.airplaneList.indices) {
            val plane = State.airplaneList[i].reg + "  (" + State.airplaneList[i].type + ")"
            planeList.add(plane)
        }
        bind.spinnerAirplane.adapter = ArrayAdapter(view.context, R.layout.support_simple_spinner_dropdown_item, planeList)
    }

    private fun restoreSettings() {
        Log.d("SettingsFragment", "restoreSettings")
        restore = true

        val s = State.settings
        val a = State.airplane

        Log.d("planName", "planName=" + s.planName)

        bind.settingFlightPlanName.setText(s.planName)
        bind.settingFrom.setText(s.departure)
        bind.settingDestination.setText(s.destination)

        // Takeoff fuel
        val p = if (State.settings.fob < C.VOL_THRESHOLD) 1 else 0
        bind.settingFuel.setText(Utils.formatDouble(Convert.toUserUnitsVol(s.fob), p))
        bind.hintTakeoffFuel.hint = getString(R.string.txtTakeoffFuel) + " (" + Convert.getUnitsVol() + ")"

        // Airplane
        val id = getAirplaneListPosition(s.airplaneId)
        if (id > 0) {
            bind.spinnerAirplane.setSelection(id)
            bind.airplaneTas.text = Utils.formatDouble(Convert.toUserUnitsSpd(a.tas))
            bind.airplaneTasUnits.text = Convert.getUnitsSpd()
            bind.airplaneTank.text = Utils.formatDouble(Convert.toUserUnitsVol(a.tank))
            bind.airplaneTankUnits.text = Convert.getUnitsVol()
            bind.airplaneFph.text = Utils.formatDouble(Convert.toUserUnitsVol(a.fph), 1)
            bind.airplaneFphUnits.text = Convert.getUnitsVol()
            bind.settingsSelectAirplaneMsg.visibility = View.GONE
        } else {
            c++
            bind.airplaneTas.text = "-"
            bind.airplaneTasUnits.text = ""
            bind.airplaneTank.text = "-"
            bind.airplaneTankUnits.text = ""
            bind.airplaneFph.text = "-"
            bind.airplaneFphUnits.text = ""
            bind.settingsSelectAirplaneMsg.visibility = View.VISIBLE
        }

        // Wind conditions
        bind.settingWindDir.setText(Utils.formatDouble(s.windDir, 1))
        bind.settingWindSpd.setText(Utils.formatDouble(Convert.toUserUnitsSpd(s.windSpd), 1))
        bind.hintWindSpd.hint = getString(R.string.txtWindSpeed) + " (" + Convert.getUnitsSpd() + ")"

        refreshSummaryBox()

        restore = false
    }

    private fun refreshSummaryBox() {
        Log.d("SettingsFragment", "refreshSummaryBox")

        val s = State.settings
        val a = State.airplane
        val t = State.totals

        if (s.airplaneId != "" && a.fph > 0.0) {
            val txtTotDist = Utils.formatDouble(Convert.toUserUnitsDis(t.dist))
            val txtTotTime = TimeUtils.formatSecondsToTime(t.time)
            val txtTotFuel = Utils.formatDouble(Convert.toUserUnitsVol(t.fuel))

            val spareFuel = s.fob - t.fuel

            // Distance opposite to the wind
            val fData1 = Utils.flightCalculator(course = s.windDir, windDir = s.windDir, windSpd = s.windSpd, tas = a.tas, fob = spareFuel, fph = a.fph)

            // Distance with the wind
            val fData2 = Utils.flightCalculator(course = GPSUtils.normalizeBearing(s.windDir + 180.0), windDir = s.windDir, windSpd = s.windSpd, tas = a.tas, fob = spareFuel, fph = a.fph)

            val txtSpareFuel = Utils.formatDouble(Convert.toUserUnitsVol(spareFuel))
            val txtExtraTime = TimeUtils.formatSecondsToTime(fData1.time)

            val dist1 = Utils.formatDouble(Convert.toUserUnitsDis(fData1.dist))
            val dist2 = Utils.formatDouble(Convert.toUserUnitsDis(fData2.dist))
            val txtExtraDist = if (dist1 == dist2) dist1 else "$dist1-$dist2"

            // Display

            // Total distance
            bind.totsDist.text = txtTotDist
            bind.totsDistUnits.text = Convert.getUnitsDis()
            bind.totFuel.text = txtTotFuel
            bind.totFuelUnits.text = Convert.getUnitsVol()

            //  Extra fuel
            bind.spareFuel.text = txtSpareFuel
            bind.spareFuelUnits.text = Convert.getUnitsVol()

            // Extra distance
            bind.extraDistance.text = txtExtraDist
            bind.extraDistanceUnits.text = Convert.getUnitsDis()

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
        return v != null && v >= 0.0 && v < State.airplane.tas
    }

    private fun getAirplaneListPosition(id: String): Int {
        // Index 0 -> "Select an airplane"
        if (id == "") return 0
        for (i in State.airplaneList.indices) {
            if (State.airplaneList[i].id == id) return i + 1
        }
        return 0
    }

    private fun getAirplaneSettings(i: Int = -1) {
        //Log.d("SettingsFragment", "getAirplaneSettings")
        if (i < 0) {
            AirplaneUtils.resetAirplaneSettings()
        } else {
            AirplaneUtils.getAirplaneByID(State.airplaneList[i].id)
        }
    }
}

object SettingUtils {
    fun isAutoNextEnabled(): Boolean {
        return State.options.gpsAssist && State.options.autoNext
    }

    fun isMapFollow(): Boolean {
        return State.options.gpsAssist && State.settings.mapFollow
    }

    fun resetAllSettings() {
        State.settings = SettingsData(planId = Utils.generateStringId())
        AirplaneUtils.resetAirplaneSettings()
        FileUtils.loadOptions()
    }
}