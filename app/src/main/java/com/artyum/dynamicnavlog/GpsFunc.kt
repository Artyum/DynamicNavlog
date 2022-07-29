package com.artyum.dynamicnavlog

import android.hardware.GeomagneticField
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

// http://www.movable-type.co.uk/scripts/latlong.html

// Calculate distance in meters between coordinates
fun calcDistance(c1: LatLng, c2: LatLng): Double {
    val lat1 = deg2rad(c1.latitude)
    val lat2 = deg2rad(c2.latitude)
    val dfi = deg2rad(c2.latitude - c1.latitude) / 2.0
    val dla = deg2rad(c2.longitude - c1.longitude) / 2.0

    val a = sin(dfi) * sin(dfi) + cos(lat1) * cos(lat2) * sin(dla) * sin(dla)
    val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))

    return c * calcEarthRadius(c1.latitude)
}

// Calculate destination point given distance and bearing from start point (distance in meters)
fun calcDestinationPos(from: LatLng, bearing: Double, distance: Double): LatLng {
    val lat1 = deg2rad(from.latitude)
    val lon1 = deg2rad(from.longitude)
    val brg = deg2rad(bearing)
    val dR = distance / calcEarthRadius(from.latitude)

    val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(brg))
    val lon2 = lon1 + atan2(sin(brg) * sin(dR) * cos(lat1), cos(dR) - sin(lat1) * sin(lat2))

    return LatLng(roundDouble(rad2deg(lat2), C.COORDS_PRECISION), roundDouble(normalizeLongitude(rad2deg(lon2)), C.COORDS_PRECISION))
}

// Calculate the radius of the Earth on a given latitude
fun calcEarthRadius(latitude: Double): Double {
    val lat = deg2rad(latitude)
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
    val lat1 = deg2rad(c1.latitude)
    val lon1 = deg2rad(c1.longitude)
    val lat2 = deg2rad(c2.latitude)
    val lon2 = deg2rad(c2.longitude)

    val y = sin(lon2 - lon1) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon2 - lon1)
    val t = atan2(y, x)

    return normalizeBearing(rad2deg(t))
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
fun normalizeLongitude(deg: Double): Double {
    return (deg + 540.0) % 360.0 - 180.0
}