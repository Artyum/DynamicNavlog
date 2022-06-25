package com.artyum.dynamicnavlog

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDateTime
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds

data class PlanListItem(
    var id: String,
    var planName: String
)

data class Settings(
    var id: String = "",
    var planName: String = "",
    var departure: String = "",
    var destination: String = "",

    var planeId: String = "",
    var fob: Double? = null, //Fuel on board at takeoff
    var planeType: String = "",
    var planeReg: String = "",
    var planeTas: Double = 0.0,
    var planeFph: Double? = null,
    var planeTank: Double? = null,

    var windDir: Double = 0.0,
    var windSpd: Double = 0.0,

    var timeInUTC: Boolean = false,
    var keepScreenOn: Boolean = false,
    var gpsAssist: Boolean = true,
    var takeoffCoords: LatLng? = null,

    var spdUnits: Int = 0,
    var distUnits: Int = 0,
    var volUnits: Int = 0,

    var mapType: Int = GoogleMap.MAP_TYPE_NORMAL,
    var mapOrientation: Int = C.MAP_ORIENTATION_NORTH,
    var autoNext: Boolean = true,
    var recordTrace: Boolean = false,
    var mapFollow: Boolean = true,
    var tfDisplayToggle: Int = C.TF_DISPLAY_REM,
    var nextRadius: Int = C.DEFAULT_NEXT_RADIUS,
    var screenOrientation: Int = C.SCREEN_PORTRAIT
)

data class Timers(
    var offblock: LocalDateTime? = null,
    var takeoff: LocalDateTime? = null,
    var landing: LocalDateTime? = null,
    var onblock: LocalDateTime? = null,
    var flightTime: Long? = null,    // Seconds
    var blockTime: Long? = null,     // Seconds
    var groundTime: Long? = null     // Seconds
)

data class Airplane(
    var id: String = "",
    var type: String = "",
    var reg: String = "",
    var rmk: String = "",
    var tas: Double = 0.0,
    var fph: Double = 0.0,
    var tank: Double = 0.0,
    var spdUnits: Int = 0,
    var volUnits: Int = 0
)

data class FlightCalc(
    val wca: Double,
    val hdg: Double,
    val gs: Double,
    val time: Long?, // Time in seconds
    val fuel: Double?
)

data class SinCosAngle(
    val sina: Float,
    val cosa: Float
)

data class ReleaseOptions(
    val initializeAds: Boolean,
    val startBillingClient: Boolean
)

data class GpsData(
    var coords: LatLng? = null,
    var time: Long = 0L,
    var rawSpeed: Double = 0.0,
    var speed: Double = 0.0,
    var altitude: Double = 0.0,
    var bearing: Float? = null,
    var hAccuracy: Double = 0.0,
    var heartbeat: Boolean = false,
    var isValid: Boolean = false
)

data class Totals(
    var dist: Double = 0.0,
    var time: Long = 0,
    var fuel: Double = 0.0
)

object C {
    const val FORMAT_DATETIME = "yyyy-MM-dd  HH:mm"
    const val FORMAT_DATETIME_SEC = "yyyy-MM-dd  HH:mm:ss"
    const val FORMAT_TIME = "HH:mm"

    const val DNL_EXTENSION = ".dnl"
    const val GPX_EXTENSION = ".gpx"
    const val CSV_EXTENSION = ".csv"
    const val TRK_EXTENSION = ".trk"  // Trace
    const val JSON_EXTENSION = ".json"
    const val INI_SETTINGS = 1
    const val INI_TIMERS = 2
    const val INI_NAVLOG = 3
    const val INI_SETTINGS_STR = "[Settings]"
    const val INI_TIMERS_STR = "[Timers]"
    const val INI_NAVLOG_STR = "[Navlog]"
    const val INI_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    const val JSON_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    const val stateFileName = "current_state$JSON_EXTENSION"
    const val airplanesFile = "airplanes$JSON_EXTENSION"

    // Speed units
    const val SPD_KNOTS = 0
    const val SPD_MPH = 1
    const val SPD_KPH = 2

    // Distance units
    const val DIS_NM = 0
    const val DIS_SM = 1
    const val DIS_KM = 2

    // Fuel units
    const val VOL_USGAL = 0
    const val VOL_UKGAL = 1
    const val VOL_LITERS = 2

    const val ZULU_SIGN = "z"

    const val PRESSURE_INHG = 0
    const val PRESSURE_HPA = 1

