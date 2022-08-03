package com.artyum.dynamicnavlog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
    private lateinit var map2: GoogleMap
    private var mapReady: Boolean = false

    private val trackMarkers = ArrayList<Marker>()
    private val trackCircles = ArrayList<Circle>()
    private val trackLines = ArrayList<Polyline>()

    private val radialMarkers = ArrayList<Marker>()
    private val radialCircles = ArrayList<Circle>()
    private val radialLines = ArrayList<Polyline>()

    private var windArrowLine: Polyline? = null
    private var traceLine: Polyline? = null

    private var radialPoint1: LatLng? = null

    private var initZoom: Boolean = true    // Zoom on fragment load (one-time)

    @Volatile
    private var mapFollow: Boolean = settings.mapFollow

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
        val mapFragment2 = childFragmentManager.findFragmentById(R.id.mapFragment2) as SupportMapFragment

        if (ActivityCompat.checkSelfPermission(con, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((activity as MainActivity), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), C.LOCATION_PERMISSION_REQ_CODE)
        } else {
            mapFragment2.getMapAsync {
                map2 = it
            }
            mapFragment.getMapAsync {
                map = it
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isCompassEnabled = true
                map.mapType = settings.mapType

                // Click on map listener - add waypoint or radial
                map.setOnMapClickListener { pos: LatLng ->
                    if (radialPoint1 == null) {
                        if (settings.takeoffPos == null) setTakeoffPoint(pos)
                        else addWaypoint(pos)
                    } else {
                        // Radial second click
                        addRadial(radialPoint1!!, pos)
                        radialPoint1 = null
                    }
                }

                // Long click on map - Start new radial
                map.setOnMapLongClickListener { pos: LatLng ->
                    radialPoint1 = pos
                    Toast.makeText(view.context, getString(R.string.txtAddRadial), Toast.LENGTH_SHORT).show()
                }

                // Drag a marker
                map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(p0: Marker) {}
                    override fun onMarkerDrag(p0: Marker) {}
                    override fun onMarkerDragEnd(m: Marker) {
                        val i: Int = m.tag.toString().toInt()
                        if (m.title == C.MAP_ITEM_TRACK.toString()) {
                            // Drag a waypoint
                            if (!isFlightInProgress() || (isFlightInProgress() && i >= getNavlogCurrentItemId())) {
                                if (i < 0) settings.takeoffPos = m.position else navlogList[i].pos = m.position
                                calcNavlog()
                                saveState()
                            }
                            drawFlightPlan()
                            refreshBottomBar()
                        } else {
                            if (i % 2 == 0) {
                                // Drag radial start point
                                radialList[i / 2].pos1 = m.position
                                recalculateRadial(i / 2)
                            } else {
                                // Drag radial endpoint
                                radialList[(i - 1) / 2].pos2 = m.position
                                recalculateRadial((i - 1) / 2)
                            }
                            drawRadials()
                        }
                    }
                })

                // Click on a marker
                map.setOnMarkerClickListener {
                    val i: Int = it.tag.toString().toInt()
                    if (it.title == C.MAP_ITEM_TRACK.toString()) {
                        // Click on a waypoint
                        if (i >= 0 && i < navlogList.size) {
                            val dialog = NavlogDialogFragment(i)
                            dialog.show(parentFragmentManager, "NavlogDialogFragment")
                        }
                    } else {
                        //Click on a radial start ot end point
                        val r = if (i % 2 == 0) i / 2 else (i - 1) / 2
                        if (r >= 0 && r < radialList.size) {
                            val dialog = RadialDialogFragment(null, null, r)
                            dialog.show(parentFragmentManager, "RadialDialogFragment")
                        }
                    }
                    true
                }

                // Map drag disables Camera Follow
                map.setOnCameraMoveStartedListener { reason: Int ->
                    if (settings.mapFollow && reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        settings.mapFollow = false
                        bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
                    }
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION) {
                        mapFollow = false
                    }
                }

                // Draw wind arrow on map move
                map.setOnCameraMoveListener { drawWindArrow() }

                mapReady = true

                drawRadials()
                drawTrace()
                drawFlightPlan()
                zoomToTrack()
                drawWindArrow()
            }

            // Start home thread
            CoroutineScope(CoroutineName("map")).launch { updateMapNavUIThread() }
        }

        setFragmentResultListener("requestKey") { _, _ ->
            refreshBottomBar()
            drawRadials()
            drawFlightPlan()
            saveState()
        }

        // Zoom buttons
        bind.btnMapZoomIn.setOnClickListener { mapZoom(1) }
        bind.btnMapZoomOut.setOnClickListener { mapZoom(-1) }

        // Button Follow
        bind.btnFollowToggle.setOnClickListener {
            if (settings.mapFollow) {
                settings.mapFollow = false
                bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
            } else {
                settings.mapFollow = true
                mapFollow = true
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
        if (settings.mapFollow) bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_lock)
        else bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)

        // Refresh bottom summary bar
        refreshBottomBar()

        // Display units in top navigation bar
        bind.txtNavGs.text = getString(R.string.txtGs) + " (" + getUnitsSpd() + ")"
        bind.txtNavDist.text = getString(R.string.txtDist) + " (" + getUnitsDis() + ")"

        // Run map update thread
        lifecycleScope.launch { updateMapThread() }
    }

    private fun drawFlightPlan() {
        if (!mapReady) return
        //Log.d(TAG, "drawFlightPlan")

        if (settings.takeoffPos != null) {
            trackMarkers.forEach { it.remove() }
            trackMarkers.clear()
            trackLines.forEach { it.remove() }
            trackLines.clear()
            trackCircles.forEach { it.remove() }
            trackCircles.clear()

            // Departure point
            if (getFlightStage() < C.STAGE_3_FLIGHT_IN_PROGRESS) addMarker(pos = settings.takeoffPos!!, type = C.MAP_ITEM_TRACK, id = -1, hue = BitmapDescriptorFactory.HUE_GREEN)

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
                        if (i >= item) addMarker(pos = navlogList[i].pos!!, type = C.MAP_ITEM_TRACK, id = i, hue = hue)

                        // Auto-next circle
                        if (isAutoNextEnabled() && i >= item) {
                            val pos = navlogList[i].pos!!
                            val radius = nm2m(nextRadiusList[options.nextRadiusIndex])
                            val fill = R.color.grayTransparent2

                            if (isFlightInProgress()) {
                                // Show only current waypoint circle
                                if (i == item) addCircle(pos = pos, radius = radius, fillColor = fill, type = C.MAP_ITEM_TRACK)
                            } else addCircle(pos = pos, radius = radius, fillColor = fill, type = C.MAP_ITEM_TRACK)
                        }

                        // Track color
                        var color: Int = R.color.trackdefault   // Default
                        if (isFlightInProgress()) {
                            if (i < item) color = R.color.trackpassed   // Passed track
                            else if (i == item) color = R.color.trackcurrent      // Current track
                        }

                        // Track line
                        if (prev != null) addLine(prev, navlogList[i].pos!!, color, C.TRACK_WIDTH, C.MAP_ITEM_TRACK)
                    } else {
                        // Inactive markers
                        if (i >= item) addMarker(pos = navlogList[i].pos!!, type = C.MAP_ITEM_TRACK, id = i, hue = BitmapDescriptorFactory.HUE_VIOLET)

                        // Inactive track line
                        if (i == 0) {
                            addLine(settings.takeoffPos!!, navlogList[i].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                            if (navlogList.size > 1 && navlogList[1].active) addLine(navlogList[0].pos!!, navlogList[i + 1].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                        } else if (i > 0 && i < navlogList.size) {
                            addLine(navlogList[i - 1].pos!!, navlogList[i].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                            if (i < navlogList.size - 1 && navlogList[i + 1].active) {
                                addLine(navlogList[i].pos!!, navlogList[i + 1].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                            }
                        }
                    }
                }
            }

            // Show not "gps-ready" waypoints
            var cnt = 0  // Count points
            for (i in navlogList.indices) if (navlogList[i].pos == null) cnt += 1
            if (cnt > 0) {
                // Draw
                var angle = 0.0
                for (i in navlogList.indices) {
                    if (navlogList[i].pos == null) {
                        val point = calcDestinationPos(settings.takeoffPos!!, angle, nm2m(10.0))
                        angle += 360.0 / cnt.toDouble()
                        addMarker(pos = point, type = C.MAP_ITEM_TRACK, id = i, hue = BitmapDescriptorFactory.HUE_YELLOW)
                    }
                }
            }
        }
    }

    private fun drawTrace() {
        if (!options.displayTrace) return
        if (!mapReady || tracePointsList.size < 2) return
        //Log.d(TAG, "drawTrace")

        traceLine?.remove()

        val line = PolylineOptions()
            .clickable(false)
            .color(ContextCompat.getColor(this.requireContext(), R.color.traceLine))
            .geodesic(true)
            .pattern(listOf(Dash(15f), Gap(15f)))
            .width(8f)

        for (i in tracePointsList.indices) line.add(tracePointsList[i])
        traceLine = map.addPolyline(line)
    }

    private fun drawWindArrow() {
        if (!mapReady) return
        if (!options.drawWindArrow) return
        //Log.d(TAG, "drawWindArrow")

        val line = PolylineOptions()
            .clickable(false)
            .color(ContextCompat.getColor(this.requireContext(), R.color.windArrow))
            .geodesic(false)
            .width(7f)

        val visibleRegion: VisibleRegion = map.projection.visibleRegion
        val center = visibleRegion.latLngBounds.center
        val angle = normalizeBearing(settings.windDir - getDeclination(center))  // Get declination from the center of the screen
        val len1 = calcDistance(center, visibleRegion.latLngBounds.southwest) / 4.0
        val len2 = len1 / 2.8

        val startPoint = calcDestinationPos(center, angle, len1 / 2.0)
        val endPoint = calcDestinationPos(startPoint, normalizeBearing(angle + 180.0), len1)
        val q1 = calcDestinationPos(endPoint, normalizeBearing(angle + 10.0), len2)
        val q2 = calcDestinationPos(endPoint, normalizeBearing(angle - 10.0), len2)

        line.add(startPoint, endPoint, q1, q2)
        windArrowLine?.remove()
        windArrowLine = map.addPolyline(line)
    }

    private fun drawRadials() {
        if (!mapReady) return
        if (!options.drawRadials) return
        //Log.d(TAG, "drawRadials")

        radialMarkers.forEach { it.remove() }
        radialMarkers.clear()
        radialCircles.forEach { it.remove() }
        radialCircles.clear()
        radialLines.forEach { it.remove() }
        radialLines.clear()

        val width = 6f

        for (i in radialList.indices) {
            val radial = radialList[i]
            val declination = getDeclination(radial.pos1)
            if (options.drawRadialsMarkers) {
                addMarker(pos = radial.pos1, type = C.MAP_ITEM_RADIAL, id = 2 * i, hue = BitmapDescriptorFactory.HUE_BLUE)
                addMarker(pos = radial.pos2, type = C.MAP_ITEM_RADIAL, id = 2 * i + 1, hue = BitmapDescriptorFactory.HUE_BLUE)
            }
            addCircle(pos = radial.pos1, radius = C.RADIAL_RADIUS_M, strokeColor = R.color.radial, fillColor = R.color.transparent, strokeWidth = width, type = C.MAP_ITEM_RADIAL)

            // Radial circle scale
            for (j in 0..11) {
                val a = normalizeBearing(j * 30.0 - declination)
                val r = if (j % 3 == 0) 0.8 else 0.9
                if (j == 0) {
                    val pc = calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M * r)
                    val p1 = calcDestinationPos(radial.pos1, a - 3.0, C.RADIAL_RADIUS_M)
                    val p2 = calcDestinationPos(radial.pos1, a + 3.0, C.RADIAL_RADIUS_M)
                    addLine(pc, p1, R.color.radial, width, C.MAP_ITEM_RADIAL)
                    addLine(pc, p2, R.color.radial, width, C.MAP_ITEM_RADIAL)
                } else {
                    val p1 = calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M)
                    val p2 = calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M * r)
                    addLine(p1, p2, R.color.radial, width, C.MAP_ITEM_RADIAL)
                }
            }

            // Radial line
            addLine(radial.pos1, radial.pos2, R.color.radial, width + 2f, C.MAP_ITEM_RADIAL)
        }
    }

    private fun zoomToAll() {
        if (!mapReady) return

        // Zoom to all waypoints and takeoff point
        var chkTO = false
        var chkItems = false
        val builder = LatLngBounds.Builder()

        if (settings.takeoffPos != null) {
            builder.include(settings.takeoffPos!!)
            chkTO = true
        }
        for (i in navlogList.indices) {
            if (isNavlogItemGpsReady(i)) {
                builder.include(navlogList[i].pos!!)
                chkItems = true
            }
        }

        if (chkTO || chkItems) {
            val bounds = builder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))

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
            if (settings.takeoffPos == null && navlogList.size == 0) {
                // New flight plan with no points -> Zoom to position
                if (gps.isValid) {
                    val cameraPosition = CameraPosition.Builder()
                        .target(gps.pos!!)
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
                    .target(gps.pos!!)
                    .zoom(15f)
                    .bearing(0f)
                    .tilt(map.cameraPosition.tilt)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else zoomToAll()
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            val item = getNavlogCurrentItemId()
            val zoom: Float
            val target: LatLng

            val bearing = if (options.mapOrientation == C.MAP_ORIENTATION_NORTH) {
                0f
            } else if (options.mapOrientation == C.MAP_ORIENTATION_TRACK) {
                if (navlogList[item].trueTrack != null) navlogList[item].trueTrack!!.toFloat() else 0f
            } else {
                // C.MAP_ORIENTATION_BEARING
                if (gps.isValid) gps.bearing!! else if (navlogList[item].trueTrack != null) navlogList[item].trueTrack!!.toFloat() else 0f
            }

            if (isMapFollow() && gps.isValid) {
                // Zoom to GPS position
                zoom = if (map.cameraPosition.zoom <= 3) 12f else map.cameraPosition.zoom
                target = gps.pos!!
            } else {
                // Zoom to previous and current waypoint

                // Get boundary
                val wpt = navlogList[item]
                val builder = LatLngBounds.Builder()
                builder.include(wpt.pos!!)
                builder.include(getPrevCoords(item)!!)
                val bounds = builder.build()
                target = bounds.center

                zoom = if (options.mapOrientation == C.MAP_ORIENTATION_NORTH) {
                    map2.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))
                    map2.cameraPosition.zoom
                } else {
                    getZoomLevel(target, navlogList[item].distance!!)
                }
            }

            val cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .bearing(bearing)
                .tilt(map.cameraPosition.tilt)
                .build()

            if (initZoom) {
                initZoom = false
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else {
                mapFollow = false
                map.stopAnimation()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        mapFollow = true
                    }

                    override fun onCancel() {}
                })
            }
        }
    }

    private fun followPosition() {
        if (!mapReady) return
        if (!settings.mapFollow) return
        if (!mapFollow) return

        var gps: GpsData
        runBlocking { gpsMutex.withLock { gps = gpsData } }

        if (gps.isValid) {
            if (map.cameraPosition.target == gps.pos) return

            val bearing = if (options.mapOrientation == C.MAP_ORIENTATION_BEARING && gps.bearing != null) gps.bearing!!
            else map.cameraPosition.bearing

            val cameraPosition = CameraPosition.Builder()
                .target(gps.pos!!)
                .zoom(map.cameraPosition.zoom)
                .bearing(bearing)
                .tilt(map.cameraPosition.tilt)
                .build()

            if (mapFollow) {
                mapFollow = false
                map.stopAnimation()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 900, object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        mapFollow = true
                    }

                    override fun onCancel() {}
                })
            }
        }
    }

    private fun addMarker(pos: LatLng, type: Int, id: Int, hue: Float) {
        val m = map.addMarker(
            MarkerOptions()
                .position(pos)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
                .title(type.toString())
        )
        if (m != null) {
            m.tag = id
            when (type) {
                C.MAP_ITEM_TRACK -> trackMarkers.add(m)
                C.MAP_ITEM_RADIAL -> radialMarkers.add(m)
            }
        }
    }

    private fun addLine(p1: LatLng, p2: LatLng, color: Int, width: Float, type: Int) {
        val l = map.addPolyline(
            PolylineOptions()
                .clickable(false)
                .color(ContextCompat.getColor(this.requireContext(), color))
                .geodesic(true)
                .width(width)
                .add(p1, p2)
        )
        when (type) {
            C.MAP_ITEM_TRACK -> trackLines.add(l)
            C.MAP_ITEM_RADIAL -> radialLines.add(l)
        }
    }

    private fun addCircle(pos: LatLng, radius: Double, strokeColor: Int = R.color.gray, fillColor: Int = R.color.transparent, strokeWidth: Float = 3f, type: Int) {
        val c = map.addCircle(
            CircleOptions()
                .center(pos)
                .radius(radius)
                .strokeColor(ContextCompat.getColor(this.requireContext(), strokeColor))
                .strokeWidth(strokeWidth)
                .fillColor(ContextCompat.getColor(this.requireContext(), fillColor))
        )
        when (type) {
            C.MAP_ITEM_TRACK -> trackCircles.add(c)
            C.MAP_ITEM_RADIAL -> radialCircles.add(c)
        }
    }

    private fun setTakeoffPoint(pos: LatLng) {
        settings.takeoffPos = LatLng(roundDouble(pos.latitude, C.POS_PRECISION), roundDouble(pos.longitude, C.POS_PRECISION))
        saveState()
        drawFlightPlan()
    }

    private fun addWaypoint(pos: LatLng) {
        data class Angles(
            val i: Int,
            val angle: Double
        )

        var position = navlogList.size
        val prevCoords: LatLng

        if (navlogList.size == 0 || getNavlogLastActiveItemId() < 0) {
            prevCoords = settings.takeoffPos!!
        } else {
            // Find nearest waypoints among all navlog items - active and not active
            val angleList = ArrayList<Angles>()

            if (isNavlogItemGpsReady(0)) angleList.add(Angles(-1, abs(calcBearingAngle(pos, settings.takeoffPos!!, navlogList[0].pos!!))))

            // Search through waypoints
            for (i in navlogList.indices) {
                if (isNavlogItemGpsReady(i) && isNavlogItemGpsReady(i + 1)) angleList.add(Angles(i, abs(calcBearingAngle(pos, navlogList[i].pos!!, navlogList[i + 1].pos!!))))
            }

            // Sort by angle
            angleList.sortByDescending { it.angle }
            if (angleList[0].angle > 90.0) {
                position = angleList[0].i + 1
                prevCoords = if (position == 0) settings.takeoffPos!! else navlogList[position - 1].pos!!
            } else prevCoords = navlogList[getNavlogLastActiveItemId()].pos!!
        }

        // True track
        val tt = calcBearing(prevCoords, pos)

        // Distance
        val dist = m2nm(calcDistance(prevCoords, pos))

        // Add item
        navlogList.add(position, NavlogItem(dest = "", pos = pos, trueTrack = tt, declination = getDeclination(pos), distance = dist))
        val dialog = NavlogDialogFragment(position)
        dialog.show(parentFragmentManager, "NavlogDialogFragment")
    }

    private fun addRadial(pos1: LatLng, pos2: LatLng) {
        val dialog = RadialDialogFragment(pos1, pos2)
        dialog.show(parentFragmentManager, "RadialDialogFragment")
    }

    private fun refreshBottomBar() {
        val p1 = if (totals.dist < C.DIST_THRESHOLD) 1 else 0
        val p2 = if (totals.fuel < C.VOL_THRESHOLD) 1 else 0
        val strDist = formatDouble(toUserUnitsDis(totals.dist), p1) + " " + getUnitsDis()
        val strFuel = formatDouble(toUserUnitsVol(totals.fuel), p2) + " " + getUnitsVol()
        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = formatSecondsToTime(totals.time)
        bind.txtTotalFuel.text = strFuel
    }

    private fun getZoomLevel(pos: LatLng, distance: Double): Float {
        val pos2 = calcDestinationPos(pos, 0.0, nm2m(distance))
        val builder = LatLngBounds.Builder()
        builder.include(pos)
        builder.include(pos2)
        val bounds = builder.build()
        // Set the camera position on second map (invisible to the user) to read the zoom level
        map2.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))
        return map2.cameraPosition.zoom
    }

    private fun mapZoom(zoom: Int) {
        val cameraPosition = CameraPosition.Builder()
            .target(map.cameraPosition.target)
            .zoom(map.cameraPosition.zoom + zoom)
            .bearing(map.cameraPosition.bearing)
            .tilt(map.cameraPosition.tilt)
            .build()

        mapFollow = false
        map.stopAnimation()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 200, object : GoogleMap.CancelableCallback {
            override fun onFinish() {
                mapFollow = true
            }

            override fun onCancel() {}
        })
    }

    private suspend fun updateMapThread() {
        while (_binding != null) {
            // Refresh the map
            if (globalRefresh) {
                globalRefresh = false

                drawRadials()
                drawTrace()
                drawFlightPlan()
                zoomToTrack()
                drawWindArrow()
            }

            // Refresh the trace
            if (timerTrace == 0) {
                timerTrace = 50
                if (isFlightInProgress()) drawTrace()
            }

            // Map follow
            if (timerFollow == 0) {
                timerFollow = if (getFlightStage() == C.STAGE_3_FLIGHT_IN_PROGRESS) 10 else 50
                followPosition()
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
                if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
                    val h = HomeItem()

                    // Show top navigation
                    a.runOnUiThread { b.topNavigation.visibility = View.VISIBLE }

                    // WPT
                    a.runOnUiThread { b.topMapWpt.text = h.getWpt() }

                    // GS
                    val (gs, _) = h.getGs()
                    a.runOnUiThread { b.topMapGs.text = gs }

                    // HDG
                    val (hdg, hdgNext, _) = h.getHdg()
                    if (h.isInsideCircle) a.runOnUiThread {
                        b.labelHdg.text = context?.getString(R.string.txtHdgNext) ?: ""
                        b.topMapHdg.text = hdgNext
                    }
                    else a.runOnUiThread {
                        b.labelHdg.text = context?.getString(R.string.txtHdg) ?: ""
                        b.topMapHdg.text = hdg
                    }

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