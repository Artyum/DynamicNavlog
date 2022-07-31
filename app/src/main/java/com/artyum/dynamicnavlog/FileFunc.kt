package com.artyum.dynamicnavlog

import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

var externalAppDir: File? = null
//var internalAppDir: File? = null

fun saveState(fileName: String = "") {
    val tag = "FileFunc"

    val jSettings = JSONObject()
    val jTimers = JSONObject()
    val jNavLog = JSONObject()
    val jRadials = JSONObject()
    val id = settings.planId

    Log.d(tag, "saveState: $id")

    // Settings
    jSettings.put("id", id)
    jSettings.put("name", settings.planName)
    jSettings.put("from", settings.departure)
    jSettings.put("dest", settings.destination)
    jSettings.put("planeId", settings.airplaneId)
    jSettings.put("fob", settings.fob)
    jSettings.put("deplat", settings.takeoffPos?.latitude)
    jSettings.put("deplng", settings.takeoffPos?.longitude)

    jSettings.put("winddir", settings.windDir)
    jSettings.put("windspeed", settings.windSpd)

    jSettings.put("maptype", settings.mapType)
    jSettings.put("maptype", settings.mapType)
    jSettings.put("mapfollow", settings.mapFollow)

    // Timers
    jTimers.put("offblock", formatDateTimeJson(timers.offblock))
    jTimers.put("takeoff", formatDateTimeJson(timers.takeoff))
    jTimers.put("landing", formatDateTimeJson(timers.landing))
    jTimers.put("onblock", formatDateTimeJson(timers.onblock))

    // NavLog
    for (i in navlogList.indices) {
        if (isNavlogItemValid(i)) {
            val item = JSONObject()
            item.put("dest", navlogList[i].dest)
            item.put("tt", navlogList[i].trueTrack)
            item.put("d", navlogList[i].declination)
            item.put("mt", navlogList[i].magneticTrack)
            item.put("dist", navlogList[i].distance)
            item.put("wca", navlogList[i].wca)
            item.put("hdg", navlogList[i].hdg)
            item.put("gs", navlogList[i].gs)
            item.put("time", navlogList[i].time)
            item.put("timei", navlogList[i].timeIncrement)
            item.put("eta", formatDateTimeJson(navlogList[i].eta))
            item.put("ata", formatDateTimeJson(navlogList[i].ata))
            item.put("fuel", navlogList[i].fuel)
            item.put("fuelr", navlogList[i].fuelRemaining)
            item.put("rmk", navlogList[i].remarks)
            item.put("act", navlogList[i].active)
            item.put("cur", navlogList[i].current)
            item.put("lat", navlogList[i].coords?.latitude)
            item.put("lng", navlogList[i].coords?.longitude)

            jNavLog.put("wpt_$i", item)
        }
    }

    // Radials
    for (i in radialList.indices) {
        val r = JSONObject()
        r.put("angle", radialList[i].angle)
        r.put("dist", radialList[i].dist)
        r.put("lat1", radialList[i].pos1.latitude)
        r.put("lng1", radialList[i].pos1.longitude)
        r.put("lat2", radialList[i].pos2.latitude)
        r.put("lng2", radialList[i].pos2.longitude)

        jRadials.put("radial_$i", r)
    }

    // Save
    val json = JSONObject()
    json.put("settings", jSettings)
    json.put("timers", jTimers)
    json.put("navlog", jNavLog)
    json.put("radials", jRadials)

    // TODO delete conversion after some time
    if (fileName != "") {
        // DNL to JSON conversion
        val fn: String = if (fileName == "current_state" + C.DNL_EXTENSION) {
            fileName.replace(C.DNL_EXTENSION, C.JSON_EXTENSION, ignoreCase = true)
        } else {
            id + C.JSON_EXTENSION
        }
        val file = File(externalAppDir, fn)
        file.writeText(json.toString())
    } else {
        // Normal save
        val file = File(externalAppDir, C.stateFile)
        file.writeText(json.toString())

        // Copy current state file to plan name file
        if (settings.planName != "") {
            val fn = id + C.JSON_EXTENSION
            val planFile = File(externalAppDir, fn)
            planFile.writeText(json.toString())
        }
    }
}

