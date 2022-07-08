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

fun generateStringId(): String {
    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val randomString = (1..15)
        .map { _ -> kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("");
    return randomString
}

fun saveState(fileName: String = "") {
    val tag = "FileFunc"

    val jSettings = JSONObject()
    val jTimers = JSONObject()
    val jNavLog = JSONObject()

    // Settings
    jSettings.put("id", settings.id)
    jSettings.put("name", settings.planName)
    jSettings.put("from", settings.departure)
    jSettings.put("dest", settings.destination)
    jSettings.put("planeId", settings.planeId)
    jSettings.put("fob", settings.fob)
    jSettings.put("deplat", settings.takeoffCoords?.latitude)
    jSettings.put("deplng", settings.takeoffCoords?.longitude)

    jSettings.put("winddir", settings.windDir)
    jSettings.put("windspeed", settings.windSpd)

    jSettings.put("spdunits", settings.spdUnits)
    jSettings.put("distunits", settings.distUnits)
    jSettings.put("volunits", settings.volUnits)

    jSettings.put("gps", settings.gpsAssist)
    jSettings.put("autonext", settings.autoNext)
    jSettings.put("nextr", settings.nextRadius)
    jSettings.put("trace", settings.recordTrace)

    jSettings.put("maptype", settings.mapType)
    jSettings.put("maporient", settings.mapOrientation)
    jSettings.put("screenon", settings.keepScreenOn)
    jSettings.put("utc", settings.timeInUTC)
    jSettings.put("maptype", settings.mapType)
    jSettings.put("mapfollow", settings.mapFollow)
    jSettings.put("screenorient", settings.screenOrientation)

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

    // Save
    val json = JSONObject()
    json.put("settings", jSettings)
    json.put("timers", jTimers)
    json.put("navlog", jNavLog)

    // Todo delete conversion after some time
    if (fileName != "") {
        // DNL to JSON conversion
        val fn: String = if (fileName == "current_state" + C.DNL_EXTENSION) {
            fileName.replace(C.DNL_EXTENSION, C.JSON_EXTENSION, ignoreCase = true)
        } else {
            settings.id + C.JSON_EXTENSION
        }
        val file = File(externalAppDir, fn)
        file.writeText(json.toString())
    } else {
        // Normal save
        val file = File(externalAppDir, C.stateFileName)
        file.writeText(json.toString())

        // Copy current state file to plan name file
        if (settings.planName != "") {
            val fn = settings.id + C.JSON_EXTENSION
            val planFile = File(externalAppDir, fn)
            Log.d(tag, "saveState: $fn")
            planFile.writeText(json.toString())
        }
    }
}

// TODO Delete this function after some time (v1.2.0 2022-06)
fun loadStateDnl(fileName: String = C.stateFileName) {
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
                        if (str[0] == "plane") newSettings.planeType = str[1]
                        if (str[0] == "reg") newSettings.planeReg = str[1]

                        if (str[0] == "deplat") deplat = getDoubleOrNull(str[1])
                        if (str[0] == "deplng") deplng = getDoubleOrNull(str[1])
                        if (deplat != null && deplng != null) {
                            newSettings.takeoffCoords = LatLng(deplat, deplng)
                            deplat = null
                            deplng = null
                        }

                        if (str[0] == "winddir") newSettings.windDir = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "windspeed") newSettings.windSpd = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "tas") newSettings.planeTas = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "fph") newSettings.planeFph = getDoubleOrNull(str[1])
                        if (str[0] == "fob") newSettings.fob = getDoubleOrNull(str[1])
                        if (str[0] == "tank") newSettings.planeTank = getDoubleOrNull(str[1])

                        if (str[0] == "units") newSettings.spdUnits = str[1].toIntOrNull() ?: 0
                        if (str[0] == "gps") newSettings.gpsAssist = str[1].toBoolean()
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: 0
                        if (str[0] == "maporient") newSettings.mapOrientation = str[1].toIntOrNull() ?: 0
                        if (str[0] == "autonext") newSettings.autoNext = str[1].toBoolean()
                        if (str[0] == "utc") newSettings.timeInUTC = str[1].toBoolean()
                        if (str[0] == "screen_on") newSettings.keepScreenOn = str[1].toBoolean()
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: GoogleMap.MAP_TYPE_NORMAL
                        if (str[0] == "mapfollow") newSettings.mapFollow = str[1].toBoolean()
                        if (str[0] == "nextr") newSettings.nextRadius = str[1].toIntOrNull() ?: C.DEFAULT_NEXT_RADIUS
                        if (str[0] == "trace") newSettings.recordTrace = str[1].toBoolean()
                        if (str[0] == "screenorient") newSettings.screenOrientation = str[1].toIntOrNull() ?: C.SCREEN_PORTRAIT
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
        newSettings.id = generateStringId()

        resetSettings()
        settings = newSettings
        timers = newTimers
        navlogList = newNavlogList
        // loadTrace()
    }
}

