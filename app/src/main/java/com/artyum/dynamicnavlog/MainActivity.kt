package com.artyum.dynamicnavlog

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.artyum.dynamicnavlog.databinding.ActivityMainBinding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    lateinit var bind: ActivityMainBinding
    lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // List of fragments without 'back arrow'
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.settingsFragment,
                R.id.navlogFragment,
                R.id.mapFragment,
                R.id.calcWindFragment,
                R.id.calcFuelFragment,
                R.id.calcTimeDistFragment,
                R.id.calcDensity2Fragment,
                R.id.calcUnitsFragment,
                R.id.airplaneListFragment,
                R.id.planListFragment,
                R.id.aboutFragment,
                R.id.timersFragment,
                R.id.optionsFragment
            ),
            bind.drawerLayout
        )

        setSupportActionBar(bind.toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        bind.bottomNav.setupWithNavController(navController)
        bind.drawerView.setupWithNavController(navController)

        // Navigate through bottom menu
        // Fix for error: Ignoring popBackStack to destination com.artyum.dynamicnavlog:id/homeFragment as it was not found on the current back stack
        bind.bottomNav.setOnItemSelectedListener { item ->
            Log.d(tag, "bottomNav->setOnItemSelectedListener->$item")
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }

                R.id.settingsFragment -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }

                R.id.mapFragment -> {
                    navController.navigate(R.id.mapFragment)
                    true
                }

                R.id.navlogFragment -> {
                    navController.navigate(R.id.navlogFragment)
                    true
                }

                else -> false
            }
        }

        // Setup LocationManager
        LocationManager.activity = this

        // Drawer menu
        val navView: NavigationView = findViewById(R.id.drawerView)

        // Initialize folders path
        //internalAppDir = getInternalAppDir()
        FileUtils.externalAppDir = getExternalAppDir()

        // New flight pLan
        navView.menu.findItem(R.id.drawerItemNew).setOnMenuItemClickListener {
            if (Utils.isEngineRunning()) {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(R.string.txtWarningFlightInProgressDialog).setCancelable(false).setPositiveButton(R.string.txtYes) { _, _ ->
                    newFlightPlan()
                    bind.drawerLayout.close()
                    navController.navigate(R.id.action_global_settingsFragment)
                }.setNegativeButton(R.string.txtNo) { dialog, _ ->
                    dialog.dismiss()
                    bind.drawerLayout.close()
                }
                val alert = builder.create()
                alert.show()
            } else {
                newFlightPlan()
                bind.drawerLayout.close()
                navController.navigate(R.id.action_global_settingsFragment)
            }
            true
        }

        // Flight plan list
        navView.menu.findItem(R.id.drawerItemOpen).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_planListFragment)
            true
        }

        // Airplanes list
        navView.menu.findItem(R.id.drawerAirplanes).setOnMenuItemClickListener {
            FileUtils.loadAirplaneList()
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_airplaneListFragment)
            true
        }

        // Options / Preferences
        navView.menu.findItem(R.id.drawerOptions).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_optionsFragment)
            true
        }

        // Wind calculator
        navView.menu.findItem(R.id.drawerWindCalc).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_calcWindFragment)
            true
        }

        // Fuel calculator
        navView.menu.findItem(R.id.drawerFuelCalc).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_calcFuelFragment)
            true
        }

        // Time & Distance
        navView.menu.findItem(R.id.drawerTimeAndDistance).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_calcTimeDistFragment)
            true
        }

        // Density altitude
        navView.menu.findItem(R.id.drawerDensity2Calc).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_calcDensity2Fragment)
            true
        }

        // Units converter
        navView.menu.findItem(R.id.drawerUnitsCalc).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_calcUnitsFragment)
            true
        }

        // About
        navView.menu.findItem(R.id.drawerItemAbout).setOnMenuItemClickListener {
            bind.drawerLayout.close()
            navController.navigate(R.id.action_global_aboutFragment)
            true
        }

        // OFF-BLOCK
        bind.btnOffBlock.setOnClickListener {
            NavLogUtils.setStageOffBlock()
            ServiceUtils.startNavlogService(this)
            displayButtons()
        }

        // TAKEOFF
        bind.btnTakeoff.setOnClickListener {
            NavLogUtils.setStageTakeoff()
            ServiceUtils.startNavlogService(this)
            displayButtons()
        }

        // BACK
        bind.btnPrevWpt.setOnClickListener {
            NavLogUtils.setStageBack()
            //drawHomeWinStar()
            displayButtons()
        }

        // NEXT
        bind.btnNextWpt.setOnClickListener {
            NavLogUtils.setStageNext()
            //drawHomeWinStar()
            displayButtons()
        }

        // NEXT LAND
        bind.btnNextLand.setOnClickListener {
            NavLogUtils.setStageLanding()
            displayButtons()
        }

        // ON-BLOCK
        bind.btnOnBlock.setOnClickListener {
            NavLogUtils.setStageOnBLock()
            ServiceUtils.stopNavlogService(this)
            displayButtons()
        }

        // Clear unused files
        FileUtils.clearFiles(C.GPX_EXTENSION)
        FileUtils.clearFiles(C.CSV_EXTENSION)

        // Load state
        FileUtils.loadOptions()
        FileUtils.loadAirplaneList()
        FileUtils.loadState()
        NavLogUtils.calcNavlog()

        // Service
        if (Utils.isEngineRunning()) ServiceUtils.startNavlogService(this)

        // Screen orientation
        setScreenOrientation()

        // Initialize fused location client
        LocationManager.locationSetup()
        LocationManager.locationSubscribe()

        // GPS health check
        CoroutineScope(CoroutineName("gpsCoroutine")).launch { gpsHealthCheckThread() }

        // Auto next waypoint thread
        CoroutineScope(CoroutineName("gpsCoroutine")).launch { detectFlightStageThread() }

        // Track recording thread
        CoroutineScope(CoroutineName("gpsCoroutine")).launch { recordTraceThread() }

        // Display bottom Buttons
        displayButtons()
    }

    // Menus // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Set screen orientation
    fun setScreenOrientation() {
        when (State.options.screenOrientation) {
            C.SCREEN_PORTRAIT -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            C.SCREEN_LANDSCAPE -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            C.SCREEN_SENSOR -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.optionsExportCsv -> {
                val fileName = FileUtils.savePlanAsCsv()
                if (fileName != "") shareFile(fileName, "text/csv") else Toast.makeText(this, getString(R.string.txtExportERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsExportGpx -> {
                val fileName = FileUtils.savePlanAsGpx()
                if (fileName != "") shareFile(fileName, "application/gpx+xml") else Toast.makeText(this, getString(R.string.txtExportERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsLoadLastTrace -> {
                if (FileUtils.loadTrace()) Toast.makeText(this, getString(R.string.txtLoadTraceOK), Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, getString(R.string.txtLoadTraceFailed), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsExportTraceGpx -> {
                val fileName = FileUtils.saveTraceAsGpx()
                if (fileName != "") shareFile(fileName, "application/gpx+xml") else Toast.makeText(this, getString(R.string.txtExportTraceERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionResetFlight -> {
                val msg = if (Utils.isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg).setCancelable(false).setPositiveButton(R.string.txtYes) { _, _ ->
                    resetFlight()
                    //Toast.makeText(this, getString(R.string.txtResetDone), Toast.LENGTH_SHORT).show()
                    //navController.navigate(R.id.homeFragment)
                    displayButtons()
                }.setNegativeButton(R.string.txtNo) { dialog, _ ->
                    dialog.dismiss()
                }
                val alert = builder.create()
                alert.show()
            }

            R.id.optionCopyPlan -> {
                val msg = if (Utils.isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg).setCancelable(false).setPositiveButton(R.string.txtYes) { _, _ ->
                    if (FileUtils.copyFlightPlan("Copy")) {
                        resetFlight()
                        Toast.makeText(this, getString(R.string.txtCopyDone), Toast.LENGTH_SHORT).show()
                        navController.navigate(R.id.settingsFragment)
                    } else Toast.makeText(this, getString(R.string.txtEmptyPlanName), Toast.LENGTH_SHORT).show()
                }.setNegativeButton(R.string.txtNo) { dialog, _ ->
                    dialog.dismiss()
                }
                val alert = builder.create()
                alert.show()
            }

            R.id.optionInvertTrack -> {
                val msg = if (Utils.isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg).setCancelable(false).setPositiveButton(R.string.txtYes) { _, _ ->
                    if (FileUtils.copyFlightPlan("Inverted")) {
                        if (NavLogUtils.invertNavlog()) {
                            resetFlight()
                            Toast.makeText(this, getString(R.string.txtReverseDone), Toast.LENGTH_SHORT).show()
                            navController.navigate(R.id.settingsFragment)
                        } else Toast.makeText(this, getString(R.string.txtReverseError), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, getString(R.string.txtEmptyPlanName), Toast.LENGTH_SHORT).show()
                }.setNegativeButton(R.string.txtNo) { dialog, _ ->
                    dialog.dismiss()
                }
                val alert = builder.create()
                alert.show()
            }
        }
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }

    // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

    private fun getInternalAppDir(): File? {
        return applicationContext.filesDir
    }

    private fun getExternalAppDir(): File? {
        return applicationContext.getExternalFilesDir(null)
    }

    private fun newFlightPlan() {
        Log.d(tag, "newFlightPlan")
        FileUtils.saveState()
        State.navlogList.clear()
        SettingUtils.resetAllSettings()
        resetFlight()
        Utils.resetRadials()
        if (State.options.gpsAssist) LocationManager.locationSubscribe() else LocationManager.locationUnsubscribe()
    }

    fun resetFlight() {
        Log.d(tag, "resetFlight")
        State.tracePointsList.clear()
        TimeUtils.resetTimers()
        NavLogUtils.resetNavlog()
        ServiceUtils.stopNavlogService(this)
        NavLogUtils.calcNavlog()
        FileUtils.saveState()
        displayButtons()
        Vars.globalRefresh = true
    }

    fun displayButtons() {
        ButtonController.hide(bind)
        val currentFragment = navController.currentDestination.toString()
        if (currentFragment.contains("HomeFragment") or currentFragment.contains("MapFragment")) ButtonController.display(bind)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == C.LOCATION_PERMISSION_REQ_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationManager.locationSubscribe()
            } else {
                Log.d(tag, "GPS Permissions NOT granted by user")
            }
        }
    }

    private fun shareFile(fileName: String, mimetype: String) {
        val msg = getString(R.string.txtPlanSavedAs) + ": $fileName"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        // https://trendoceans.com/how-to-fix-exposed-beyond-app-through-clipdata-item-geturi/
        val share = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = mimetype
            val stream = FileProvider.getUriForFile(
                applicationContext,
                //BuildConfig.APPLICATION_ID + "." + localClassName + ".provider",  //com.artyum.dynamicnavlog.MainActivity.provider
                BuildConfig.APPLICATION_ID + ".provider",                  //com.artyum.dynamicnavlog.provider
                File(FileUtils.externalAppDir, fileName)
            )
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_STREAM, stream)
        }, null)
        startActivity(share)
    }

    private suspend fun gpsHealthCheckThread() {
        var gpsFailCnt = 0

        while (true) {
            //Log.d(tag, "gpsHealthCheckThread: $gpsFailCnt")
            if (State.options.gpsAssist) {
                Vars.gpsMutex.withLock {
                    if (!Vars.gpsData.heartbeat) {
                        if (gpsFailCnt >= C.GPS_ALIVE_SEC) {
                            // GPS lost
                            Vars.gpsData.isValid = false
                            if (Utils.isFlightInProgress()) runOnUiThread { bind.gpsLostBox.visibility = View.VISIBLE }
                        } else gpsFailCnt += 1
                    } else {
                        gpsFailCnt = 0
                        Vars.gpsData.heartbeat = false
                        Vars.gpsData.isValid = true
                        runOnUiThread { bind.gpsLostBox.visibility = View.GONE }
                    }
                }
            } else {
                Vars.gpsData.isValid = false
                runOnUiThread { bind.gpsLostBox.visibility = View.GONE }
            }
            delay(1000)
        }
    }

    private suspend fun detectFlightStageThread() {
        var speedCnt = 0
        var gps: GpsData
        var prevTime = 0L
        var prevDist = 0.0

        while (true) {
            // Loop every 1 sec
            val curTime = System.currentTimeMillis() / 1000L
            if (curTime != prevTime && SettingUtils.isAutoNextEnabled()) {
                prevTime = curTime
                //Log.d(tag, "autoNextThread")

                Vars.gpsMutex.withLock { gps = Vars.gpsData.copy() }

                if (gps.isValid) {
                    val stage = NavLogUtils.getFlightStage()

                    if (stage == C.STAGE_2_ENGINE_RUNNING) {
                        // Detect takeoff
                        if (gps.speedMps > State.options.autoTakeoffSpd) speedCnt += 1 else speedCnt = 0
                        if (speedCnt >= C.AUTO_NEXT_WAIT_SEC) {
                            // Auto Takeoff
                            NavLogUtils.setStageTakeoff()
                            ServiceUtils.startNavlogService(this)
                            speedCnt = 0
                        }
                    } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
                        val item = NavLogUtils.getNavlogCurrentItemId()
                        val last = NavLogUtils.getNavlogLastActiveItemId()

                        if (State.navlogList[item].pos != null) {
                            val dist = Units.m2nm(GPSUtils.calcDistance(gps.pos!!, State.navlogList[item].pos!!))
                            if (dist <= C.nextRadiusList[State.options.nextRadiusIndex]) {
                                if (item < last) {
                                    // Auto Next Waypoint
                                    // Detect passed waypoint when airplane is in the circle and the distance from waypoint is increasing
                                    if (prevDist == 0.0) prevDist = dist
                                    else if (dist > prevDist) {
                                        //Log.d(tag, "Auto next wpt")
                                        prevDist = 0.0
                                        NavLogUtils.setStageNext()
                                    } else prevDist = dist
                                } else {
                                    // Auto Landing
                                    if (gps.speedMps < State.options.autoLandingSpd) speedCnt += 1 else speedCnt = 0
                                    if (speedCnt >= C.AUTO_NEXT_WAIT_SEC) {
                                        //Log.d(tag, "Auto landing")
                                        NavLogUtils.setStageLanding()
                                        runOnUiThread { displayButtons() }
                                        speedCnt = 0
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // gps not valid
                    speedCnt = 0
                }
            }
        }
    }

    private suspend fun recordTraceThread() {
        var gps: GpsData
        var prevPos = LatLng(0.0, 0.0)

        while (true) {
            val stage = NavLogUtils.getFlightStage()
            if (stage > C.STAGE_1_BEFORE_ENGINE_START && stage < C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
                Vars.gpsMutex.withLock { gps = Vars.gpsData.copy() }
                if (gps.isValid && prevPos != gps.pos) {
                    if (GPSUtils.calcDistance(prevPos, gps.pos!!) >= C.MINIMAL_TRACE_POINTS_DIST) {
                        prevPos = gps.pos!!
                        State.tracePointsList.add(gps.pos!!)
                        FileUtils.saveTracePoint(gps.pos!!)
                    }
                }
            }
            delay(5000)
        }
    }
}