fun loadState(fileName: String = C.stateFile) {
    val tag = "FileFunc"

    val file = File(externalAppDir, fileName)
    if (!file.exists()) return
    Log.d(tag, "loadState: $fileName")

    val newSettings = Settings()
    val newTimers = Timers()
    val newNavlogList = ArrayList<NavlogItem>()
    val newRadialList = ArrayList<Radial>()

    val json: JSONObject

    try {
        json = JSONObject(file.readText())
    } catch (e: Exception) {
        Log.d(tag, e.toString())
        return
    }

    // Settings
    val jSettings = JSONObject(getItem(json, "settings") ?: "{}")
    if (jSettings.length() > 0) {
        newSettings.planId = getItem(jSettings, "id") ?: generateStringId()
        newSettings.planName = getItem(jSettings, "name") ?: ""
        newSettings.departure = getItem(jSettings, "from") ?: ""
        newSettings.destination = getItem(jSettings, "dest") ?: ""
        newSettings.airplaneId = getItem(jSettings, "planeId") ?: ""

        val depLat = getDoubleOrNull(getItem(jSettings, "deplat"))
        val depLng = getDoubleOrNull(getItem(jSettings, "deplng"))
        if (depLat != null && depLng != null) newSettings.takeoffPos = LatLng(depLat, depLng)

        newSettings.windDir = getDoubleOrNull(getItem(jSettings, "winddir")) ?: 0.0
        newSettings.windSpd = getDoubleOrNull(getItem(jSettings, "windspeed")) ?: 0.0
        newSettings.fob = getDoubleOrNull(getItem(jSettings, "fob")) ?: 0.0
        newSettings.mapType = getItem(jSettings, "maptype")?.toIntOrNull() ?: 0
        newSettings.mapType = getItem(jSettings, "maptype")?.toIntOrNull() ?: GoogleMap.MAP_TYPE_NORMAL
        newSettings.mapFollow = getItem(jSettings, "mapfollow")?.toBoolean() ?: true
    }

    // Timers
    val jTimers = JSONObject(getItem(json, "timers") ?: "{}")
    if (jTimers.length() > 0) {
        newTimers.offblock = str2DateTimeJson(getItem(jTimers, "offblock"))
        newTimers.takeoff = str2DateTimeJson(getItem(jTimers, "takeoff"))
        newTimers.landing = str2DateTimeJson(getItem(jTimers, "landing"))
        newTimers.onblock = str2DateTimeJson(getItem(jTimers, "onblock"))
    }

    // NavLog
    val jNavLog = JSONObject(getItem(json, "navlog") ?: "{}")
    (0 until jNavLog.length()).forEach {
        val key = "wpt_$it"

        val wpt = JSONObject(getItem(jNavLog, key) ?: "{}")
        if (wpt.length() > 0) {
            val dest = getItem(wpt, "dest")?.uppercase() ?: ""
            val tt = getDoubleOrNull(getItem(wpt, "tt"))
            val d = getDoubleOrNull(getItem(wpt, "d"))
            val mt = getDoubleOrNull(getItem(wpt, "mt"))
            val dist = getDoubleOrNull(getItem(wpt, "dist"))
            val wca = getDoubleOrNull(getItem(wpt, "wca"))
            val hdg = getDoubleOrNull(getItem(wpt, "hdg"))
            val gs = getDoubleOrNull(getItem(wpt, "gs"))
            val time = getItem(wpt, "time")?.toLongOrNull()
            val timeInc = getItem(wpt, "timei")?.toLongOrNull()
            val eta = str2DateTimeJson(getItem(wpt, "eta"))
            val ata = str2DateTimeJson(getItem(wpt, "ata"))
            val fuel = getDoubleOrNull(getItem(wpt, "fuel"))
            val fuelR = getDoubleOrNull(getItem(wpt, "fuelr"))
            val remarks = getItem(wpt, "rmk") ?: ""
            val active = getItem(wpt, "act")?.toBoolean() ?: true
            val current = getItem(wpt, "cur")?.toBoolean() ?: false

            val lat = getDoubleOrNull(getItem(wpt, "lat"))
            val lng = getDoubleOrNull(getItem(wpt, "lng"))
            val coords: LatLng? = if (lat != null && lng != null) LatLng(lat, lng) else null

            if (dest.isNotEmpty() && mt != null && dist != null) newNavlogList.add(
                NavlogItem(
                    dest = dest,
                    trueTrack = tt,
                    declination = d,
                    magneticTrack = mt,
                    distance = dist,
                    wca = wca,
                    hdg = hdg,
                    gs = gs,
                    time = time,
                    timeIncrement = timeInc,
                    eta = eta,
                    ata = ata,
                    fuel = fuel,
                    fuelRemaining = fuelR,
                    remarks = remarks,
                    active = active,
                    current = current,
                    coords = coords
                )
            )
        }
    }

    // Radials
    val jRadials = JSONObject(getItem(json, "radials") ?: "{}")
    (0 until jRadials.length()).forEach {
        val key = "radial_$it"
        val r = JSONObject(getItem(jRadials, key) ?: "{}")
        if (r.length() > 0) {
            val angle = getDoubleOrNull(getItem(r, "angle"))
            val dist = getDoubleOrNull(getItem(r, "dist"))
            val lat1 = getDoubleOrNull(getItem(r, "lat1"))
            val lng1 = getDoubleOrNull(getItem(r, "lng1"))
            val lat2 = getDoubleOrNull(getItem(r, "lat2"))
            val lng2 = getDoubleOrNull(getItem(r, "lng2"))

            if (angle != null && dist != null && lat1 != null && lng1 != null && lat2 !== null && lng2 != null) {
                newRadialList.add(Radial(angle = angle, dist = dist, pos1 = LatLng(lat1, lng1), pos2 = LatLng(lat2, lng2)))
            }
        }
    }

    resetAllSettings()
    settings = newSettings
    timers = newTimers
    navlogList = newNavlogList
    radialList = newRadialList

    // Load airplane
    getAirplaneByID(settings.airplaneId)

    // Load trace
    if (isFlightInProgress()) loadTrace()
}

