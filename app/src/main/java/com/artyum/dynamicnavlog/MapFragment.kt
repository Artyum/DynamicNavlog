package com.artyum.dynamicnavlog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import com.artyum.dynamicnavlog.databinding.FragmentMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class MapFragment : Fragment(R.layout.fragment_map) {
    private val TAG = "MapFragment"
    private var _binding: FragmentMapBinding? = null
    private val bind get() = _binding!!

    private lateinit var map: GoogleMap
    private var mapReady: Boolean = false

    private val trackMarkers = ArrayList<Marker>()
    private val trackLines = ArrayList<Polyline>()
    private val trackCircles = ArrayList<Circle>()
    private var traceLine: Polyline? = null

    @Volatile
    var timerTrace = 0

    @Volatile
    var timerFollow = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.mapLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).displayButtons()

        val con = (activity as MainActivity).applicationContext
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment

        if (ActivityCompat.checkSelfPermission(con, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((activity as MainActivity), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), C.LOCATION_PERMISSION_REQ_CODE)
        } else {
            mapFragment.getMapAsync {
                map = it

                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isCompassEnabled = true
                map.mapType = settings.mapType

                // Click on map listener
                map.setOnMapClickListener { pos: LatLng ->
                    if (settings.takeoffCoords == null) setTakeoffPoint(pos)
                    else addWaypoint(pos)
                }

                // Drag marker listener
                map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(p0: Marker) {}
                    override fun onMarkerDrag(p0: Marker) {}
                    override fun onMarkerDragEnd(m: Marker) {
                        val i: Int = m.tag as Int
                        if (!isFlightInProgress() || (isFlightInProgress() && i >= getNavlogCurrentItemId())) {
                            if (i < 0) settings.takeoffCoords = m.position else navlogList[i].coords = m.position
                            calcNavlog()
                            saveState()
                        }
                        drawFlightPlan()
                        refreshBottomBar()
                    }
                })

                // Click on Marker
                map.setOnMarkerClickListener { p0 ->
                    val i: Int = p0.tag as Int
                    if (i >= 0) {
                        val dialog = NavlogDialogFragment(i)
                        dialog.show(parentFragmentManager, "NavlogDialogFragment")
                    }
                    true
                }

                // Map drag disables Camera Follow
                map.setOnCameraMoveStartedListener { reason: Int ->
                    if (settings.mapFollow && reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        settings.mapFollow = false
                        bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
                    }
                }

                mapReady = true

                // Draw flight plan
                drawFlightPlan()

                // Zoom to track
                zoomToTrack()

                // Draw trace
                drawTrace()

                refreshDisplay = true
            }

            // Start home thread
            CoroutineScope(CoroutineName("map")).launch { updateMapNavUIThread() }
        }

        setFragmentResultListener("requestKey") { _, _ ->
            saveState()
            drawFlightPlan()
        }

        // Button Follow
        bind.btnFollowToggle.setOnClickListener {
            if (settings.mapFollow) {
                settings.mapFollow = false
                bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
            } else {
                settings.mapFollow = true
                bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_lock)
                timerFollow = 0
            }
            saveState()
        }

        // Button Map type
        bind.btnMapType.setOnClickListener {
            if (mapReady) {
                when (map.mapType) {
                    GoogleMap.MAP_TYPE_NORMAL -> {
                        map.mapType = GoogleMap.MAP_TYPE_HYBRID
                        Toast.makeText(view.context, getString(R.string.txtMapTypeHybrid), Toast.LENGTH_SHORT).show()
                    }
                    GoogleMap.MAP_TYPE_HYBRID -> {
                        map.mapType = GoogleMap.MAP_TYPE_TERRAIN
                        Toast.makeText(view.context, getString(R.string.txtMapTypeTerrain), Toast.LENGTH_SHORT).show()
                    }
                    GoogleMap.MAP_TYPE_TERRAIN -> {
                        map.mapType = GoogleMap.MAP_TYPE_NORMAL
                        Toast.makeText(view.context, getString(R.string.txtMapTypeNormal), Toast.LENGTH_SHORT).show()
                    }
                }
                settings.mapType = map.mapType
            }
            saveState()
        }

        // Follow map mode
        if (settings.gpsAssist) {
            if (settings.mapFollow && settings.gpsAssist) bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_lock)
            else bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
        } else {
            bind.btnFollowToggle.visibility = View.GONE
        }

        // Refresh bottom summary bar
        refreshBottomBar()

        // Display plan units
        bind.txtNavGs.text = getString(R.string.txtGs) + " (" + getUnitsSpd() + ")"
        bind.txtNavDist.text = getString(R.string.txtDist) + " (" + getUnitsDis() + ")"

        // Run map update thread
        lifecycleScope.launch { updateMapThread() }
    }

    private fun drawFlightPlan() {
        if (!mapReady) return
        //Log.d(TAG, "drawFlightPlan")

        if (settings.takeoffCoords != null) {
            trackMarkers.forEach { it.remove() }
            trackMarkers.clear()
            trackLines.forEach { it.remove() }
            trackLines.clear()
            trackCircles.forEach { it.remove() }
            trackCircles.clear()

            // Departure point
            if (getFlightStage() < C.STAGE_3_FLIGHT_IN_PROGRESS) addMarker(p = settings.takeoffCoords!!, title = C.DEPARTURE_MARKER_TITLE, tag = -1, hue = BitmapDescriptorFactory.HUE_GREEN)

            // Track
            val item = getNavlogCurrentItemId()
            val last = getNavlogLastActiveItemId()

            for (i in navlogList.indices) {
                if (isNavlogItemGpsReady(i)) {
                    if (navlogList[i].active) {
                        val prev = getPrevCoords(i)

                        // Markers
                        var hue = BitmapDescriptorFactory.HUE_RED                      // Default
                        if (i == last) hue = BitmapDescriptorFactory.HUE_ORANGE        // Last
                        if (isFlightInProgress()) {
                            if (i == item) hue = BitmapDescriptorFactory.HUE_MAGENTA   // Current
                            if (i < item) hue = BitmapDescriptorFactory.HUE_AZURE      // Passed
                        }
                        if (i >= item) addMarker(p = navlogList[i].coords!!, title = navlogList[i].dest, tag = i, hue = hue)

                        // Auto-next circle
                        if (isAutoNextEnabled() && i >= item) {
                            if (isFlightInProgress()) {
                                if (i == item) {
                                    if (isInsideCircle) addCircle(navlogList[i].coords!!, R.color.greenTransparent)
                                    else addCircle(navlogList[i].coords!!)
                                }
                            } else addCircle(navlogList[i].coords!!)
                        }

                        // Track color
                        var color: Int = R.color.trackdefault   // Default
                        if (isFlightInProgress()) {
                            if (i < item) color = R.color.trackpassed   // Passed track
                            else if (i == item) color = R.color.trackcurrent      // Current track
                        }

                        // Track line
                        if (prev != null) addLine(prev, navlogList[i].coords!!, color, C.TRACK_WIDTH)
                    } else {
                        // Inactive markers
                        if (i >= item) addMarker(p = navlogList[i].coords!!, title = navlogList[i].dest, tag = i, hue = BitmapDescriptorFactory.HUE_VIOLET)

                        // Inactive track line
                        if (i == 0) {
                            addLine(settings.takeoffCoords!!, navlogList[i].coords!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH)
                            if (navlogList.size > 1 && navlogList[1].active) addLine(navlogList[0].coords!!, navlogList[i + 1].coords!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH)
                        } else if (i > 0 && i < navlogList.size) {
                            addLine(navlogList[i - 1].coords!!, navlogList[i].coords!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH)
                            if (i < navlogList.size - 1 && navlogList[i + 1].active) {
                                addLine(navlogList[i].coords!!, navlogList[i + 1].coords!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH)
                            }
                        }
                    }
                }
            }

            // Show not "gps-ready" waypoints
            var cnt = 0  // Count points
            for (i in navlogList.indices) if (navlogList[i].coords == null) cnt += 1
            if (cnt > 0) {
                // Draw
                var angle = 0.0
                for (i in navlogList.indices) {
                    if (navlogList[i].coords == null) {
                        val point = calcDestinationCoords(settings.takeoffCoords!!, angle, nm2m(10.0))
                        angle += 360.0 / cnt.toDouble()
                        addMarker(p = point, title = navlogList[i].dest, tag = i, hue = BitmapDescriptorFactory.HUE_YELLOW)
                    }
                }
            }
        }
    }

    private fun drawTrace() {
        if (!isDisplayFlightTrace()) return
        if (!mapReady || tracePointsList.size == 0) return
        Log.d(TAG, "drawTrace")

        if (traceLine != null) traceLine!!.remove()

        val opt = PolylineOptions()
            .clickable(false)
            .color(ContextCompat.getColor(this.requireContext(), R.color.traceLine))
            .geodesic(true)
            .pattern(listOf(Dash(15f), Gap(15f)))
            .width(8f)

        for (i in tracePointsList.indices) opt.add(tracePointsList[i])
        traceLine = map.addPolyline(opt)
    }

    private fun zoomToAll() {
        if (!mapReady) return

        // Zoom to all waypoints and takeoff point
        var chkTO = false
        var chkItems = false
        val builder = LatLngBounds.Builder()

        if (settings.takeoffCoords != null) {
            builder.include(settings.takeoffCoords!!)
            chkTO = true
        }
        for (i in navlogList.indices) {
            if (isNavlogItemGpsReady(i)) {
                builder.include(navlogList[i].coords!!)
                chkItems = true
            }
        }

        if (chkTO || chkItems) {
            val bounds = builder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130))

            // Zoom only to TakeOff point
            val zoom: Float = if (chkTO && !chkItems) 9f else map.cameraPosition.zoom

            val cameraPosition = CameraPosition.Builder()
                .target(bounds.center)
                .zoom(zoom)
                .bearing(0f)
                .tilt(map.cameraPosition.tilt)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    private fun zoomToTrack() {
        if (!mapReady) return

        // Get current GPS
        var gps: GpsData
        runBlocking { gpsMutex.withLock { gps = gpsData } }

        val stage = getFlightStage()

        if (stage == C.STAGE_1_BEFORE_ENGINE_START || stage == C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            if (settings.takeoffCoords == null && navlogList.size == 0) {
                // New flight plan with no points -> Zoom to position
                if (gps.isValid) {
                    val cameraPosition = CameraPosition.Builder()
                        .target(gps.coords!!)
                        .zoom(10f)
                        .bearing(0f)
                        .tilt(0f)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                } else zoomToAll()
            } else zoomToAll()
        } else if (stage == C.STAGE_2_ENGINE_RUNNING || stage == C.STAGE_4_AFTER_LANDING) {
            if (gps.isValid) {
                // Zoom to current gps position
                val cameraPosition = CameraPosition.Builder()
                    .target(gps.coords!!)
                    .zoom(15f)
                    .bearing(0f)
                    .tilt(map.cameraPosition.tilt)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else zoomToAll()
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            // Zoom to prev and current waypoint
            val item = getNavlogCurrentItemId()
            val zoom: Float
            val target: LatLng
            val bearing = if (settings.mapOrientation != C.MAP_ORIENTATION_NORTH && navlogList[item].trueTrack != null) navlogList[item].trueTrack!!.toFloat() else 0f

            if (isMapFollow() && gps.isValid) {
                // Zoom to GPS pos
                zoom = 12f
                target = gps.coords!!
            } else {
                val builder = LatLngBounds.Builder()

                // Include current coords
                val cur = navlogList[item].coords
                if (cur != null) builder.include(cur)

                // Include prev coords
                val prev = getPrevCoords(item)
                if (prev != null) builder.include(prev)

                val bounds = builder.build()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))
                zoom = map.cameraPosition.zoom
                target = bounds.center
            }

            val cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .bearing(bearing)
                .tilt(map.cameraPosition.tilt)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    private fun followPosition() {
        if (!mapReady) return

        var gps: GpsData
        runBlocking { gpsMutex.withLock { gps = gpsData } }

        if (gps.isValid) {
            val bearing = if (settings.mapOrientation == C.MAP_ORIENTATION_BEARING && gps.bearing != null) gps.bearing!!
            else map.cameraPosition.bearing

            val cameraPosition = CameraPosition.Builder()
                .target(gps.coords!!)
                .zoom(map.cameraPosition.zoom)
                .bearing(bearing)
                .tilt(map.cameraPosition.tilt)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 450, null)
        }
    }

    private fun addMarker(p: LatLng, title: String, tag: Int, hue: Float) {
        val m = map.addMarker(
            MarkerOptions()
                .position(p)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
                .title(title)
        )
        if (m != null) {
            m.tag = tag
            trackMarkers.add(m)
        }
    }

    private fun addLine(p1: LatLng, p2: LatLng, color: Int, width: Float) {
        val l = map.addPolyline(
            PolylineOptions()
                .clickable(false)
                .color(ContextCompat.getColor(this.requireContext(), color))
                .geodesic(true)
                .width(width)
                .add(p1, p2)
        )
        trackLines.add(l)
    }

    private fun addCircle(p: LatLng, fillColor: Int = R.color.grayTransparent2) {
        val c = map.addCircle(
            CircleOptions()
                .center(p)
                .radius(nm2m(nextRadiusList[settings.nextRadius]))
                .strokeColor(ContextCompat.getColor(this.requireContext(), R.color.gray))
                .strokeWidth(3f)
                .fillColor(ContextCompat.getColor(this.requireContext(), fillColor))
        )
        trackCircles.add(c)
    }

    private fun setTakeoffPoint(coords: LatLng) {
        settings.takeoffCoords = LatLng(roundDouble(coords.latitude, C.COORDS_PRECISION), roundDouble(coords.longitude, C.COORDS_PRECISION))
        saveState()
        drawFlightPlan()
    }

    private fun addWaypoint(coords: LatLng) {
        data class Angles(
            val i: Int,
            val angle: Double
        )

        var position = navlogList.size
        val prevCoords: LatLng

        if (navlogList.size == 0) {
            prevCoords = settings.takeoffCoords!!
        } else {
            // Find nearest waypoints among all navlog items - active and not active
            val angleList = ArrayList<Angles>()

            if (isNavlogItemGpsReady(0)) angleList.add(Angles(-1, abs(calcBearingAngle(coords, settings.takeoffCoords!!, navlogList[0].coords!!))))

            // Search through waypoints
            for (i in navlogList.indices) {
                if (isNavlogItemGpsReady(i) && isNavlogItemGpsReady(i + 1)) angleList.add(Angles(i, abs(calcBearingAngle(coords, navlogList[i].coords!!, navlogList[i + 1].coords!!))))
            }

            // Sort by angle
            angleList.sortByDescending { it.angle }
            if (angleList[0].angle > 90.0) {
                position = angleList[0].i + 1
                prevCoords = if (position == 0) settings.takeoffCoords!! else navlogList[position - 1].coords!!
            } else prevCoords = navlogList[getNavlogLastActiveItemId()].coords!!
        }

        // True track
        val tt = calcBearing(prevCoords, coords)

        // Magnetic declination
        val d = getDeclination(coords)

        // Distance
        val dist = meters2distUnits(calcDistance(prevCoords, coords))

        // Add item
        navlogList.add(position, NavlogItem(dest = "", coords = coords, trueTrack = tt, declination = d, distance = dist))
        val dialog = NavlogDialogFragment(position)
        dialog.show(parentFragmentManager, "NavlogDialogFragment")
    }

    private fun refreshBottomBar() {
        val strDist = formatDouble(toUnitsDis(totals.dist)) + " " + getUnitsDis()
        val strFuel = formatDouble(toUnitsVol(totals.fuel)) + " " + getUnitsVol()

        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = formatSecondsToTime(totals.time)
        bind.txtTotalFuel.text = strFuel
    }

    private suspend fun updateMapThread() {
        while (true) {
            // Refresh the map
            if (refreshDisplay) {
                refreshDisplay = false
                drawFlightPlan()
                drawTrace()
                zoomToTrack()
            }

            // Refresh the trace
            if (timerTrace == 0) {
                timerTrace = 50
                if (isFlightInProgress()) drawTrace()
            }

            // Map follow
            if (timerFollow == 0) {
                timerFollow = if (getFlightStage() == C.STAGE_3_FLIGHT_IN_PROGRESS) 5 else 50
                if (settings.mapFollow) followPosition()
            }

            if (timerTrace > 0) timerTrace -= 1
            if (timerFollow > 0) timerFollow -= 1
            delay(100)
        }
    }

    private fun updateMapNavUIThread() {
        var prevTime = 0L

        while (true) {
            val a = activity as? MainActivity ?: break
            val b = _binding ?: break

            // Loop every 1 sec
            val curTime = System.currentTimeMillis() / 1000L
            if (curTime != prevTime) {
                prevTime = curTime

                val stage = getFlightStage()
                if (stage > C.STAGE_1_BEFORE_ENGINE_START && stage < C.STAGE_4_AFTER_LANDING) {
                    val h = HomeItem()

                    // Show top navigation
                    a.runOnUiThread { b.topNavigation.visibility = View.VISIBLE }

                    // WPT
                    a.runOnUiThread { b.topMapWpt.text = h.getWpt() }

                    // GS
                    val (gs, _) = h.getGs()
                    a.runOnUiThread { b.topMapGs.text = gs }

                    // HDG
                    val (hdg, _, _) = h.getHdg()
                    a.runOnUiThread { b.topMapHdg.text = hdg }

                    // ETE
                    a.runOnUiThread { b.topMapEte.text = h.getEte().ete }

                    // Dist
                    a.runOnUiThread { b.topMapDist.text = h.getDist().dist }

                    // Track indicator
                    if (h.gps.isValid) {
                        val dab = h.getDtkAngleBar()
                        a.runOnUiThread { b.topTrackIndicator.visibility = View.VISIBLE }
                        a.runOnUiThread { b.txtTrackAngleIndicatorLeft.progress = dab.left }
                        a.runOnUiThread { b.txtTrackAngleIndicatorRight.progress = dab.right }
                        if (dab.hit) {
                            a.runOnUiThread { b.txtTrackAngleIndicatorLeft.trackColor = ContextCompat.getColor(b.txtTrackAngleIndicatorLeft.context, R.color.colorPrimary) }
                            a.runOnUiThread { b.txtTrackAngleIndicatorRight.setIndicatorColor(ContextCompat.getColor(b.txtTrackAngleIndicatorRight.context, R.color.colorPrimary)) }
                        } else {
                            a.runOnUiThread { b.txtTrackAngleIndicatorLeft.trackColor = ContextCompat.getColor(b.txtTrackAngleIndicatorLeft.context, R.color.red) }
                            a.runOnUiThread { b.txtTrackAngleIndicatorRight.setIndicatorColor(ContextCompat.getColor(b.txtTrackAngleIndicatorRight.context, R.color.red)) }
                        }
                    } else {
                        a.runOnUiThread { b.topTrackIndicator.visibility = View.GONE }
                    }
                } else {
                    // Hide top navigation
                    a.runOnUiThread { b.topNavigation.visibility = View.GONE }
                }
            }
        }
    }
}