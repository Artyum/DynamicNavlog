package com.artyum.dynamicnavlog

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDateTime

object NavLogUtils {
    fun getFlightStage(): Int {
        val ret: Int = if (State.timers.offblock == null) C.STAGE_1_BEFORE_ENGINE_START
        else if (State.timers.takeoff == null) C.STAGE_2_ENGINE_RUNNING
        else if (State.timers.landing == null) C.STAGE_3_FLIGHT_IN_PROGRESS
        else if (State.timers.onblock == null) C.STAGE_4_AFTER_LANDING
        else C.STAGE_5_AFTER_ENGINE_SHUTDOWN
        return ret
    }

    fun calcNavlog(adapter: NavlogAdapter? = null) {
        Log.d("NavLogFunc", "calcNavlog")

        // Clear empty items
        clearNavlogInvalidItems(adapter)

        // Remove items in free version
        //if (!isAppPurchased) while (State.navlogList.size > C.FREE_WPT_NUMBER_LIMIT) State.navlogList.removeLast()

        // Recalculate GPS data
        recalculateWaypoints()

        // Flight calculator
        recalculateFlight(adapter)

        // Recalculate ETA
        recalculateEta()

        // Recalculate totals
        recalculateTotals()
    }

    fun clearNavlogInvalidItems(adapter: NavlogAdapter?) {
        var i = State.navlogList.size - 1
        while (i >= 0) {
            if (!isNavlogItemValid(i)) {
                State.navlogList.removeAt(i)
                adapter?.notifyItemRemoved(i)
            }
            i -= 1
        }
    }

    fun recalculateEta() {
        if (isNavlogReady() && State.timers.takeoff != null) {
            var i: Int = getNavlogCurrentItemId()
            while (i < State.navlogList.size) {
                if (State.navlogList[i].active) {
                    val prev = getNavlogPrevItemId(i)
                    val prevTime: LocalDateTime =
                        if (i == getNavlogFirstActiveItemId()) State.timers.takeoff!! else if (i == getNavlogCurrentItemId()) State.navlogList[prev].ata!! else State.navlogList[prev].eta!!
                    val eta: LocalDateTime = prevTime.plusSeconds(State.navlogList[i].time!!)
                    State.navlogList[i].eta = eta
                }
                i += 1
            }
        }
    }

    fun getNavlogCurrentItemId(): Int {
        if (State.navlogList.size > 0) {
            for (i in State.navlogList.indices) {
                if (State.navlogList[i].current && isNavlogItemValid(i)) return i
            }
        }
        return -1
    }

    fun getNavlogPrevItemId(id: Int?): Int {
        var i: Int = if (id != null) id - 1 else getNavlogCurrentItemId() - 1
        while (i >= 0) {
            if (State.navlogList[i].active && isNavlogItemValid(i)) return i
            i -= 1
        }
        return -1
    }

    fun getNavlogNextItemId(id: Int?): Int {
        var i: Int = if (id != null) id + 1 else getNavlogCurrentItemId() + 1
        while (i <= State.navlogList.lastIndex) {
            if (State.navlogList[i].active && isNavlogItemValid(i)) return i
            i += 1
        }
        return -1
    }

    fun getNavlogFirstActiveItemId(): Int {
        if (State.navlogList.size == 0) return -1
        for (i in State.navlogList.indices) {
            if (State.navlogList[i].active && isNavlogItemValid(i)) return i
        }
        return -1
    }

    fun getNavlogLastActiveItemId(): Int {
        var i = State.navlogList.lastIndex
        while (i >= 0) {
            if (State.navlogList[i].active && isNavlogItemValid(i)) return i
            i -= 1
        }
        return -1
    }

    fun isNavlogReady(): Boolean {
        if (State.navlogList.size > 0 && Utils.isSettingsReady()) {
            // Check if there is at least 1 active item
            for (i in State.navlogList.indices) {
                if (State.navlogList[i].active && isNavlogItemValid(i)) return true
            }
        }
        return false
    }