fun str2DateTimeJson(str: String?): LocalDateTime? {
    val dt: LocalDateTime? = try {
        LocalDateTime.parse(str, DateTimeFormatter.ofPattern(C.JSON_TIME_PATTERN))
    } catch (e: Exception) {
        null
    }
    return dt
}

fun loadState(fileName: String = C.stateFileName) {
    val tag = "FileFunc"

    val file = File(externalAppDir, fileName)
    if (!file.exists()) return
    Log.d(tag, "loadState: $fileName")

    val newSettings = Settings()
    val newTimers = Timers()
    val newNavlogList = ArrayList<NavlogItem>()

    val json: JSONObject

    try {
        json = JSONObject(file.readText())
    } catch (e: Exception) {
        Log.d(tag, e.toString())
        return
    }

    // Settings
    val jSettings = JSONObject(json["settings"].toString())

    newSettings.id = if (jSettings.has("id")) jSettings["id"].toString() else generateStringId()
    newSettings.planName = if (jSettings.has("name")) jSettings["name"].toString() else ""
    newSettings.departure = if (jSettings.has("from")) jSettings["from"].toString() else ""
    newSettings.destination = if (jSettings.has("dest")) jSettings["dest"].toString() else ""

    newSettings.planeId = if (jSettings.has("planeId")) jSettings["planeId"].toString() else ""

    val deplat = if (jSettings.has("deplat")) jSettings["deplat"].toString().toDoubleOrNull() else null
    val deplng = if (jSettings.has("deplng")) jSettings["deplng"].toString().toDoubleOrNull() else null
    if (deplat != null && deplng != null) newSettings.takeoffCoords = LatLng(deplat, deplng)

    newSettings.windDir = if (jSettings.has("winddir")) jSettings["winddir"].toString().toDoubleOrNull() ?: 0.0 else 0.0
    newSettings.windSpd = if (jSettings.has("windspeed")) jSettings["windspeed"].toString().toDoubleOrNull() ?: 0.0 else 0.0
    newSettings.fob = if (jSettings.has("fob")) jSettings["fob"].toString().toDoubleOrNull() else null
    newSettings.spdUnits = if (jSettings.has("spdunits")) jSettings["spdunits"].toString().toIntOrNull() ?: 0 else 0
    newSettings.distUnits = if (jSettings.has("distunits")) jSettings["distunits"].toString().toIntOrNull() ?: 0 else 0
    newSettings.volUnits = if (jSettings.has("volunits")) jSettings["volunits"].toString().toIntOrNull() ?: 0 else 0
    newSettings.gpsAssist = jSettings["gps"].toString().toBoolean()
    newSettings.mapType = if (jSettings.has("maptype")) jSettings["maptype"].toString().toIntOrNull() ?: 0 else 0
    newSettings.mapOrientation = if (jSettings.has("maporient")) jSettings["maporient"].toString().toIntOrNull() ?: 0 else 0
    newSettings.autoNext = jSettings["autonext"].toString().toBoolean()
    newSettings.timeInUTC = jSettings["utc"].toString().toBoolean()
    newSettings.keepScreenOn = jSettings["screenon"].toString().toBoolean()
    newSettings.mapType = if (jSettings.has("maptype")) jSettings["maptype"].toString().toIntOrNull() ?: GoogleMap.MAP_TYPE_NORMAL else GoogleMap.MAP_TYPE_NORMAL
    newSettings.mapFollow = jSettings["mapfollow"].toString().toBoolean()
    newSettings.nextRadius = if (jSettings.has("nextr")) jSettings["nextr"].toString().toIntOrNull() ?: C.DEFAULT_NEXT_RADIUS else C.DEFAULT_NEXT_RADIUS
    newSettings.recordTrace = jSettings["trace"].toString().toBoolean()
    newSettings.screenOrientation = if (jSettings.has("screenorient")) jSettings["screenorient"].toString().toIntOrNull() ?: C.SCREEN_SENSOR else C.SCREEN_SENSOR

    // Timers
    val jTimers = JSONObject(json["timers"].toString())
    newTimers.offblock = str2DateTimeJson(jTimers["offblock"].toString())
    newTimers.takeoff = str2DateTimeJson(jTimers["takeoff"].toString())
    newTimers.landing = str2DateTimeJson(jTimers["landing"].toString())
    newTimers.onblock = str2DateTimeJson(jTimers["onblock"].toString())

    // NavLog
    val jNavLog = JSONObject(json["navlog"].toString())
    (0 until jNavLog.length()).forEach {
        val wpt = JSONObject(jNavLog["wpt_$it"].toString())

        val dest = wpt["dest"].toString().uppercase()
        val tt = if (wpt.has("tt")) wpt["tt"].toString().toDoubleOrNull() else null
        val d = if (wpt.has("d")) wpt["d"].toString().toDoubleOrNull() else null
        val mt = if (wpt.has("mt")) wpt["mt"].toString().toDoubleOrNull() else null
        val dist = if (wpt.has("dist")) wpt["dist"].toString().toDoubleOrNull() else null
        val wca = if (wpt.has("wca")) wpt["wca"].toString().toDoubleOrNull() else null
        val hdg = if (wpt.has("hdg")) wpt["hdg"].toString().toDoubleOrNull() else null
        val gs = if (wpt.has("gs")) wpt["gs"].toString().toDoubleOrNull() else null
        val time = if (wpt.has("time")) wpt["time"].toString().toLongOrNull() else null
        val timeInc = if (wpt.has("timei")) wpt["timei"].toString().toLongOrNull() else null
        val eta = str2DateTimeJson(wpt["eta"].toString())
        val ata = str2DateTimeJson(wpt["ata"].toString())
        val fuel = if (wpt.has("fuel")) wpt["fuel"].toString().toDoubleOrNull() else null
        val fuelR = if (wpt.has("fuelr")) wpt["fuelr"].toString().toDoubleOrNull() else null
        val remarks = wpt["rmk"].toString()
        val active = wpt["act"].toString().toBoolean()
        val current = wpt["cur"].toString().toBoolean()
        val lat = if (wpt.has("lat")) wpt["lat"].toString().toDoubleOrNull() else null
        val lng = if (wpt.has("lng")) wpt["lng"].toString().toDoubleOrNull() else null
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

    resetSettings()
    settings = newSettings
    timers = newTimers
    navlogList = newNavlogList
    loadTrace()

    // Load airplane
    getAirplaneSettingsByID(settings.planeId)
}

fun deleteFile(fileName: String) {
    val file = File(externalAppDir, fileName)
    if (file.exists()) file.delete()
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
        if (fileName != "files" && fileName != C.stateFileName && fileName != C.airplanesFile && fileName.endsWith(C.JSON_EXTENSION)) {
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
// Todo delete this function with loadStateDnl
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
    str.put("lat", formatDouble(p.latitude, C.COORDS_PRECISION))
    str.put("lng", formatDouble(p.longitude, C.COORDS_PRECISION))
    str.put("date", getCurrentDate())
    str.put("time", getCurrentTime())

    val fileName = settings.id + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    file.appendText(str.toString())
    file.appendText("\n")
}

fun loadTrace() {
    val fileName = settings.id + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    if (file.exists()) {
        tracePointsList.clear()
        val lines = file.readLines()
        for (line in lines) {
            if (line != "") {
                val json = JSONObject(line)
                val lat = json["lat"].toString().toDoubleOrNull()
                val lng = json["lng"].toString().toDoubleOrNull()
                if (lat != null && lng != null) tracePointsList.add(LatLng(lat, lng))
            }
        }
    }
}

fun deleteTrace() {
    tracePointsList.clear()
    deleteFile(settings.id + C.TRK_EXTENSION)
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
        file.writeText("PLAN NAME;" + settings.planName + "\n")
        file.appendText("DEP / DEST;" + settings.departure + " / " + settings.destination + "\n")
        file.appendText("PLANE;" + settings.planeType + " / " + settings.planeReg + "\n")
        file.appendText("WIND DIR / SPD;" + formatDouble(settings.windDir) + " / " + formatDouble(settings.windSpd) + ' ' + getUnitsSpd() + "\n")
        file.appendText("TAS;" + formatDouble(settings.planeTas) + ' ' + getUnitsSpd() + "\n")
        file.appendText("FUEL / FPH;" + formatDouble(settings.fob) + " / " + formatDouble(settings.planeFph) + "\n")

        // Table header
        file.appendText("\n")
        file.appendText("DEST;TT;d;MT;DIST;WCA;HDG;GS;TIME;T.INC;ETA;ATA;FUEL;F.REM;RMK\n")

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

    if (navlogList.size > 0 && settings.takeoffCoords != null) {
        var name = "Start"
        var lat = formatDouble(settings.takeoffCoords?.latitude, C.COORDS_PRECISION)
        var lng = formatDouble(settings.takeoffCoords?.longitude, C.COORDS_PRECISION)
        wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
        trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")

        for (i in navlogList.indices) {
            if (navlogList[i].active && navlogList[i].coords != null) {
                export = true
                name = navlogList[i].dest
                lat = formatDouble(navlogList[i].coords?.latitude, C.COORDS_PRECISION)
                lng = formatDouble(navlogList[i].coords?.longitude, C.COORDS_PRECISION)
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
    val tag = "saveTraceAsGpx"
    val trkFile = settings.id + C.TRK_EXTENSION
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
    settings.id = generateStringId()
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

    airplaneList.clear()
    val json = JSONObject(file.readText())

    (0 until json.length()).forEach {
        val a: JSONObject
        try {
            a = JSONObject(json["airplane_$it"].toString())
        } catch (e: Exception) {
            Log.d(tag, e.toString())
            return
        }

        val id = if (a.has("id")) a["id"].toString() else null
        val type = if (a.has("type")) a["type"].toString() else ""
        val reg = if (a.has("reg")) a["reg"].toString() else ""
        val rmk = if (a.has("rmk")) a["rmk"].toString() else ""

        val tas = if (a.has("tas")) a["tas"].toString().toDoubleOrNull() ?: 0.0 else 0.0
        val tank = if (a.has("tank")) a["tank"].toString().toDoubleOrNull() ?: 0.0 else 0.0
        val fph = if (a.has("fph")) a["fph"].toString().toDoubleOrNull() ?: 0.0 else 0.0
        val su = if (a.has("su")) a["su"].toString().toIntOrNull() ?: 0 else 0
        val vu = if (a.has("vu")) a["vu"].toString().toIntOrNull() ?: 0 else 0

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