    const val TEMP_F = 0
    const val TEMP_C = 1

    const val ELEV_FT = 0
    const val ELEV_M = 1

    const val DIST_THRESHOLD = 10
    const val TIME_THRESHOLD = 10

    const val H_DELIMITER = ":"
    const val M_DELIMITER = "\'"
    const val S_DELIMITER = "\""

    const val EARTH_RADIUS_SHORT_M = 6356752.3142
    const val EARTH_RADIUS_LONG_M = 6378137.0

    const val GPS_ALIVE_SEC = 30
    const val GPS_MINIMUM_RAWSPEED = 1
    const val MAX_ANGLE_INDICATOR = 10
    const val COORDS_PRECISION = 6
    const val LOCATION_PERMISSION_REQ_CODE = 99
    const val DEPARTURE_MARKER_TITLE = "Departure"
    const val TF_DISPLAY_CUR = 0
    const val TF_DISPLAY_REM = 1

    const val AUTO_TAKEOFF_SPEED_MPS = 20
    const val AUTO_LANDING_SPEED_MPS = 20   // 15=~30kt / 20=~40kt / 25=~50kt
    const val AUTO_NEXT_WAIT_SEC = 4        // Takeoff if speed is AUTO_TAKEOFF_SPEED_MPS for AUTO_NEXT_WAIT_SEC seconds

    const val DEFAULT_NEXT_RADIUS = 0       // Index in nextRadiusList array

    // Map option
    const val MAP_ORIENTATION_NORTH = 0
    const val MAP_ORIENTATION_TRACK = 1
    const val MAP_ORIENTATION_BEARING = 2

    //Screen orientation
    const val SCREEN_PORTRAIT = 0
    const val SCREEN_LANDSCAPE = 1
    const val SCREEN_SENSOR = 2

    // Limit in free version
    const val FREE_PURCHASE_DELAY_SEC = 10   // Time this time the purchase message is hidden
    const val FREE_WPT_NUMBER_LIMIT = 10     // Limit of waypoints in free version (disabled)

    // Flight stages
    const val STAGE_1_BEFORE_ENGINE_START = 1
    const val STAGE_2_ENGINE_RUNNING = 2
    const val STAGE_3_FLIGHT_IN_PROGRESS = 3
    const val STAGE_4_AFTER_LANDING = 4
    const val STAGE_5_AFTER_ENGINE_SHUTDOWN = 5

    // Track line width
    const val TRACK_WIDTH = 20f
    const val TRACK_INACTIVE_WIDTH = 10f
}

var navlogList = ArrayList<NavlogItem>()
val planList = ArrayList<PlanListItem>()
val airplaneList = ArrayList<Airplane>()
val tracePointsList = ArrayList<LatLng>()

var settings = Settings()
var timers = Timers()
val totals = Totals()
var editAirplaneID: String? = null

// Next circle radius in NM
val nextRadiusList = arrayListOf(0.5, 1.0, 2.0)

var serviceRunning = false
var locationSubscribed = false
var autoNextRunning = false
var isAppPurchased = false

var gpsData = GpsData()
val gpsMutex = Mutex()
var autoRefreshMap = false        // Refresh map on auto-next waypoint
var autoRefreshButtons = false    // Refresh buttons on HomePage

fun roundDouble(value: Double, precision: Int): Double = (value * 10.0.pow(precision)).roundToLong() / 10.0.pow(precision)

fun angleCalc(rad: Double): SinCosAngle {
    return SinCosAngle(sin(rad).toFloat(), cos(rad).toFloat())
}