fun deleteFile(fileName: String) {
    val file = File(externalAppDir, fileName)
    if (file.exists()) file.delete()
}

fun saveOptions() {
    val tag = "FileFunc"
    Log.d(tag, "saveOptions")

    val jOptions = JSONObject()
    jOptions.put("spdunits", options.spdUnits)
    jOptions.put("distunits", options.distUnits)
    jOptions.put("volunits", options.volUnits)
    jOptions.put("screenorient", options.screenOrientation)
    jOptions.put("screenon", options.keepScreenOn)
    jOptions.put("utc", options.timeInUTC)
    jOptions.put("takeoffspd", options.autoTakeoffSpd)
    jOptions.put("landingspd", options.autoLandingSpd)
    jOptions.put("maporient", options.mapOrientation)
    jOptions.put("trace", options.displayTrace)
    jOptions.put("maparrow", options.drawWindArrow)
    jOptions.put("radials", options.drawRadials)
    jOptions.put("radialsm", options.drawRadialsMarkers)
    jOptions.put("hints", options.showHints)
    jOptions.put("gps", options.gpsAssist)
    jOptions.put("autonext", options.autoNext)
    jOptions.put("nextr", options.nextRadius)

    val json = JSONObject()
    json.put("options", jOptions)

    val file = File(externalAppDir, C.optionsFile)
    file.writeText(json.toString())
}