    fun isNavlogItemValid(i: Int): Boolean {
        return i < State.navlogList.size && State.navlogList[i].dest != "" && State.navlogList[i].mt != null && State.navlogList[i].dist != null
    }

    fun setFirstCurrent() {
        val i = getNavlogFirstActiveItemId()
        if (i != -1) State.navlogList[i].current = true
    }

    fun resetCurrent() {
        val i = getNavlogCurrentItemId()
        if (i != -1) State.navlogList[i].current = false
    }

    fun resetNavlog() {
        for (i in State.navlogList.indices) {
            State.navlogList[i].eta = null
            State.navlogList[i].ata = null
        }
        resetCurrent()
    }

    fun resetNavlogItem(i: Int) {
        if (i < 0 || i >= State.navlogList.size) return

        State.navlogList[i].wca = null
        State.navlogList[i].hdg = null
        State.navlogList[i].gs = null
        State.navlogList[i].time = null
        State.navlogList[i].timeIncrement = null
        State.navlogList[i].fuel = null
        State.navlogList[i].fuelRemaining = null
        //State.navlogList[i].eta = null
        //State.navlogList[i].ata = null
    }

    fun resetAllNavlogItems() {
        for (i in State.navlogList.indices) resetNavlogItem(i)
    }

    fun setNextWaypoint() {
        val item = getNavlogCurrentItemId()
        val next = getNavlogNextItemId(item)
        val last = getNavlogLastActiveItemId()

        if (next != -1 && next <= last) {
            State.navlogList[item].ata = LocalDateTime.now()
            State.navlogList[next].current = true
            State.navlogList[item].current = false
        }
    }

    fun setPrevWaypoint() {
        val i = getNavlogCurrentItemId()
        val prev = getNavlogPrevItemId(null)

        if (prev >= 0) {
            State.navlogList[prev].ata = null
            State.navlogList[prev].current = true
            State.navlogList[i].current = false
        }
    }

    fun getPrevCoords(i: Int): LatLng? {
        return if (i < 0) State.settings.takeoffPos
        else {
            val prev = getNavlogPrevItemId(i)
            if (prev < 0) State.settings.takeoffPos
            else State.navlogList[prev].pos
        }
    }

    fun getNextCoords(i: Int): LatLng? {
        val next = getNavlogNextItemId(i)
        return if (next < 0) null
        else State.navlogList[next].pos
    }

    fun invertNavlog(): Boolean {
        val first = getNavlogFirstActiveItemId()
        val last = getNavlogLastActiveItemId()

        if (first < 0 || last < 0) return false

        // Swap Departure and Destination
        val dep = State.settings.departure
        State.settings.departure = State.settings.destination
        State.settings.destination = dep

        // Save takeoff position as new last position
        val newLastPos = State.settings.takeoffPos!!
        State.settings.takeoffPos = State.navlogList[last].pos

        val newNavlogList = ArrayList<NavlogItemData>()
        for (i in last - 1 downTo first) {
            if (State.navlogList[i].active) {
                newNavlogList.add(State.navlogList[i])
            }
        }

        // Add last waypoint
        val item = NavlogItemData(
            dest = if (State.settings.destination == "") "END" else State.settings.destination,
            mt = GPSUtils.normalizeBearing(State.navlogList[first].mt!!.plus(180.0)),
            dist = State.navlogList[first].dist,
            pos = newLastPos
        )
        newNavlogList.add(item)

        // Set new NavlogList
        State.navlogList = newNavlogList

        return true
    }