fun paintWindCircle(imgView: ImageView, resources: Resources, course: Double, windDir: Double, hdg: Double, speedRatio: Double) {
    val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.isAntiAlias = true

    val customTypeface = resources.getFont(R.font.hind_vadodara)
    paint.typeface = customTypeface

    //paint.typeface = Typeface.create(ResourcesCompat.getFont(this, R.font.sail), Typeface.NORMAL)
    //paint.typeface = Typeface.create("Helvetica", Typeface.NORMAL)

    val x = canvas.width / 2f
    val y = canvas.height / 2f
    val radius = 190f
    val radiusCourse = radius * 0.5f
    val radiusHdg = radiusCourse * speedRatio.toFloat()
    val radiusArrow = radius * 0.2f
    val radiusShort = radius * 0.9f
    val angleArrow = 10f
    val smallCircleRadius = 5f

    var x1: Float
    var x2: Float
    var y1: Float
    var y2: Float
    var sc: SinCosAngle

    paint.textSize = 25f
    paint.style = Paint.Style.FILL
    paint.strokeWidth = 1f

    // HDG
    paint.color = ResourcesCompat.getColor(resources, R.color.cyan, null)
    canvas.drawText("HDG", 0f, 30f, paint)
    canvas.drawText(hdg.roundToInt().toString(), 0f, 60f, paint)

    // WIND
    paint.color = ResourcesCompat.getColor(resources, R.color.blue, null)
    canvas.drawText("WIND", 0f, 380f, paint)
    canvas.drawText(windDir.roundToInt().toString(), 0f, 350f, paint)

    // DTK
    paint.color = ResourcesCompat.getColor(resources, R.color.magenta, null)
    val textBounds = Rect()
    var txt = "DTK"
    paint.getTextBounds(txt, 0, txt.length, textBounds)
    canvas.drawText(txt, (canvas.width - textBounds.width() - 3).toFloat(), 30f, paint)
    txt = course.roundToInt().toString()
    paint.getTextBounds(txt, 0, txt.length, textBounds)
    canvas.drawText(txt, (canvas.width - textBounds.width() - 3).toFloat(), 60f, paint)

    canvas.rotate(-hdg.toFloat(), x, y)

    // Circle
    paint.strokeWidth = 1f
    paint.style = Paint.Style.STROKE
    paint.color = ResourcesCompat.getColor(resources, R.color.gray, null)
    canvas.drawCircle(x, y, radius, paint)
    paint.style = Paint.Style.FILL

    // Scale 5 deg
    paint.strokeWidth = 1.5f
    for (i in 0..72) {
        sc = angleCalc(deg2rad(i * 5.0 - 90.0))
        x1 = x + sc.cosa * radius
        y1 = y + sc.sina * radius
        x2 = x + sc.cosa * radiusShort * 1.04f
        y2 = y + sc.sina * radiusShort * 1.04f
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    // Scale 10 deg
    for (i in 0..36) {
        sc = angleCalc(deg2rad(i * 10.0 - 90.0))
        x1 = x + sc.cosa * radius
        y1 = y + sc.sina * radius
        x2 = x + sc.cosa * radiusShort
        y2 = y + sc.sina * radiusShort
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    // NEWS
    paint.strokeWidth = 1.5f
    paint.textSize = 30f
    paint.color = ResourcesCompat.getColor(resources, R.color.gray, null)
    val news = arrayOf("N", "E", "S", "W")
    x1 = x
    y1 = radius * 0.25f
    canvas.rotate(-90f, x, y)
    for (i in 0..3) {
        canvas.rotate(90f, x, y)
        val txtBounds = Rect()
        paint.getTextBounds(news[i], 0, news[i].length, txtBounds)
        canvas.drawText(news[i], x1 - txtBounds.width() / 2f, y1 + txtBounds.height() / 2f, paint)
    }

    // Degrees
    paint.textSize -= 5f
    canvas.rotate(60f, x, y)
    for (i in 0..11) {
        canvas.rotate(30f, x, y)
        if (i % 3 != 0) {
            var num = (i * 30).toString()
            num = (num.subSequence(0, num.length - 1)).toString()
            val txtBounds = Rect()
            paint.getTextBounds(num, 0, num.length, txtBounds)
            canvas.drawText(num, x1 - txtBounds.width() / 2f, y1 + txtBounds.height() / 2f, paint)
        }
    }

    canvas.rotate(30f, x, y)
    paint.strokeWidth = 3f

    // Course
    paint.color = ResourcesCompat.getColor(resources, R.color.magenta, null)
    sc = angleCalc(deg2rad(course - 90f))
    x2 = x + sc.cosa * radiusCourse
    y2 = y + sc.sina * radiusCourse
    canvas.drawLine(x, y, x2, y2, paint)
    x1 = x + sc.cosa * radius
    y1 = y + sc.sina * radius
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(x1, y1, smallCircleRadius, paint)
    paint.style = Paint.Style.FILL
    sc = angleCalc(deg2rad(course - 90f - angleArrow))
    x1 = x2 - sc.cosa * radiusArrow
    y1 = y2 - sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)
    sc = angleCalc(deg2rad(course - 90f + angleArrow))
    x1 = x2 - sc.cosa * radiusArrow
    y1 = y2 - sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)

    //Heading
    paint.color = ResourcesCompat.getColor(resources, R.color.cyan, null)
    sc = angleCalc(deg2rad(hdg - 90.0))
    x2 = x + sc.cosa * radiusHdg
    y2 = y + sc.sina * radiusHdg
    canvas.drawLine(x, y, x2, y2, paint)
    x1 = x + sc.cosa * radius
    y1 = y + sc.sina * radius
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(x1, y1, smallCircleRadius, paint)
    paint.style = Paint.Style.FILL
    sc = angleCalc(deg2rad(hdg - 90f - angleArrow))
    x1 = x2 - sc.cosa * radiusArrow
    y1 = y2 - sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)
    sc = angleCalc(deg2rad(hdg - 90f + angleArrow))
    x1 = x2 - sc.cosa * radiusArrow
    y1 = y2 - sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)

    // Wind
    paint.color = ResourcesCompat.getColor(resources, R.color.blue, null)
    sc = angleCalc(deg2rad(windDir - 90f))
    x1 = x + sc.cosa * radius
    y1 = y + sc.sina * radius
    x2 = x + sc.cosa * radiusCourse
    y2 = y + sc.sina * radiusCourse
    canvas.drawLine(x1, y1, x2, y2, paint)
    sc = angleCalc(deg2rad(windDir - 90f - angleArrow))
    x1 = x2 + sc.cosa * radiusArrow
    y1 = y2 + sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)
    sc = angleCalc(deg2rad(windDir - 90f + angleArrow))
    x1 = x2 + sc.cosa * radiusArrow
    y1 = y2 + sc.sina * radiusArrow
    canvas.drawLine(x1, y1, x2, y2, paint)

    imgView.setImageBitmap(bitmap)
    imgView.visibility = View.VISIBLE
}

