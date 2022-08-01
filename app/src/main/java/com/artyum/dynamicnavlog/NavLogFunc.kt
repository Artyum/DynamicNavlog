package com.artyum.dynamicnavlog

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDateTime

fun calcNavlog(adapter: NavlogAdapter? = null) {
    Log.d("NavLogFunc", "calcNavlog")

    // Clear empty items
    clearNavlogInvalidItems(adapter)

    // Remove items in free version
    //if (!isAppPurchased) while (navlogList.size > C.FREE_WPT_NUMBER_LIMIT) navlogList.removeLast()

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
    var i = navlogList.size - 1
    while (i >= 0) {
        if (!isNavlogItemValid(i)) {
            navlogList.removeAt(i)
            adapter?.notifyItemRemoved(i)
        }
        i -= 1
    }
}

fun recalculateEta() {
    if (isNavlogReady() && timers.takeoff != null) {
        var i: Int = getNavlogCurrentItemId()
        while (i < navlogList.size) {
            if (navlogList[i].active) {
                val prev = getNavlogPrevItemId(i)
                val prevTime: LocalDateTime = if (i == getNavlogFirstActiveItemId()) timers.takeoff!! else if (i == getNavlogCurrentItemId()) navlogList[prev].ata!! else navlogList[prev].eta!!
                val eta: LocalDateTime = prevTime.plusSeconds(navlogList[i].time!!)
                navlogList[i].eta = eta
            }
            i += 1
        }
    }
}

fun getNavlogCurrentItemId(): Int {
    if (navlogList.size > 0) {
        for (i in navlogList.indices) {
            if (navlogList[i].current && isNavlogItemValid(i)) return i
        }
    }
    return -1
}

fun getNavlogPrevItemId(id: Int?): Int {
    var i: Int = if (id != null) id - 1 else getNavlogCurrentItemId() - 1
    while (i >= 0) {
        if (navlogList[i].active && isNavlogItemValid(i)) return i
        i -= 1
    }
    return -1
}

fun getNavlogNextItemId(id: Int?): Int {
    var i: Int = if (id != null) id + 1 else getNavlogCurrentItemId() + 1
    while (i <= navlogList.lastIndex) {
        if (navlogList[i].active && isNavlogItemValid(i)) return i
        i += 1
    }
    return -1
}

fun getNavlogFirstActiveItemId(): Int {
    if (navlogList.size == 0) return -1
    for (i in navlogList.indices) {
        if (navlogList[i].active && isNavlogItemValid(i)) return i
    }
    return -1
}

fun getNavlogLastActiveItemId(): Int {
    var i = navlogList.lastIndex
    while (i >= 0) {
        if (navlogList[i].active && isNavlogItemValid(i)) return i
        i -= 1
    }
    return -1
}

fun isNavlogReady(): Boolean {
    if (navlogList.size > 0 && isSettingsReady()) {
        // Check if there is at least 1 active item
        for (i in navlogList.indices) {
            if (navlogList[i].active && isNavlogItemValid(i)) return true
        }
    }
    return false
}

fun isNavlogItemValid(i: Int): Boolean {
    return i < navlogList.size && navlogList[i].dest != "" && navlogList[i].magneticTrack != null && navlogList[i].distance != null
}

fun isNavlogItemGpsReady(i: Int): Boolean {
    return i < navlogList.size && navlogList[i].coords != null && navlogList[i].declination != null && navlogList[i].trueTrack != null && navlogList[i].magneticTrack != null
}

fun isNavlogGpsReady(): Boolean {
    if (settings.takeoffPos == null || navlogList.size == 0) return false
    for (i in navlogList.indices) {
        if (!isNavlogItemGpsReady(i)) return false
    }
    return true
}

fun setFirstCurrent() {
    val i = getNavlogFirstActiveItemId()
    if (i != -1) navlogList[i].current = true
}

fun resetCurrent() {
    val i = getNavlogCurrentItemId()
    if (i != -1) navlogList[i].current = false
}

fun resetNavlog() {
    for (i in navlogList.indices) {
        navlogList[i].eta = null
        navlogList[i].ata = null
    }
    resetCurrent()
}

fun resetNavlogItem(i: Int) {
    if (i < 0 || i >= navlogList.size) return

    navlogList[i].wca = null
    navlogList[i].hdg = null
    navlogList[i].gs = null
    navlogList[i].time = null
    navlogList[i].timeIncrement = null
    navlogList[i].fuel = null
    navlogList[i].fuelRemaining = null
    //navlogList[i].eta = null
    //navlogList[i].ata = null
}

fun resetAllNavlogItems() {
    for (i in navlogList.indices) resetNavlogItem(i)
}

fun setNextWaypoint() {
    val item = getNavlogCurrentItemId()
    val next = getNavlogNextItemId(item)
    val last = getNavlogLastActiveItemId()

    if (next != -1 && next <= last) {
        navlogList[item].ata = LocalDateTime.now()
        navlogList[next].current = true
        navlogList[item].current = false
    }

    // Refresh map on next-waypoint
    refreshDisplay = true
}

fun setPrevWaypoint() {
    val i = getNavlogCurrentItemId()
    val prev = getNavlogPrevItemId(null)

    if (prev >= 0) {
        navlogList[prev].ata = null
        navlogList[prev].current = true
        navlogList[i].current = false
    }
}

