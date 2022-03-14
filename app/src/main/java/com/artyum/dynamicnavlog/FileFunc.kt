package com.artyum.dynamicnavlog

import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val stateFileName = "current_state" + C.DNL_EXTENSION
var externalAppDir: File? = null
//var internalAppDir: File? = null

fun encodePlanName(): String {
    return settings.planName.trim().replace(" ", "_")
}

fun decodePlanName(name: String): String {
    return name.replace(C.DNL_EXTENSION, "").replace("_", " ")
}

fun saveState() {
    val tag = "FileFunc"
    val planName = encodePlanName()
    Log.d(tag, "saveState: ${planName + C.DNL_EXTENSION}")

    // Save current state
    val file = File(externalAppDir, stateFileName)

    file.writeText("${C.INI_SETTINGS_STR}\n")
    file.appendText("name;${settings.planName}\n")
    file.appendText("from;${settings.departure}\n")
    file.appendText("dest;${settings.destination}\n")
    file.appendText("plane;${settings.planeType}\n")
    file.appendText("reg;${settings.registration}\n")
    file.appendText("deplat;${settings.takeoffCoords?.latitude}\n")
    file.appendText("deplng;${settings.takeoffCoords?.longitude}\n")

    file.appendText("winddir;${settings.windDir}\n")
    file.appendText("windspeed;${settings.windSpd}\n")
    file.appendText("tas;${settings.tas}\n")
    file.appendText("fph;${settings.fph}\n")
    file.appendText("fob;${settings.fuelOnBoard}\n")
    file.appendText("tank;${settings.tankCapacity}\n")

    file.appendText("units;${settings.units}\n")
    file.appendText("gps;${settings.gpsAssist}\n")
    file.appendText("maptype;${settings.mapType}\n")
    file.appendText("maporient;${settings.mapOrientation}\n")
    file.appendText("autonext;${settings.autoNext}\n")
    file.appendText("utc;${settings.timeInUTC}\n")
    file.appendText("screen_on;${settings.keepScreenOn}\n")
    file.appendText("maptype;${settings.mapType}\n")
    file.appendText("mapfollow;${settings.mapFollow}\n")
    file.appendText("nextRadius;${settings.nextRadius}\n")
    file.appendText("trace;${settings.trace}\n")

    // Timers
    file.appendText("\n${C.INI_TIMERS_STR}\n")
    file.appendText("offblock;" + formatIniDateTime(timers.offblock) + "\n")
    file.appendText("takeoff;" + formatIniDateTime(timers.takeoff) + "\n")
    file.appendText("landing;" + formatIniDateTime(timers.landing) + "\n")
    file.appendText("onblock;" + formatIniDateTime(timers.onblock) + "\n")

    file.appendText("\n${C.INI_NAVLOG_STR}\n")
    for (i in navlogList.indices) {
        if (isNavlogItemValid(i)) {
            val msg = navlogList[i].dest + ";" +                    // 0
                    navlogList[i].trueTrack + ";" +                 // 1
                    navlogList[i].declination + ";" +               // 2
                    navlogList[i].magneticTrack + ";" +             // 3
                    navlogList[i].distance + ";" +                  // 4
                    navlogList[i].wca + ";" +                       // 5
                    navlogList[i].hdg + ";" +                       // 6
                    navlogList[i].gs + ";" +                        // 7
                    navlogList[i].time + ";" +                      // 8
                    navlogList[i].timeIncrement + ";" +             // 9
                    formatIniDateTime(navlogList[i].eta) + ";" +    // 10
                    formatIniDateTime(navlogList[i].ata) + ";" +    // 11
                    navlogList[i].fuel + ";" +                      // 12
                    navlogList[i].fuelRemaining + ";" +             // 13
                    navlogList[i].remarks.replace("\n", "\\n") + ";" +  // 14
                    navlogList[i].active + ";" +                    // 15
                    navlogList[i].current + ";" +                   // 16
                    navlogList[i].coords?.latitude + ";" +          // 17
                    navlogList[i].coords?.longitude                 // 18

            file.appendText(msg + "\n")
        }
    }

    // Save as Flight Plan
    if (planName != "") {
        val fileName = planName + C.DNL_EXTENSION
        val planFile = File(externalAppDir, fileName)
        //Log.d(tag, "Files.copy ${file.name}-> ${planFile.name}")
        Files.copy(file.toPath(), planFile.toPath(), REPLACE_EXISTING)
    }
}

