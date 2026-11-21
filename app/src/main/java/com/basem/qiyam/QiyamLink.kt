package com.basem.qiyam

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant
import java.time.LocalDate

/** What Qiyam (the alarm app) shares with Istiqamah. */
data class QiyamSummary(
    val enabled: Boolean,
    val streak: Int,
    val best: Int,
    val total: Int,
    val wokeNights: Set<LocalDate>,
    val nextWake: Instant,
    val lastThird: Instant,
    val fajr: Instant,
    val telegram: Boolean,
)

/**
 * Reads Qiyam's read-only summary (content://com.basem.qiyam.summary/summary). Both apps are
 * signed with the same key, which is what lets Istiqamah, and only Istiqamah, read it.
 */
object QiyamLink {
    const val PACKAGE = "com.basem.qiyam"
    private val URI: Uri = Uri.parse("content://com.basem.qiyam.summary/summary")

    /** Tests and screenshots set this to show a summary without the Qiyam app installed. */
    @Volatile var override: QiyamSummary? = null

    @Volatile private var cached: QiyamSummary? = null

    /** Latest summary, or null if Qiyam isn't installed. Reads Qiyam each call: keep it off hot paths. */
    fun read(ctx: Context): QiyamSummary? {
        override?.let { return it }
        return runCatching {
            ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun i(n: String) = c.getInt(c.getColumnIndexOrThrow(n))
                fun l(n: String) = c.getLong(c.getColumnIndexOrThrow(n))
                QiyamSummary(
                    enabled = i("enabled") == 1,
                    streak = i("streak"), best = i("best"), total = i("total"),
                    wokeNights = c.getString(c.getColumnIndexOrThrow("woke_nights")).split(",")
                        .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet(),
                    nextWake = Instant.ofEpochMilli(l("next_wake")),
                    lastThird = Instant.ofEpochMilli(l("last_third")),
                    fajr = Instant.ofEpochMilli(l("fajr")),
                    telegram = i("telegram") == 1,
                )
            }
        }.getOrNull()
    }

    /** Refreshes from Qiyam and copies its nights into Istiqamah, so the Qiyam item ticks itself off. */
    fun sync(ctx: Context): QiyamSummary? {
        val s = read(ctx)
        cached = s
        if (s != null) Prefs(ctx).mergeWokeNights(s.wokeNights.map { it.toString() }.toSet())
        Prefs(ctx).qiyamSeenAt = if (s != null) System.currentTimeMillis() else 0L
        return s
    }

    fun last(): QiyamSummary? = override ?: cached

    /** Opens Qiyam to change alarm settings, or its download page if it isn't installed. */
    fun open(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch != null) ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/basemelshafei/qiyam-alarm/releases/latest"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
