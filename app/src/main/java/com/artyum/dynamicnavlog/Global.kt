package com.artyum.dynamicnavlog

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDateTime
import kotlin.math.*

data class Settings(
    @Volatile
    var planId: String = "",
    var planName: String = "",
    var departure: String = "",
    var destination: String = "",
    var airplaneId: String = "",
    var fob: Double = 0.0,                // Fuel-On-Board / Fuel for takeoff
    var windDir: Double = 0.0,
    var windSpd: Double = 0.0,
    var takeoffPos: LatLng? = null,
    var mapType: Int = GoogleMap.MAP_TYPE_NORMAL,
    var mapFollow: Boolean = true,
    var tfDisplayToggle: Int = C.TF_DISPLAY_REM,  // Switch to display time and fuel on the Navlog screen
)

data class NavlogItem(
    var dest: String,                     // Waypoint name
    var tt: Double? = null,               // True track
    var d: Double? = null,                // Declination
    var mt: Double? = null,               // Magnetic track
    var dist: Double? = null,             // Leg length (nm)
    var wca: Double? = null,              // Wind Correction Angle
    var hdg: Double? = null,              // Heading
    var gs: Double? = null,               // Ground speed (kt)
    var time: Long? = null,               // Leg time in seconds
    var timeIncrement: Long? = null,      // Time incrementar
    var fuel: Double? = null,             // Fuel required for leg
    var fuelRemaining: Double? = null,    // Fuel remaining
    var eta: LocalDateTime? = null,       // ETA
    var ata: LocalDateTime? = null,       // ATA
    var remarks: String = "",             // Waypoint notes
    var active: Boolean = true,           // Is waypoint active
    var current: Boolean = false,         // Is waypoint current
    var pos: LatLng? = null               // Waypoint coordinates
)

