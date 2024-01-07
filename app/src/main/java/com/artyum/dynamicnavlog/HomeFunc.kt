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
    var distToCurrentWpt: Double = 0.0  // Distance remaining to next WPT
    var distFromPrevWpt: Double = 0.0   // Distance in straight line from previous WPT
    var distPct: Double = 0.0           // Distance travelled between WPTs in %
    var isInsideCircle: Boolean = false

    init {
        if (State.options.gpsAssist) runBlocking { Vars.gpsMutex.withLock { gps = Vars.gpsData.copy() } }
        stage = NavLogUtils.getFlightStage()
        item = NavLogUtils.getNavlogCurrentItemId()
        prev = NavLogUtils.getNavlogPrevItemId(item)
        next = NavLogUtils.getNavlogNextItemId(item)
        first = NavLogUtils.getNavlogFirstActiveItemId()
        last = NavLogUtils.getNavlogLastActiveItemId()

        if (stage >= C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            engineTimeSec = Duration.between(State.timers.offblock, State.timers.onblock).toMillis() / 1000
        } else if (stage >= C.STAGE_2_ENGINE_RUNNING) {
            engineTimeSec = Duration.between(State.timers.offblock, LocalDateTime.now()).toMillis() / 1000
        }

        if (State.airplane.fph > 0.0) {
            fuelUsed = engineTimeSec.toDouble() / 3600.0 * State.airplane.fph

            fuelMaxTime = State.settings.fob / State.airplane.fph
            fuelRemaining = State.settings.fob - fuelUsed
            fuelTimeRemaining = fuelMaxTime * 3600.0 - engineTimeSec.toDouble()
        }

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (NavLogUtils.isNavlogReady()) estFlightTimeSec = State.totals.time
            fuelToLand = State.airplane.fph * State.totals.time.toDouble() / 3600.0
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            prevTime = if (item == first) State.timers.takeoff!! else State.navlogList[prev].ata!!

            // ETA to Waypoint
            val eta2wpt = prevTime!!.plusSeconds(State.navlogList[item].time!!)

            // ETE
            eteSec = if (gps.isValid) {
                if (gps.speedMps >= C.GPS_MINIMUM_RAW_SPEED) {
                    val dist = GPSUtils.calcDistance(gps.pos!!, State.navlogList[item].pos!!)
                    (dist / gps.speedMps).toLong()   // Time in seconds
                } else 0L
            } else {
                Duration.between(LocalDateTime.now(), eta2wpt).toMillis() / 1000
            }

            // Dist
            if (gps.isValid) {
                val prevPos = NavLogUtils.getPrevCoords(item)
                distToCurrentWpt = GPSUtils.calcDistance(gps.pos!!, State.navlogList[item].pos!!)
                distFromPrevWpt = GPSUtils.calcDistance(prevPos!!, gps.pos!!)
                val distTotal = distFromPrevWpt + distToCurrentWpt
                distPct = distFromPrevWpt / distTotal * 100.0
                distToCurrentWpt = Units.m2nm(distToCurrentWpt)
                distFromPrevWpt = Units.m2nm(distFromPrevWpt)
            } else {
                val legTime = Duration.between(prevTime, eta2wpt).toMillis() / 1000
                val legPct = eteSec.toDouble() / legTime.toDouble()
                distPct = (1.0 - legPct) * 100.0
                distToCurrentWpt = State.navlogList[item].dist!! * legPct
                distFromPrevWpt = State.navlogList[item].dist!! - distToCurrentWpt
            }

            // Estimated flight time
            estFlightTimeSec = Duration.between(State.timers.takeoff, State.navlogList[last].eta).toMillis() / 1000
            if (eteSec < 0) estFlightTimeSec -= eteSec

            // ETA to Landing
            eta = State.timers.takeoff!!.plusSeconds(estFlightTimeSec)

            // Time to land
            timeToLand = if (item == last) eteSec else estFlightTimeSec - Duration.between(State.timers.takeoff, LocalDateTime.now()).toMillis() / 1000

            // Track angle / Direct MT
            if (gps.isValid) {
                trackAngle = GPSUtils.calcBearingAngle(State.navlogList[item].pos!!, gps.pos!!, NavLogUtils.getPrevCoords(item)!!)
                dmt = GPSUtils.normalizeBearing(GPSUtils.calcBearing(gps.pos!!, State.navlogList[item].pos!!) + State.navlogList[item].d!!)
            }

            // Fuel to land
            fuelToLand = State.airplane.fph * timeToLand.toDouble() / 3600.0

            // Inside circle
            isInsideCircle = distToCurrentWpt <= C.nextRadiusList[State.options.nextRadiusIndex]
        } else if (stage >= C.STAGE_4_AFTER_LANDING) {
            estFlightTimeSec = Duration.between(State.timers.takeoff, State.timers.landing).toMillis() / 1000
        }
    }

    fun getWpt(): String {
        if (stage >= C.STAGE_4_AFTER_LANDING) return ""
        return if (item >= 0) State.navlogList[item].dest
        else {
            if (first >= 0) State.navlogList[first].dest
            else ""
        }
    }

    fun getHdg(): List<String> {
        var hdg = ""
        var hdgNext = ""
        var hdgDct = ""

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0) hdg = Utils.formatDouble(State.navlogList[first].hdg)
            val n = NavLogUtils.getNavlogNextItemId(first)
            if (n >= 0) hdgNext = Utils.formatDouble(State.navlogList[n].hdg)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            hdg = Utils.formatDouble(State.navlogList[item].hdg)
            if (next >= 0) hdgNext = Utils.formatDouble(State.navlogList[next].hdg)

            if (gps.isValid) {
                // Direct HDG (in HDG box)
                val fc = Utils.flightCalculator(dmt, State.settings.windDir, State.settings.windSpd, State.airplane.tas)
                hdgDct = Utils.formatDouble(fc.hdg)
            }
        }
        return listOf(hdg, hdgNext, hdgDct)
    }

    fun getEte(): HomeEte {
        val ret = HomeEte()

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0) ret.ete = TimeUtils.formatSecondsToTime(State.navlogList[first].time)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (eteSec < 0) {
                ret.ete = TimeUtils.formatSecondsToTime(-eteSec)
                ret.sign = true
            } else ret.ete = TimeUtils.formatSecondsToTime(eteSec)

            ret.sw = TimeUtils.formatMillisToTime(Duration.between(prevTime, LocalDateTime.now()).toMillis())
        }
        if (eteSec == 0L) ret.ete = "-"
        return ret
    }

    fun getGs(): List<String> {
        var gs = ""
        var gsDiff = ""

        if (gps.isValid) {
            gs = Utils.formatDouble(Units.toUserUnitsSpd(gps.speedKt))
            if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
                val diff = Units.toUserUnitsSpd(gps.speedKt - State.navlogList[item].gs!!)!!
                gsDiff = if (diff > 0.0) ("+" + Utils.formatDouble(diff)) else Utils.formatDouble(diff)
            }
        } else {
            if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS && first >= 0) gs = Utils.formatDouble(Units.toUserUnitsSpd(State.navlogList[first].gs))
            else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) gs = Utils.formatDouble(Units.toUserUnitsSpd(State.navlogList[item].gs))
        }
        return listOf(gs, gsDiff)
    }

    fun getDtk(): List<String> {
        var mt = ""
        var mtDct = ""
        var angle = ""

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS && first >= 0) {
            mt = Utils.formatDouble(State.navlogList[first].mt)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            mt = Utils.formatDouble(State.navlogList[item].mt)
            if (gps.isValid) {
                angle = if (abs(trackAngle) >= C.DIST_THRESHOLD) Utils.formatDouble(trackAngle) else Utils.formatDouble(trackAngle, 1)
                mtDct = Utils.formatDouble(dmt)
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
        if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS && distToCurrentWpt > 0 && gps.isValid && gps.bearing != null && item >= 0) {
            val p = GPSUtils.calcDestinationPos(gps.pos!!, gps.bearing!!.toDouble(), Units.nm2m(distToCurrentWpt))
            val d = GPSUtils.calcDistance(p, State.navlogList[item].pos!!)
            if (Units.m2nm(d) > C.nextRadiusList[State.options.nextRadiusIndex]) hit = false
        }

        return HomeDtkAngleBar(left, right, hit)
    }

    fun getDist(): HomeDist {
        val ret = HomeDist()

        if (stage < C.STAGE_3_FLIGHT_IN_PROGRESS) {
            if (first >= 0 && State.navlogList[first].dist != null) {
                if (State.navlogList[first].dist!! > C.DIST_THRESHOLD) ret.dist = Utils.formatDouble(Units.toUserUnitsDis(State.navlogList[first].dist!!))
                else ret.dist = Utils.formatDouble(Units.toUserUnitsDis(State.navlogList[first].dist!!), 1)
            }
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            var p = if (Units.toUserUnitsDis(abs(distToCurrentWpt))!! > C.DIST_THRESHOLD) 0 else 1
            ret.dist = Utils.formatDouble(Units.toUserUnitsDis(abs(distToCurrentWpt)), p)

            p = if (Units.toUserUnitsDis(abs(distFromPrevWpt))!! > C.DIST_THRESHOLD) 0 else 1
            ret.distTravelled = Utils.formatDouble(Units.toUserUnitsDis(abs(distFromPrevWpt)), p)

            ret.pct = Utils.formatDouble(distPct) + "%"
            if (distToCurrentWpt < 0.0) ret.sign = true

            if (distPct > 100.0) {
                distPct = (1.0 - (distPct - 100.0) / distPct) * 100.0
                ret.overflow = true
            }
            ret.progress = distPct.roundToInt()
        }

        return ret
    }

    fun getRemarks(): String {
        var ret = ""
        if (item >= 0) ret = State.navlogList[item].remarks
        else if (first >= 0) ret = State.navlogList[first].remarks
        return ret
    }

    fun getEta(): List<String> {
        var etaStr = "-"
        var diffStr = ""

        //ETA
        if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            etaStr = TimeUtils.formatDateTime(eta!!, C.FORMAT_TIME)
        } else if (stage > C.STAGE_3_FLIGHT_IN_PROGRESS) {
            etaStr = TimeUtils.formatDateTime(State.timers.landing!!, C.FORMAT_TIME)
        }

        // DIFF - difference between actual and planed time
        val timeDeviationMin: Long = ((estFlightTimeSec - State.totals.time) / 60.0).roundToLong()
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
            if (estFlightTimeSec > 0) ttl = TimeUtils.formatSecondsToTime(estFlightTimeSec)
        } else if (stage == C.STAGE_3_FLIGHT_IN_PROGRESS) {
            ttl = TimeUtils.formatSecondsToTime(timeToLand)
            val p = (State.totals.time - timeToLand).toDouble() / State.totals.time.toDouble() * 100.0
            pct = Utils.formatDouble(p) + '%'
            progress = p.roundToInt()
        }

        return HomeTTL(ttl, pct, progress)
    }

    fun getFuel(): HomeFuelTime {
        val ret = HomeFuelTime()

        if (stage < C.STAGE_5_AFTER_ENGINE_SHUTDOWN && fuelToLand > 0.0) ret.ftl = Utils.formatDouble(Units.toUserUnitsVol(fuelToLand))
        if (distToCurrentWpt < 0.0) ret.ftlmark = "?"
        if (engineTimeSec > 0.0) ret.engineTime = TimeUtils.formatSecondsToTime(engineTimeSec) else ret.engineTime = "-"

        ret.fuelTime = TimeUtils.formatSecondsToTime(fuelTimeRemaining.toLong())
        ret.fuelRemaining = Utils.formatDouble(Units.toUserUnitsVol(fuelRemaining))

        if (State.airplane.tank > 0.0) {
            val pct = fuelRemaining / State.airplane.tank * 100.0
            ret.fuelPct = Utils.formatDouble(pct) + '%'
            ret.fuelPctBar = pct.roundToInt()
        }

        return ret
    }
}