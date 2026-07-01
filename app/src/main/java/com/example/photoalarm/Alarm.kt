package com.example.photoalarm

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One alarm. Persisted as JSON (see [toJson] / [fromJson]).
 *
 * [days] holds Calendar day constants (SUNDAY=1 .. SATURDAY=7).
 * If [days] is empty the alarm is a one-shot: it fires at the next occurrence
 * of hour:minute and then disables itself.
 */
data class Alarm(
    val id: Int,
    var hour: Int = 7,
    var minute: Int = 0,
    var label: String = "",
    var days: MutableSet<Int> = mutableSetOf(),
    var enabled: Boolean = true,
    var vibrate: Boolean = true,
    var gradualVolume: Boolean = true,
    var ringtoneUri: String? = null,
    var snoozeEnabled: Boolean = true,
    var snoozeMinutes: Int = 5,
    var maxSnoozes: Int = 3,
    var objectCount: Int = 1
) {
    fun timeText(): String = String.format("%02d:%02d", hour, minute)

    /** Short human summary of repeat days, in Romanian. */
    fun daysText(): String {
        if (days.isEmpty()) return "O singură dată"
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        if (days.size == 7) return "În fiecare zi"
        if (days == weekdays) return "Luni–Vineri"
        if (days == weekend) return "Weekend"
        val names = mapOf(
            Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar", Calendar.WEDNESDAY to "Mie",
            Calendar.THURSDAY to "Joi", Calendar.FRIDAY to "Vin",
            Calendar.SATURDAY to "Sâm", Calendar.SUNDAY to "Dum"
        )
        val order = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        return order.filter { days.contains(it) }.joinToString(", ") { names[it]!! }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("days", JSONArray(days.toList()))
        put("enabled", enabled)
        put("vibrate", vibrate)
        put("gradualVolume", gradualVolume)
        put("ringtoneUri", ringtoneUri ?: JSONObject.NULL)
        put("snoozeEnabled", snoozeEnabled)
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("objectCount", objectCount)
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daySet = mutableSetOf<Int>()
            val arr = o.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) daySet.add(arr.getInt(i))
            val uri = if (o.isNull("ringtoneUri")) null else o.optString("ringtoneUri", null)
            return Alarm(
                id = o.getInt("id"),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                label = o.optString("label", ""),
                days = daySet,
                enabled = o.optBoolean("enabled", true),
                vibrate = o.optBoolean("vibrate", true),
                gradualVolume = o.optBoolean("gradualVolume", true),
                ringtoneUri = uri,
                snoozeEnabled = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                maxSnoozes = o.optInt("maxSnoozes", 3),
                objectCount = o.optInt("objectCount", 1)
            )
        }
    }
}