fun loadOptions() {
    val tag = "FileFunc"
    Log.d(tag, "loadOptions")
    val file = File(externalAppDir, C.optionsFile)
    if (file.exists()) {
        val newOptions = Options()
        val json: JSONObject

        try {
            json = JSONObject(file.readText())
        } catch (e: Exception) {
            Log.d(tag, e.toString())
            return
        }

        val jOptions = JSONObject(getItem(json, "options") ?: "{}")
        if (jOptions.length() > 0) {
            newOptions.spdUnits = getItem(jOptions, "spdunits")?.toIntOrNull() ?: 0
            newOptions.distUnits = getItem(jOptions, "distunits")?.toIntOrNull() ?: 0
            newOptions.volUnits = getItem(jOptions, "volunits")?.toIntOrNull() ?: 0
            newOptions.screenOrientation = getItem(jOptions, "screenorient")?.toIntOrNull() ?: C.SCREEN_SENSOR
            newOptions.timeInUTC = getItem(jOptions, "utc")?.toBoolean() ?: false
            newOptions.keepScreenOn = getItem(jOptions, "screenon")?.toBoolean() ?: false
            newOptions.autoTakeoffSpd = getDoubleOrNull(getItem(jOptions, "takeoffspd")) ?: C.AUTO_TAKEOFF_MIN_SPEED_KT
            newOptions.autoLandingSpd = getDoubleOrNull(getItem(jOptions, "landingspd")) ?: C.AUTO_LANDING_MIN_SPEED_KT
            newOptions.mapOrientation = getItem(jOptions, "maporient")?.toIntOrNull() ?: C.MAP_ORIENTATION_NORTH
            newOptions.displayTrace = getItem(jOptions, "trace")?.toBoolean() ?: true
            newOptions.drawWindArrow = getItem(jOptions, "maparrow")?.toBoolean() ?: true
            newOptions.drawRadials = getItem(jOptions, "radials")?.toBoolean() ?: true
            newOptions.drawRadialsMarkers = getItem(jOptions, "radialsm")?.toBoolean() ?: true
            newOptions.showHints = getItem(jOptions, "hints")?.toBoolean() ?: true
            newOptions.gpsAssist = getItem(jOptions, "gps")?.toBoolean() ?: true
            newOptions.autoNext = getItem(jOptions, "autonext")?.toBoolean() ?: true
            newOptions.nextRadius = getItem(jOptions, "nextr")?.toIntOrNull() ?: C.DEFAULT_NEXT_RADIUS
            options = newOptions
        }
    } else {
        options = Options()
        saveOptions()
    }
}

