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

fun saveState() {
    val tag = "saveState"

    val jSettings = JSONObject()
    val jTimers = JSONObject()
    val jNavLog = JSONObject()
    val jRadials = JSONObject()

    val s = G.vm.settings.value!!
    val t = G.vm.timers.value!!

    if (s.planId == "") s.planId = generateStringId()
    Log.d(tag, "saveState planId: " + s.planId)

    // Settings
    jSettings.put("id", s.planId)
    jSettings.put("name", s.planName)
    jSettings.put("from", s.departure)
    jSettings.put("dest", s.destination)
    jSettings.put("planeId", s.airplaneId)
    jSettings.put("fob", s.fob)
    jSettings.put("deplat", s.takeoffPos?.latitude)
    jSettings.put("deplng", s.takeoffPos?.longitude)

    jSettings.put("winddir", s.windDir)
    jSettings.put("windspeed", s.windSpd)

    jSettings.put("maptype", s.mapType)
    jSettings.put("maptype", s.mapType)
    jSettings.put("mapfollow", s.mapFollow)

    // Timers
    jTimers.put("offblock", formatDateTimeJson(t.offblock))
    jTimers.put("takeoff", formatDateTimeJson(t.takeoff))
    jTimers.put("landing", formatDateTimeJson(t.landing))
    jTimers.put("onblock", formatDateTimeJson(t.onblock))

    // NavLog
    for (i in navlogList.indices) {
        if (isNavlogItemValid(i)) {
            val item = JSONObject()
            item.put("dest", navlogList[i].dest)
            item.put("tt", navlogList[i].tt)
            item.put("d", navlogList[i].d)
            item.put("mt", navlogList[i].mt)
            item.put("dist", navlogList[i].dist)
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
            item.put("lat", navlogList[i].pos?.latitude)
            item.put("lng", navlogList[i].pos?.longitude)

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

    // Normal save
    val file = File(externalAppDir, C.stateFile)
    file.writeText(json.toString())

    // Copy current state file to plan name file
    if (G.vm.settings.value!!.planName != "") {
        val fn = s.planId + C.JSON_EXTENSION
        val planFile = File(externalAppDir, fn)
        planFile.writeText(json.toString())
    }
}

fun loadState(fileName: String = C.stateFile) {
    val tag = "loadState"

    val file = File(externalAppDir, fileName)
    if (!file.exists()) return

    Log.d(tag, "loadState filename: $fileName")

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
            val pos: LatLng? = if (lat != null && lng != null) LatLng(lat, lng) else null

            if (dest.isNotEmpty() && mt != null && dist != null) newNavlogList.add(
                NavlogItem(
                    dest = dest,
                    tt = tt,
                    d = d,
                    mt = mt,
                    dist = dist,
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
                    pos = pos
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
    G.vm.settings.value = newSettings
    G.vm.timers.value = newTimers
    navlogList = newNavlogList
    radialList = newRadialList

    // Load airplane
    getAirplaneByID(G.vm.settings.value!!.airplaneId)

    Log.d(tag, "loadState completed planId: " + newSettings.planId)

    // Load trace
    if (isFlightInProgress()) loadTrace()
}

fun deleteFile(fileName: String) {
    val file = File(externalAppDir, fileName)
    if (file.exists()) file.delete()
}

fun saveOptions() {
    Log.d("saveOptions", "saveOptions")

    val jOptions = JSONObject()
    jOptions.put("spdunits", G.vm.options.value!!.spdUnits)
    jOptions.put("distunits", G.vm.options.value!!.distUnits)
    jOptions.put("volunits", G.vm.options.value!!.volUnits)
    jOptions.put("screenorient", G.vm.options.value!!.screenOrientation)
    jOptions.put("screenon", G.vm.options.value!!.keepScreenOn)
    jOptions.put("utc", G.vm.options.value!!.timeInUTC)
    jOptions.put("takeoffspd", G.vm.options.value!!.autoTakeoffSpd)
    jOptions.put("landingspd", G.vm.options.value!!.autoLandingSpd)
    jOptions.put("maporient", G.vm.options.value!!.mapOrientation)
    jOptions.put("trace", G.vm.options.value!!.displayTrace)
    jOptions.put("maparrow", G.vm.options.value!!.drawWindArrow)
    jOptions.put("radials", G.vm.options.value!!.drawRadials)
    jOptions.put("radialsm", G.vm.options.value!!.drawRadialsMarkers)
    jOptions.put("hints", G.vm.options.value!!.showHints)
    jOptions.put("gps", G.vm.options.value!!.gpsAssist)
    jOptions.put("autonext", G.vm.options.value!!.autoNext)
    jOptions.put("nextr", G.vm.options.value!!.nextRadiusIndex)
    jOptions.put("blockedit", G.vm.options.value!!.blockPlanEdit)

    val json = JSONObject()
    json.put("options", jOptions)

    val file = File(externalAppDir, C.optionsFile)
    file.writeText(json.toString())
}

fun loadOptions() {
    val tag = "loadOptions"
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
            newOptions.nextRadiusIndex = getItem(jOptions, "nextr")?.toIntOrNull() ?: C.DEFAULT_NEXT_RADIUS
            newOptions.blockPlanEdit = getItem(jOptions, "blockedit")?.toBoolean() ?: false
            G.vm.options.value = newOptions
        }
    } else {
        G.vm.options.value = Options()
        saveOptions()
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
    val tag = "loadFlightPlanList"
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

    val fileName = G.vm.settings.value!!.planId + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    file.appendText(str.toString())
    file.appendText("\n")
}

fun loadTrace(): Boolean {
    val fileName = G.vm.settings.value!!.planId + C.TRK_EXTENSION
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
            globalRefresh = true
            return true
        } else tracePointsList.clear()
    }
    return false
}

fun deleteTrace() {
    tracePointsList.clear()
    deleteFile(G.vm.settings.value!!.planId + C.TRK_EXTENSION)
}

fun encodeFlightPlanName(): String {
    var str = G.vm.settings.value!!.planName
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
        file.writeText("PLAN NAME;;" + G.vm.settings.value!!.planName + "\n")
        file.appendText("DEP/DEST;;" + G.vm.settings.value!!.departure + "/" + G.vm.settings.value!!.destination + "\n")
        file.appendText("PLANE;;" + G.vm.airplane.value!!.type + "/" + G.vm.airplane.value!!.reg + "\n")
        file.appendText("WIND DIR/SPD;;" + formatDouble(G.vm.settings.value!!.windDir) + "/" + formatDouble(G.vm.settings.value!!.windSpd) + getUnitsSpd() + "\n")
        file.appendText("TAS;;" + formatDouble(G.vm.airplane.value!!.tas) + getUnitsSpd() + "\n")
        file.appendText("FUEL/FPH;;" + formatDouble(G.vm.settings.value!!.fob) + "/" + formatDouble(G.vm.airplane.value!!.fph) + "\n")
        file.appendText("UNITS DIST/SPD/FUEL;;" + getUnitsSpd() + "/" + getUnitsDis() + "/" + getUnitsVol() + "\n")

        // Table header
        file.appendText("\n")
        file.appendText("DEST;TT;d;MT;DIST;WCA;HDG;GS;TIME;T.INC;FUEL;F.REM;ETA;ATA;RMK\n")

        for (i in navlogList.indices) {
            if (navlogList[i].active) {
                var msg = navlogList[i].dest + ";" +
                        formatDouble(navlogList[i].tt) + ";" +
                        formatDouble(navlogList[i].d) + ";" +
                        formatDouble(navlogList[i].mt) + ";" +
                        formatDouble(navlogList[i].dist) + ";" +
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

    if (navlogList.size > 0 && G.vm.settings.value!!.takeoffPos != null) {
        var name = "Start"
        var lat = formatDouble(G.vm.settings.value!!.takeoffPos?.latitude, C.POS_PRECISION)
        var lng = formatDouble(G.vm.settings.value!!.takeoffPos?.longitude, C.POS_PRECISION)
        wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
        trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")

        for (i in navlogList.indices) {
            if (navlogList[i].active && navlogList[i].pos != null) {
                export = true
                name = navlogList[i].dest
                lat = formatDouble(navlogList[i].pos?.latitude, C.POS_PRECISION)
                lng = formatDouble(navlogList[i].pos?.longitude, C.POS_PRECISION)
                wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
                trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")
            }
        }
    }

    if (export) {
        val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx version=\"1.0\">\n" +
                "\t<name>${G.vm.settings.value!!.planName}</name>\n" +
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
    val trkFile = G.vm.settings.value!!.planId + C.TRK_EXTENSION
    val file = File(externalAppDir, trkFile)

    if (file.exists()) {
        var cnt = 0
        var trkpt = ""
        var item: JSONObject

        val lines = file.readLines()

        for (line in lines) {
            try {
                item = JSONObject(line)
                // Json fields: "lat", "lng", "date", "time"
                trkpt += ("\t<trkpt lat=\"${item["lat"]}\" lon=\"${item["lng"]}\"><ele></ele><time>${item["date"]}T${item["time"]}</time></trkpt>\n")
                cnt += 1
            } catch (e: Exception) {
            }
        }

        if (cnt > 1) {
            val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<gpx version=\"1.0\">\n" +
                    "\t<name>${G.vm.settings.value!!.planName}</name>\n" +
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
    G.vm.settings.value!!.planId = generateStringId()
    G.vm.settings.value!!.planName = G.vm.settings.value!!.planName + " - $postfix"
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