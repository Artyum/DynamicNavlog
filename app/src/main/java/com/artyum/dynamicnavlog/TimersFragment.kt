package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.artyum.dynamicnavlog.databinding.FragmentTimersBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

class TimersFragment : Fragment(R.layout.fragment_timers) {
    private var _binding: FragmentTimersBinding? = null
    private val bind get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentTimersBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.timersLayout.keepScreenOn = settings.keepScreenOn

        refreshView()
        calcSummary()
    }

    private fun refreshView() {
        var str: String
        val z = if (settings.timeInUTC) "z" else ""

        bind.flightName.setText(settings.planName)

        str = if (timers.offblock != null) formatDateTime(timers.offblock, C.FORMAT_DATETIME) + z else ""
        bind.timeOffBlock.setText(str)

        str = if (timers.takeoff != null) formatDateTime(timers.takeoff, C.FORMAT_DATETIME) + z else ""
        bind.timeTakeoff.setText(str)

        str = if (timers.landing != null) formatDateTime(timers.landing, C.FORMAT_DATETIME) + z else ""
        bind.timeLanding.setText(str)

        str = if (timers.onblock != null) formatDateTime(timers.onblock, C.FORMAT_DATETIME) + z else ""
        bind.timeOnBlock.setText(str)
    }

    private fun calcSummary() {
        var gnd1: Long = 0
        var gnd2: Long = 0
        var gndT: Long = 0
        var flightTime: Long = 0
        var blockTime: Long = 0

        // Ground time
        if (timers.offblock != null && timers.takeoff != null) gnd1 = Duration.between(timers.offblock, timers.takeoff).toMillis() / 1000
        if (timers.landing != null && timers.onblock != null) gnd2 = Duration.between(timers.landing, timers.onblock).toMillis() / 1000
        if (gnd1 != 0L && gnd2 != 0L) gndT = gnd1 + gnd2

        // Flight time
        if (timers.takeoff != null && timers.landing != null) flightTime = Duration.between(timers.takeoff, timers.landing).toMillis() / 1000

        // Block time
        if (timers.offblock != null && timers.onblock != null) blockTime = Duration.between(timers.offblock, timers.onblock).toMillis() / 1000

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

fun formatDateTime(t: LocalDateTime?, pattern: String): String {
    if (t == null) return ""

    val ldtZoned: ZonedDateTime = t.atZone(ZoneId.systemDefault())
    val utcZoned: ZonedDateTime = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"))

    return if (settings.timeInUTC)
        utcZoned.format(DateTimeFormatter.ofPattern(pattern))
    else
        ldtZoned.format(DateTimeFormatter.ofPattern(pattern))
}

fun formatIniDateTime(t: LocalDateTime?): String {
    return if (t != null) {
        val ldtZoned: ZonedDateTime = t.atZone(ZoneId.systemDefault())
        ldtZoned.format(DateTimeFormatter.ofPattern(C.INI_TIME_PATTERN))
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

fun strTime2sec(strTime: String): Double? {
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