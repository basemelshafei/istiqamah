package com.basem.qiyam

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QiyamLinkTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    @After fun clear() { QiyamLink.override = null }

    @Test fun withoutQiyamInstalledThereIsNoSummary() {
        assertNull(QiyamLink.sync(ctx))
        assertEquals(0L, Prefs(ctx).qiyamSeenAt)
    }

    @Test fun qiyamNightsTickOffTheQiyamItem() {
        QiyamLink.override = QiyamSummary(
            enabled = true, streak = 1, best = 1, total = 1, wokeNights = setOf(today),
            nextWake = Instant.now(), lastThird = Instant.now(), fajr = Instant.now(), telegram = false,
        )
        QiyamLink.sync(ctx)
        val qiyam = Plan.items(Prefs(ctx), DayLog(ctx), today, zone).first { it.id == "qiyam" }
        assertEquals(1f, qiyam.progress)
        assertTrue(Prefs(ctx).qiyamSeenAt > 0L)
    }
}
