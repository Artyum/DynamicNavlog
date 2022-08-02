package com.artyum.dynamicnavlog

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.artyum.dynamicnavlog.databinding.FragmentHomeBinding
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val bind get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.homeLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).displayButtons()

        // GPS indicator
        if (!options.gpsAssist) bind.txtTrackAngleIndicatorBox.visibility = View.GONE

        // Display units
        displayUnits()

        // Draw Wind Circle
        drawHomeWinStar()

        // Start home thread
        lifecycleScope.launch { updateHomeUIThread() }
    }

    private fun displayUnits() {
        bind.txtHomeDistUnits.text = getUnitsDis()
        bind.txtHomeGsUnits.text = getUnitsSpd()
    }

    private fun drawHomeWinStar() {
        val sr = if (airplane.tas == 0.0) 1.0 else airplane.tas

        if (getFlightStage() == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            // Flight in progress
            val i = getNavlogCurrentItemId()
            generateWindCircle(
                bind.imgHomeView, resources,
                course = navlogList[i].magneticTrack!!,
                windDir = settings.windDir,
                hdg = navlogList[i].hdg!!,
                speedRatio = navlogList[i].gs!! / sr
            )
        } else if (isNavlogReady() && timers.takeoff == null) {
            // Stage OffBlock
            val first = getNavlogFirstActiveItemId()
            generateWindCircle(
                bind.imgHomeView, resources,
                course = navlogList[first].magneticTrack!!,
                windDir = settings.windDir,
                hdg = navlogList[first].hdg!!,
                speedRatio = navlogList[first].gs!! / sr
            )
        } else {
            // Plan not ready
            generateWindCircle(
                bind.imgHomeView, resources,
                course = 0.0,
                windDir = 180.0,
                hdg = 0.0,
                speedRatio = 1.0
            )
        }
    }

    private suspend fun updateHomeUIThread() {
        var prevTime = 0L

        while (true) {
            val a = activity as? MainActivity ?: break
            val b = _binding ?: break

            // Loop every 1 sec
            val curTime = System.currentTimeMillis() / 1000L
            if (curTime != prevTime || globalRefresh) {
                prevTime = curTime
                globalRefresh = false
                //println("Home Thread: $coroutineContext")

                val h = HomeItem()

                // NAVIGATION DISPLAY
                setItemsColor(h.stage)

                // WPT
                safeSetText(a, b.txtHomeDest, h.getWpt())

                // HDG
                val (hdg, hdgNext, hdgDct) = h.getHdg()
                safeSetText(a, b.txtHomeHdg, hdg)
                safeSetText(a, b.txtHomeHdgNext, hdgNext)
                safeSetText(a, b.txtDctHdg, hdgDct)

                // ETE
                val retEte = h.getEte()
                safeSetText(a, b.txtHomeEte, retEte.ete)
                safeSetText(a, b.txtHomeStopwatch, retEte.sw)
                if (h.stage != C.STAGE_1_BEFORE_ENGINE_START) {
                    if (!retEte.sign) {
                        safeVisibility(a, b.txtHomeEtePlus, View.GONE)
                    } else {
                        safeVisibility(a, b.txtHomeEtePlus, View.VISIBLE)
                        safeTextColor(a, b.txtHomeEte, R.color.black)
                    }
                }

                // GS
                val (gs, gsDiff) = h.getGs()
                safeSetText(a, b.txtHomeGs, gs)
                safeSetText(a, b.txtGsGpsDiff, gsDiff)
                setGpsTag(a, b, h)

                // DTK
                val (mt, mtDct, angle) = h.getDtk()
                safeSetText(a, b.txtHomeDtk, mt)
                safeSetText(a, b.txtDctMt, mtDct)
                safeSetText(a, b.txtTrackAngle, angle)
                if (h.gps.isValid) {
                    val dab = h.getDtkAngleBar()
                    safeVisibility(a, b.txtTrackAngleIndicatorBox, View.VISIBLE)
                    safeSetProgressBar(a, b.txtTrackAngleIndicatorLeft, dab.left)
                    safeSetProgressBar(a, b.txtTrackAngleIndicatorRight, dab.right)
                    if (dab.hit) {
                        safeSetProgressBarColor(a, b.txtTrackAngleIndicatorLeft, R.color.colorPrimary)
                        safeSetProgressBarColor2(a, b.txtTrackAngleIndicatorRight, R.color.colorPrimary)
                    } else {
                        safeSetProgressBarColor(a, b.txtTrackAngleIndicatorLeft, R.color.red)
                        safeSetProgressBarColor2(a, b.txtTrackAngleIndicatorRight, R.color.red)
                    }
                } else {
                    safeVisibility(a, b.txtTrackAngleIndicatorBox, View.GONE)
                }

                // DIST
                val retDist = h.getDist()
                safeSetText(a, b.txtHomeDist, retDist.dist)
                safeSetText(a, b.txtHomeDistPct, retDist.pct)
                safeSetText(a, b.txtHomeDistFromPrevWpt, retDist.distTravelled)
                if (h.stage != C.STAGE_1_BEFORE_ENGINE_START) {
                    if (!retDist.sign) {
                        safeVisibility(a, b.txtHomeDistPlus, View.GONE)
                    } else {
                        safeTextColor(a, b.txtHomeDist, R.color.black)
                        safeVisibility(a, b.txtHomeDistPlus, View.VISIBLE)
                    }
                }
                safeSetProgressBar(a, b.txtHomeDistPctBar, retDist.progress)
                if (retDist.overflow) safeSetProgressBarColor(a, b.txtHomeDistPctBar, R.color.red) else safeSetProgressBarColor(a, b.txtHomeDistPctBar, R.color.lightBlue)

                // REMARKS
                val remarks = h.getRemarks()
                if (remarks == "") safeVisibility(a, b.boxHomeRemarks, View.GONE)
                else {
                    safeSetText(a, b.txtHomeRemarks, remarks)
                    safeVisibility(a, b.boxHomeRemarks, View.VISIBLE)
                }

                // FLIGHT COMPUTER

                // ETA
                val (eta, diff) = h.getEta()
                safeSetText(a, b.txtHomeEta, eta)
                safeSetText(a, b.txtHomeTimeDeviation, diff)

                // TIME TO LAND
                val retTTL = h.getTimeToLand()
                safeSetText(a, b.txtHomeTimeToLand, retTTL.ttl)
                safeSetText(a, b.txtHomeTimeToLandPct, retTTL.pct)
                safeSetProgressBar(a, b.txtHomeTimeToLandBar, retTTL.progress)

                // FUEL TO LAND
                val ft = h.getFuel()
                safeSetText(a, b.txtHomeFuelToLand, ft.ftl)
                safeSetText(a, b.txtHomeFuelToLandNotice, ft.ftlmark)
                safeSetText(a, b.txtHomeEngineTime, ft.engineTime)
                safeSetText(a, b.txtHomeTimeRemaining, ft.fuelTime)
                safeSetText(a, b.txtHomeFuelRemaining, ft.fuelRemaining)
                safeSetText(a, b.txtHomeFuelRemainingPct, ft.fuelPct)
                safeSetProgressBar(a, b.txtHomeFuelPctBar, ft.fuelPctBar)
            }

            delay(50)
        }
    }

    private fun setItemsColor(stage: Int) {
        val color = if (stage != C.STAGE_3_FLIGHT_IN_PROGRESS) R.color.lightBlack else R.color.magenta
        bind.txtHomeDest.setTextColor(ContextCompat.getColor(this.requireContext(), color))
        bind.txtHomeHdg.setTextColor(ContextCompat.getColor(this.requireContext(), color))
        bind.txtHomeEte.setTextColor(ContextCompat.getColor(this.requireContext(), color))
        bind.txtHomeGs.setTextColor(ContextCompat.getColor(this.requireContext(), color))
        bind.txtHomeDtk.setTextColor(ContextCompat.getColor(this.requireContext(), color))
        bind.txtHomeDist.setTextColor(ContextCompat.getColor(this.requireContext(), color))
    }

    private fun setGpsTag(a: MainActivity, b: FragmentHomeBinding, h: HomeItem) {
        if (options.gpsAssist) {
            safeVisibility(a, b.txtGsGpsTag, View.VISIBLE)
            if (h.gps.isValid) {
                a.runOnUiThread { b.txtGsGpsTag.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG.dec() }
                safeTextColor(a, b.txtGsGpsTag, R.color.green)
            } else {
                a.runOnUiThread { b.txtGsGpsTag.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG }
                safeTextColor(a, b.txtGsGpsTag, R.color.red)
            }
        } else safeVisibility(a, b.txtGsGpsTag, View.GONE)
    }

// // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

    private fun safeSetText(a: MainActivity, item: TextView, str: String) {
        a.runOnUiThread { item.text = str }
    }

    private fun safeVisibility(a: MainActivity, item: TextView, visibility: Int) {
        a.runOnUiThread { item.visibility = visibility }
    }

    private fun safeVisibility(a: MainActivity, item: LinearLayout, visibility: Int) {
        a.runOnUiThread { item.visibility = visibility }
    }

    private fun safeVisibility(a: MainActivity, item: ConstraintLayout, visibility: Int) {
        a.runOnUiThread { item.visibility = visibility }
    }

    private fun safeTextColor(a: MainActivity, item: TextView, color: Int) {
        a.runOnUiThread { item.setTextColor(ContextCompat.getColor(item.context, color)) }
    }

    private fun safeSetProgressBar(a: MainActivity, item: ProgressBar, pct: Int) {
        a.runOnUiThread { item.progress = pct }
    }

    private fun safeSetProgressBarColor(a: MainActivity, item: LinearProgressIndicator, color: Int) {
        a.runOnUiThread { item.trackColor = ContextCompat.getColor(item.context, color) }
    }

    private fun safeSetProgressBarColor2(a: MainActivity, item: LinearProgressIndicator, color: Int) {
        a.runOnUiThread { item.setIndicatorColor(ContextCompat.getColor(item.context, color)) }
    }
}