fun flightCalculator(course: Double, windDir: Double, windSpd: Double, tas: Double, dist: Double? = null, fph: Double? = null): FlightCalc {
    var windSpdL = windSpd
    var tasL = tas
    var distL = dist

    // Convert to Knots
    if (settings.spdUnits != C.SPD_KNOTS) {
        if (settings.spdUnits == C.SPD_KPH) {
            windSpdL = kph2kt(windSpdL)
            tasL = kph2kt(tasL)
        }
        if (settings.spdUnits == C.SPD_MPH) {
            windSpdL = mph2kt(windSpdL)
            tasL = mph2kt(tasL)
        }
    }
    // Convert to Nautical miles
    if (dist != null && settings.distUnits != C.DIS_NM) {
        if (settings.distUnits == C.DIS_SM) distL = sm2nm(dist)
        if (settings.distUnits == C.DIS_KM) distL = km2nm(dist)
    }

    val wtAngle = deg2rad(course - windDir + 180f)
    val sinWca = windSpdL * sin(wtAngle) / tasL
    var wca = asin(sinWca)
    var gs = tasL * cos(wca) + windSpdL * cos(wtAngle)
    wca = rad2deg(wca)
    val hdg = normalizeBearing(course + wca)

    var time: Long? = null
    var fuel: Double? = null

    if (distL != null) {
        time = (distL / gs * 60f * 60f).toLong()
        if (fph != null) {
            fuel = distL / gs * fph
        }
    }

    // Convert back to plan units
    if (settings.spdUnits == C.SPD_KPH) gs = kt2kph(gs)
    if (settings.spdUnits == C.SPD_MPH) gs = kt2mph(gs)

    return FlightCalc(wca = wca, hdg = hdg, gs = gs, time = time, fuel = fuel)
}

fun isFlightInProgress(): Boolean {
    return isNavlogReady() && timers.takeoff != null && timers.landing == null
}

fun isEngineRunning(): Boolean {
    return timers.offblock != null && timers.onblock == null
}

fun isFlightOver(): Boolean {
    return timers.offblock != null && timers.takeoff != null && timers.landing != null
}

fun isSettingsReady(): Boolean {
    return settings.planeId != "" && settings.planeTas > settings.windSpd
}

fun convertSettingsSpdUnits(old: Int, new: Int) {
    Log.d("Global.kt", "convertSettingsSpdUnits")
    var ratio = 1.0
    if (old == C.SPD_KNOTS && new == C.SPD_KPH) ratio = kt2kph(1.0)
    if (old == C.SPD_KNOTS && new == C.SPD_MPH) ratio = kt2mph(1.0)

    if (old == C.SPD_MPH && new == C.SPD_KPH) ratio = mph2kph(1.0)
    if (old == C.SPD_MPH && new == C.SPD_KNOTS) ratio = mph2kt(1.0)

    if (old == C.SPD_KPH && new == C.SPD_KNOTS) ratio = kph2kt(1.0)
    if (old == C.SPD_KPH && new == C.SPD_MPH) ratio = kph2mph(1.0)

    settings.windSpd = settings.windSpd * ratio
    val a = getAirplaneByID(settings.planeId)
    if (a != null) {
        settings.planeTas = a.tas
    }
}