// TODO Delete this function after some time (v1.2.0 2022-06)
fun loadStateDnl(fileName: String = C.stateFile) {
    //Log.d("FileFunc", "loadStateDnl: $fileName")
    val file = File(externalAppDir, fileName)

    if (file.exists()) {
        val newSettings = Settings()
        val newTimers = Timers()
        val newNavlogList = ArrayList<NavlogItem>()

        var mode = 0
        var deplat: Double? = null
        var deplng: Double? = null

        val lines = file.readLines()
        for (line in lines) {
            if (line != "") {
                if (line == C.INI_SETTINGS_STR || line == C.INI_TIMERS_STR || line == C.INI_NAVLOG_STR) {
                    when (line) {
                        C.INI_SETTINGS_STR -> mode = C.INI_SETTINGS
                        C.INI_TIMERS_STR -> mode = C.INI_TIMERS
                        C.INI_NAVLOG_STR -> mode = C.INI_NAVLOG
                    }
                } else {
                    val str = line.split(";")

                    if (mode == C.INI_SETTINGS && str.size == 2 && str[1] != "") {
                        if (str[0] == "name") newSettings.planName = str[1]
                        if (str[0] == "from") newSettings.departure = str[1]
                        if (str[0] == "dest") newSettings.destination = str[1]

                        if (str[0] == "deplat") deplat = getDoubleOrNull(str[1])
                        if (str[0] == "deplng") deplng = getDoubleOrNull(str[1])
                        if (deplat != null && deplng != null) {
                            newSettings.takeoffPos = LatLng(deplat, deplng)
                            deplat = null
                            deplng = null
                        }

                        if (str[0] == "winddir") newSettings.windDir = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "windspeed") newSettings.windSpd = getDoubleOrNull(str[1]) ?: 0.0

                        //if (str[0] == "units") newSettings.spdUnits = str[1].toIntOrNull() ?: 0
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: 0
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: GoogleMap.MAP_TYPE_NORMAL
                        if (str[0] == "mapfollow") newSettings.mapFollow = str[1].toBoolean()
                    }

                    if (mode == C.INI_TIMERS && str.size == 2 && str[1] != "" && str[1] != "null") {
                        val ldt = LocalDateTime.parse(str[1], DateTimeFormatter.ofPattern(C.INI_TIME_PATTERN))
                        if (str[0] == "offblock") newTimers.offblock = ldt
                        if (str[0] == "takeoff") newTimers.takeoff = ldt
                        if (str[0] == "landing") newTimers.landing = ldt
                        if (str[0] == "onblock") newTimers.onblock = ldt
                    }

                    if (mode == C.INI_NAVLOG && str.size == 19) {
                        // Indexes
                        val iDest = 0
                        val iTrueTrack = 1
                        val iDeclination = 2
                        val iMagneticTrack = 3
                        val iDistance = 4
                        val iWca = 5
                        val iHdg = 6
                        val iGs = 7
                        val iTime = 8
                        val iTimeR = 9
                        val iETA = 10
                        val iATA = 11
                        val iFuel = 12
                        val iFuelR = 13
                        val iRemarks = 14
                        val iActive = 15
                        val iCurrent = 16
                        val iLat = 17
                        val iLon = 18

                        var chk = true

                        if (str[iDest] == "") chk = false

                        val tt = getDoubleOrNull(str[iTrueTrack])
                        val d = getDoubleOrNull(str[iDeclination])

                        val mt = getDoubleOrNull(str[iMagneticTrack])
                        if (mt == null) chk = false

                        val dist = getDoubleOrNull(str[iDistance])
                        if (dist == null) chk = false

                        val wca = getDoubleOrNull(str[iWca])
                        val hdg = getDoubleOrNull(str[iHdg])
                        val gs = getDoubleOrNull(str[iGs])
                        val time = str[iTime].toLongOrNull()
                        val timeInc = str[iTimeR].toLongOrNull()

                        var eta: LocalDateTime? = null
                        if (str[iETA] != "null" && str[iETA] != "") eta = LocalDateTime.parse(str[iETA], DateTimeFormatter.ofPattern(C.INI_TIME_PATTERN))

                        var ata: LocalDateTime? = null
                        if (str[iATA] != "null" && str[iATA] != "") ata = LocalDateTime.parse(str[iATA], DateTimeFormatter.ofPattern(C.INI_TIME_PATTERN))

                        val fuel = getDoubleOrNull(str[iFuel])
                        val fuelR = getDoubleOrNull(str[iFuelR])
                        val remarks = str[iRemarks].replace("\\n", "\n")

                        val lat = getDoubleOrNull(str[iLat])
                        val lon = getDoubleOrNull(str[iLon])
                        var coords: LatLng? = null
                        if (lat != null && lon != null) coords = LatLng(lat, lon)

                        if (chk) newNavlogList.add(
                            NavlogItem(
                                dest = str[iDest].uppercase(),
                                trueTrack = tt,
                                declination = d,
                                magneticTrack = mt!!,
                                distance = dist!!,
                                wca = wca,
                                hdg = hdg,
                                gs = gs,
                                time = time,
                                timeIncrement = timeInc,
                                eta = eta,
                                ata = ata,
                                fuel = fuel,
                                fuelRemaining = fuelR,
                                remarks = remarks,
                                active = str[iActive].toBoolean(),
                                current = str[iCurrent].toBoolean(),
                                coords = coords
                            )
                        )
                    }
                }
            }
        }
        newSettings.planId = generateStringId()

        resetAllSettings()
        settings = newSettings
        timers = newTimers
        navlogList = newNavlogList
        // loadTrace()
    }
}

