package com.artyum.dynamicnavlog

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.*
import com.android.billingclient.api.*
import com.artyum.dynamicnavlog.databinding.ActivityMainBinding
import com.google.android.gms.ads.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var bind: ActivityMainBinding
    lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var mAdView: AdView

    //Billing
    private lateinit var billingClient: BillingClient
    private var billingItem: SkuDetails? = null
    private var queryPurchasesRetryCnt = (60 * 10) / 5 // 10 minutes

    // GPS
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        val view = bind.root
        setContentView(view)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.findNavController()

        // List of fragments without 'back arrow'
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment, R.id.settingsFragment, R.id.navlogFragment, R.id.mapFragment,
                R.id.purchaseFragment, R.id.calcWindFragment, R.id.calcFuelFragment, R.id.calcTimeDistFragment, R.id.calcDensity2Fragment, R.id.calcUnitsFragment,
                R.id.airplaneListFragment, R.id.planListFragment, R.id.aboutFragment, R.id.timersFragment, R.id.optionsFragment
            ),
            bind.drawerLayout
        )

        setSupportActionBar(bind.toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        bind.bottomNav.setupWithNavController(navController)
        bind.drawerView.setupWithNavController(navController)

        // Drawer menu
        val navView: NavigationView = findViewById(R.id.drawerView)

        // Initialize folders path
        //internalAppDir = getInternalAppDir()
        externalAppDir = getExternalAppDir()

        // Purchase
        navView.menu.findItem(R.id.drawerItemPurchase).setOnMenuItemClickListener {
            navController.navigate(PurchaseFragmentDirections.actionGlobalPurchaseFragment())
            bind.drawerLayout.close()
            true
        }

        // New flight pLan
        navView.menu.findItem(R.id.drawerItemNew).setOnMenuItemClickListener {
            if (isEngineRunning()) {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(R.string.txtWarningFlightInProgressDialog)
                    .setCancelable(false)
                    .setPositiveButton(R.string.txtYes) { _, _ ->
                        newFlightPlan()
                        navController.navigate(SettingsFragmentDirections.actionGlobalSettingsFragment())
                        bind.drawerLayout.close()
                    }
                    .setNegativeButton(R.string.txtNo) { dialog, _ ->
                        dialog.dismiss()
                        bind.drawerLayout.close()
                    }
                val alert = builder.create()
                alert.show()
            } else {
                newFlightPlan()
                navController.navigate(SettingsFragmentDirections.actionGlobalSettingsFragment())
                bind.drawerLayout.close()
            }
            true
        }

        // Flight plan list
        navView.menu.findItem(R.id.drawerItemOpen).setOnMenuItemClickListener {
            navController.navigate(PlanListFragmentDirections.actionGlobalPlanListFragment())
            bind.drawerLayout.close()
            true
        }

        // Airplanes list
        navView.menu.findItem(R.id.drawerAirplanes).setOnMenuItemClickListener {
            loadAirplaneList()
            navController.navigate(AirplaneListFragmentDirections.actionGlobalAirplaneListFragment())
            bind.drawerLayout.close()
            true
        }

        // Preferences
        navView.menu.findItem(R.id.drawerOptions).setOnMenuItemClickListener {
            navController.navigate(OptionsFragmentDirections.actionGlobalOptionsFragment())
            bind.drawerLayout.close()
            true
        }

        // Wind calculator
        navView.menu.findItem(R.id.drawerWindCalc).setOnMenuItemClickListener {
            navController.navigate(CalcWindFragmentDirections.actionGlobalCalcWindFragment())
            bind.drawerLayout.close()
            true
        }

        // Fuel calculator
        navView.menu.findItem(R.id.drawerFuelCalc).setOnMenuItemClickListener {
            navController.navigate(CalcFuelFragmentDirections.actionGlobalCalcFuelFragment())
            bind.drawerLayout.close()
            true
        }

        // Time & Distance
        navView.menu.findItem(R.id.drawerTimeAndDistance).setOnMenuItemClickListener {
            navController.navigate(CalcTimeDistFragmentDirections.actionGlobalCalcTimeDistFragment())
            bind.drawerLayout.close()
            true
        }

        // Density altitude
        navView.menu.findItem(R.id.drawerDensity2Calc).setOnMenuItemClickListener {
            navController.navigate(CalcDensity2FragmentDirections.actionGlobalCalcDensity2Fragment())
            bind.drawerLayout.close()
            true
        }

        // Units converter
        navView.menu.findItem(R.id.drawerUnitsCalc).setOnMenuItemClickListener {
            navController.navigate(CalcUnitsFragmentDirections.actionGlobalCalcUnitsFragment())
            bind.drawerLayout.close()
            true
        }

        // About
        navView.menu.findItem(R.id.drawerItemAbout).setOnMenuItemClickListener {
            navController.navigate(AboutFragmentDirections.actionGlobalAboutFragment())
            bind.drawerLayout.close()
            true
        }

        // OFF-BLOCK
        bind.btnOffBlock.setOnClickListener {
            setStageOffBlock()
            startNavlogService()
            displayButtons()
        }

        // TAKEOFF
        bind.btnTakeoff.setOnClickListener {
            setStageTakeoff()
            startNavlogService()
            displayButtons()
        }

        // BACK
        bind.btnPrevWpt.setOnClickListener {
            setStageBack()
            //drawHomeWinStar()
            displayButtons()
        }

        // NEXT
        bind.btnNextWpt.setOnClickListener {
            setStageNext()
            //drawHomeWinStar()
            displayButtons()
        }

        // NEXT LAND
        bind.btnNextLand.setOnClickListener {
            setStageLanding()
            displayButtons()
        }

        // ON-BLOCK
        bind.btnOnBlock.setOnClickListener {
            setStageOnBLock()
            stopNavlogService()
            displayButtons()
        }

        // Check purchase file to enable or disable ads
        checkPurchaseFile()

        // Billing client
        if (releaseOptions.startBillingClient) startBillingClient() else disableAds()

        // Convert all saves to xml
        //TODO delete this after some time v1.2.0 2022-06
        convertAllDnlToJson()

        // Clear unused files
        clearFiles(C.GPX_EXTENSION)
        clearFiles(C.CSV_EXTENSION)

        // Load state
        loadOptions()
        loadAirplaneList()
        loadState()
        calcNavlog()

        // Service
        if (isEngineRunning()) startNavlogService()

        // Screen orientation
        setScreenOrientation()

        // Initialize fused location client
        locationSetup()
        locationSubscribe()

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.optionsExportCsv -> {
                val fileName = savePlanAsCsv()
                if (fileName != "") shareFile(fileName, "text/csv") else Toast.makeText(this, getString(R.string.txtExportERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsExportGpx -> {
                val fileName = savePlanAsGpx()
                if (fileName != "") shareFile(fileName, "application/gpx+xml") else Toast.makeText(this, getString(R.string.txtExportERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsLoadLastTrace -> {
                if (loadTrace()) Toast.makeText(this, getString(R.string.txtLoadTraceOK), Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, getString(R.string.txtLoadTraceFailed), Toast.LENGTH_SHORT).show()
            }

            R.id.optionsExportTraceGpx -> {
                val fileName = saveTraceAsGpx()
                if (fileName != "") shareFile(fileName, "application/gpx+xml") else Toast.makeText(this, getString(R.string.txtExportTraceERR), Toast.LENGTH_SHORT).show()
            }

            R.id.optionResetFlight -> {
                val msg = if (isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.txtYes) { _, _ ->
                        resetFlight()
                        //Toast.makeText(this, getString(R.string.txtResetDone), Toast.LENGTH_SHORT).show()
                        //navController.navigate(R.id.homeFragment)
                        displayButtons()
                    }
                    .setNegativeButton(R.string.txtNo) { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
            }

            R.id.optionCopyPlan -> {
                val msg = if (isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.txtYes) { _, _ ->
                        copyFlightPlan("Copy")
                        resetFlight()
                        Toast.makeText(this, getString(R.string.txtCopyDone), Toast.LENGTH_SHORT).show()
                        navController.navigate(R.id.settingsFragment)
                    }
                    .setNegativeButton(R.string.txtNo) { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
            }

            R.id.optionInvertTrack -> {
                val msg = if (isFlightInProgress()) R.string.txtWarningFlightInProgressDialog else R.string.txtWarningAreYouSure
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.txtYes) { _, _ ->
                        copyFlightPlan("Inverted")
                        invertNavlog()
                        resetFlight()
                        Toast.makeText(this, getString(R.string.txtReverseDone), Toast.LENGTH_SHORT).show()
                        navController.navigate(R.id.settingsFragment)
                    }
                    .setNegativeButton(R.string.txtNo) { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
            }
        }
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }

    // Billing // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //
    private fun startBillingClient() {
        Log.d(TAG, "startBillingClient")

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "onBillingSetupFinished")
                    queryAvailableProducts()
                    //queryPurchaseHistory()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "onBillingServiceDisconnected")
                checkPurchaseFile()
            }
        })
    }

    private fun queryAvailableProducts() {
        Log.d(TAG, "queryAvailableProducts")

        val skuList = ArrayList<String>()
        skuList.add(Release.GOOGLE_PLAY_PRODUCT_ID)

        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)

        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            Log.d(TAG, "querySkuDetailsAsync")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !skuDetailsList.isNullOrEmpty()) {
                Log.d(TAG, "querySkuDetailsAsync success")
                for (skuDetails in skuDetailsList) {
                    val json = JSONObject(skuDetails.originalJson)
                    val productId = json.getString("productId")
                    if (productId == Release.GOOGLE_PLAY_PRODUCT_ID) {
                        Log.d(TAG, "querySkuDetailsAsync item found!")
                        billingItem = skuDetails
                        setPurchaseOptionVisibility(true)
                    }
                    //println("Title: ${skuDetails.title}")
                    //println("Description: ${skuDetails.description}")
                    //println("Price: ${skuDetails.price}")
                    //println("Currency: ${skuDetails.priceCurrencyCode}")
                    //println("JSON: ${skuDetails.originalJson}")
                }
            } else {
                Log.d(TAG, "querySkuDetailsAsync item NOT found")
                //Thread.sleep(1000)
                //queryAvailableProducts()
            }
        }
    }

    private fun queryPurchases() {
        Log.d(TAG, "queryPurchasesAsync")
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isNotEmpty()) {
                    var itemExists = false
                    for (purchase in purchases) {
                        val json = JSONObject(purchase.originalJson)
                        val productId = json.getString("productId")
                        if (productId == Release.GOOGLE_PLAY_PRODUCT_ID && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            Log.d(TAG, "queryPurchasesAsync item found!")
                            itemExists = true
                            if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
                            checkPurchaseToken(purchase.purchaseToken)
                        }
                    }
                    if (!itemExists) {
                        Log.d(TAG, "queryPurchasesAsync item NOT found!")
                        enableAds()
                    }
                } else {
                    if (queryPurchasesRetryCnt > 0) {
                        Log.d(TAG, "queryPurchasesAsync RETRY $queryPurchasesRetryCnt")
                        Thread.sleep(5000)
                        queryPurchases()
                        queryPurchasesRetryCnt -= 1
                    } else {
                        Log.d(TAG, "queryPurchasesAsync RETRY END")
                    }
                }
            } else {
                Log.d(TAG, "queryPurchasesAsync response NOT OK; responseCode: " + billingResult.responseCode)
            }
        }
    }

    fun launchPurchase() {
        Log.d(TAG, "launchPurchase")

        if (billingItem != null) {
            val json = JSONObject(billingItem!!.originalJson)
            val productId = json.getString("productId")
            //Log.d(TAG, "launchPurchase productId: $productId")
            if (productId == Release.GOOGLE_PLAY_PRODUCT_ID) {
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(billingItem!!)
                    .build()
                billingClient.launchBillingFlow(this, billingFlowParams)
            } else {
                Log.d(TAG, "billingItem item NOT found")
                Toast.makeText(this, getString(R.string.txtCannotConnectToGooglePlay), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.txtCannotConnectToGooglePlay), Toast.LENGTH_LONG).show()
            Log.d(TAG, "billingItem is null")
        }
    }

    private val purchaseUpdateListener = PurchasesUpdatedListener { billingResult, purchases ->
        Log.d(TAG, "purchaseUpdateListener")

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                Log.d(TAG, "PURCHASE responseCode OK")
                //println(purchase.originalJson)
                acknowledgePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "PURCHASE USER CANCELED")
        } else {
            // Payment declined
            Log.d(TAG, "PURCHASE OTHER PROBLEM")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        Log.d(TAG, "acknowledgePurchase")

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "PurchaseState.PURCHASED")
            if (!purchase.isAcknowledged) {
                Log.d(TAG, "!purchase.isAcknowledged")
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "acknowledgePurchase OK")
                        savePurchaseToken(purchase.purchaseToken)
                    } else {
                        Log.d(TAG, "acknowledgePurchase NOT OK")
                        Log.d(TAG, "billingResponseCode: " + billingResult.responseCode)
                        Log.d(TAG, "billingDebugMessage: " + billingResult.debugMessage)
                    }
                }
            } else {
                Log.d(TAG, "acknowledgePurchase isAcknowledged")
            }
        } else {
            Log.d(TAG, "acknowledgePurchase purchaseState NOT PURCHASED")
        }
    }

    // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //
    // Advertisement

    private fun enableAds() {
        Log.d(TAG, "enableAds")
        isAppPurchased = false
        mAdView = findViewById(R.id.adView)

        mAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                runOnUiThread { bind.adStaticInfo.visibility = View.GONE }
                runOnUiThread { bind.boxAdView.visibility = View.VISIBLE }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                val msg = "enableAds onAdFailedToLoad Code: ${adError.code} Message: ${adError.message}"
                Log.d(TAG, msg)
                runOnUiThread { bind.adStaticInfo.visibility = View.VISIBLE }
                runOnUiThread { bind.boxAdView.visibility = View.GONE }
            }
        }
        runOnUiThread { bind.adBox.visibility = View.VISIBLE }
        runOnUiThread { bind.boxAdView.visibility = View.GONE }

        deletePurchaseFile()

        if (releaseOptions.initializeAds) {
            Log.d(TAG, "initializeAds")
            MobileAds.initialize(applicationContext)
            val adRequest = AdRequest.Builder().build()
            runOnUiThread { mAdView.loadAd(adRequest) }
        } else {
            CoroutineScope(CoroutineName("main")).launch { delayPurchaseNotice() }
        }
    }

    private fun disableAds() {
        Log.d(TAG, "disableAds")
        isAppPurchased = true
        runOnUiThread { bind.adBox.visibility = View.GONE }
        setPurchaseOptionVisibility(false)
    }

    private fun setPurchaseOptionVisibility(visibility: Boolean) {
        Log.d(TAG, "setPurchaseOptionVisibility")
        var visible = visibility
        if (visible && isAppPurchased) {
            Log.d(TAG, "isAppPurchased=true")
            visible = false
        }
        Log.d(TAG, "groupPurchase visible: $visible")
        val navView: NavigationView = findViewById(R.id.drawerView)
        runOnUiThread { navView.menu.setGroupVisible(R.id.groupPurchase, visible) }
    }

    // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //
    // Purchase file

    private fun checkPurchaseToken(purchaseToken: String) {
        Log.d(TAG, "checkPurchaseToken")
        val file = File(getInternalAppDir(), Release.GOOGLE_PLAY_PRODUCT_ID)
        if (file.exists()) {
            val lines = file.readLines()
            for (line in lines) {
                if (line == purchaseToken) {
                    Log.d(TAG, "purchaseToken check OK")
                    disableAds()
                } else {
                    Log.d(TAG, "purchaseToken check NOT OK")
                    enableAds()
                    file.delete()
                }
            }
        } else {
            savePurchaseToken(purchaseToken)
        }
    }

    private fun savePurchaseToken(purchaseToken: String) {
        Log.d(TAG, "savePurchaseToken")
        val file = File(getInternalAppDir(), Release.GOOGLE_PLAY_PRODUCT_ID)
        file.writeText(purchaseToken)
        disableAds()
    }

    private fun checkPurchaseFile() {
        Log.d(TAG, "checkPurchaseFile")
        val file = File(getInternalAppDir(), Release.GOOGLE_PLAY_PRODUCT_ID)
        if (file.exists()) disableAds()
        else enableAds()
    }

    private fun deletePurchaseFile() {
        Log.d(TAG, "deletePurchaseFile")
        val file = File(getInternalAppDir(), Release.GOOGLE_PLAY_PRODUCT_ID)
        if (file.exists()) file.delete()
    }

    // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

    private fun getInternalAppDir(): File? {
        return applicationContext.filesDir
    }

    private fun getExternalAppDir(): File? {
        return applicationContext.getExternalFilesDir(null)
    }

    private fun newFlightPlan() {
        saveState()
        navlogList.clear()
        resetAllSettings()
        resetFlight()
        resetRadials()
        if (options.gpsAssist) locationSubscribe() else locationUnsubscribe()
    }

    fun resetFlight() {
        tracePointsList.clear()
        resetTimers()
        resetNavlog()
        stopNavlogService()
        calcNavlog()
        saveState()
        displayButtons()
        globalRefresh = true
    }

    fun displayButtons() {
        /*
        Buttons structure
        btnBox                // Main box with all buttons
          - btnOffBlock
          - btnTakeoff
          - btnBoxPrevNext    // Box with Back/Next
              - btnPrevWpt
              - btnNextWpt
              - btnNextLand
          - btnLanding
          - btnOnBlock
        */

        // Hide all buttons
        hideButtons()

        // Display buttons only on specific Fragments
        val currentFragment = navController.currentDestination.toString()
        if (!currentFragment.contains("HomeFragment") and !currentFragment.contains("MapFragment")) return

        val stage = getFlightStage()

        if (stage == C.STAGE_1_BEFORE_ENGINE_START) {
            if (isNavlogReady()) bind.btnOffBlock.visibility = View.VISIBLE
        } else if (stage == C.STAGE_2_ENGINE_RUNNING) {
            if (!isAutoNextEnabled()) bind.btnTakeoff.visibility = View.VISIBLE
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (!isAutoNextEnabled()) {
                val item = getNavlogCurrentItemId()
                val first = getNavlogFirstActiveItemId()
                val last = getNavlogLastActiveItemId()
                bind.btnBoxPrevNext.visibility = View.VISIBLE
                if (item < last) {
                    if (item == first) disableBtnPrev() else enableBtnPrev()
                    showBtnNext()
                    hideBtnNextLand()
                } else {
                    hideBtnNext()
                    enableBtnPrev()
                    showBtnNextLand()
                }
            }
        } else if (stage == C.STAGE_4_AFTER_LANDING) {
            bind.btnOnBlock.visibility = View.VISIBLE
        }
    }

    private fun hideButtons() {
        bind.btnBoxPrevNext.visibility = View.GONE
        bind.btnOffBlock.visibility = View.GONE
        bind.btnTakeoff.visibility = View.GONE
        bind.btnLanding.visibility = View.GONE
        bind.btnOnBlock.visibility = View.GONE
    }

    private fun disableBtnPrev() {
        bind.btnPrevWpt.isEnabled = false
        (bind.btnPrevWpt as MaterialButton).setStrokeColorResource(R.color.grayTransparent)
        (bind.btnPrevWpt as MaterialButton).setTextColor(bind.btnPrevWpt.context.getColor(R.color.grayTransparent))
    }

    private fun enableBtnPrev() {
        bind.btnPrevWpt.isEnabled = true
        (bind.btnPrevWpt as MaterialButton).setStrokeColorResource(R.color.colorPrimaryTransparent)
        (bind.btnPrevWpt as MaterialButton).setTextColor(bind.btnPrevWpt.context.getColor(R.color.colorPrimaryTransparent))
    }

    private fun disableBtnNext() {
        bind.btnNextWpt.isEnabled = false
        (bind.btnNextWpt as MaterialButton).background.setTint(bind.btnNextWpt.context.getColor(R.color.grayTransparent2))
    }

    private fun enableBtnNext() {
        bind.btnNextWpt.isEnabled = true
        (bind.btnNextWpt as MaterialButton).background.setTint(bind.btnNextWpt.context.getColor(R.color.colorPrimaryTransparent))
    }

    private fun hideBtnNext() {
        bind.btnNextWpt.visibility = View.GONE
        disableBtnNext()
    }

    private fun showBtnNext() {
        bind.btnNextWpt.visibility = View.VISIBLE
        enableBtnNext()
    }

    private fun showBtnNextLand() {
        bind.btnNextLand.visibility = View.VISIBLE
    }

    private fun hideBtnNextLand() {
        bind.btnNextLand.visibility = View.GONE
    }

    fun setScreenOrientation() {
        when (options.screenOrientation) {
            C.SCREEN_PORTRAIT -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            C.SCREEN_LANDSCAPE -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            C.SCREEN_SENSOR -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun startNavlogService() {
        if (!serviceRunning) {
            serviceRunning = true
            val serviceIntent = Intent(this, Service::class.java)
            startService(serviceIntent)
        }
    }

    private fun stopNavlogService() {
        serviceRunning = false
        val serviceIntent = Intent(this, Service::class.java)
        stopService(serviceIntent)
    }

    // GPS features
    private fun locationSetup() {
        Log.d(TAG, "locationSetup")
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // https://developer.android.com/training/location/change-location-settings
        locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            maxWaitTime = 1000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(l: LocationResult) {
                super.onLocationResult(l)
                CoroutineScope(CoroutineName("gpsCoroutine")).launch { setGpsData(l.lastLocation) }
            }
        }
    }

    fun locationSubscribe() {
        if (options.gpsAssist && !locationSubscribed) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage(R.string.txtLocationMessage)
                    .setCancelable(false)
                    .setPositiveButton(R.string.txtOk) { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), C.LOCATION_PERMISSION_REQ_CODE)
                    }
                val alert = builder.create()
                alert.show()
            } else {
                Log.d(TAG, "locationSubscribe")
                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                locationSubscribed = true
            }
        }
    }

    fun locationUnsubscribe() {
        Log.d(TAG, "locationUnsubscribe")

        val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) Log.d(TAG, "Location Callback removed")
            else Log.d(TAG, "Failed to remove Location Callback")
            locationSubscribed = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == C.LOCATION_PERMISSION_REQ_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationSubscribe()
            } else {
                Log.d(TAG, "GPS Permissions NOT granted by user")
            }
        }
    }

    private suspend fun setGpsData(loc: Location?) {
        if (loc == null) return
        gpsMutex.withLock {
            gpsData.time = loc.time

            gpsData.pos = (LatLng(roundDouble(loc.latitude, C.POS_PRECISION), roundDouble(loc.longitude, C.POS_PRECISION)))
            gpsData.speedMps = loc.speed.toDouble()
            gpsData.speedKt = mps2kt(gpsData.speedMps)

            //gpsData.hAccuracy = loc.accuracy.toDouble()  // Get the estimated horizontal accuracy of this location, radial, in meters (68%)
            //gpsData.altitude = loc.altitude              // Altitude if available, in meters above the WGS 84 reference ellipsoid.
            if (loc.hasBearing()) gpsData.bearing = loc.bearing else gpsData.bearing = null

            gpsData.heartbeat = true
            //println("GPS time: ${gpsData.time}")
            //println(gpsData.pos!!.latitude.toString() + " " + gpsData.pos!!.longitude.toString() + " rawSpeed=" + formatDouble(gpsData.rawSpeed, 2) + " spd=" + formatDouble(gpsData.speed, 2))
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
                File(externalAppDir, fileName)
            )
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_STREAM, stream)
        }, null)
        startActivity(share)
    }

    private suspend fun gpsHealthCheckThread() {
        var gpsFailCnt = 0

        while (true) {
            //Log.d(TAG, "gpsHealthCheckThread: $gpsFailCnt")
            if (options.gpsAssist) {
                gpsMutex.withLock {
                    if (!gpsData.heartbeat) {
                        if (gpsFailCnt >= C.GPS_ALIVE_SEC) {
                            // GPS lost
                            gpsData.isValid = false
                            if (isFlightInProgress()) runOnUiThread { bind.gpsLostBox.visibility = View.VISIBLE }
                        } else gpsFailCnt += 1
                    } else {
                        gpsFailCnt = 0
                        gpsData.heartbeat = false
                        gpsData.isValid = true
                        runOnUiThread { bind.gpsLostBox.visibility = View.GONE }
                    }
                }
            } else {
                gpsData.isValid = false
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
            if (curTime != prevTime && isAutoNextEnabled()) {
                prevTime = curTime
                //Log.d(TAG, "autoNextThread")

                gpsMutex.withLock { gps = gpsData }

                if (gps.isValid) {
                    val stage = getFlightStage()

                    if (stage == C.STAGE_2_ENGINE_RUNNING) {
                        // Detect takeoff
                        if (gps.speedMps > options.autoTakeoffSpd) speedCnt += 1 else speedCnt = 0
                        if (speedCnt >= C.AUTO_NEXT_WAIT_SEC) {
                            // Auto Takeoff
                            setStageTakeoff()
                            startNavlogService()
                            speedCnt = 0
                        }
                    } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
                        val item = getNavlogCurrentItemId()
                        val last = getNavlogLastActiveItemId()

                        if (navlogList[item].pos != null) {
                            val dist = m2nm(calcDistance(gps.pos!!, navlogList[item].pos!!))
                            if (dist <= nextRadiusList[options.nextRadiusIndex]) {
                                if (item < last) {
                                    // Auto Next Waypoint
                                    // Detect passed waypoint when airplane is in the circle and the distance from waypoint is increasing
                                    if (prevDist == 0.0) prevDist = dist
                                    else if (dist > prevDist) {
                                        //Log.d(TAG, "Auto next wpt")
                                        prevDist = 0.0
                                        setStageNext()
                                    } else prevDist = dist
                                } else {
                                    // Auto Landing
                                    if (gps.speedMps < options.autoLandingSpd) speedCnt += 1 else speedCnt = 0
                                    if (speedCnt >= C.AUTO_NEXT_WAIT_SEC) {
                                        //Log.d(TAG, "Auto landing")
                                        setStageLanding()
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
            val stage = getFlightStage()
            if (stage > C.STAGE_1_BEFORE_ENGINE_START && stage < C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
                gpsMutex.withLock { gps = gpsData }
                if (gps.isValid && prevPos != gps.pos) {
                    if (calcDistance(prevPos, gps.pos!!) >= C.MINIMAL_TRACE_POINTS_DIST) {
                        prevPos = gps.pos!!
                        tracePointsList.add(gps.pos!!)
                        saveTracePoint(gps.pos!!)
                    }
                }
            }
            delay(5000)
        }
    }

    // Countdown to hide "support the developer message"
    private suspend fun delayPurchaseNotice() {
        for (i in C.FREE_PURCHASE_DELAY_SEC downTo 1) {
            runOnUiThread { bind.purchaseCountdown.text = i.toString() }
            delay(1000)
        }
        runOnUiThread { bind.adBox.visibility = View.GONE }
    }
}