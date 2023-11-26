package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentCalcDensity2Binding
import kotlin.math.pow

class CalcDensity2Fragment : Fragment(R.layout.fragment_calc_density2) {
    private var _binding: FragmentCalcDensity2Binding? = null
    private val bind get() = _binding!!

    private var pressureUnits: Int = C.PRESSURE_INHG
    private var airTempUnits: Int = C.TEMP_F
    private var dewpointUnits: Int = C.TEMP_F
    private var elevationUnits: Int = C.ELEV_FT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcDensity2Binding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.density2Layout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            densityCalculate()
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            allClear()
        }

        bind.radioPressure.setOnCheckedChangeListener { _, checkedId ->
            pressureUnits = when (checkedId) {
                R.id.radioINHG -> C.PRESSURE_INHG
                else -> C.PRESSURE_HPA
            }
        }

        bind.radioAirTemperature.setOnCheckedChangeListener { _, checkedId ->
            airTempUnits = when (checkedId) {
                R.id.radioAirF -> C.TEMP_F
                else -> C.TEMP_C
            }
        }

        bind.radioDewpoint.setOnCheckedChangeListener { _, checkedId ->
            dewpointUnits = when (checkedId) {
                R.id.radioAirF -> C.TEMP_F
                else -> C.TEMP_C
            }
        }

        bind.radioElevation.setOnCheckedChangeListener { _, checkedId ->
            elevationUnits = when (checkedId) {
                R.id.radioFT -> C.ELEV_FT
                else -> C.ELEV_M
            }
        }
    }

    private fun densityCalculate() {
        val inAirTemp = Utils.getDoubleOrNull(bind.edtDensityAirTemperature.text.toString())
        val air_f: Double
        val air_c: Double
        val air_k: Double

        val inDewpoint = Utils.getDoubleOrNull(bind.edtDensityDewpoint.text.toString())
        val dewpoint_f: Double
        val dewpoint_c: Double
        //var dewpoint_k: Double

        val inPressure = Utils.getDoubleOrNull(bind.edtDensityAltimeter.text.toString())
        val pressure_inhg: Double
        val pressure_hpa: Double

        var inElev = Utils.getDoubleOrNull(bind.edtDensityElevation.text.toString())
        if (inElev == null) {
            inElev = 0.0
            bind.edtDensityElevation.setText("0")
        }
        val elev_ft: Double
        val elev_m: Double

        if (inAirTemp != null && inDewpoint != null && inPressure != null) {
            // Air temperature units
            if (airTempUnits == C.TEMP_F) {
                air_f = inAirTemp
                air_c = Convert.f2c(air_f)
            } else {
                air_c = inAirTemp
                //air_f = c2f(air_c)
            }
            air_k = Convert.c2k(air_c)

            // Dewpoint units
            if (dewpointUnits == C.TEMP_F) {
                dewpoint_f = inDewpoint
                dewpoint_c = Convert.f2c(dewpoint_f)
            } else {
                dewpoint_c = inDewpoint
                //dewpoint_f = c2f(dewpoint_c)
            }
            //dewpoint_k = c2k(dewpoint_c)

            // Pressure units
            if (pressureUnits == C.PRESSURE_INHG) {
                pressure_inhg = inPressure
                pressure_hpa = Convert.inhg2hpa(pressure_inhg)
            } else {
                pressure_hpa = inPressure
                //pressure_inhg = hpa2inhg(pressure_hpa)
            }

            // Elevation units
            if (elevationUnits == C.ELEV_FT) {
                elev_ft = inElev
                elev_m = Convert.ft2m(elev_ft)
            } else {
                elev_m = inElev
                //elev_ft = m2ft(elev_m)
            }

            // Validation
            var chk = true
            if (air_c <= -273.15 || dewpoint_c <= -273.15) {
                Toast.makeText(this.context, getString(R.string.txtInvalidTemperature), Toast.LENGTH_SHORT).show()
                chk = false
            }
            if (pressure_hpa <= 800.0 || pressure_hpa >= 1200.0) {
                Toast.makeText(this.context, getString(R.string.txtInvalidPressure), Toast.LENGTH_SHORT).show()
                chk = false
            }
            if (air_c < dewpoint_c) {
                Toast.makeText(this.context, getString(R.string.txtInvalidTemperature2), Toast.LENGTH_SHORT).show()
                chk = false
            }
            if (elev_m < -500.0 || elev_m > 10000.0) {
                Toast.makeText(this.context, getString(R.string.txtInvalidElevation), Toast.LENGTH_SHORT).show()
                chk = false
            }

            if (chk) {
                //Saturated vapor pressure Es
                val es = 6.11 * 10.0.pow((7.5 * air_c) / (237.3 + air_c))

                // Actual vapor pressure E
                val e = 6.11 * 10.0.pow((7.5 * dewpoint_c) / (237.3 + dewpoint_c))

                // Relative humidity Rh [%]
                val rh = e / es

                // Air pressure P [HPa]
                val p = ((pressure_hpa.pow(0.190263)) - (8.417286 * 10.0.pow(-5)) * elev_m).pow(1.0 / 0.190263)

                // Water vapour pressure Pv [HPa]
                val pv = rh * 6.1078 * 10.0.pow(7.5 * air_c / (air_c + 237.3))

                // Absolute pressure Pd [HPa]
                val pd = p - pv

                // Air density ad [kg/m3]
                val ad = (pd * 100.0 / (287.058 * air_k)) + (pv * 100.0 / (461.495 * air_k))

                // Relative air density rd [%]
                val rd = ad / 1.225

                // Density altitude H [km]
                val h = 44.3308 - 42.2665 * (ad.pow(0.234696))

                // Output
                bind.outRelativeHumidity.text = Utils.formatDouble(rh * 100, 1)
                bind.outRelativeHumidityUnits.text = "%"

                bind.outAirDensity.text = Utils.formatDouble(ad, 3)
                bind.outAirDensityUnits.text = "kg/m³"

                bind.outRelativeDensity.text = Utils.formatDouble(rd * 100, 1)
                bind.outRelativeDensityUnits.text = "%"

                if (pressureUnits == C.PRESSURE_INHG) {
                    bind.outAbsolutePressure.text = Utils.formatDouble(Convert.hpa2inhg(pd), 2)
                    bind.outAbsolutePressureUnits.text = "inHG"
                } else {
                    bind.outAbsolutePressure.text = Utils.formatDouble(pd, 2)
                    bind.outAbsolutePressureUnits.text = "HPa"
                }

                if (elevationUnits == C.ELEV_FT) {
                    bind.outDensityAltitude.text = Utils.formatDouble(Convert.km2ft(h), 2)
                    bind.outDensityAltitudeUnits.text = "ft"
                } else {
                    bind.outDensityAltitude.text = Utils.formatDouble(Convert.km2m(h), 2)
                    bind.outDensityAltitudeUnits.text = "m"
                }
            } else {
                clearOutput()
            }

        } else {
            Toast.makeText(this.context, getString(R.string.txtInvalidDensityParams2), Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearOutput() {
        bind.outAirDensity.text = ""
        bind.outAirDensityUnits.text = ""
        bind.outRelativeHumidity.text = ""
        bind.outRelativeHumidityUnits.text = ""
        bind.outRelativeDensity.text = ""
        bind.outRelativeDensityUnits.text = ""
        bind.outAbsolutePressure.text = ""
        bind.outAbsolutePressureUnits.text = ""
        bind.outDensityAltitude.text = ""
        bind.outDensityAltitudeUnits.text = ""
    }

    private fun allClear() {
        clearOutput()
        bind.edtDensityAirTemperature.setText("")
        bind.edtDensityDewpoint.setText("")
        bind.edtDensityAltimeter.setText("")
        bind.edtDensityElevation.setText("")

        bind.radioAirF.isChecked = true
        bind.radioDewF.isChecked = true
        bind.radioINHG.isChecked = true
        bind.radioFT.isChecked = true

        airTempUnits = C.TEMP_F
        dewpointUnits = C.TEMP_F
        pressureUnits = C.PRESSURE_INHG
        elevationUnits = C.ELEV_FT
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