fun getPlanNameFromJson(fileName: String): String {
    val file = File(externalAppDir, fileName)
    if (!file.exists()) return ""

    val json = JSONObject(file.readText())
    val jSettings = JSONObject(json["settings"].toString())
    return jSettings["name"].toString()
}

fun loadFlightPlanList(search: String = "") {
    val tag = "refreshFlightPlanList"
    planList.clear()
    if (externalAppDir == null) return

    externalAppDir!!.walk().forEach {
        val fileName = it.name
        if (fileName != "files" && fileName != C.optionsFile && fileName != C.stateFile && fileName != C.airplanesFile && fileName.endsWith(C.JSON_EXTENSION)) {
            if (search != "") {
                // Search inside file
                var addItem = false
                val searchItems = listOf("name", "from", "dest", "plane", "reg")

                val file = File(externalAppDir, fileName)
                val data: String
                val json: JSONObject

                try {
                    data = file.readText()
                    json = JSONObject(data)
                } catch (e: Exception) {
                    Log.d(tag, e.toString())
                    return
                }

                // Find in settings
                val jSettings = JSONObject(json["settings"].toString())
                for (item in searchItems) {
                    if (jSettings[item].toString().indexOf(search, ignoreCase = true) != -1) addItem = true
                }

                if (addItem) planList.add(PlanListItem(id = fileName, planName = getPlanNameFromJson(fileName)))
            } else {
                planList.add(PlanListItem(id = fileName, planName = getPlanNameFromJson(fileName)))
            }
        }
    }
    planList.sortBy { it.planName.lowercase() }
}

// Fun converts all dnl files to json files
// TODO delete this function with loadStateDnl
fun convertAllDnlToJson() {
    if (externalAppDir == null) return
    var deleteTrk = false

    externalAppDir!!.walk().forEach {
        val fileName = it.name
        if (fileName != "files" && fileName.endsWith(C.DNL_EXTENSION, ignoreCase = true)) {
            Log.d("convertAllDnlToJson", "Convert $fileName")
            loadStateDnl(fileName)
            saveState(fileName)
            deleteFile(fileName)
            deleteTrk = true
        }
    }

    // Delete all track files
    if (deleteTrk) {
        externalAppDir!!.walk().forEach {
            val fileName = it.name
            if (fileName != "files" && fileName.endsWith(C.TRK_EXTENSION, ignoreCase = true)) deleteFile(fileName)
        }
    }
}

fun clearFiles(extension: String) {
    if (externalAppDir == null) return
    externalAppDir!!.walk().forEach {
        val fileName = it.name
        if (fileName != "files" && fileName.endsWith(extension, ignoreCase = true)) deleteFile(fileName)
    }
}

fun saveTracePoint(p: LatLng) {
    Log.d("FileFunc", "saveTracePoints")
    if (tracePointsList.size == 0) return

    val str = JSONObject()
    str.put("lat", formatDouble(p.latitude, C.POS_PRECISION))
    str.put("lng", formatDouble(p.longitude, C.POS_PRECISION))
    str.put("date", getCurrentDate())
    str.put("time", getCurrentTime())

    val fileName = settings.planId + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    file.appendText(str.toString())
    file.appendText("\n")
}

fun loadTrace(): Boolean {
    val fileName = settings.planId + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    if (file.exists()) {
        tracePointsList.clear()
        val lines = file.readLines()
        for (line in lines) {
            if (line != "") {
                val json = JSONObject(line)
                val lat = getDoubleOrNull(getItem(json, "lat"))
                val lng = getDoubleOrNull(getItem(json, "lng"))
                if (lat != null && lng != null) tracePointsList.add(LatLng(lat, lng))
            }
        }
        if (tracePointsList.size > 1) {
            refreshDisplay = true
            return true
        } else tracePointsList.clear()
    }
    return false
}

fun deleteTrace() {
    tracePointsList.clear()
    deleteFile(settings.planId + C.TRK_EXTENSION)
}

