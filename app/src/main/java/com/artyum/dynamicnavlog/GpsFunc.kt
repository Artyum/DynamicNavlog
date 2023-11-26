package com.artyum.dynamicnavlog

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// http://www.movable-type.co.uk/scripts/latlong.html
object GPSUtils {
    // Calculate distance in meters between coordinates
    fun calcDistance(c1: LatLng, c2: LatLng): Double {
        val lat1 = Convert.deg2rad(c1.latitude)
        val lat2 = Convert.deg2rad(c2.latitude)
        val dfi = Convert.deg2rad(c2.latitude - c1.latitude) / 2.0
        val dla = Convert.deg2rad(c2.longitude - c1.longitude) / 2.0

        val a = sin(dfi) * sin(dfi) + cos(lat1) * cos(lat2) * sin(dla) * sin(dla)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))

        return c * calcEarthRadius(c1.latitude)
    }

    // Calculate destination point given distance and bearing from start point (distance in meters)
    fun calcDestinationPos(from: LatLng, bearing: Double, distance: Double): LatLng {
        val lat1 = Convert.deg2rad(from.latitude)
        val lon1 = Convert.deg2rad(from.longitude)
        val brg = Convert.deg2rad(bearing)
        val dR = distance / calcEarthRadius(from.latitude)

        val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(dR) * cos(lat1), cos(dR) - sin(lat1) * sin(lat2))

        return LatLng(Utils.roundDouble(Convert.rad2deg(lat2), C.POS_PRECISION), Utils.roundDouble(normalizeLongitude(Convert.rad2deg(lon2)), C.POS_PRECISION))
    }

    // Calculate the radius of the Earth on a given latitude
    private fun calcEarthRadius(latitude: Double): Double {
        val lat = Convert.deg2rad(latitude)
        val a = C.EARTH_RADIUS_LONG_M
        val b = C.EARTH_RADIUS_SHORT_M

        val q = a * a * cos(lat)
        val w = b * b * sin(lat)
        val e = a * cos(lat)
        val r = b * sin(lat)

        return sqrt((q * q + w * w) / (e * e + r * r))
    }

    // Calculate bearing between two points
    fun calcBearing(c1: LatLng, c2: LatLng): Double {
        val lat1 = Convert.deg2rad(c1.latitude)
        val lon1 = Convert.deg2rad(c1.longitude)
        val lat2 = Convert.deg2rad(c2.latitude)
        val lon2 = Convert.deg2rad(c2.longitude)

        val y = sin(lon2 - lon1) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon2 - lon1)
        val t = atan2(y, x)

        return normalizeBearing(Convert.rad2deg(t))
    }

    // Calculate angle from-destination-current
    fun calcBearingAngle(destination: LatLng, current: LatLng, from: LatLng): Double {
        val b1 = calcBearing(destination, current)
        val b2 = calcBearing(destination, from)
        return normalizeLongitude(b1 - b2)
    }

    // Returns magnetic declination at coordinates
    // -E / +W
    fun getDeclination(c: LatLng): Double {
        val gf = GeomagneticField(c.latitude.toFloat(), c.longitude.toFloat(), 0f, System.currentTimeMillis())
        return -gf.declination.toDouble()
    }

    // Make bearing in 0.0..360.0 deg
    fun normalizeBearing(deg: Double): Double {
        return (deg + 360.0) % 360.0
    }

    // Make longitude in -180.0..180.0
    private fun normalizeLongitude(deg: Double): Double {
        return (deg + 540.0) % 360.0 - 180.0
    }
}

object LocationManager {
    lateinit var activity: AppCompatActivity
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private const val tag = "LocationManager"

    fun locationSetup() {
        Log.d(tag, "locationSetup")
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

        // https://developer.android.com/training/location/change-location-settings
        // https://developer.android.com/reference/android/location/LocationRequest.Builder#setDurationMillis(long)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setIntervalMillis(1000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(l: LocationResult) {
                super.onLocationResult(l)
                CoroutineScope(CoroutineName("gpsCoroutine")).launch { setGpsData(l.lastLocation) }
            }
        }
    }

    fun locationSubscribe() {
        if (State.options.gpsAssist && !Vars.isLocationSubscribed) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder = AlertDialog.Builder(activity)
                builder.setMessage(R.string.txtLocationMessage).setCancelable(false).setPositiveButton(R.string.txtOk) { _, _ ->
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), C.LOCATION_PERMISSION_REQ_CODE)
                }
                val alert = builder.create()
                alert.show()
            } else {
                Log.d(tag, "locationSubscribe")
                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                Vars.isLocationSubscribed = true
            }
        }
    }

    fun locationUnsubscribe() {
        Log.d(tag, "locationUnsubscribe")

        val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) Log.d(tag, "Location Callback removed")
            else Log.d(tag, "Failed to remove Location Callback")
            Vars.isLocationSubscribed = false
        }
    }

    suspend fun setGpsData(loc: Location?) {
        if (loc == null) return
        Vars.gpsMutex.withLock {
            if (!Vars.isLocationSubscribed) return

            Vars.gpsData.time = loc.time
            Vars.gpsData.pos = (LatLng(Utils.roundDouble(loc.latitude, C.POS_PRECISION), Utils.roundDouble(loc.longitude, C.POS_PRECISION)))
            Vars.gpsData.speedMps = loc.speed.toDouble()
            Vars.gpsData.speedKt = Convert.mps2kt(Vars.gpsData.speedMps)

            //gpsData.hAccuracy = loc.accuracy.toDouble()  // Get the estimated horizontal accuracy of this location, radial, in meters (68%)
            //gpsData.altitude = loc.altitude              // Altitude if available, in meters above the WGS 84 reference ellipsoid.
            if (loc.hasBearing()) Vars.gpsData.bearing = loc.bearing else Vars.gpsData.bearing = null

            Vars.gpsData.heartbeat = true
            //println("GPS time: ${gpsData.time}")
            //println(gpsData.pos!!.latitude.toString() + " " + gpsData.pos!!.longitude.toString() + " rawSpeed=" + Utils.formatDouble(gpsData.rawSpeed, 2) + " spd=" + Utils.formatDouble(gpsData.speed, 2))
        }
    }
}