fun loadState(fileName: String = stateFileName) {
    val tag = "FileFunc"
    Log.d(tag, "loadState: $fileName")

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
                        if (str[0] == "reg") newSettings.registration = str[1]

                        if (str[0] == "deplat") deplat = getDoubleOrNull(str[1])
                        if (str[0] == "deplng") deplng = getDoubleOrNull(str[1])
                        if (deplat != null && deplng != null) {
                            newSettings.takeoffCoords = LatLng(deplat, deplng)
                            deplat = null
                            deplng = null
                        }

                        if (str[0] == "winddir") newSettings.windDir = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "windspeed") newSettings.windSpd = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "tas") newSettings.tas = getDoubleOrNull(str[1]) ?: 0.0
                        if (str[0] == "fph") newSettings.fph = getDoubleOrNull(str[1])
                        if (str[0] == "fob") newSettings.fuelOnBoard = getDoubleOrNull(str[1])
                        if (str[0] == "tank") newSettings.tankCapacity = getDoubleOrNull(str[1])

                        if (str[0] == "units") newSettings.units = str[1].toIntOrNull() ?: 0
                        if (str[0] == "gps") newSettings.gpsAssist = str[1].toBoolean()
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: 0
                        if (str[0] == "maporient") newSettings.mapOrientation = str[1].toIntOrNull() ?: 0
                        if (str[0] == "autonext") newSettings.autoNext = str[1].toBoolean()
                        if (str[0] == "utc") newSettings.timeInUTC = str[1].toBoolean()
                        if (str[0] == "screen_on") newSettings.keepScreenOn = str[1].toBoolean()
                        if (str[0] == "maptype") newSettings.mapType = str[1].toIntOrNull() ?: GoogleMap.MAP_TYPE_NORMAL
                        if (str[0] == "mapfollow") newSettings.mapFollow = str[1].toBoolean()
                        if (str[0] == "nextRadius") newSettings.nextRadius = str[1].toIntOrNull() ?: C.DEFAULT_NEXT_RADIUS
                        if (str[0] == "trace") newSettings.trace = str[1].toBoolean()
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

        resetSettings()
        settings = newSettings
        timers = newTimers
        navlogList = newNavlogList
        loadTrace()
    }
}

fun deleteState(fileName: String) {
    val file = File(externalAppDir, fileName)
    if (file.exists()) file.delete()
}

fun refreshFlightPlanList(search: String = "") {
    val appPath = externalAppDir
    planList.clear()
    appPath?.walk()?.forEach {
        val fileName = it.name
        if (fileName != "files" && fileName != stateFileName && fileName.endsWith(C.DNL_EXTENSION)) {
            if (search != "") {
                // If file name contains search string
                if (fileName.contains(search, true)) planList.add(PlanListItem(fileName))
                else {
                    // Search inside file
                    val file = File(externalAppDir, fileName)
                    val lines = file.readLines()
                    var addItem = false
                    for (line in lines) {
                        if (line.startsWith("name;") || line.startsWith("from;") || line.startsWith("dest;") || line.startsWith("plane;") || line.startsWith("reg;")) {
                            val str = line.split(";")
                            if (str.size == 2 && str[1] != "") {
                                if (str[1].contains(search, true)) addItem = true
                            }
                        }
                    }
                    if (addItem) planList.add(PlanListItem(fileName))
                }
            } else planList.add(PlanListItem(fileName))
        }
    }
    planList.sortBy { it.fileName.lowercase() }
}

/*fun saveTracePosition(gps: GpsData) {
    val tag = "FileFunc"
    Log.d(tag, "saveTrackPosition")

    val time = LocalDateTime.now()
    val lat = formatDouble(gps.coords.latitude, C.COORDS_PRECISION)
    val lng = formatDouble(gps.coords.longitude, C.COORDS_PRECISION)
    val spd = formatDouble(gps.rawSpeed, 1)
    val alt = formatDouble(gps.altitude, 1)
    val msg = "$time;$lat;$lng;$spd;$alt"

    val fileName = normalizePlantName() + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    file.appendText(msg)
}*/

fun saveTracePoint(p: LatLng) {
    Log.d("FileFunc", "saveTracePoints")
    if (tracePointsList.size == 0) return
    val fileName = encodePlanName() + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    val str = formatDouble(p.latitude, C.COORDS_PRECISION) + ";" + formatDouble(p.longitude, C.COORDS_PRECISION) + "\n"
    file.appendText(str)
}

fun loadTrace() {
    val fileName = encodePlanName() + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    if (file.exists()) {
        tracePointsList.clear()
        val lines = file.readLines()
        for (line in lines) {
            if (line != "") {
                val c = line.split(';')
                if (c.size == 2) {
                    val lat = getDoubleOrNull(c[0])
                    val lng = getDoubleOrNull(c[1])
                    if (lat != null && lng != null) tracePointsList.add(LatLng(lat, lng))
                }
            }
        }
    }
}

fun deleteTrace() {
    tracePointsList.clear()
    val fileName = encodePlanName() + C.TRK_EXTENSION
    val file = File(externalAppDir, fileName)
    if (file.exists()) file.delete()
}

fun saveAsCsv(): String {
    var fileName = ""

    if (navlogList.size > 0) {
        fileName = encodePlanName() + C.CSV_EXTENSION
        val file = File(externalAppDir, fileName)

        // Header
        file.writeText("Dest;TT;d;MT;DIST;WCA;HDG;GS;TIME;T.INC;ETA;ATA;FUEL;F.REM;RMK\n")

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
                        ";" +   // ETA
                        ";" +   // ATA
                        formatDouble(navlogList[i].fuel, 1) + ";" +
                        formatDouble(navlogList[i].fuelRemaining) + ";" +
                        navlogList[i].remarks.replace("\n", " ")
                msg = msg.replace("null", "")
                file.appendText(msg + "\n")
            }
        }
    }

    return fileName
}

fun saveAsGpx(): String {
    var fileName = ""
    var gpx = ""
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
        gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx version=\"1.0\">\n" +
                "\t<name>Example gpx</name>\n" +
                wpt +
                "<trk><name>Example gpx</name><trkseg>\n" +
                trkpt +
                "</trkseg></trk></gpx>"

        fileName = encodePlanName() + C.GPX_EXTENSION
        val file = File(externalAppDir, fileName)
        file.writeText(gpx)
    }
    return fileName
}