fun getPrevCoords(i: Int): LatLng? {
    return if (i < 0) settings.takeoffPos
    else {
        val prev = getNavlogPrevItemId(i)
        if (prev < 0) settings.takeoffPos
        else navlogList[prev].coords
    }
}

fun getNextCoords(i: Int): LatLng? {
    val next = getNavlogNextItemId(i)
    return if (next < 0) null
    else navlogList[next].coords
}

fun invertNavlog() {
    val first = getNavlogFirstActiveItemId()
    val last = getNavlogLastActiveItemId()

    // Swap Departure and Destination
    val tmp = settings.departure
    settings.departure = settings.destination
    settings.destination = tmp

    // Save takeoff coords as new last coords
    val newLastCoords = settings.takeoffPos!!
    settings.takeoffPos = navlogList[last].coords

    val newNavlogList = ArrayList<NavlogItem>()
    for (i in last - 1 downTo first) {
        if (navlogList[i].active) {
            newNavlogList.add(navlogList[i])
        }
    }

    // Add last waypoint
    val item = NavlogItem(
        dest = settings.destination,
        magneticTrack = navlogList[first].magneticTrack?.plus(180.0),
        distance = navlogList[first].distance,
        coords = newLastCoords
    )
    newNavlogList.add(item)

    // Save new NavLog
    navlogList = newNavlogList
}

fun recalculateFlight(adapter: NavlogAdapter?) {
    if (isNavlogReady()) {
        var fuelRemaining: Double?
        var timeIncrement: Long = 0

        var item = getNavlogCurrentItemId()
        val first = getNavlogFirstActiveItemId()

        if (item < 0 || item == first) {
            item = first
            fuelRemaining = settings.fob
        } else {
            fuelRemaining = navlogList[getNavlogPrevItemId(item)].fuelRemaining
        }

        while (item < navlogList.size) {
            if (navlogList[item].active && isNavlogItemValid(item)) {
                resetNavlogItem(item)

                val fDFata = flightCalculator(
                    course = navlogList[item].magneticTrack!!,
                    windDir = settings.windDir,
                    windSpd = settings.windSpd,
                    tas = airplane.tas,
                    dist = navlogList[item].distance,
                    fph = airplane.fph
                )

                navlogList[item].wca = fDFata.wca
                navlogList[item].hdg = fDFata.hdg
                navlogList[item].gs = fDFata.gs
                navlogList[item].time = fDFata.time
                navlogList[item].fuel = fDFata.fuel
                navlogList[item].fuelRemaining = null

                if (fDFata.time != null) {
                    timeIncrement += fDFata.time
                    navlogList[item].timeIncrement = timeIncrement
                }

                if (fDFata.fuel != null) {
                    if (fuelRemaining != null) fuelRemaining -= fDFata.fuel
                    navlogList[item].fuelRemaining = fuelRemaining
                }

                adapter?.notifyItemChanged(item)
            }
            item += 1
        }
    } else {
        if (!isFlightInProgress()) resetAllNavlogItems()
    }
}

fun recalculateTotals() {
    totals.dist = 0.0
    totals.time = 0
    totals.fuel = 0.0

    for (i in navlogList.indices) {
        if (navlogList[i].active && isNavlogItemValid(i)) {
            totals.dist += navlogList[i].distance ?: 0.0
            totals.time += navlogList[i].time ?: 0L
        }
    }
    totals.fuel = airplane.fph * totals.time / 3600.0
    if (settings.fob < totals.fuel) settings.fob = totals.fuel
}

fun recalculateWaypoints() {
    if (settings.takeoffPos == null) return
    //Log.d("NavLogFunc", "recalculateWaypoints")

    for (i in navlogList.indices) {
        if (navlogList[i].active) {
            val prevCoords = getPrevCoords(i)
            if (prevCoords != null && navlogList[i].coords != null) {
                val tt = calcBearing(prevCoords, navlogList[i].coords!!)
                val declination = getDeclination(navlogList[i].coords!!)
                val mt = normalizeBearing(tt + declination)
                val dist = m2nm(calcDistance(prevCoords, navlogList[i].coords!!))

                navlogList[i].trueTrack = tt
                navlogList[i].declination = declination
                navlogList[i].magneticTrack = mt
                navlogList[i].distance = dist
            }
        }
    }
}

fun setStageOffBlock() {
    timers.offblock = LocalDateTime.now()
    saveState()
    tracePointsList.clear()
    deleteTrace()
    refreshDisplay = true
}

fun setStageTakeoff() {
    if (timers.offblock == null) setStageOffBlock()
    timers.takeoff = LocalDateTime.now()
    setFirstCurrent()
    calcNavlog()
    saveState()
    refreshDisplay = true
}

fun setStageBack() {
    setPrevWaypoint()
    calcNavlog()
    saveState()
    refreshDisplay = true
}

fun setStageNext() {
    setNextWaypoint()
    calcNavlog()
    saveState()
    refreshDisplay = true
}

fun setStageLanding() {
    timers.landing = LocalDateTime.now()
    navlogList[getNavlogCurrentItemId()].ata = LocalDateTime.now()
    calcNavlog()
    saveState()
    refreshDisplay = true
}

fun setStageOnBLock() {
    timers.onblock = LocalDateTime.now()
    saveState()
    refreshDisplay = true
}