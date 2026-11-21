package com.basem.qiyam

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import java.time.ZoneId

/**
 * Compose state that every stored value's getter reads and every setter bumps. Any part of the
 * screen that shows stored data redraws the moment it changes, whoever changed it (the screen,
 * the alarm service, or a background thread).
 */
object Changes {
    private val counter = mutableIntStateOf(0)
    fun watch() { counter.intValue }
    fun bump() { counter.intValue++ }
}

/** Settings and long-lived state. Day-by-day records live in [DayLog]. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("qiyam", Context.MODE_PRIVATE)

    private fun watch() = Changes.watch()

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        Changes.bump()
    }

    private fun bool(k: String, d: Boolean): Boolean { watch(); return sp.getBoolean(k, d) }
    private fun int(k: String, d: Int): Int { watch(); return sp.getInt(k, d) }
    private fun long(k: String, d: Long): Long { watch(); return sp.getLong(k, d) }
    private fun str(k: String, d: String): String { watch(); return sp.getString(k, d) ?: d }
    private fun dbl(k: String, d: Double): Double {
        watch(); return java.lang.Double.longBitsToDouble(sp.getLong(k, java.lang.Double.doubleToLongBits(d)))
    }


    val hasLocation: Boolean
        get() { watch(); return sp.contains("lat") }

    var lat: Double
        get() = dbl("lat", 51.5074)
        set(v) = edit { putLong("lat", java.lang.Double.doubleToLongBits(v)) }

    var lng: Double
        get() = dbl("lng", -0.1278)
        set(v) = edit { putLong("lng", java.lang.Double.doubleToLongBits(v)) }

    var placeName: String
        get() = str("place", "London (default)")
        set(v) = edit { putString("place", v) }

    var method: Method
        get() = runCatching { Method.valueOf(str("method", Method.MUSLIM_WORLD_LEAGUE.name)) }
            .getOrDefault(Method.MUSLIM_WORLD_LEAGUE)
        set(v) = edit { putString("method", v.name) }




    // --- Tracker -----------------------------------------------------------------------------

    /** Planned gym days, as java.time.DayOfWeek values 1 (Mon) to 7 (Sun). */
    var gymDays: Set<Int>
        get() { watch(); return sp.getStringSet("gymDays", setOf("1", "2", "4", "5"))!!.mapNotNull { it.toIntOrNull() }.toSet() }
        set(v) = edit { putStringSet("gymDays", v.map { it.toString() }.toSet()) }

    /** What you're memorising now, e.g. "Al-Mulk 1–10". */
    var quranCurrent: String
        get() = str("quranNow", "")
        set(v) = edit { putString("quranNow", v.trim()) }

    // --- Connected apps --------------------------------------------------------------------

    /** Daily step goal for the Body ring. */
    var stepGoal: Int
        get() = int("stepGoal", 8000)
        set(v) = edit { putInt("stepGoal", v) }

    var healthSyncedAt: Long
        get() = long("hcSynced", 0L)
        set(v) = edit { putLong("hcSynced", v) }

    /** Personal Lyfta API key (my.lyfta.app › API). Stays on this phone. */
    var lyftaKey: String
        get() = str("lyftaKey", "")
        set(v) = edit { putString("lyftaKey", v.trim()) }

    var lyftaSyncedAt: Long
        get() = long("lyftaSynced", 0L)
        set(v) = edit { putLong("lyftaSynced", v) }

    // --- Generic access for the Career and Wealth stores ---------------------------------------
    fun getInt(k: String, d: Int = 0) = int(k, d)
    fun putInt(k: String, v: Int) = edit { putInt(k, v) }
    fun getStr(k: String, d: String = "") = str(k, d)
    fun putStr(k: String, v: String) = edit { putString(k, v) }
    fun getBool(k: String, d: Boolean = false) = bool(k, d)
    fun putBool(k: String, v: Boolean) = edit { putBoolean(k, v) }
    fun getLong(k: String, d: Long = 0L) = long(k, d)
    fun putLong(k: String, v: Long) = edit { putLong(k, v) }

    // --- Streaks ----------------------------------------------------------------------------

    /** Nights (keyed by the date of their Fajr) on which you got up. */
    val wokeNights: Set<String>
        get() { watch(); return sp.getStringSet("woke", emptySet())!!.toSet() }

    fun markWoke(night: LocalDate) {
        val updated = (wokeNights + night.toString()).sorted().takeLast(400).toSet()
        edit { putStringSet("woke", updated) }
    }

    /** Adds nights reported by the Qiyam app (Istiqamah never removes any). */
    fun mergeWokeNights(nights: Set<String>) {
        val updated = (wokeNights + nights).sorted().takeLast(400).toSet()
        if (updated != wokeNights) edit { putStringSet("woke", updated) }
    }

    /** When the Qiyam app's summary was last read (0 = Qiyam not found). */
    var qiyamSeenAt: Long
        get() = long("qiyamSeen", 0L)
        set(v) = edit { putLong("qiyamSeen", v) }

    fun woke(night: LocalDate) = night.toString() in wokeNights

    /** Consecutive nights up to and including the most recent one. */
    fun streak(currentNight: LocalDate): Int {
        val set = wokeNights
        var d = if (currentNight.toString() in set) currentNight else currentNight.minusDays(1)
        var n = 0
        while (d.toString() in set) { n++; d = d.minusDays(1) }
        return n
    }

    fun best(): Int {
        val days = wokeNights.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    /** Rotates through the reminders so you don't see the same one two nights running. */
    fun nextReminderIndex(size: Int): Int {
        val i = sp.getInt("rem", -1) + 1
        sp.edit().putInt("rem", i).apply()
        return Math.floorMod(i, size)
    }

    val zone: ZoneId get() = ZoneId.systemDefault()
}
