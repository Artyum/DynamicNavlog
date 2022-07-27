package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.artyum.dynamicnavlog.databinding.FragmentTimersBinding
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class TimersFragment : Fragment(R.layout.fragment_timers) {
    private var _binding: FragmentTimersBinding? = null
    private val bind get() = _binding!!

    private var tOffblock: LocalDateTime? = null
    private var tTakeoff: LocalDateTime? = null
    private var tLanding: LocalDateTime? = null
    private var tOnblock: LocalDateTime? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimersBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.timersLayout.keepScreenOn = options.keepScreenOn
        (activity as MainActivity).hideButtons()

        tOffblock = roundToMinutes(timers.offblock)
        tTakeoff = roundToMinutes(timers.takeoff)
        tLanding = roundToMinutes(timers.landing)
        tOnblock = roundToMinutes(timers.onblock)

        refreshView()
        calcSummary()
    }

    private fun refreshView() {
        bind.flightName.setText(settings.planName)

        var str = if (timers.offblock != null) formatDateTime(tOffblock, C.FORMAT_DATETIME) else ""
        bind.timeOffBlock.setText(str)

        str = if (timers.takeoff != null) formatDateTime(tTakeoff, C.FORMAT_DATETIME) else ""
        bind.timeTakeoff.setText(str)

        str = if (timers.landing != null) formatDateTime(tLanding, C.FORMAT_DATETIME) else ""
        bind.timeLanding.setText(str)

        str = if (timers.onblock != null) formatDateTime(tOnblock, C.FORMAT_DATETIME) else ""
        bind.timeOnBlock.setText(str)
    }

    private fun calcSummary() {
        var gnd1: Long = 0
        var gnd2: Long = 0
        var gndT: Long = 0
        var flightTime: Long = 0
        var blockTime: Long = 0

        // Ground time
        if (tOffblock != null && tTakeoff != null) gnd1 = Duration.between(tOffblock, tTakeoff).toMillis() / 1000
        if (tLanding != null && tOnblock != null) gnd2 = Duration.between(tLanding, tOnblock).toMillis() / 1000
        if (gnd1 != 0L && gnd2 != 0L) gndT = gnd1 + gnd2

        // Flight time
        if (tTakeoff != null && tLanding != null) flightTime = Duration.between(tTakeoff, tLanding).toMillis() / 1000

        // Block time
        if (tOffblock != null && tOnblock != null) blockTime = Duration.between(tOffblock, tOnblock).toMillis() / 1000

        // Display
        val stage = getFlightStage()
        if (stage >= C.STAGE_3_FLIGHT_IN_PROGRESS) bind.outGroundTime1.setText(formatSecondsToTime(gnd1))
        if (stage >= C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            bind.outGroundTime2.setText(formatSecondsToTime(gnd2))
            bind.outGroundTimeT.setText(formatSecondsToTime(gndT))
            bind.outBlockTime.setText(formatSecondsToTime(blockTime))
        }
        if (stage >= C.STAGE_4_AFTER_LANDING) bind.outFlightTime.setText(formatSecondsToTime(flightTime))
    }
}

fun roundToMinutes(t: LocalDateTime?): LocalDateTime? {
    if (t == null) return null
    return if (t.second <= 30) t.minusSeconds(t.second.toLong()) else t.plusSeconds((60 - t.second).toLong())
}

fun formatDateTime(t: LocalDateTime?, pattern: String): String {
    if (t == null) return ""

    val ldtZoned: ZonedDateTime = t.atZone(ZoneId.systemDefault())
    val utcZoned: ZonedDateTime = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"))

    return if (options.timeInUTC)
        utcZoned.format(DateTimeFormatter.ofPattern(pattern)) + C.ZULU_SIGN
    else
        ldtZoned.format(DateTimeFormatter.ofPattern(pattern))
}

fun formatDateTimeJson(t: LocalDateTime?): String {
    return if (t != null) {
        val ldtZoned: ZonedDateTime = t.atZone(ZoneId.systemDefault())
        ldtZoned.format(DateTimeFormatter.ofPattern(C.JSON_TIME_PATTERN))
    } else "null"
}

fun formatEpochTime(timestamp: Long): String {
    val sdf = SimpleDateFormat(C.FORMAT_DATETIME_SEC, Locale.US)
    val netDate = Date(timestamp)
    return sdf.format(netDate)
}