fun encodeFlightPlanName(): String {
    var str = settings.planName
    val reservedChars = arrayOf("|", "\\", "?", "*", "<", "\"", ":", ">", "/", "!", "@", "#", "$", "%", "^", "&", "~", "{", "}", "[", "]", ";", " ")
    for (chr in reservedChars) str = str.replace(chr, "_")
    return str.trim()
}

fun savePlanAsCsv(): String {
    var fileName = ""

    if (navlogList.size > 0) {
        fileName = encodeFlightPlanName() + C.CSV_EXTENSION
        val file = File(externalAppDir, fileName)

        // Plan settings
        file.writeText("PLAN NAME;;" + settings.planName + "\n")
        file.appendText("DEP/DEST;;" + settings.departure + "/" + settings.destination + "\n")
        file.appendText("PLANE;;" + airplane.type + "/" + airplane.reg + "\n")
        file.appendText("WIND DIR/SPD;;" + formatDouble(settings.windDir) + "/" + formatDouble(settings.windSpd) + getUnitsSpd() + "\n")
        file.appendText("TAS;;" + formatDouble(airplane.tas) + getUnitsSpd() + "\n")
        file.appendText("FUEL/FPH;;" + formatDouble(settings.fob) + "/" + formatDouble(airplane.fph) + "\n")
        file.appendText("UNITS DIST/SPD/FUEL;;" + getUnitsSpd() + "/" + getUnitsDis() + "/" + getUnitsVol() + "\n")

        // Table header
        file.appendText("\n")
        file.appendText("DEST;TT;d;MT;DIST;WCA;HDG;GS;TIME;T.INC;FUEL;F.REM;ETA;ATA;RMK\n")

        for (i in navlogList.indices) {
            if (navlogList[i].active) {
                var msg = navlogList[i].dest + ";" +
                        formatDouble(navlogList[i].trueTrack) + ";" +
                        formatDouble(navlogList[i].declination) + ";" +
                        formatDouble(navlogList[i].magneticTrack) + ";" +
                        formatDouble(navlogList[i].distance) + ";" +
                        formatDouble(navlogList[i].wca) + ";" +
                        formatDouble(navlogList[i].hdg) + ";" +
                        formatDouble(navlogList[i].gs) + ";" +
                        formatSecondsToTime(navlogList[i].time) + ";" +
                        formatSecondsToTime(navlogList[i].timeIncrement) + ";" +
                        formatDouble(navlogList[i].fuel, 1) + ";" +
                        formatDouble(navlogList[i].fuelRemaining) + ";" +
                        ";" +   // ETA
                        ";" +   // ATA
                        navlogList[i].remarks.replace("\n", " ")
                msg = msg.replace("null", "")
                file.appendText(msg + "\n")
            }
        }
    }

    return fileName
}

fun savePlanAsGpx(): String {
    var fileName = ""
    var export = false
    var wpt = ""
    var trkpt = ""

    if (navlogList.size > 0 && settings.takeoffPos != null) {
        var name = "Start"
        var lat = formatDouble(settings.takeoffPos?.latitude, C.POS_PRECISION)
        var lng = formatDouble(settings.takeoffPos?.longitude, C.POS_PRECISION)
        wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
        trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")

        for (i in navlogList.indices) {
            if (navlogList[i].active && navlogList[i].coords != null) {
                export = true
                name = navlogList[i].dest
                lat = formatDouble(navlogList[i].coords?.latitude, C.POS_PRECISION)
                lng = formatDouble(navlogList[i].coords?.longitude, C.POS_PRECISION)
                wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
                trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")
            }
        }
    }

    if (export) {
        val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx version=\"1.0\">\n" +
                "\t<name>${settings.planName}</name>\n" +
                wpt +
                "<trk><name>Track</name><trkseg>\n" +
                trkpt +
                "</trkseg></trk></gpx>"

        fileName = encodeFlightPlanName() + C.GPX_EXTENSION
        val file = File(externalAppDir, fileName)
        file.writeText(gpx)
    }
    return fileName
}