data class Radial(
    var angle: Double,
    var dist: Double,
    var pos1: LatLng,
    var pos2: LatLng
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

data class Options(
    var spdUnits: Int = 0,
    var distUnits: Int = 0,
    var volUnits: Int = 0,
    var screenOrientation: Int = C.SCREEN_SENSOR,
    var timeInUTC: Boolean = false,
    var keepScreenOn: Boolean = false,
    var autoTakeoffSpd: Double = kt2mps(40.0),      // Minimum speed for takeoff detection in m/s
    var autoLandingSpd: Double = kt2mps(30.0),      // Maximum speed for landing detection in m/s
    var mapOrientation: Int = C.MAP_ORIENTATION_NORTH,
    var displayTrace: Boolean = true,
    var drawWindArrow: Boolean = true,
    var drawRadials: Boolean = true,
    var drawRadialsMarkers: Boolean = true,
    var gpsAssist: Boolean = true,
    var autoNext: Boolean = true,
    var nextRadiusIndex: Int = C.DEFAULT_NEXT_RADIUS,  // Index in nextRadiusList
    var showHints: Boolean = true
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

data class GpsData(
    var pos: LatLng? = null,
    var time: Long = 0L,
    var speedMps: Double = 0.0,
    var speedKt: Double = 0.0,
    var bearing: Float? = null,
    //var altitude: Double = 0.0,
    //var hAccuracy: Double = 0.0,
    var heartbeat: Boolean = false,
    var isValid: Boolean = false
)

data class Totals(
    var dist: Double = 0.0,
    var time: Long = 0,
    var fuel: Double = 0.0
)

data class PlanListItem(
    var id: String,
    var planName: String
)

data class FlightData(
    val wca: Double,
    val hdg: Double,
    val gs: Double,
    val time: Long?, // Time in seconds
    val fuel: Double?,
    val dist: Double?
)

data class SinCosAngle(
    val sina: Float,
    val cosa: Float
)

data class ReleaseOptions(
    val initializeAds: Boolean,
    val startBillingClient: Boolean
)

object C {
    const val FORMAT_DATETIME = "yyyy-MM-dd   HH:mm"
    const val FORMAT_DATETIME_SEC = "yyyy-MM-dd   HH:mm:ss"
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

    const val stateFile = "current_state$JSON_EXTENSION"
    const val airplanesFile = "airplanes$JSON_EXTENSION"
    const val optionsFile = "options$JSON_EXTENSION"

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

    const val SIGN_ZULU = "ᶻ"
    const val SIGN_EAST = "ᴱ"
    const val SIGN_WEST = "ᵂ"

    const val PRESSURE_INHG = 0
    const val PRESSURE_HPA = 1

    const val TEMP_F = 0
    const val TEMP_C = 1

    const val ELEV_FT = 0
    const val ELEV_M = 1

    // Threshold for precision digits
    const val DIST_THRESHOLD = 10
    const val TIME_THRESHOLD = 10
    const val VOL_THRESHOLD = 10

    // Time delimiters
    const val H_DELIMITER = ":"
    const val M_DELIMITER = "\'"
    const val S_DELIMITER = "\""
    //const val H_DELIMITER = "h"
    //const val M_DELIMITER = "m"
    //const val S_DELIMITER = "s"
    // Subscript
    //const val H_DELIMITER = "\u2095"
    //const val M_DELIMITER = "\u2098"
    //const val S_DELIMITER = "\u209B"

    const val EARTH_RADIUS_SHORT_M = 6356752.3142
    const val EARTH_RADIUS_LONG_M = 6378137.0

    const val GPS_ALIVE_SEC = 30

    const val MAX_ANGLE_INDICATOR = 10
    const val POS_PRECISION = 6
    const val LOCATION_PERMISSION_REQ_CODE = 99
    const val TF_DISPLAY_CUR = 0
    const val TF_DISPLAY_REM = 1

    const val GPS_MINIMUM_RAW_SPEED = 2          // Minimum speed to calculate ETE 3m/s = ~6kt = ~10kph
    const val AUTO_TAKEOFF_MIN_SPEED_KT = 1.0    // 10kt = 19kph  15kt = 28kph  20kt = 37kph
    const val AUTO_LANDING_MIN_SPEED_KT = 1.0    // 30kt = 55kph  40kt = 74kph  50kt = 92kph
    const val AUTO_NEXT_WAIT_SEC = 3             // Takeoff if speed is AUTO_TAKEOFF_SPEED_MPS for AUTO_NEXT_WAIT_SEC seconds

    const val DEFAULT_NEXT_RADIUS = 0            // Index in nextRadiusList array

    // Map option
    const val MAP_ORIENTATION_NORTH = 0
    const val MAP_ORIENTATION_TRACK = 1
    const val MAP_ORIENTATION_BEARING = 2
    const val MAP_ZOOM_PADDING = 170

    //Screen orientation
    const val SCREEN_PORTRAIT = 0
    const val SCREEN_LANDSCAPE = 1
    const val SCREEN_SENSOR = 2              // Auto

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

    //Trace
    const val MINIMAL_TRACE_POINTS_DIST = 100.0    // Minimal distance between points to record trace in meters

    // Marker line and circle types
    const val MAP_ITEM_TRACK = 0
    const val MAP_ITEM_RADIAL = 1
    const val RADIAL_RADIUS_M = 3000.0
}

var navlogList = ArrayList<NavlogItem>()
var radialList = ArrayList<Radial>()
var planList = ArrayList<PlanListItem>()
var airplaneList = ArrayList<Airplane>()
var tracePointsList = ArrayList<LatLng>()

var settings = Settings()
var airplane = Airplane()
var options = Options()
var timers = Timers()

var totals = Totals()
val nextRadiusList = arrayListOf(0.5, 1.0, 2.0)  // Next circle radius in NM

var serviceRunning = false
var locationSubscribed = false
var isAppPurchased = false

var gpsData = GpsData()
var gpsMutex = Mutex()

@Volatile
var globalRefresh = false   // Refresh home, navlog and map pages on flight stage or waypoint change

fun generateStringId(): String {
    var randomString = ""
    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    // No. of combinations
    // 2 ->               1 891
    // 3 ->              37 820
    // 4 ->             557 845
    // 5 ->           6 471 002
    // 6 ->          61 474 519
    // 7 ->         491 796 152
    // 8 ->       3 381 098 545
    // 9 ->      20 286 591 270
    // 10 ->    107 518 933 731
    // 15 -> 93 052 749 919 920

    randomString = (1..10)
        .map { _ -> kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("");

    return randomString
}

fun roundDouble(value: Double, precision: Int): Double = (value * 10.0.pow(precision)).roundToLong() / 10.0.pow(precision)

fun angleCalc(rad: Double): SinCosAngle {
    return SinCosAngle(sin(rad).toFloat(), cos(rad).toFloat())
}

fun generateWindCircle(imgView: ImageView, resources: Resources, course: Double, windDir: Double, hdg: Double, speedRatio: Double) {
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

    // Heading
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

fun generateWindArrow(imgView: ImageView, resources: Resources, bearing: Double) {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.isAntiAlias = true

    val customTypeface = resources.getFont(R.font.hind_vadodara)
    paint.typeface = customTypeface

    // Center
    val x = canvas.width / 2f
    val y = canvas.height / 2f

    // Map bearing to canvas angle
    val angle = -bearing - 90.0

    val len = canvas.width / 2
    val len2 = (canvas.width / 2.7).toFloat()

    paint.strokeWidth = 3f
    paint.style = Paint.Style.FILL
    paint.color = ResourcesCompat.getColor(resources, R.color.windArrow, null)

    // Back line
    var sc: SinCosAngle = angleCalc(deg2rad(angle))
    var x1 = x + sc.cosa * len
    var y1 = y + sc.sina * len
    canvas.drawLine(x, y, x1, y1, paint)

    // Front line
    sc = angleCalc(deg2rad(angle + 180))
    x1 = x + sc.cosa * len
    y1 = y + sc.sina * len
    canvas.drawLine(x, y, x1, y1, paint)

    // Arrow 1
    sc = angleCalc(deg2rad(angle + 10))
    val x2 = x1 + sc.cosa * len2
    val y2 = y1 + sc.sina * len2
    canvas.drawLine(x1, y1, x2, y2, paint)

    // Arrow 2
    sc = angleCalc(deg2rad(angle - 10))
    val x3 = x1 + sc.cosa * len2
    val y3 = y1 + sc.sina * len2
    canvas.drawLine(x2, y2, x3, y3, paint)

    imgView.setImageBitmap(bitmap)
}

fun flightCalculator(course: Double, windDir: Double, windSpd: Double, tas: Double, dist: Double? = null, fob: Double? = null, fph: Double? = null): FlightData {
    val wtAngle = deg2rad(course - windDir + 180f)
    val sinWca = windSpd * sin(wtAngle) / tas

    // WCA
    var wca = asin(sinWca)

    // GS
    val gs = tas * cos(wca) + windSpd * cos(wtAngle)

    // HDG
    wca = rad2deg(wca)
    val hdg = normalizeBearing(course + wca)

    var time: Long? = null
    var fuel: Double? = null
    var dis: Double? = null

    if (dist != null) {
        // Time
        time = (dist / gs * 3600.0).toLong()

        // Fuel required
        if (fph != null && fph > 0.0) {
            fuel = dist / gs * fph
        }
    } else {
        if (fob != null && fph != null && fph > 0.0) {
            // Distance
            val h = fob / fph
            dis = gs * h

            // Time
            time = (h * 3600.0).toLong()
        }
    }

    return FlightData(wca = wca, hdg = hdg, gs = gs, time = time, fuel = fuel, dist = dis)
}

fun isFlightInProgress(): Boolean {
    return isNavlogReady() && timers.takeoff != null && timers.landing == null
}

fun isEngineRunning(): Boolean {
    return timers.offblock != null && timers.onblock == null
}

fun isSettingsReady(): Boolean {
    return settings.airplaneId != "" && airplane.tas > settings.windSpd
}

fun formatDouble(value: Double?, precision: Int = 0): String {
    if (value == null || value.isNaN()) return ""
    var tmp = if (precision == 0) value.roundToInt().toString() else roundDouble(value, precision).toString()
    if (tmp.contains('.')) tmp = tmp.trimEnd('0').trimEnd('.')
    return tmp
}

fun getDoubleOrNull(value: String?): Double? {
    if (value == null) return null
    var str = value.trim()
    val regex = Regex("[^0-9.,-]")
    str = regex.replace(str, "")
    return str.replace(",", ".").toDoubleOrNull()
}

fun clearString(str: String?): String {
    return str?.trim() ?: ""
}

fun resetRadials() {
    radialList.clear()
}
