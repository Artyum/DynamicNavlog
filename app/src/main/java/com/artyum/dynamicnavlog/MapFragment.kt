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
import com.artyum.dynamicnavlog.openaip.OpenAIPClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import okhttp3.internal.Util
import kotlin.math.abs

class MapFragment : Fragment(R.layout.fragment_map) {
    private var _binding: FragmentMapBinding? = null
    private val bind get() = _binding!!
    private lateinit var map: GoogleMap
    private lateinit var map2: GoogleMap   // Used for setting zoom level
    private var mapReady: Boolean = false

    private val trackMarkers = ArrayList<Marker>()
    private val trackCircles = ArrayList<Circle>()
    private val trackLines = ArrayList<Polyline>()

    private val radialMarkers = ArrayList<Marker>()
    private val radialCircles = ArrayList<Circle>()
    private val radialLines = ArrayList<Polyline>()
    private var traceLine: Polyline? = null

    private var radialStartPoint: LatLng? = null
    private var initZoom: Boolean = true    // Zoom on fragment (one-time)
    private var mapFollow: Boolean = true

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
        bind.mapLayout.keepScreenOn = State.options.keepScreenOn
        (activity as MainActivity).displayButtons()

        val context = (activity as MainActivity).applicationContext
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        val mapFragmentHidden = childFragmentManager.findFragmentById(R.id.mapFragmentHidden) as SupportMapFragment

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((activity as MainActivity), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), C.LOCATION_PERMISSION_REQ_CODE)
        } else {
            mapFragmentHidden.getMapAsync {
                map2 = it
            }
            mapFragment.getMapAsync { it ->
                map = it
                map.isMyLocationEnabled = State.options.gpsAssist
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isCompassEnabled = true
                map.mapType = State.settings.mapType

                // Click on map listener - add waypoint or radial
                map.setOnMapClickListener { pos: LatLng ->
                    if (Utils.isPlanEditDisabled()) return@setOnMapClickListener

                    if (radialStartPoint == null) {
                        if (State.settings.takeoffPos == null) setTakeoffPoint(pos)
                        else addWaypoint(pos)
                    } else {
                        // Radial second click
                        addRadial(radialStartPoint!!, pos)
                        radialStartPoint = null
                    }
                }

                // Long click on map - Start new radial
                map.setOnMapLongClickListener { pos: LatLng ->
                    if (Utils.isPlanEditDisabled()) return@setOnMapLongClickListener

                    radialStartPoint = pos
                    Toast.makeText(view.context, getString(R.string.txtAddRadial), Toast.LENGTH_SHORT).show()
                }

                // Drag a marker
                map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(p0: Marker) {}
                    override fun onMarkerDrag(p0: Marker) {}
                    override fun onMarkerDragEnd(m: Marker) {
                        radialStartPoint = null
                        val i: Int = m.tag.toString().toInt()
                        if (m.title == C.MAP_ITEM_TRACK.toString()) {
                            // Drag a waypoint
                            if (!Utils.isFlightInProgress() || (Utils.isFlightInProgress() && i >= NavLogUtils.getNavlogCurrentItemId())) {
                                if (i < 0) State.settings.takeoffPos = m.position else State.navlogList[i].pos = m.position
                                NavLogUtils.calcNavlog()
                                FileUtils.saveState()
                            }
                            drawFlightPlan()
                            refreshBottomBar()
                        } else {
                            if (i % 2 == 0) {
                                // Drag radial start point
                                State.radialList[i / 2].pos1 = m.position
                                recalculateRadial(i / 2)
                            } else {
                                // Drag radial endpoint
                                State.radialList[(i - 1) / 2].pos2 = m.position
                                recalculateRadial((i - 1) / 2)
                            }
                            drawRadials()
                        }
                    }
                })

                // Click on a marker
                map.setOnMarkerClickListener {
                    if (Utils.isPlanEditDisabled()) return@setOnMarkerClickListener true

                    val i: Int = it.tag.toString().toInt()
                    if (it.title == C.MAP_ITEM_TRACK.toString()) {
                        // Click on a waypoint
                        if (i >= 0 && i < State.navlogList.size) {
                            val dialog = NavlogDialogFragment(i)
                            dialog.show(parentFragmentManager, "NavlogDialogFragment")
                        }
                    } else {
                        //Click on a radial start ot end point
                        val r = if (i % 2 == 0) i / 2 else (i - 1) / 2
                        if (r >= 0 && r < State.radialList.size) {
                            val dialog = RadialDialogFragment(null, null, r)
                            dialog.show(parentFragmentManager, "RadialDialogFragment")
                        }
                    }
                    true
                }

                // Map drag disables Camera Follow
                map.setOnCameraMoveStartedListener { reason: Int ->
                    if (State.settings.mapFollow && reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        State.settings.mapFollow = false
                        mapFollow = true
                        bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
                    }
                }

                // Draw wind arrow on map move
                if (_binding != null) map.setOnCameraMoveListener { drawWindArrow() }

                mapReady = true

                drawRadials()
                drawTrace()
                drawFlightPlan()
                zoomToTrack()
                drawWindArrow()
            }
        }

        setFragmentResultListener("requestKey") { _, _ ->
            refreshBottomBar()
            drawRadials()
            drawFlightPlan()
            FileUtils.saveState()
            (activity as MainActivity).displayButtons()
        }

        // Zoom buttons
        bind.btnMapZoomIn.setOnClickListener { mapZoom(1) }
        bind.btnMapZoomOut.setOnClickListener { mapZoom(-1) }

        // Button Follow
        if (State.options.gpsAssist) {
            bind.btnFollowToggle.visibility = View.VISIBLE
            bind.btnFollowToggle.setOnClickListener {
                if (State.settings.mapFollow) {
                    State.settings.mapFollow = false
                    bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)
                } else {
                    State.settings.mapFollow = true
                    bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_lock)
                    timerFollow = 0
                }
                mapFollow = true
                FileUtils.saveState()
            }
        } else {
            bind.btnFollowToggle.visibility = View.GONE
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
                State.settings.mapType = map.mapType
            }
            FileUtils.saveState()
        }

        // Follow map mode
        if (State.settings.mapFollow) bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_lock)
        else bind.btnFollowToggle.setImageResource(R.drawable.ic_gps_unlock)

        // Refresh bottom summary bar
        refreshBottomBar()

        // Display units in top navigation bar
        val txtGs = getString(R.string.txtGs) + " (" + Units.getUnitsSpd() + ")"
        bind.txtNavGs.text = txtGs
        val txtDist = getString(R.string.txtDist) + " (" + Units.getUnitsDis() + ")"
        bind.txtNavDist.text = txtDist

        // Start home thread
        CoroutineScope(CoroutineName("map")).launch { updateNavigationBoxThread() }

        // Start OpenAIP thread
        //CoroutineScope(CoroutineName("OpenAIP")).launch { openAIPThread() }

        // Run map update thread
        lifecycleScope.launch { updateMapThread() }
    }

    private fun drawFlightPlan() {
        if (!mapReady) return
        if (State.settings.takeoffPos == null) return

        val stage = NavLogUtils.getFlightStage()
        trackMarkers.forEach { it.remove() }
        trackMarkers.clear()
        trackLines.forEach { it.remove() }
        trackLines.clear()
        trackCircles.forEach { it.remove() }
        trackCircles.clear()

        // Departure point
        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) addMarker(pos = State.settings.takeoffPos!!, type = C.MAP_ITEM_TRACK, id = -1, hue = BitmapDescriptorFactory.HUE_GREEN)

        // Track
        val item = NavLogUtils.getNavlogCurrentItemId()
        val last = NavLogUtils.getNavlogLastActiveItemId()

        for (i in State.navlogList.indices) {
            if (State.navlogList[i].active) {
                val prev = NavLogUtils.getPrevCoords(i)

                // Markers
                var hue = BitmapDescriptorFactory.HUE_RED                      // Default
                if (i == last) hue = BitmapDescriptorFactory.HUE_ORANGE        // Last
                if (Utils.isFlightInProgress()) {
                    if (i == item) hue = BitmapDescriptorFactory.HUE_MAGENTA   // Current
                    if (i < item) hue = BitmapDescriptorFactory.HUE_AZURE      // Passed
                }
                if (stage < C.STAGE_4_AFTER_LANDING) {
                    if (i >= item) addMarker(pos = State.navlogList[i].pos!!, type = C.MAP_ITEM_TRACK, id = i, hue = hue)
                }

                // Auto-next circle
                if (SettingUtils.isAutoNextEnabled() && i >= item) {
                    val pos = State.navlogList[i].pos!!
                    val radius = Units.nm2m(C.nextRadiusList[State.options.nextRadiusIndex])
                    val fill = R.color.grayTransparent2

                    if (Utils.isFlightInProgress()) {
                        // Show only current waypoint circle
                        if (i == item) addCircle(pos = pos, radius = radius, fillColor = fill, type = C.MAP_ITEM_TRACK)
                    } else addCircle(pos = pos, radius = radius, fillColor = fill, type = C.MAP_ITEM_TRACK)
                }

                // Track color
                var color: Int = R.color.trackdefault   // Default
                if (Utils.isFlightInProgress()) {
                    if (i < item) color = R.color.trackpassed   // Passed track
                    else if (i == item) color = R.color.trackcurrent      // Current track
                }

                // Track line
                if (prev != null) addLine(prev, State.navlogList[i].pos!!, color, C.TRACK_WIDTH, C.MAP_ITEM_TRACK)
            } else {
                // Inactive markers
                if (i >= item) addMarker(pos = State.navlogList[i].pos!!, type = C.MAP_ITEM_TRACK, id = i, hue = BitmapDescriptorFactory.HUE_VIOLET)

                // Inactive track line
                if (i == 0) {
                    addLine(State.settings.takeoffPos!!, State.navlogList[i].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                    if (State.navlogList.size > 1 && State.navlogList[1].active) addLine(State.navlogList[0].pos!!, State.navlogList[i + 1].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                } else if (i > 0 && i < State.navlogList.size) {
                    addLine(State.navlogList[i - 1].pos!!, State.navlogList[i].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                    if (i < State.navlogList.size - 1 && State.navlogList[i + 1].active) {
                        addLine(State.navlogList[i].pos!!, State.navlogList[i + 1].pos!!, R.color.trackinactive, C.TRACK_INACTIVE_WIDTH, C.MAP_ITEM_TRACK)
                    }
                }
            }
        }
    }

    private fun drawTrace() {
        if (!State.options.displayTrace) return
        if (!mapReady || State.tracePointsList.size < 2) return

        traceLine?.remove()
        val line = PolylineOptions()
            .clickable(false)
            .color(ContextCompat.getColor(this.requireContext(), R.color.traceLine))
            .geodesic(true)
            .pattern(listOf(Dash(15f), Gap(15f))).width(8f)

        for (i in State.tracePointsList.indices) line.add(State.tracePointsList[i])
        traceLine = map.addPolyline(line)
    }

    private fun drawWindArrow() {
        if (!mapReady) return
        if (!State.options.drawWindArrow) return
        if (_binding != null) Utils.generateWindArrow(bind.mapWindIndicator, resources, GPSUtils.normalizeBearing(map.cameraPosition.bearing - State.settings.windDir + GPSUtils.getDeclination(map.cameraPosition.target)))
    }

    private fun drawRadials() {
        if (!mapReady) return
        if (!State.options.drawRadials) return

        radialMarkers.forEach { it.remove() }
        radialMarkers.clear()
        radialCircles.forEach { it.remove() }
        radialCircles.clear()
        radialLines.forEach { it.remove() }
        radialLines.clear()

        val width = 6f

        for (i in State.radialList.indices) {
            val radial = State.radialList[i]
            val declination = GPSUtils.getDeclination(radial.pos1)
            if (State.options.drawRadialsMarkers) {
                addMarker(pos = radial.pos1, type = C.MAP_ITEM_RADIAL, id = 2 * i, hue = BitmapDescriptorFactory.HUE_BLUE)
                addMarker(pos = radial.pos2, type = C.MAP_ITEM_RADIAL, id = 2 * i + 1, hue = BitmapDescriptorFactory.HUE_BLUE)
            }
            addCircle(pos = radial.pos1, radius = C.RADIAL_RADIUS_M, strokeColor = R.color.radial, fillColor = R.color.transparent, strokeWidth = width, type = C.MAP_ITEM_RADIAL)

            // Radial circle scale
            for (j in 0..11) {
                val a = GPSUtils.normalizeBearing(j * 30.0 - declination)
                val r = if (j % 3 == 0) 0.8 else 0.9
                if (j == 0) {
                    val pc = GPSUtils.calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M * r)
                    val p1 = GPSUtils.calcDestinationPos(radial.pos1, a - 3.0, C.RADIAL_RADIUS_M)
                    val p2 = GPSUtils.calcDestinationPos(radial.pos1, a + 3.0, C.RADIAL_RADIUS_M)
                    addLine(pc, p1, R.color.radial, width, C.MAP_ITEM_RADIAL)
                    addLine(pc, p2, R.color.radial, width, C.MAP_ITEM_RADIAL)
                } else {
                    val p1 = GPSUtils.calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M)
                    val p2 = GPSUtils.calcDestinationPos(radial.pos1, a, C.RADIAL_RADIUS_M * r)
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

        if (State.settings.takeoffPos != null) {
            builder.include(State.settings.takeoffPos!!)
            chkTO = true
        }
        for (i in State.navlogList.indices) {
            builder.include(State.navlogList[i].pos!!)
            chkItems = true
        }

        if (chkTO || chkItems) {
            val bounds = builder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))

            // Zoom only to TakeOff point
            val zoom: Float = if (chkTO && !chkItems) 9f else map.cameraPosition.zoom

            val cameraPosition = CameraPosition.Builder().target(bounds.center).zoom(zoom).bearing(0f).tilt(map.cameraPosition.tilt).build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    private fun zoomToTrack() {
        if (!mapReady) return

        // Get current GPS
        var gps: GpsData
        runBlocking { Vars.gpsMutex.withLock { gps = Vars.gpsData.copy() } }

        val stage = NavLogUtils.getFlightStage()

        if (stage == C.STAGE_1_BEFORE_ENGINE_START || stage == C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            if (State.settings.takeoffPos == null && State.navlogList.size == 0) {
                // New flight plan with no points -> Zoom to position
                if (gps.isValid) {
                    val cameraPosition = CameraPosition.Builder().target(gps.pos!!).zoom(10f).bearing(0f).tilt(0f).build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                } else zoomToAll()
            } else zoomToAll()
        } else if (stage == C.STAGE_2_ENGINE_RUNNING || stage == C.STAGE_4_AFTER_LANDING) {
            if (gps.isValid) {
                // Zoom to current gps position
                val cameraPosition = CameraPosition.Builder().target(gps.pos!!).zoom(15f).bearing(0f).tilt(map.cameraPosition.tilt).build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else zoomToAll()
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            val item = NavLogUtils.getNavlogCurrentItemId()
            val zoom: Float
            val target: LatLng

            val bearing = if (State.options.mapOrientation == C.MAP_ORIENTATION_NORTH) {
                0f
            } else if (State.options.mapOrientation == C.MAP_ORIENTATION_TRACK) {
                if (State.navlogList[item].tt != null) State.navlogList[item].tt!!.toFloat() else 0f
            } else {
                // C.MAP_ORIENTATION_BEARING
                if (gps.isValid && gps.bearing != null) gps.bearing!! else if (State.navlogList[item].tt != null) State.navlogList[item].tt!!.toFloat() else 0f
            }

            if (SettingUtils.isMapFollow() && gps.isValid) {
                // Zoom to GPS position
                zoom = if (map.cameraPosition.zoom <= 3) 12f else map.cameraPosition.zoom
                target = gps.pos!!
            } else {
                // Zoom to previous and current waypoint

                // Get boundary
                val wpt = State.navlogList[item]
                val builder = LatLngBounds.Builder()
                builder.include(wpt.pos!!)
                builder.include(NavLogUtils.getPrevCoords(item)!!)
                val bounds = builder.build()
                target = bounds.center

                zoom = if (State.options.mapOrientation == C.MAP_ORIENTATION_NORTH) {
                    map2.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))
                    map2.cameraPosition.zoom
                } else {
                    getZoomLevel(target, State.navlogList[item].dist!!)
                }
            }

            val cameraPosition = CameraPosition.Builder().target(target).zoom(zoom).bearing(bearing).tilt(map.cameraPosition.tilt).build()

            if (initZoom) {
                initZoom = false
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            } else {
                mapFollow = false
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        //println("zoomToTrack finish")
                        mapFollow = true
                    }

                    override fun onCancel() {
                        //println("zoomToTrack cancel")
                    }
                })
            }
        }
    }

    private fun followPosition() {
        if (!mapReady) return
        if (!State.settings.mapFollow) return
        if (!mapFollow) return

        var gps: GpsData
        runBlocking { Vars.gpsMutex.withLock { gps = Vars.gpsData.copy() } }

        if (gps.isValid) {
            if (map.cameraPosition.target == gps.pos) return

            val bearing = if (State.options.mapOrientation == C.MAP_ORIENTATION_BEARING && gps.bearing != null) gps.bearing!!
            else map.cameraPosition.bearing

            val cameraPosition = CameraPosition.Builder().target(gps.pos!!).zoom(map.cameraPosition.zoom).bearing(bearing).tilt(map.cameraPosition.tilt).build()

            if (mapFollow) {
                mapFollow = false
                println("followPosition start")
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 900, object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        //println("followPosition finish")
                        mapFollow = true
                    }

                    override fun onCancel() {
                        //println("followPosition cancel")
                        mapFollow = false
                    }
                })
            }
        }
    }

    private fun addMarker(pos: LatLng, type: Int, id: Int, hue: Float) {
        val draggable = !Utils.isPlanEditDisabled()
        val m = map.addMarker(
            MarkerOptions().position(pos).draggable(draggable).icon(BitmapDescriptorFactory.defaultMarker(hue)).title(type.toString())
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
            PolylineOptions().clickable(false).color(ContextCompat.getColor(this.requireContext(), color)).geodesic(true).width(width).add(p1, p2)
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
        State.settings.takeoffPos = LatLng(Utils.roundDouble(pos.latitude, C.POS_PRECISION), Utils.roundDouble(pos.longitude, C.POS_PRECISION))
        FileUtils.saveState()
        drawFlightPlan()
    }

    private fun addWaypoint(pos: LatLng) {
        data class Angles(
            val i: Int, val angle: Double
        )

        var position = State.navlogList.size
        val prevCoords: LatLng

        if (State.navlogList.size == 0 || NavLogUtils.getNavlogLastActiveItemId() < 0) {
            prevCoords = State.settings.takeoffPos!!
        } else {
            // Find nearest waypoints among all navlog items - active and not active
            val angleList = ArrayList<Angles>()

            angleList.add(Angles(-1, abs(GPSUtils.calcBearingAngle(pos, State.settings.takeoffPos!!, State.navlogList[0].pos!!))))

            // Search through waypoints
            for (i in 0 until State.navlogList.size - 1) {
                angleList.add(Angles(i, abs(GPSUtils.calcBearingAngle(pos, State.navlogList[i].pos!!, State.navlogList[i + 1].pos!!))))
            }

            // Sort by angle
            angleList.sortByDescending { it.angle }
            if (angleList[0].angle > 90.0) {
                position = angleList[0].i + 1
                prevCoords = if (position == 0) State.settings.takeoffPos!! else State.navlogList[position - 1].pos!!
            } else prevCoords = State.navlogList[NavLogUtils.getNavlogLastActiveItemId()].pos!!
        }

        // True track
        val tt = GPSUtils.calcBearing(prevCoords, pos)

        // Distance
        val dist = Units.m2nm(GPSUtils.calcDistance(prevCoords, pos))

        // Add item
        State.navlogList.add(position, NavlogItemData(dest = "", pos = pos, tt = tt, d = GPSUtils.getDeclination(pos), dist = dist))
        val dialog = NavlogDialogFragment(position)
        dialog.show(parentFragmentManager, "NavlogDialogFragment")
    }

    private fun addRadial(pos1: LatLng, pos2: LatLng) {
        val dialog = RadialDialogFragment(pos1, pos2)
        dialog.show(parentFragmentManager, "RadialDialogFragment")
    }

    private fun recalculateRadial(i: Int) {
        if (i < 0 || i > State.radialList.size - 1) return
        State.radialList[i].angle = GPSUtils.normalizeBearing(GPSUtils.calcBearing(State.radialList[i].pos1, State.radialList[i].pos2) + GPSUtils.getDeclination(State.radialList[i].pos1))
        State.radialList[i].dist = Units.m2nm(GPSUtils.calcDistance(State.radialList[i].pos1, State.radialList[i].pos2))
    }

    private fun refreshBottomBar() {
        val p1 = if (State.totals.dist < C.DIST_THRESHOLD) 1 else 0
        val p2 = if (State.totals.fuel < C.VOL_THRESHOLD) 1 else 0
        val strDist = Utils.formatDouble(Units.toUserUnitsDis(State.totals.dist), p1) + " " + Units.getUnitsDis()
        val strFuel = Utils.formatDouble(Units.toUserUnitsVol(State.totals.fuel), p2) + " " + Units.getUnitsVol()
        bind.txtTotalDist.text = strDist
        bind.txtTotalTime.text = TimeUtils.formatSecondsToTime(State.totals.time)
        bind.txtTotalFuel.text = strFuel
    }

    private fun getZoomLevel(pos: LatLng, distance: Double): Float {
        val pos2 = GPSUtils.calcDestinationPos(pos, 0.0, Units.nm2m(distance))
        val builder = LatLngBounds.Builder()
        builder.include(pos)
        builder.include(pos2)
        val bounds = builder.build()
        // Set the camera position on second map (invisible to the user) to read the zoom level
        map2.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, C.MAP_ZOOM_PADDING))
        return map2.cameraPosition.zoom
    }

    private fun mapZoom(zoom: Int) {
        val cameraPosition = CameraPosition.Builder().target(map.cameraPosition.target).zoom(map.cameraPosition.zoom + zoom).bearing(map.cameraPosition.bearing).tilt(map.cameraPosition.tilt).build()

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
            if (Vars.globalRefresh) {
                Vars.globalRefresh = false
                drawRadials()
                drawTrace()
                drawFlightPlan()
                zoomToTrack()
                drawWindArrow()
            }

            // Refresh the trace
            if (timerTrace == 0) {
                timerTrace = 50
                if (Utils.isFlightInProgress()) drawTrace()
            }

            // Map follow
            if (timerFollow == 0) {
                timerFollow = if (NavLogUtils.getFlightStage() == C.STAGE_3_FLIGHT_IN_PROGRESS) 10 else 50
                followPosition()
            }

            if (timerTrace > 0) timerTrace -= 1
            if (timerFollow > 0) timerFollow -= 1
            delay(100)
        }
    }

    private fun updateNavigationBoxThread() {
        var prevTime = 0L

        while (true) {
            val a = activity as? MainActivity ?: break
            val b = _binding ?: break

            // Loop every 1 sec
            val curTime = System.currentTimeMillis() / 1000L
            if (curTime != prevTime) {
                prevTime = curTime

                val stage = NavLogUtils.getFlightStage()
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

    private fun openAIPThread() {
        val tag = "openAIPThread"
        var prevTime = 0L
        var position= LatLng(0.0, 0.0)
        var prevPosition = LatLng(0.0, 0.0)

        while (true) {
            val a = activity as? MainActivity ?: break
            val b = _binding ?: break

            // Loop every 1 sec
            val curTime = System.currentTimeMillis() / 1000L
            if (curTime != prevTime) {
                prevTime = curTime

                if (mapReady) {
                    //val health = OpenAIPClient.checkHealth()
                    //Log.d(tag, Utils.formatJson(health!!))

                    //val airports = OpenAIPClient.getAirports("52.224527,20.963743", 15000)
                    //Log.d(tag, Utils.formatJson(airports!!))

                    a.runOnUiThread {
                        // Get current position
                        val cameraPosition = map.cameraPosition
                        position = cameraPosition.target
                    }
                    // If the position has changed
                    if (prevPosition.latitude != position.latitude || prevPosition.longitude != position.longitude) {
                        prevPosition = position
                        Log.d(tag, Utils.formatDouble(position.latitude, 6))
                        Log.d(tag, Utils.formatDouble(position.longitude, 6))
                    }
                }
            }
        }
    }
}