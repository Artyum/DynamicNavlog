package com.artyum.dynamicnavlog

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class HomeDist(var dist: String = "", var sign: Boolean = false, var pct: String = "", var progress: Int = 0, var overflow: Boolean = false, var distTravelled: String = "")
data class HomeEte(var ete: String = "", var sw: String = "", var sign: Boolean = false)
data class HomeTTL(var ttl: String = "", var pct: String = "", var progress: Int = 0)
data class HomeFuelTime(var ftl: String = "", var ftlmark: String = "", var engineTime: String = "", var fuelTime: String = "", var fuelRemaining: String = "", var fuelPct: String = "", var fuelPctBar: Int = 0)
data class HomeDtkAngleBar(var left: Int, var right: Int, var hit: Boolean = true)

class HomeItem() {
    val stage: Int
    var gps = GpsData()
    val item: Int
    val prev: Int
    val next: Int
    val first: Int
    val last: Int
    var prevTime: LocalDateTime? = null
    var eta: LocalDateTime? = null
    var eteSec: Long = 0
    var trackAngle: Double = 0.0
    var dmt: Double = 0.0
    var estFlightTimeSec: Long = 0
    var timeToLand: Long = 0
    var fuelToLand: Double = 0.0
    var engineTimeSec: Long = 0
    var fuelMaxTime: Double = 0.0
    var fuelTimeRemaining: Double = 0.0
    var fuelRemaining: Double = 0.0
    var fuelUsed: Double = 0.0
    var distFromPrevWpt: Double = 0.0   // Distance in straight line from previous WPT
    var distRemaining: Double = 0.0     // Distance remaining to next WPT
    var distPct: Double = 0.0           // Distance travelled between WPTs in %

