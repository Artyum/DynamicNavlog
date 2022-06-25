package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentCalcDensityBinding
import kotlin.math.pow

class CalcDensityFragment : Fragment(R.layout.fragment_calc_density) {
    private var _binding: FragmentCalcDensityBinding? = null
    private val bind get() = _binding!!

    private var pressureUnits: Int = C.PRESSURE_INHG
    private var temperatureUnits: Int = C.TEMP_F
    private var elevationUnits: Int = C.ELEV_FT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcDensityBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        bind.radioTemperature.setOnCheckedChangeListener { _, checkedId ->
            temperatureUnits = when (checkedId) {
                R.id.radioF -> C.TEMP_F
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
        val inPressure = getDoubleOrNull(bind.edtDensityStation.text.toString())
        val pressure_inhg: Double
        val pressure_hpa: Double

        val inTemp = getDoubleOrNull(bind.edtDensityTemperature.text.toString())
        val temp_f: Double
        val temp_c: Double
        val temp_k: Double

        var inElev = getDoubleOrNull(bind.edtDensityElevation.text.toString())
        if (inElev == null) inElev = 0.0
        val elev_ft: Double
        val elev_m: Double

        if (inPressure != null && inTemp != null) {
            // Pressure units
            if (pressureUnits == C.PRESSURE_INHG) {
                pressure_inhg = inPressure
                pressure_hpa = inhg2hpa(pressure_inhg)
            } else {
                pressure_hpa = inPressure
                pressure_inhg = hpa2inhg(pressure_hpa)
            }

            // Temperature units
            if (temperatureUnits == C.TEMP_F) {
                temp_f = inTemp
                temp_c = f2c(temp_f)
            } else {
                temp_c = inTemp
                temp_f = c2f(temp_c)
            }
            temp_k = c2k(temp_c)

            // Elevation units
            if (elevationUnits == C.ELEV_FT) {
                elev_ft = inElev
                elev_m = ft2m(elev_ft)
            } else {
                elev_m = inElev
                elev_ft = m2ft(elev_m)
            }

//        println("pressure_inhg $pressure_inhg")
//        println("pressure_hpa $pressure_hpa")
//        println("temp_f $temp_f")
//        println("temp_c $temp_c")
//        println("temp_k $temp_k")
//        println("elev_ft $elev_ft")
//        println("elev_m $elev_m")

            val units = if (elevationUnits == C.ELEV_FT) " ft" else " m"

            // Pressure altitude
            var pressureAltitude = 145366.45 * (1.0 - (pressure_hpa / 1013.25).pow(0.190284))
            val pressureAltitude_ft = pressureAltitude
            var elevation = elev_ft + pressureAltitude
            if (elevationUnits == C.ELEV_M) {
                pressureAltitude = ft2m(pressureAltitude)
                elevation = ft2m(elevation)
            }
            var strPA = formatDouble(pressureAltitude, 1)
            var strElev = formatDouble(elevation, 1)
            if (pressureAltitude > 0.0) strPA = "+$strPA"
            bind.outPressureAltitude.setText("$strPA$units")
            if (inElev != 0.0) bind.outPressureAltitudeElev.setText("$strElev$units") else bind.outPressureAltitudeElev.setText("")

            // Density altitude in meters
            val psl = 1013.25
            val tsl = 288.15
            val ro = 0.0065
            val R = 8.3144598
            val g = 9.80665
            val M = 0.028964

            var densityAltitude = (tsl / ro) * (1.0 - ((pressure_hpa / psl) / (temp_k / tsl)).pow((((g * M) / (ro * R)) - 1.0).pow(-1.0)))
            elevation = elev_m + densityAltitude
            if (elevationUnits == C.ELEV_FT) {
                densityAltitude = m2ft(densityAltitude)
                elevation = m2ft(elevation)
            }
            var strDA = formatDouble(densityAltitude, 1)
            strElev = formatDouble(elevation, 1)
            if (densityAltitude > 0.0) strDA = "+$strDA"
            bind.outDensityAltitude.setText("$strDA$units")
            if (inElev != 0.0) bind.outDensityAltitudeElev.setText("$strElev$units") else bind.outDensityAltitudeElev.setText("")

            // Density altitude NWS in feets
            var densityAltitudeNWS = 145442.16 * (1.0 - (17.326 * (pressure_inhg / (459.67 + temp_f))).pow(0.235))
            elevation = elev_ft + densityAltitudeNWS
            if (elevationUnits == C.ELEV_M) {
                densityAltitudeNWS = ft2m(densityAltitudeNWS)
                elevation = ft2m(elevation)
            }
            strDA = formatDouble(densityAltitudeNWS, 1)
            strElev = formatDouble(elevation, 1)
            if (densityAltitudeNWS > 0.0) strDA = "+$strDA"
            bind.outDensityAltitudeNws.setText("$strDA$units")
            if (inElev != 0.0) bind.outDensityAltitudeNwsElev.setText("$strElev$units") else bind.outDensityAltitudeNwsElev.setText("")

            // Density altitude aproximation in feet
            var densityAltitudeAprox = pressureAltitude_ft + 118.8 * (temp_c + pressureAltitude_ft / 500.0 - 15.0)
            elevation = elev_ft + densityAltitudeAprox
            if (elevationUnits == C.ELEV_M) {
                densityAltitudeAprox = ft2m(densityAltitudeAprox)
                elevation = ft2m(elevation)
            }
            strDA = formatDouble(densityAltitudeAprox, 1)
            strElev = formatDouble(elevation, 1)
            if (densityAltitudeAprox > 0.0) strDA = "+$strDA"
            bind.outDensityAltitudeAprox.setText("$strDA$units")
            if (inElev != 0.0) bind.outDensityAltitudeAproxElev.setText("$strElev$units") else bind.outDensityAltitudeAproxElev.setText("")
        } else {
            Toast.makeText(this.context, getString(R.string.txtInvalidDensityParams), Toast.LENGTH_SHORT).show()
        }
    }

    private fun allClear() {
        bind.edtDensityStation.setText("")
        bind.edtDensityTemperature.setText("")
        bind.edtDensityElevation.setText("")
        bind.outPressureAltitude.setText("")
        bind.outPressureAltitudeElev.setText("")
        bind.outDensityAltitude.setText("")
        bind.outDensityAltitudeElev.setText("")
        bind.outDensityAltitudeNws.setText("")
        bind.outDensityAltitudeNwsElev.setText("")
        bind.outDensityAltitudeAprox.setText("")
        bind.outDensityAltitudeAproxElev.setText("")

        bind.radioINHG.isChecked = true
        bind.radioF.isChecked = true
        bind.radioFT.isChecked = true

        pressureUnits = C.PRESSURE_INHG
        temperatureUnits = C.TEMP_F
        elevationUnits = C.ELEV_FT
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
