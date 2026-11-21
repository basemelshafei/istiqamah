package com.basem.qiyam.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.basem.qiyam.DayLog
import com.basem.qiyam.Prayer
import com.basem.qiyam.PrayerStatus
import com.basem.qiyam.Prefs
import java.io.File
import java.time.LocalDate

/** Shared helpers: realistic sample data and saving what's on screen as a PNG. */
object Shots {
    val dir = File("build/screenshots").apply { mkdirs() }

    fun seed(activity: ComponentActivity) {
        val prefs = Prefs(activity)
        val log = DayLog(activity)
        val today = LocalDate.now(prefs.zone)
        prefs.lat = 51.5074; prefs.lng = -0.1278; prefs.placeName = "London"
        prefs.quranCurrent = "Al-Mulk 1–15"
        prefs.gymDays = setOf(1, 2, 4, 5)

        val statuses = listOf(PrayerStatus.JAMAAH, PrayerStatus.ON_TIME, PrayerStatus.ON_TIME, PrayerStatus.LATE, PrayerStatus.JAMAAH)
        for (i in 1L..20L) {
            val d = today.minusDays(i)
            Prayer.entries.forEachIndexed { k, p ->
                val s = if ((i + k) % 9 == 0L) PrayerStatus.MISSED else statuses[((i + k) % statuses.size).toInt()]
                log.setPrayer(d, p, s)
            }
            if (i % 4 != 0L) log.setValue(d, "adhkar_am", 1)
            if (i % 3 != 0L) log.setValue(d, "adhkar_pm", 1)
            if (i % 2 == 0L) log.setValue(d, "quran_new", 1)
            if (i % 5 != 0L) log.setValue(d, "quran_rev", 1)
            if (d.dayOfWeek.value in prefs.gymDays && i % 7 != 3L) {
                log.setValue(d, "gym", 1); log.setText(d, "gym", listOf("Push", "Pull", "Legs", "Upper")[(i % 4).toInt()])
            }
            if (d.dayOfWeek.value == 1 || d.dayOfWeek.value == 4) log.setValue(d, "fast", 1)
            if (i <= 6) prefs.markWoke(d)
        }
        prefs.markWoke(today)
        for (i in 0L..60L step 3) log.setWeight(today.minusDays(i), 79.4f + i * 0.045f)
        // Connected apps: Lyfta workouts and a week of Fitbit data.
        com.basem.qiyam.Lyfta.save(activity, com.basem.qiyam.LyftaSample.json(today))
        prefs.lyftaKey = "sample"; prefs.lyftaSyncedAt = System.currentTimeMillis()
        prefs.healthSyncedAt = System.currentTimeMillis()
        for (i in 0L..6L) {
            val d = today.minusDays(i)
            log.setText(d, "hc_steps", (if (i == 0L) 6240 else 7000 + (i * 911) % 4000).toString())
            log.setText(d, "hc_sleep_min", (370 + (i * 17) % 60).toString())
            log.setText(d, "hc_rhr", (56 + i % 4).toString())
        }
        // What the Qiyam app would report.
        com.basem.qiyam.QiyamLink.override = com.basem.qiyam.QiyamSummary(
            enabled = true, streak = 7, best = 12, total = 41,
            wokeNights = (0L..6L).map { today.minusDays(it) }.toSet(),
            nextWake = java.time.Instant.now().plusSeconds(6 * 3600), lastThird = java.time.Instant.now().plusSeconds(6 * 3600),
            fajr = java.time.Instant.now().plusSeconds(10 * 3600), telegram = true,
        )
        prefs.qiyamSeenAt = System.currentTimeMillis()
        // Career
        com.basem.qiyam.Career.setExamDate(prefs, today.plusDays(62))
        listOf("secure" to 2, "resilient" to 2, "performing" to 1, "cost" to 1).forEach { (id, v) -> com.basem.qiyam.Career.setConfidence(prefs, id, v) }
        listOf(28L to 612, 21L to 655, 14L to 690, 7L to 731, 2L to 748).forEach { (ago, sc) ->
            com.basem.qiyam.Career.addScore(prefs, com.basem.qiyam.PracticeScore(today.minusDays(ago), sc, if (ago > 10) "Tutorials Dojo" else "AWS Skill Builder"))
        }
        listOf("c_net" to 2, "c_iam" to 2, "c_compute" to 1, "c_serverless" to 1, "c_iac" to 1, "c_containers" to 1).forEach { (id, v) -> com.basem.qiyam.Career.setTopicLevel(prefs, id, v) }
        com.basem.qiyam.Career.addProject(prefs, "Kafka-Coaims", "https://github.com/basemelshafei/Kafka-Coaims")
        com.basem.qiyam.Career.addProject(prefs, "Battery_Vision_Pipeline", "")
        com.basem.qiyam.Career.toggleMilestone(prefs, "Kafka-Coaims", 0); com.basem.qiyam.Career.toggleMilestone(prefs, "Kafka-Coaims", 1)
        prefs.putBool("p_gh", true); prefs.putBool("p_li", true)
        for (i in 0L..80L) if ((i * 7) % 5 != 0L) log.setValue(today.minusDays(i), "gh", ((i * 13) % 7).toInt())
        for (i in 1L..6L) com.basem.qiyam.Career.addStudy(log, today.minusDays(i), 30 + (i * 10 % 30).toInt())
        com.basem.qiyam.Career.addStudy(log, today, 30)
        // Wealth
        com.basem.qiyam.Wealth.save(prefs, com.basem.qiyam.Platform("t212", "Trading 212", "GBP", listOf(
            com.basem.qiyam.Holding("VUAG", "Vanguard S&P 500", 40.0, 3420.0, 3812.4),
            com.basem.qiyam.Holding("NVDA", "NVIDIA", 6.0, 610.0, 702.3),
            com.basem.qiyam.Holding("MSFT", "Microsoft", 3.0, 905.0, 951.6)), System.currentTimeMillis()))
        com.basem.qiyam.Wealth.save(prefs, com.basem.qiyam.Platform("etoro", "eToro", "USD", listOf(
            com.basem.qiyam.Holding("AMZN", "Amazon", 5.0, 900.0, 1012.0),
            com.basem.qiyam.Holding("BTC", "Bitcoin", 0.01, 950.0, 880.0)), System.currentTimeMillis()))
        com.basem.qiyam.Wealth.addIncome(prefs, com.basem.qiyam.IncomeEntry(today.minusDays(3), -24.0, "n8n cloud plan"))
        prefs.putBool("w_niche", true); prefs.putBool("w_demo", true)
        log.setPrayer(today, Prayer.FAJR, PrayerStatus.JAMAAH)
        log.setValue(today, "adhkar_am", 1)
    }

    fun save(rule: AndroidComposeTestRule<out ActivityScenarioRule<*>, *>, name: String) {
        rule.waitForIdle()
        val bmp = runCatching { rule.onRoot().captureToImage().asAndroidBitmap() }.getOrElse {
            // Fallback: draw the window into a bitmap.
            var out: Bitmap? = null
            rule.runOnUiThread {
                val v = (rule.activity as android.app.Activity).window.decorView.rootView
                out = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
            }
            out!!
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun tab(rule: AndroidComposeTestRule<*, *>, tag: String) {
        rule.onNodeWithTag(tag).performClick(); rule.waitForIdle()
    }
}