    fun recalculateFlight(adapter: NavlogAdapter?) {
        if (isNavlogReady()) {
            var fuelRemaining: Double?
            var timeIncrement: Long = 0

            var item = getNavlogCurrentItemId()
            val first = getNavlogFirstActiveItemId()

            if (item < 0 || item == first) {
                item = first
                fuelRemaining = State.settings.fob
            } else {
                fuelRemaining = State.navlogList[getNavlogPrevItemId(item)].fuelRemaining
            }

            while (item < State.navlogList.size) {
                if (State.navlogList[item].active && isNavlogItemValid(item)) {
                    resetNavlogItem(item)

                    val fDFata = Utils.flightCalculator(
                        course = State.navlogList[item].mt!!,
                        windDir = State.settings.windDir,
                        windSpd = State.settings.windSpd,
                        tas = State.airplane.tas,
                        dist = State.navlogList[item].dist,
                        fph = State.airplane.fph
                    )

                    State.navlogList[item].wca = fDFata.wca
                    State.navlogList[item].hdg = fDFata.hdg
                    State.navlogList[item].gs = fDFata.gs
                    State.navlogList[item].time = fDFata.time
                    State.navlogList[item].fuel = fDFata.fuel
                    State.navlogList[item].fuelRemaining = null

                    if (fDFata.time != null) {
                        timeIncrement += fDFata.time
                        State.navlogList[item].timeIncrement = timeIncrement
                    }

                    if (fDFata.fuel != null) {
                        if (fuelRemaining != null) fuelRemaining -= fDFata.fuel
                        State.navlogList[item].fuelRemaining = fuelRemaining
                    }

                    adapter?.notifyItemChanged(item)
                }
                item += 1
            }
        } else {
            if (!Utils.isFlightInProgress()) resetAllNavlogItems()
        }
    }

    fun recalculateTotals() {
        State.totals.dist = 0.0
        State.totals.time = 0
        State.totals.fuel = 0.0

        for (i in State.navlogList.indices) {
            if (State.navlogList[i].active && isNavlogItemValid(i)) {
                State.totals.dist += State.navlogList[i].dist ?: 0.0
                State.totals.time += State.navlogList[i].time ?: 0L
            }
        }
        State.totals.fuel = State.airplane.fph * State.totals.time / 3600.0
        if (State.settings.fob < State.totals.fuel) State.settings.fob = State.totals.fuel
    }

    fun recalculateWaypoints() {
        if (State.settings.takeoffPos == null) return
        //Log.d("NavLogFunc", "recalculateWaypoints")

        for (i in State.navlogList.indices) {
            if (State.navlogList[i].active) {
                val prevCoords = getPrevCoords(i)
                if (prevCoords != null && State.navlogList[i].pos != null) {
                    val tt = GPSUtils.calcBearing(prevCoords, State.navlogList[i].pos!!)
                    val d = GPSUtils.getDeclination(State.navlogList[i].pos!!)
                    val mt = GPSUtils.normalizeBearing(tt + d)
                    val dist = Convert.m2nm(GPSUtils.calcDistance(prevCoords, State.navlogList[i].pos!!))

                    State.navlogList[i].tt = tt
                    State.navlogList[i].d = d
                    State.navlogList[i].mt = mt
                    State.navlogList[i].dist = dist
                }
            }
        }
    }

    fun setStageOffBlock() {
        State.timers.offblock = LocalDateTime.now()
        FileUtils.saveState()
        State.tracePointsList.clear()
        FileUtils.deleteTrace()
        Vars.globalRefresh = true
    }

    fun setStageTakeoff() {
        if (State.timers.offblock == null) setStageOffBlock()
        State.timers.takeoff = LocalDateTime.now()
        setFirstCurrent()
        calcNavlog()
        FileUtils.saveState()
        Vars.globalRefresh = true
    }

    fun setStageBack() {
        setPrevWaypoint()
        calcNavlog()
        FileUtils.saveState()
        Vars.globalRefresh = true
    }

    fun setStageNext() {
        setNextWaypoint()
        calcNavlog()
        FileUtils.saveState()
        Vars.globalRefresh = true
    }

    fun setStageLanding() {
        State.timers.landing = LocalDateTime.now()
        State.navlogList[getNavlogCurrentItemId()].ata = LocalDateTime.now()
        calcNavlog()
        FileUtils.saveState()
        Vars.globalRefresh = true
    }

    fun setStageOnBLock() {
        State.timers.onblock = LocalDateTime.now()
        FileUtils.saveState()
        Vars.globalRefresh = true
    }
}