fun convertSettingsDistUnits(old: Int, new: Int) {
    Log.d("Global.kt", "convertSettingsDistUnits")
    var ratio = 1.0
    if (old == C.DIS_NM && new == C.DIS_KM) ratio = nm2km(1.0)
    if (old == C.DIS_NM && new == C.DIS_SM) ratio = nm2sm(1.0)

    if (old == C.DIS_SM && new == C.DIS_NM) ratio = sm2nm(1.0)
    if (old == C.DIS_SM && new == C.DIS_KM) ratio = sm2km(1.0)

    if (old == C.DIS_KM && new == C.DIS_NM) ratio = km2nm(1.0)
    if (old == C.DIS_KM && new == C.DIS_SM) ratio = km2sm(1.0)

    for (i in navlogList.indices) navlogList[i].distance = navlogList[i].distance!! * ratio
}

fun convertSettingsVolUnits(old: Int, new: Int) {
    Log.d("Global.kt", "convertSettingsVolUnits")
    var ratio = 1.0
    if (old == C.VOL_LITERS && new == C.VOL_USGAL) ratio = l2usgal(1.0)
    if (old == C.VOL_LITERS && new == C.VOL_UKGAL) ratio = l2ukgal(1.0)

    if (old == C.VOL_USGAL && new == C.VOL_LITERS) ratio = usgal2l(1.0)
    if (old == C.VOL_USGAL && new == C.VOL_UKGAL) ratio = usgal2ukgal(1.0)

    if (old == C.VOL_UKGAL && new == C.VOL_LITERS) ratio = ukgal2l(1.0)
    if (old == C.VOL_UKGAL && new == C.VOL_USGAL) ratio = ukgal2usgal(1.0)

    if (settings.fob != null) settings.fob = settings.fob!! * ratio
    val a = getAirplaneByID(settings.planeId)
    if (a != null) {
        settings.planeTank = a.tank
        settings.planeFph = a.fph
    }
}

fun formatDouble(value: Double?, precision: Int = 0): String {
    if (value == null || value.isNaN()) return ""
    var tmp = if (precision == 0) value.roundToInt().toString() else roundDouble(value, precision).toString()
    if (tmp.contains('.')) tmp = tmp.trimEnd('0').trimEnd('.')
    return tmp
}

fun getUnitsSpd(): String {
    when (settings.spdUnits) {
        C.SPD_KNOTS -> return "kt"
        C.SPD_MPH -> return "mph"
        C.SPD_KPH -> return "kph"
    }
    return ""
}

fun getUnitsDist(): String {
    when (settings.distUnits) {
        C.DIS_NM -> return "nm"
        C.DIS_SM -> return "miles"
        C.DIS_KM -> return "km"
    }
    return ""
}

fun getUnitsVolume(): String {
    when (settings.volUnits) {
        C.VOL_USGAL -> return "gal"
        C.VOL_UKGAL -> return "gal"
        C.VOL_LITERS -> return "lit"
    }
    return ""
}

fun getNextRadiusUnits(i: Int): String {
    return formatDouble(nextRadiusList[i], 1) + " nm"
}

fun distUnits2meters(d: Double): Double {
    when (settings.distUnits) {
        C.DIS_NM -> return nm2m(d)
        C.DIS_SM -> return sm2m(d)
        C.DIS_KM -> return km2m(d)
    }
    return 0.0
}

fun meters2distUnits(d: Double): Double {
    when (settings.distUnits) {
        C.DIS_NM -> return m2nm(d)
        C.DIS_SM -> return m2sm(d)
        C.DIS_KM -> return m2km(d)
    }
    return 0.0
}

fun getDoubleOrNull(value: String): Double? {
    var str = value.trim()
    val regex = Regex("[^0-9.,-]")
    str = regex.replace(str, "")
    return str.replace(",", ".").toDoubleOrNull()
}

fun clearString(str: String): String {
    //return str.replace(";", " ").trim()
    return str.trim()
}