package com.artyum.dynamicnavlog

import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileUtils {
    var externalAppDir: File? = null
    //var internalAppDir: File? = null

    fun saveState() {
        val tag = "saveState"

        val jSettings = JSONObject()
        val jTimers = JSONObject()
        val jNavLog = JSONObject()
        val jRadials = JSONObject()

        val s = State.settings
        val t = State.timers
        val nl = State.navlogList
        val rl = State.radialList

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
        jTimers.put("offblock", TimeUtils.formatDateTimeJson(t.offblock))
        jTimers.put("takeoff", TimeUtils.formatDateTimeJson(t.takeoff))
        jTimers.put("landing", TimeUtils.formatDateTimeJson(t.landing))
        jTimers.put("onblock", TimeUtils.formatDateTimeJson(t.onblock))

        // NavLog
        for (i in nl.indices) {
            if (NavLogUtils.isNavlogItemValid(i)) {
                val item = JSONObject()
                item.put("dest", nl[i].dest)
                item.put("tt", nl[i].tt)
                item.put("d", nl[i].d)
                item.put("mt", nl[i].mt)
                item.put("dist", nl[i].dist)
                item.put("wca", nl[i].wca)
                item.put("hdg", nl[i].hdg)
                item.put("gs", nl[i].gs)
                item.put("time", nl[i].time)
                item.put("timei", nl[i].timeIncrement)
                item.put("eta", TimeUtils.formatDateTimeJson(nl[i].eta))
                item.put("ata", TimeUtils.formatDateTimeJson(nl[i].ata))
                item.put("fuel", nl[i].fuel)
                item.put("fuelr", nl[i].fuelRemaining)
                item.put("rmk", nl[i].remarks)
                item.put("act", nl[i].active)
                item.put("cur", nl[i].current)
                item.put("lat", nl[i].pos?.latitude)
                item.put("lng", nl[i].pos?.longitude)

                jNavLog.put("wpt_$i", item)
            }
        }

        // Radials
        for (i in rl.indices) {
            val r = JSONObject()
            r.put("angle", rl[i].angle)
            r.put("dist", rl[i].dist)
            r.put("lat1", rl[i].pos1.latitude)
            r.put("lng1", rl[i].pos1.longitude)
            r.put("lat2", rl[i].pos2.latitude)
            r.put("lng2", rl[i].pos2.longitude)

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
        Log.d(tag, "saveState: " + C.stateFile + " OK")

        // Copy current state file to plan name file
        if (State.settings.planName != "") {
            if (State.settings.planId == "") State.settings.planId = Utils.generateStringId()
            val fn = State.settings.planId + C.JSON_EXTENSION
            val planFile = File(externalAppDir, fn)
            planFile.writeText(json.toString())
            Log.d(tag, "saveState: planId: " + State.settings.planId + "; planName: " + State.settings.planName)
        } else {
            Log.d(tag, "saveState: Empty planName")
        }
    }

    fun loadState(fileName: String = C.stateFile) {
        val tag = "loadState"

        val file = File(externalAppDir, fileName)
        if (!file.exists()) return

        Log.d(tag, "loadState filename: $fileName")

        val newSettings = SettingsData()
        val newTimers = TimersData()
        val newNavlogList = ArrayList<NavlogItemData>()
        val newRadialList = ArrayList<RadialData>()

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
            newSettings.planId = getItem(jSettings, "id") ?: Utils.generateStringId()
            newSettings.planName = getItem(jSettings, "name") ?: ""
            newSettings.departure = getItem(jSettings, "from") ?: ""
            newSettings.destination = getItem(jSettings, "dest") ?: ""
            newSettings.airplaneId = getItem(jSettings, "planeId") ?: ""

            val depLat = Utils.getDoubleOrNull(getItem(jSettings, "deplat"))
            val depLng = Utils.getDoubleOrNull(getItem(jSettings, "deplng"))
            if (depLat != null && depLng != null) newSettings.takeoffPos = LatLng(depLat, depLng)

            newSettings.windDir = Utils.getDoubleOrNull(getItem(jSettings, "winddir")) ?: 0.0
            newSettings.windSpd = Utils.getDoubleOrNull(getItem(jSettings, "windspeed")) ?: 0.0
            newSettings.fob = Utils.getDoubleOrNull(getItem(jSettings, "fob")) ?: 0.0
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
                val tt = Utils.getDoubleOrNull(getItem(wpt, "tt"))
                val d = Utils.getDoubleOrNull(getItem(wpt, "d"))
                val mt = Utils.getDoubleOrNull(getItem(wpt, "mt"))
                val dist = Utils.getDoubleOrNull(getItem(wpt, "dist"))
                val wca = Utils.getDoubleOrNull(getItem(wpt, "wca"))
                val hdg = Utils.getDoubleOrNull(getItem(wpt, "hdg"))
                val gs = Utils.getDoubleOrNull(getItem(wpt, "gs"))
                val time = getItem(wpt, "time")?.toLongOrNull()
                val timeInc = getItem(wpt, "timei")?.toLongOrNull()
                val eta = str2DateTimeJson(getItem(wpt, "eta"))
                val ata = str2DateTimeJson(getItem(wpt, "ata"))
                val fuel = Utils.getDoubleOrNull(getItem(wpt, "fuel"))
                val fuelR = Utils.getDoubleOrNull(getItem(wpt, "fuelr"))
                val remarks = getItem(wpt, "rmk") ?: ""
                val active = getItem(wpt, "act")?.toBoolean() ?: true
                val current = getItem(wpt, "cur")?.toBoolean() ?: false

                val lat = Utils.getDoubleOrNull(getItem(wpt, "lat"))
                val lng = Utils.getDoubleOrNull(getItem(wpt, "lng"))
                val pos: LatLng? = if (lat != null && lng != null) LatLng(lat, lng) else null

                if (dest.isNotEmpty() && mt != null && dist != null) newNavlogList.add(
                    NavlogItemData(
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
                val angle = Utils.getDoubleOrNull(getItem(r, "angle"))
                val dist = Utils.getDoubleOrNull(getItem(r, "dist"))
                val lat1 = Utils.getDoubleOrNull(getItem(r, "lat1"))
                val lng1 = Utils.getDoubleOrNull(getItem(r, "lng1"))
                val lat2 = Utils.getDoubleOrNull(getItem(r, "lat2"))
                val lng2 = Utils.getDoubleOrNull(getItem(r, "lng2"))

                if (angle != null && dist != null && lat1 != null && lng1 != null && lat2 !== null && lng2 != null) {
                    newRadialList.add(
                        RadialData(
                            angle = angle,
                            dist = dist,
                            pos1 = LatLng(lat1, lng1),
                            pos2 = LatLng(lat2, lng2)
                        )
                    )
                }
            }
        }

        SettingUtils.resetAllSettings()
        State.settings = newSettings
        State.timers = newTimers
        State.navlogList = newNavlogList
        State.radialList = newRadialList

        // Load airplane
        AirplaneUtils.getAirplaneByID(State.settings.airplaneId)

        Log.d(tag, "loadState completed planId: " + State.settings.planId + "; planName:" + State.settings.planName)

        // Load trace
        if (Utils.isFlightInProgress()) FileUtils.loadTrace()
    }

    fun deleteFile(fileName: String) {
        val file = File(externalAppDir, fileName)
        if (file.exists()) file.delete()
    }

    fun saveOptions() {
        Log.d("saveOptions", "saveOptions")

        val jOptions = JSONObject()
        val o = State.options

        jOptions.put("spdunits", o.spdUnits)
        jOptions.put("distunits", o.distUnits)
        jOptions.put("volunits", o.volUnits)
        jOptions.put("screenorient", o.screenOrientation)
        jOptions.put("screenon", o.keepScreenOn)
        jOptions.put("utc", o.timeInUTC)
        jOptions.put("takeoffspd", o.autoTakeoffSpd)
        jOptions.put("landingspd", o.autoLandingSpd)
        jOptions.put("maporient", o.mapOrientation)
        jOptions.put("trace", o.displayTrace)
        jOptions.put("maparrow", o.drawWindArrow)
        jOptions.put("radials", o.drawRadials)
        jOptions.put("radialsm", o.drawRadialsMarkers)
        jOptions.put("hints", o.showHints)
        jOptions.put("gps", o.gpsAssist)
        jOptions.put("autonext", o.autoNext)
        jOptions.put("nextr", o.nextRadiusIndex)
        jOptions.put("blockedit", o.blockPlanEdit)

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
            val newOptions = OptionsData()
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
                newOptions.autoTakeoffSpd = Utils.getDoubleOrNull(getItem(jOptions, "takeoffspd")) ?: C.AUTO_TAKEOFF_MIN_SPEED_KT
                newOptions.autoLandingSpd = Utils.getDoubleOrNull(getItem(jOptions, "landingspd")) ?: C.AUTO_LANDING_MIN_SPEED_KT
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

                State.options = newOptions
            }
        } else {
            State.options = OptionsData()
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
        State.planList.clear()
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

                    if (addItem) State.planList.add(PlanListItemData(id = fileName, planName = getPlanNameFromJson(fileName)))
                } else {
                    State.planList.add(PlanListItemData(id = fileName, planName = getPlanNameFromJson(fileName)))
                }
            }
        }
        State.planList.sortBy { it.planName.lowercase() }
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
        if (State.tracePointsList.size == 0) return

        val str = JSONObject()
        str.put("lat", Utils.formatDouble(p.latitude, C.POS_PRECISION))
        str.put("lng", Utils.formatDouble(p.longitude, C.POS_PRECISION))
        str.put("date", TimeUtils.getCurrentDate())
        str.put("time", TimeUtils.getCurrentTime())

        val fileName = State.settings.planId + C.TRK_EXTENSION
        val file = File(externalAppDir, fileName)
        file.appendText(str.toString())
        file.appendText("\n")
    }

    fun loadTrace(): Boolean {
        val fileName = State.settings.planId + C.TRK_EXTENSION
        val file = File(externalAppDir, fileName)
        if (file.exists()) {
            State.tracePointsList.clear()
            val lines = file.readLines()
            for (line in lines) {
                if (line != "") {
                    val json = JSONObject(line)
                    val lat = Utils.getDoubleOrNull(getItem(json, "lat"))
                    val lng = Utils.getDoubleOrNull(getItem(json, "lng"))
                    if (lat != null && lng != null) State.tracePointsList.add(LatLng(lat, lng))
                }
            }
            if (State.tracePointsList.size > 1) {
                Vars.globalRefresh = true
                return true
            } else State.tracePointsList.clear()
        }
        return false
    }

    fun deleteTrace() {
        State.tracePointsList.clear()
        deleteFile(State.settings.planId + C.TRK_EXTENSION)
    }

    fun encodeFlightPlanName(): String {
        var str: String
        str = State.settings.planName
        val reservedChars = arrayOf("|", "\\", "?", "*", "<", "\"", ":", ">", "/", "!", "@", "#", "$", "%", "^", "&", "~", "{", "}", "[", "]", ";", " ")
        for (chr in reservedChars) str = str.replace(chr, "_")
        return str.trim()
    }

    fun savePlanAsCsv(): String {
        var fileName = ""

        if (State.navlogList.size > 0) {
            fileName = encodeFlightPlanName() + C.CSV_EXTENSION
            val file = File(externalAppDir, fileName)

            // Plan settings
            file.writeText("PLAN NAME;;" + State.settings.planName + "\n")
            file.appendText("DEP/DEST;;" + State.settings.departure + "/" + State.settings.destination + "\n")
            file.appendText("PLANE;;" + State.airplane.type + "/" + State.airplane.reg + "\n")
            file.appendText("WIND DIR/SPD;;" + Utils.formatDouble(State.settings.windDir) + "/" + Utils.formatDouble(State.settings.windSpd) + Convert.getUnitsSpd() + "\n")
            file.appendText("TAS;;" + Utils.formatDouble(State.airplane.tas) + Convert.getUnitsSpd() + "\n")
            file.appendText("FUEL/FPH;;" + Utils.formatDouble(State.settings.fob) + "/" + Utils.formatDouble(State.airplane.fph) + "\n")
            file.appendText("UNITS DIST/SPD/FUEL;;" + Convert.getUnitsSpd() + "/" + Convert.getUnitsDis() + "/" + Convert.getUnitsVol() + "\n")

            // Table header
            file.appendText("\n")
            file.appendText("DEST;TT;d;MT;DIST;WCA;HDG;GS;TIME;T.INC;FUEL;F.REM;ETA;ATA;RMK\n")

            for (i in State.navlogList.indices) {
                if (State.navlogList[i].active) {
                    var msg =
                        State.navlogList[i].dest + ";" + Utils.formatDouble(State.navlogList[i].tt) + ";" + Utils.formatDouble(State.navlogList[i].d) + ";" + Utils.formatDouble(State.navlogList[i].mt) + ";"
                    msg += Utils.formatDouble(State.navlogList[i].dist) + ";" + Utils.formatDouble(State.navlogList[i].wca) + ";" + Utils.formatDouble(State.navlogList[i].hdg) + ";" + Utils.formatDouble(State.navlogList[i].gs) + ";"
                    msg += TimeUtils.formatSecondsToTime(State.navlogList[i].time) + ";" + TimeUtils.formatSecondsToTime(State.navlogList[i].timeIncrement) + ";" + Utils.formatDouble(State.navlogList[i].fuel, 1) + ";"
                    msg += Utils.formatDouble(State.navlogList[i].fuelRemaining) + ";" + ";" + ";" + State.navlogList[i].remarks.replace("\n", " ")

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

        if (State.navlogList.size > 0 && State.settings.takeoffPos != null) {
            var name = "Start"
            var lat = Utils.formatDouble(State.settings.takeoffPos?.latitude, C.POS_PRECISION)
            var lng = Utils.formatDouble(State.settings.takeoffPos?.longitude, C.POS_PRECISION)
            wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
            trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")

            for (i in State.navlogList.indices) {
                if (State.navlogList[i].active && State.navlogList[i].pos != null) {
                    export = true
                    name = State.navlogList[i].dest
                    lat = Utils.formatDouble(State.navlogList[i].pos?.latitude, C.POS_PRECISION)
                    lng = Utils.formatDouble(State.navlogList[i].pos?.longitude, C.POS_PRECISION)
                    wpt += ("\t<wpt lat=\"$lat\" lon=\"$lng\"><name>$name</name></wpt>\n")
                    trkpt += ("\t<trkpt lat=\"$lat\" lon=\"$lng\"></trkpt>\n")
                }
            }
        }

        if (export) {
            val gpx =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<gpx version=\"1.0\">\n" + "\t<name>${State.settings.planName}</name>\n" + wpt + "<trk><name>Track</name><trkseg>\n" + trkpt + "</trkseg></trk></gpx>"

            fileName = encodeFlightPlanName() + C.GPX_EXTENSION
            val file = File(externalAppDir, fileName)
            file.writeText(gpx)
        }
        return fileName
    }

    fun saveTraceAsGpx(): String {
        val trkFile = State.settings.planId + C.TRK_EXTENSION
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
                } catch (_: Exception) {
                }
            }

            if (cnt > 1) {
                val gpx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<gpx version=\"1.0\">\n" +
                        "\t<name>${State.settings.planName}</name>\n" +
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
    fun copyFlightPlan(postfix: String): Boolean {
        if (State.settings.planName == "") return false

        State.settings.planId = Utils.generateStringId()
        State.settings.planName = State.settings.planName + " - $postfix"

        FileUtils.saveState()
        return true
    }

    fun saveAirplaneList() {
        val file = File(externalAppDir, C.airplanesFile)

        val json = JSONObject()
        var i = 0

        for (p in State.airplaneList) {
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
        State.airplaneList.clear()

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
            val tas = Utils.getDoubleOrNull(getItem(jItem, "tas")) ?: 0.0
            val tank = Utils.getDoubleOrNull(getItem(jItem, "tank")) ?: 0.0
            val fph = Utils.getDoubleOrNull(getItem(jItem, "fph")) ?: 0.0
            val su = getItem(jItem, "su")?.toIntOrNull() ?: 0
            val vu = getItem(jItem, "vu")?.toIntOrNull() ?: 0

            if (id != null) {
                State.airplaneList.add(
                    AirplaneData(
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
        State.airplaneList.sortBy { it.reg }
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
}