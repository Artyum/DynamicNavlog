package com.artyum.dynamicnavlog

import kotlin.math.PI

// Convert Units - Measurement Unit Converter
// https://www.convertunits.com/

// Angle
fun deg2rad(deg: Double): Double = deg * PI / 180f
fun rad2deg(rad: Double): Double = rad * 180f / PI

// Temperature
fun f2c(v: Double): Double = (v - 32.0) / 1.8
fun c2f(v: Double): Double = v * 1.8 + 32.0
fun k2c(v: Double): Double = v - 273.15
fun c2k(v: Double): Double = v + 273.15

// Distance
fun sm2nm(v: Double): Double = v * 0.86897624190065
fun sm2m(v: Double): Double = v * 1609.344
fun sm2km(v: Double): Double = v * 1.609344
fun km2nm(v: Double): Double = v * 0.5399568034557235
fun km2sm(v: Double): Double = v * 0.62137119223733
fun m2nm(v: Double): Double = v * 0.0005399568034557236
fun m2sm(v: Double): Double = v * 0.00062137119223733
fun m2km(v: Double): Double = v * 0.001
fun m2ft(v: Double): Double = v * 3.280839895013123
fun km2m(v: Double): Double = v * 1000.0
fun km2ft(v: Double): Double = v * 3280.8398950131
fun ft2nm(v: Double): Double = v * 0.0001645788336933
fun ft2m(v: Double): Double = v * 0.3048
fun nm2sm(v: Double): Double = v * 1.1507794480235
fun nm2km(v: Double): Double = v * 1.852
fun nm2m(v: Double): Double = v * 1852.0
fun nm2ft(v: Double): Double = v * 6076.1154855643

// Speed
fun mph2kt(v: Double): Double = v * 0.86897624190065
fun kph2kt(v: Double): Double = v * 0.5399568034557235
fun mps2kt(v: Double): Double = v * 1.9438444924406
fun mps2mph(v: Double): Double = v * 2.2369362920544025
fun mps2kph(v: Double): Double = v * 3.6
fun fpm2kt(v: Double): Double = v * 0.0098747300215983
fun kt2mph(v: Double): Double = v * 1.1507794480235
fun kt2kph(v: Double): Double = v * 1.852
fun kt2mps(v: Double): Double = v * 0.51444444444444
fun kt2fpm(v: Double): Double = v * 101.26859142607
fun mph2kph(v: Double): Double = v * 1.609344
fun kph2mph(v: Double): Double = v * 0.621371192

// Pressure
fun mmhg2inhg(v: Double): Double = v * 0.039370079197446
fun hpa2inhg(v: Double): Double = v * 0.029529983071445
fun atm2inhg(v: Double): Double = v * 29.921252401895
fun inhg2mmhg(v: Double): Double = v * 25.399999704976
fun inhg2hpa(v: Double): Double = v * 33.863886666667
fun inhg2atm(v: Double): Double = v * 0.033421057652767

// Weight & Mass
fun lb2kg(v: Double): Double = v * 0.45359237
fun oz2kg(v: Double): Double = v * 0.028349523125
fun kg2lb(v: Double): Double = v * 2.2046226218488
fun kg2oz(v: Double): Double = v * 35.27396194958

// Volume
fun usgal2l(v: Double): Double = v * 3.7854118
fun ukgal2l(v: Double): Double = v * 4.54609
fun l2usgal(v: Double): Double = v * 0.26417205124156
fun l2ukgal(v: Double): Double = v * 0.21996924829908776
fun usgal2ukgal(v: Double): Double = v * 0.8326741881485
fun ukgal2usgal(v: Double): Double = v * 1.2009499204287

fun distUnits2meters(d: Double): Double {
    when (options.distUnits) {
        C.DIS_NM -> return nm2m(d)
        C.DIS_SM -> return sm2m(d)
        C.DIS_KM -> return km2m(d)
    }
    return 0.0
}

fun meters2distUnits(d: Double): Double {
    when (options.distUnits) {
        C.DIS_NM -> return m2nm(d)
        C.DIS_SM -> return m2sm(d)
        C.DIS_KM -> return m2km(d)
    }
    return 0.0
}

fun getUnitsSpd(): String {
    when (options.spdUnits) {
        C.SPD_KNOTS -> return "kt"
        C.SPD_MPH -> return "mph"
        C.SPD_KPH -> return "kph"
    }
    return ""
}

fun getUnitsDis(): String {
    when (options.distUnits) {
        C.DIS_NM -> return "nm"
        C.DIS_SM -> return "sm"
        C.DIS_KM -> return "km"
    }
    return ""
}

fun getUnitsVol(): String {
    when (options.volUnits) {
        C.VOL_USGAL -> return "gal"
        C.VOL_UKGAL -> return "gal"
        C.VOL_LITERS -> return "l"
    }
    return ""
}

// Convert to display units / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / /

fun toUnitsSpd(v: Double?): Double? {
    if (v == null) return null
    return when (options.spdUnits) {
        C.SPD_MPH -> kt2mph(v)
        C.SPD_KPH -> kt2kph(v)
        else -> v
    }
}

fun toUnitsDis(v: Double?): Double? {
    if (v == null) return null
    return when (options.distUnits) {
        C.DIS_SM -> nm2sm(v)
        C.DIS_KM -> nm2km(v)
        else -> v
    }
}

fun toUnitsVol(v: Double?): Double? {
    if (v == null) return null
    return when (options.volUnits) {
        C.VOL_USGAL -> l2usgal(v)
        C.VOL_UKGAL -> l2ukgal(v)
        else -> v
    }
}

// Convert from user to internal units -> kt / nm / l / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / / /

fun fromUnitsSpd(v: Double?): Double? {
    if (v == null) return null
    return when (options.spdUnits) {
        C.SPD_MPH -> mph2kt(v)
        C.SPD_KPH -> kph2kt(v)
        else -> v
    }
}

fun fromUnitsDis(v: Double?): Double? {
    if (v == null) return null
    return when (options.distUnits) {
        C.DIS_SM -> sm2nm(v)
        C.DIS_KM -> km2nm(v)
        else -> v
    }
}

fun fromUnitsVol(v: Double?): Double? {
    if (v == null) return null
    return when (options.volUnits) {
        C.VOL_USGAL -> usgal2l(v)
        C.VOL_UKGAL -> ukgal2l(v)
        else -> v
    }
}