fun saveTraceAsGpx(): String {
    //val tag = "saveTraceAsGpx"
    val trkFile = settings.planId + C.TRK_EXTENSION
    val file = File(externalAppDir, trkFile)

    if (file.exists()) {
        var export = false
        var trkpt = ""
        var item: JSONObject

        val lines = file.readLines()

        for (line in lines) {
            try {
                item = JSONObject(line)
                // Json fields: "lat", "lng", "date", "time"
                trkpt += ("\t<trkpt lat=\"${item["lat"]}\" lon=\"${item["lng"]}\"><ele></ele><time>${item["date"]}T${item["time"]}</time></trkpt>\n")
                export = true
            } catch (e: Exception) {
            }
        }

        if (export) {
            val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<gpx version=\"1.0\">\n" +
                    "\t<name>${settings.planName}</name>\n" +
                    "<trk><name>Track</name><trkseg>\n" +
                    trkpt +
                    "</trkseg></trk></gpx>"

            val gpxFileName = encodeFlightPlanName() + C.GPX_EXTENSION
            val gpxFile = File(externalAppDir, gpxFileName)
            gpxFile.writeText(gpx)
            return gpxFileName
        }
    }
    return ""
}

// Copy flight plan with new ID
fun copyFlightPlan(postfix: String) {
    settings.planId = generateStringId()
    settings.planName = settings.planName + " - $postfix"
    saveState()
}

fun saveAirplaneList() {
    val file = File(externalAppDir, C.airplanesFile)

    val json = JSONObject()
    var i = 0

    for (p in airplaneList) {
        val jAirplane = JSONObject()
        jAirplane.put("id", p.id)
        jAirplane.put("type", p.type)
        jAirplane.put("reg", p.reg)
        jAirplane.put("rmk", p.rmk)
        jAirplane.put("tas", p.tas)
        jAirplane.put("tank", p.tank)
        jAirplane.put("fph", p.fph)
        jAirplane.put("su", p.spdUnits)
        jAirplane.put("vu", p.volUnits)
        json.put("airplane_$i", jAirplane)
        i += 1
    }
    file.writeText(json.toString())
}

fun loadAirplaneList() {
    val tag = "loadAirplaneList"
    val file = File(externalAppDir, C.airplanesFile)
    if (!file.exists()) return

    val json: JSONObject
    airplaneList.clear()

    try {
        json = JSONObject(file.readText())
    } catch (e: Exception) {
        Log.d(tag, e.toString())
        return
    }

    (0 until json.length()).forEach {
        val key = "airplane_$it"
        val jItem = JSONObject(getItem(json, key) ?: "{}")
        if (jItem.length() == 0) return

        val id = getItem(jItem, "id")
        val type = getItem(jItem, "type") ?: ""
        val reg = getItem(jItem, "reg") ?: ""
        val rmk = getItem(jItem, "rmk") ?: ""
        val tas = getDoubleOrNull(getItem(jItem, "tas")) ?: 0.0
        val tank = getDoubleOrNull(getItem(jItem, "tank")) ?: 0.0
        val fph = getDoubleOrNull(getItem(jItem, "fph")) ?: 0.0
        val su = getItem(jItem, "su")?.toIntOrNull() ?: 0
        val vu = getItem(jItem, "vu")?.toIntOrNull() ?: 0

        if (id != null) {
            airplaneList.add(
                Airplane(
                    id = id,
                    type = type,
                    reg = reg,
                    rmk = rmk,
                    tas = tas,
                    tank = tank,
                    fph = fph,
                    spdUnits = su,
                    volUnits = vu
                )
            )
        }
    }
    airplaneList.sortBy { it.reg }
}

fun str2DateTimeJson(str: String?): LocalDateTime? {
    val dt: LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ofPattern(C.JSON_TIME_PATTERN))
    } catch (e: Exception) {
        null
    }
    return dt
}

fun getItem(json: JSONObject, item: String): String? {
    return if (json.has(item)) json[item].toString() else null
}