package com.artyum.dynamicnavlog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentCalcTimedistBinding
import kotlin.math.roundToLong

class CalcTimeDistFragment : Fragment(R.layout.fragment_calc_timedist) {
    private var _binding: FragmentCalcTimedistBinding? = null
    private val bind get() = _binding!!
    private val unitsDistanceList = ArrayList<String>()
    private val unitsSpeedList = ArrayList<String>()
    var unitsDistance: Int = 0
    var unitsSpeed: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalcTimedistBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.timeDistLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        val con = view.context

        unitsDistanceList
        unitsDistanceList.add("nm")   // 0
        unitsDistanceList.add("sm")   // 1
        unitsDistanceList.add("km")   // 2
        unitsDistanceList.add("m")    // 3
        unitsDistanceList.add("ft")   // 4

        unitsSpeedList.clear()
        unitsSpeedList.add("kt")      // 0
        unitsSpeedList.add("mph")     // 1
        unitsSpeedList.add("kph")     // 2
        unitsSpeedList.add("m/s")     // 3
        unitsSpeedList.add("ft/min")  // 4

        bind.spinnerDistance.adapter = ArrayAdapter(con, R.layout.support_simple_spinner_dropdown_item, unitsDistanceList)
        bind.spinnerDistance.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                unitsDistance = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }

        }

        bind.spinnerSpeed.adapter = ArrayAdapter(con, R.layout.support_simple_spinner_dropdown_item, unitsSpeedList)
        bind.spinnerSpeed.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                unitsSpeed = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                return
            }
        }

        bind.btnCalculate.setOnClickListener {
            it.hideKeyboard()
            var dist = Utils.getDoubleOrNull(bind.edtDistance.text.toString())
            var speed = Utils.getDoubleOrNull(bind.edtSpeed.text.toString())
            var time: Double?
            val p = 2

            val txtTime = bind.edtTime.text.toString()
            time = TimeUtils.strTime2Sec(txtTime)
            bind.edtTime.setText(TimeUtils.formatSecondsToTime(time?.roundToLong(), true))

            if (dist != null) {
                if (unitsDistance == 1) dist = Units.sm2nm(dist)
                if (unitsDistance == 2) dist = Units.km2nm(dist)
                if (unitsDistance == 3) dist = Units.m2nm(dist)
                if (unitsDistance == 4) dist = Units.ft2nm(dist)
            }

            if (speed != null) {
                if (unitsSpeed == 1) speed = Units.mph2kt(speed)
                if (unitsSpeed == 2) speed = Units.kph2kt(speed)
                if (unitsSpeed == 3) speed = Units.mps2kt(speed)
                if (unitsSpeed == 4) speed = Units.fpm2kt(speed)
            }

            //distance = speed * time
            if (dist == null && speed != null && time != null) {
                dist = speed * (time / 3600.0)
                if (unitsDistance == 1) dist = Units.nm2sm(dist)
                if (unitsDistance == 2) dist = Units.nm2km(dist)
                if (unitsDistance == 3) dist = Units.nm2m(dist)
                if (unitsDistance == 4) dist = Units.nm2ft(dist)
                bind.edtDistance.setText(Utils.formatDouble(dist, p))
            }

            //speed = distance / time
            if (dist != null && speed == null && time != null && time > 0) {
                speed = dist / (time / 3600.0)
                if (unitsSpeed == 1) speed = Units.kt2mph(speed)
                if (unitsSpeed == 2) speed = Units.kt2kph(speed)
                if (unitsSpeed == 3) speed = Units.kt2mps(speed)
                if (unitsSpeed == 4) speed = Units.kt2fpm(speed)
                bind.edtSpeed.setText(Utils.formatDouble(speed, p))
            }

            //time = distance / speed
            if (dist != null && speed != null && time == null && speed > 0.0) {
                time = dist / speed * 3600.0
                bind.edtTime.setText(TimeUtils.formatSecondsToTime(time.roundToLong(), true))
            }
        }

        bind.btnClear.setOnClickListener {
            it.hideKeyboard()
            bind.edtDistance.setText("")
            bind.edtSpeed.setText("")
            bind.edtTime.setText("")
            bind.spinnerDistance.setSelection(0)
            bind.spinnerSpeed.setSelection(0)
            unitsDistance = 0
            unitsSpeed = 0
        }
    }

    private fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }
}
