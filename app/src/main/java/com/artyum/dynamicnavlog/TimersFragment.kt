package com.artyum.dynamicnavlog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.artyum.dynamicnavlog.databinding.FragmentTimersBinding
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class TimersFragment : Fragment(R.layout.fragment_timers) {
    private var _binding: FragmentTimersBinding? = null
    private val bind get() = _binding!!
    private var tOffblock: LocalDateTime? = null
    private var tTakeoff: LocalDateTime? = null
    private var tLanding: LocalDateTime? = null
    private var tOnblock: LocalDateTime? = null
    private lateinit var vm: GlobalViewModel

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
        vm = ViewModelProvider(requireActivity())[GlobalViewModel::class.java]
        bind.timersLayout.keepScreenOn = vm.options.value!!.keepScreenOn
        (activity as MainActivity).displayButtons()

        bind.utcSwitch.setOnCheckedChangeListener { _, isChecked ->
            val opt = vm.options.value!!.timeInUTC
            vm.options.value!!.timeInUTC = isChecked
            displayTimestamps()
            vm.options.value!!.timeInUTC = opt
        }

        bind.utcSwitch.isChecked = vm.options.value!!.timeInUTC

        // Test
        //vm.timers.value!!.offblock = LocalDateTime.of(2022, 9, 25, 15, 15, 34)
        //vm.timers.value!!.takeoff = LocalDateTime.of(2022, 9, 25, 15, 22, 14)
        //vm.timers.value!!.landing = LocalDateTime.of(2022, 9, 25, 17, 5, 14)
        //vm.timers.value!!.onblock = LocalDateTime.of(2022, 9, 25, 17, 13, 54)

        tOffblock = roundToMinutes(vm.timers.value!!.offblock)
        tTakeoff = roundToMinutes(vm.timers.value!!.takeoff)
        tLanding = roundToMinutes(vm.timers.value!!.landing)
        tOnblock = roundToMinutes(vm.timers.value!!.onblock)

        displayTimestamps()
        displaySummary()
    }

    private fun displayTimestamps() {
        bind.flightName.text = vm.settings.value!!.planName

        var str = if (vm.timers.value!!.offblock != null) formatDateTime(tOffblock, C.FORMAT_DATE) else ""
        bind.flightDate.text = str

        str = if (vm.timers.value!!.offblock != null) formatDateTime(tOffblock, C.FORMAT_TIME) else ""
        bind.timeOffBlock.text = str

        str = if (vm.timers.value!!.takeoff != null) formatDateTime(tTakeoff, C.FORMAT_TIME) else ""
        bind.timeTakeoff.text = str

        str = if (vm.timers.value!!.landing != null) formatDateTime(tLanding, C.FORMAT_TIME) else ""
        bind.timeLanding.text = str

        str = if (vm.timers.value!!.onblock != null) formatDateTime(tOnblock, C.FORMAT_TIME) else ""
        bind.timeOnBlock.text = str
    }

    private fun displaySummary() {
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
        if (stage >= C.STAGE_3_FLIGHT_IN_PROGRESS) bind.outGroundTime1.text = formatSecondsToTime(sec = gnd1, showSec = false)
        if (stage >= C.STAGE_5_AFTER_ENGINE_SHUTDOWN) {
            bind.outGroundTime2.text = formatSecondsToTime(sec = gnd2, showSec = false)
            bind.outGroundTimeT.text = formatSecondsToTime(sec = gndT, showSec = false)
            bind.outBlockTime.text = formatSecondsToTime(sec = blockTime, showSec = false)
        }
        if (stage >= C.STAGE_4_AFTER_LANDING) bind.outFlightTime.text = formatSecondsToTime(flightTime, showSec = false)
    }
}

fun roundToMinutes(t: LocalDateTime?): LocalDateTime? {
    if (t == null) return null
    return t.truncatedTo(ChronoUnit.MINUTES)
}

fun formatDateTime(t: LocalDateTime?, pattern: String): String {
    if (t == null) return ""

    val ldtZoned: ZonedDateTime = t.atZone(ZoneId.systemDefault())
    val utcZoned: ZonedDateTime = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"))

    return if (G.vm.options.value!!.timeInUTC) utcZoned.format(DateTimeFormatter.ofPattern(pattern)) + C.SIGN_ZULU
    else ldtZoned.format(DateTimeFormatter.ofPattern(pattern))
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

fun formatMillisToTime(millis: Long?, showSec: Boolean = true): String {
    if (millis == null) return ""
    if (millis <= 0L) return "0" + C.S_DELIMITER

    var h = TimeUnit.MILLISECONDS.toHours(millis)
    var m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    var s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    val sign = if (h < 0 || m < 0 || s < 0) "-" else ""
    if (h < 0) h = -h
    if (m < 0) m = -m
    if (s < 0) s = -s

    val hStr = h.toString()
    val mStr = m.toString()
    val sStr = s.toString()

    var msg: String = sign

    // Hour
    if (h > 0) msg += hStr + C.H_DELIMITER

    // Min
    if (h > 0) msg += mStr.padStart(2, '0') + C.M_DELIMITER
    else if (m > 0) msg += mStr + C.M_DELIMITER

    // Sec
    if (h == 0L && m < C.TIME_THRESHOLD && showSec) {
        msg += if (m > 0) sStr.padStart(2, '0') + C.S_DELIMITER
        else sStr + C.S_DELIMITER
    }
    return msg
}

fun formatSecondsToTime(sec: Long?, showSec: Boolean = true): String {
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
    if (G.vm.timers.value!!.offblock == null) return C.STAGE_1_BEFORE_ENGINE_START
    if (G.vm.timers.value!!.takeoff == null) return C.STAGE_2_ENGINE_RUNNING
    if (G.vm.timers.value!!.landing == null) return C.STAGE_3_FLIGHT_IN_PROGRESS
    if (G.vm.timers.value!!.onblock == null) return C.STAGE_4_AFTER_LANDING
    return C.STAGE_5_AFTER_ENGINE_SHUTDOWN
}

fun resetTimers() {
    G.vm.timers.value = Timers()
}

fun getCurrentDate(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}