    init {
        if (settings.gpsAssist) runBlocking { gpsMutex.withLock { gps = gpsData } }
        stage = getFlightStage()
        item = getNavlogCurrentItemId()
        prev = getNavlogPrevItemId(item)
        next = getNavlogNextItemId(item)
        first = getNavlogFirstActiveItemId()
        last = getNavlogLastActiveItemId()

        if (stage >= C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            engineTimeSec = Duration.between(timers.offblock, timers.onblock).toMillis() / 1000
        } else if (stage >= C.STAGE_2_ENGINE_RUNNING) {
            engineTimeSec = Duration.between(timers.offblock, LocalDateTime.now()).toMillis() / 1000
        }

        if (settings.planeFph != null) {
            fuelUsed = engineTimeSec.toDouble() / 3600.0 * settings.planeFph!!

            if (settings.fob != null) {
                fuelMaxTime = settings.fob!! / settings.planeFph!!
                fuelRemaining = settings.fob!! - fuelUsed
                fuelTimeRemaining = fuelMaxTime * 3600.0 - engineTimeSec.toDouble()
            }
        }

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (isNavlogReady()) estFlightTimeSec = totals.time

            if (settings.planeFph != null) {
                fuelToLand = settings.planeFph!! * totals.time.toDouble() / 3600.0
            }

        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            prevTime = if (item == first) timers.takeoff!! else navlogList[prev].ata!!

            // ETA to Waypoint
            val eta2wpt = prevTime!!.plusSeconds(navlogList[item].time!!)

            // ETE
            eteSec = if (gps.isValid && isNavlogItemGpsReady(item)) {
                if (gps.rawSpeed > C.GPS_MINIMUM_RAWSPEED) {
                    val dist = calcDistance(gps.coords!!, navlogList[item].coords!!)
                    (dist / gps.rawSpeed).toLong()   // Time in seconds
                } else 0L
            } else {
                Duration.between(LocalDateTime.now(), eta2wpt).toMillis() / 1000
            }

            // Dist
            if (gps.isValid) {
                val prevCoords = getPrevCoords(item)
                distRemaining = calcDistance(gps.coords!!, navlogList[item].coords!!)
                distFromPrevWpt = calcDistance(prevCoords!!, gps.coords!!)
                //val distTotal = calcDistance(prevCoords, navlogList[item].coords!!)
                val distTotal = distFromPrevWpt + distRemaining
                distPct = distFromPrevWpt / distTotal * 100.0

                distRemaining = meters2distUnits(distRemaining)
                distFromPrevWpt = meters2distUnits(distFromPrevWpt)

            } else {
                val legTime = Duration.between(prevTime, eta2wpt).toMillis() / 1000
                val legPct = eteSec.toDouble() / legTime.toDouble()
                distPct = (1.0 - legPct) * 100.0
                distRemaining = navlogList[item].distance!! * legPct
                distFromPrevWpt = navlogList[item].distance!! - distRemaining
            }

            // Estimated flight time
            estFlightTimeSec = Duration.between(timers.takeoff, navlogList[last].eta).toMillis() / 1000
            if (eteSec < 0) estFlightTimeSec -= eteSec

            // ETA to Landing
            eta = timers.takeoff!!.plusSeconds(estFlightTimeSec)

            // Time to land
            timeToLand = if (item == last) eteSec else estFlightTimeSec - Duration.between(timers.takeoff, LocalDateTime.now()).toMillis() / 1000

            // Track angle / Direct MT
            if (gps.isValid) {
                trackAngle = calcBearingAngle(navlogList[item].coords!!, gps.coords!!, getPrevCoords(item)!!)
                dmt = normalizeBearing(calcBearing(gps.coords!!, navlogList[item].coords!!) + navlogList[item].declination!!)
            }

            // Fuel to land
            if (settings.planeFph != null) fuelToLand = settings.planeFph!! * timeToLand.toDouble() / 3600.0

        } else if (stage >= C.STAGE_4_AFTER_LANDING) {
            estFlightTimeSec = Duration.between(timers.takeoff, timers.landing).toMillis() / 1000
        }
    }

    fun getWpt(): String {
        if (stage >= C.STAGE_4_AFTER_LANDING) return ""
        return if (item >= 0) navlogList[item].dest
        else {
            if (first >= 0) navlogList[first].dest
            else ""
        }
    }

    fun getHdg(): List<String> {
        var hdg = ""
        var hdgNext = ""
        var hdgDct = ""

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0) hdg = formatDouble(navlogList[first].hdg)
            val n = getNavlogNextItemId(first)
            if (n >= 0) hdgNext = formatDouble(navlogList[n].hdg)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            hdg = formatDouble(navlogList[item].hdg)
            if (next >= 0) hdgNext = formatDouble(navlogList[next].hdg)

            if (gps.isValid) {
                // Direct HDG (in HDG box)
                val fc = flightCalculator(dmt, settings.windDir, settings.windSpd, settings.planeTas)
                hdgDct = formatDouble(fc.hdg)
            }
        }
        return listOf(hdg, hdgNext, hdgDct)
    }

    fun getEte(): HomeEte {
        val ret = HomeEte()

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0) ret.ete = formatSecondsToTime(navlogList[first].time)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (eteSec < 0) {
                ret.ete = formatSecondsToTime(-eteSec)
                ret.sign = true
            } else ret.ete = formatSecondsToTime(eteSec)

            ret.sw = formatMillisToTime(Duration.between(prevTime, LocalDateTime.now()).toMillis())
        }
        if (eteSec == 0L) ret.ete = "-"
        return ret
    }

    fun getGs(): List<String> {
        var gs = ""
        var gsDiff = ""

        if (gps.isValid) {
            gs = formatDouble(gps.speed)
            if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
                val diff = gps.speed - navlogList[item].gs!!
                gsDiff = if (diff > 0.0) ("+" + formatDouble(diff)) else formatDouble(diff)
            }
        } else {
            if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS && first >= 0) gs = formatDouble(navlogList[first].gs)
            else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) gs = formatDouble(navlogList[item].gs)
        }
        return listOf(gs, gsDiff)
    }

    fun getDtk(): List<String> {
        var mt = ""
        var mtDct = ""
        var angle = ""

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS && first >= 0) {
            mt = formatDouble(navlogList[first].magneticTrack)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            mt = formatDouble(navlogList[item].magneticTrack)
            if (gps.isValid) {
                angle = if (abs(trackAngle) >= C.DIST_THRESHOLD) formatDouble(trackAngle) else formatDouble(trackAngle, 1)
                mtDct = formatDouble(dmt)
            }
        }

        return listOf(mt, mtDct, angle)
    }

    fun getDtkAngleBar(): HomeDtkAngleBar {
        val left: Int
        val right: Int
        var hit = true

        val anglePct = ((abs(trackAngle) / C.MAX_ANGLE_INDICATOR.toDouble()) * 100.0).roundToInt()
        if (trackAngle < 0) {
            left = 100 - anglePct
            right = 0
        } else {
            left = 100
            right = anglePct
        }

        // Check hit waypoint circle
        if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS && distRemaining > 0 && gps.isValid && gps.bearing != null && item >= 0) {
            val p = calcDestinationCoords(gps.coords!!, gps.bearing!!.toDouble(), distUnits2meters(distRemaining))
            val d = calcDistance(p, navlogList[item].coords!!)
            if (m2nm(d) > nextRadiusList[settings.nextRadius]) hit = false
        }

        return HomeDtkAngleBar(left, right, hit)
    }

    fun getDist(): HomeDist {
        val ret = HomeDist()

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0 && navlogList[first].distance != null) {
                if (navlogList[first].distance!! > C.DIST_THRESHOLD) ret.dist = formatDouble(navlogList[first].distance!!)
                else ret.dist = formatDouble(navlogList[first].distance!!, 1)
            }
            return ret
        } else if (stage > C.STAGE_3_FLIGHT_IN_PROGRESS) return ret

        // stage == C.STAGE_3_FLIGHT_IN_PROGRESS
        ret.dist = if (abs(distRemaining) > C.DIST_THRESHOLD) formatDouble(abs(distRemaining)) else formatDouble(abs(distRemaining), 1)
        ret.distTravelled = if (abs(distFromPrevWpt) > C.DIST_THRESHOLD) formatDouble(abs(distFromPrevWpt)) else formatDouble(abs(distFromPrevWpt), 1)
        ret.pct = formatDouble(distPct) + "%"
        if (distRemaining < 0.0) ret.sign = true

        if (distPct > 100.0) {
            distPct = (1.0 - (distPct - 100.0) / distPct) * 100.0
            ret.overflow = true
        }
        ret.progress = distPct.roundToInt()
        return ret
    }

    fun getRemarks(): String {
        var ret = ""
        if (item >= 0) ret = navlogList[item].remarks
        else if (first >= 0) ret = navlogList[first].remarks
        return ret
    }

    fun getEta(): List<String> {
        var etaStr = "-"
        var diffStr = ""

        //ETA
        if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            etaStr = formatDateTime(eta!!, C.FORMAT_TIME)
        } else if (stage > C.STAGE_3_FLIGHT_IN_PROGRESS) {
            etaStr = formatDateTime(timers.landing!!, C.FORMAT_TIME)
        }

        // DIFF - difference between actual and planed time
        val timeDeviationMin: Long = ((estFlightTimeSec - totals.time) / 60.0).roundToLong()
        if (timeDeviationMin != 0L) {
            diffStr = if (timeDeviationMin > 0) "+$timeDeviationMin\'"
            else "$timeDeviationMin\'"
        }

        return listOf(etaStr, diffStr)
    }

    fun getTimeToLand(): HomeTTL {
        var ttl = ""
        var pct = ""
        var progress = 0

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (estFlightTimeSec > 0) ttl = formatSecondsToTime(estFlightTimeSec)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            ttl = formatSecondsToTime(timeToLand)
            val p = (totals.time - timeToLand).toDouble() / totals.time.toDouble() * 100.0
            pct = formatDouble(p) + '%'
            progress = p.roundToInt()
        }

        return HomeTTL(ttl, pct, progress)
    }

    fun getFuel(): HomeFuelTime {
        val ret = HomeFuelTime()

        if (stage < C.STAGE_5_AFTER_ENGINE_SHUTDOWN && fuelToLand > 0.0) ret.ftl = formatDouble(fuelToLand)
        if (distRemaining < 0.0) ret.ftlmark = "?"
        if (engineTimeSec > 0.0) ret.engineTime = formatSecondsToTime(engineTimeSec) else ret.engineTime = "-"
        if (settings.fob != null && settings.planeFph != null) {
            ret.fuelTime = formatSecondsToTime(fuelTimeRemaining.toLong())
            ret.fuelRemaining = formatDouble(fuelRemaining)
        }

        if (settings.planeTank != null && settings.planeTank!! > 0.0) {
            val pct = fuelRemaining / settings.planeTank!! * 100.0
            ret.fuelPct = formatDouble(pct) + '%'
            ret.fuelPctBar = pct.roundToInt()
        }

        return ret
    }
}