package com.example.photoalarm

import android.content.Context
import org.json.JSONArray

/** Persists the list of alarms as JSON in SharedPreferences. */
object AlarmStore {
    private const val PREFS = "photo_alarm_store"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): MutableList<Alarm> {
        val raw = prefs(context).getString(KEY_ALARMS, "[]") ?: "[]"
        val list = mutableListOf<Alarm>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            try {
                list.add(Alarm.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        list.sortWith(compareBy({ it.hour }, { it.minute }))
        return list
    }

    fun get(context: Context, id: Int): Alarm? = getAll(context).firstOrNull { it.id == id }

    private fun saveAll(context: Context, alarms: List<Alarm>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    /** Creates a new alarm with a fresh id (not yet scheduled). */
    fun newAlarm(context: Context): Alarm {
        val id = prefs(context).getInt(KEY_NEXT_ID, 1)
        prefs(context).edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return Alarm(id = id)
    }

    /** Inserts or updates an alarm by id. */
    fun put(context: Context, alarm: Alarm) {
        val all = getAll(context)
        val idx = all.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) all[idx] = alarm else all.add(alarm)
        saveAll(context, all)
    }

    fun delete(context: Context, id: Int) {
        val all = getAll(context).filter { it.id != id }
        saveAll(context, all)
    }
}