fun formatMillisToTime(millis: Long?, showSec: Boolean = false): String {
    if (millis == null) return ""
    if (millis <= 0L) return "0" + C.S_DELIMITER

    var h = TimeUnit.MILLISECONDS.toHours(millis)
    var m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    var s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    val sign = if (h < 0 || m < 0 || s < 0) "-" else ""
    if (h < 0) h = -h
    if (m < 0) m = -m
    if (s < 0) s = -s

    val hstr = h.toString()
    val mstr = m.toString()
    val sstr = s.toString()

    var msg: String = sign

    // Hour
    if (h > 0) msg += hstr + C.H_DELIMITER

    // Min
    if (h > 0) msg += mstr.padStart(2, '0') + C.M_DELIMITER
    if (h == 0L && m > 0) msg += mstr + C.M_DELIMITER

    // Sec
    if (h == 0L && m < C.TIME_THRESHOLD && !showSec) {
        if (m > 0) msg += sstr.padStart(2, '0') + C.S_DELIMITER
        else msg += sstr + C.S_DELIMITER
    } else if (showSec) {
        if (h == 0L && m == 0L) msg += sstr + C.S_DELIMITER
        else msg += sstr.padStart(2, '0') + C.S_DELIMITER
    }

    return msg
}

fun formatSecondsToTime(sec: Long?, showSec: Boolean = false): String {
    if (sec == null) return ""
    return formatMillisToTime(sec * 1000, showSec)
}

fun strTime2Sec(strTime: String): Double? {
    if (strTime == "") return null

    var str = strTime
    var h: Long? = null
    var m: Long? = null
    var s: Long? = null

    // sec
    if (!str.contains(C.H_DELIMITER) && !str.contains(C.M_DELIMITER) && str.contains(C.S_DELIMITER)) {
        str = str.replace(C.S_DELIMITER, "")
        h = 0
        m = 0
        s = str.toLongOrNull()
    }

    // min
    if (!str.contains(C.H_DELIMITER) && !str.contains(C.S_DELIMITER)) {
        str = str.replace(C.M_DELIMITER, "")
        val min = getDoubleOrNull(str)
        if (min != null) return (min * 60.0)
    }

    // min sec
    if (!str.contains(C.H_DELIMITER) && str.contains(C.M_DELIMITER) && str.contains(C.S_DELIMITER)) {
        h = 0
        val tmp = str.split(C.M_DELIMITER)
        if (tmp.size == 2) {
            m = tmp[0].toLongOrNull()
            s = tmp[1].replace(C.S_DELIMITER, "").toLongOrNull()
        }
    }

    // hour min
    if (str.contains(C.H_DELIMITER) && !str.contains(C.S_DELIMITER)) {
        s = 0
        val tmp = str.split(C.H_DELIMITER)
        if (tmp.size == 2) {
            h = tmp[0].toLongOrNull()
            m = tmp[1].replace(C.M_DELIMITER, "").toLongOrNull()
        }
    }

    // hour min sec
    if (str.contains(C.H_DELIMITER) && str.contains(C.M_DELIMITER) && str.contains(C.S_DELIMITER)) {
        var tmp = str.split(C.H_DELIMITER)
        if (tmp.size == 2) {
            h = tmp[0].toLongOrNull()
            tmp = tmp[1].split(C.M_DELIMITER)
            if (tmp.size == 2) {
                m = tmp[0].toLongOrNull()
                s = tmp[1].replace(C.S_DELIMITER, "").toLongOrNull()
            }
        }
    }

    if (h != null && m != null && s != null) {
        return (h * 3600 + m * 60 + s).toDouble()
    } else return null
}

//hours   = resultInt % 86400 / 3600
//minutes = resultInt % 86400 % 3600 / 60
//seconds = resultInt % 86400 % 3600 % 60

fun getFlightStage(): Int {
    if (timers.offblock == null) return C.STAGE_1_BEFORE_ENGINE_START
    if (timers.takeoff == null) return C.STAGE_2_ENGINE_RUNNING
    if (timers.landing == null) return C.STAGE_3_FLIGHT_IN_PROGRESS
    if (timers.onblock == null) return C.STAGE_4_AFTER_LANDING
    return C.STAGE_5_AFTER_ENGINE_SHUTDOWN
}

fun resetTimers() {
    timers.offblock = null
    timers.takeoff = null
    timers.landing = null
    timers.onblock = null
    timers.groundTime = null
    timers.flightTime = null
    timers.blockTime = null
}

fun getCurrentDate(): String {
    return SimpleDateFormat("yyyy-MM-dd").format(Date())
}

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm:ss").format(Date())
}