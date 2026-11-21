package com.basem.qiyam

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CareerWealthTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs get() = Prefs(ctx)

    @Test fun readinessFollowsExamWeights() {
        assertEquals(0, Career.readiness(prefs))
        Career.setConfidence(prefs, "secure", 3) // 30% of the exam, fully confident
        assertEquals(30, Career.readiness(prefs))
        Career.domains.forEach { Career.setConfidence(prefs, it.id, 3) }
        assertEquals(100, Career.readiness(prefs))
    }

    @Test fun practiceVerdicts() {
        val d = LocalDate.of(2026, 11, 1)
        assertTrue(Career.verdict(emptyList()).startsWith("Take"))
        assertTrue(Career.verdict(listOf(PracticeScore(d, 650, ""), PracticeScore(d, 690, ""))).startsWith("Keep going"))
        assertTrue(Career.verdict(listOf(PracticeScore(d, 820, ""), PracticeScore(d, 845, ""))).startsWith("Ready"))
    }

    @Test fun githubCalendarAndEvents() {
        val cal = """{"data":{"user":{"contributionsCollection":{"contributionCalendar":{"weeks":[
            {"contributionDays":[{"date":"2026-11-20","contributionCount":4},{"date":"2026-11-21","contributionCount":0}]}]}}}}}"""
        assertEquals(4, GitHub.parseCalendar(cal)[LocalDate.of(2026, 11, 20)])
        val events = """[{"type":"PushEvent","created_at":"2026-11-20T10:00:00Z"},{"type":"WatchEvent","created_at":"2026-11-20T11:00:00Z"},
            {"type":"PushEvent","created_at":"2026-11-20T12:00:00Z"}]"""
        assertEquals(2, GitHub.parseEvents(events, ZoneOffset.UTC)[LocalDate.of(2026, 11, 20)])
    }

    @Test fun trading212PositionsUseAccountCurrency() {
        val json = """[{"averagePricePaid":150.0,"currentPrice":180.0,"quantity":2,
            "instrument":{"ticker":"AAPL_US_EQ","name":"Apple","currency":"USD"},
            "walletImpact":{"currency":"GBP","currentValue":290.5,"totalCost":240.0,"unrealizedProfitLoss":50.5}}]"""
        val (h, ccy) = Wealth.parseTrading212Positions(json)
        assertEquals("GBP", ccy)
        assertEquals("AAPL", h[0].symbol)
        assertEquals(50.5, h[0].pnl, 0.001)
    }

    @Test fun etoroIncludesCopyTradingAndNames() {
        val json = """{"clientPortfolio":{"positions":[{"instrumentID":1001,"units":3,"amount":500.0,"unrealizedPnL":{"pnL":25.0}}],
            "mirrors":[{"positions":[{"instrumentID":1002,"units":1,"amount":200.0,"unrealizedPnL":{"pnL":-10.0}}]}],"credit":100}}"""
        val ps = Wealth.parseEtoro(json)
        assertEquals(2, ps.size)
        assertEquals(525.0, ps.first { it.first == 1001 }.second.value, 0.001)
        val names = Wealth.parseEtoroInstruments("""{"instrumentDisplayDatas":[{"instrumentID":1001,"symbolFull":"AAPL","instrumentDisplayName":"Apple"}]}""")
        assertEquals("AAPL" to "Apple", names[1001])
    }

    @Test fun manualTradesAtAverageCost() {
        val d = LocalDate.of(2026, 1, 1)
        val ps = Wealth.manualPositions(
            listOf(
                Trade(d, "VUSA", true, 10.0, 100.0, 0.0, "GBP"),
                Trade(d.plusDays(1), "VUSA", true, 10.0, 120.0, 0.0, "GBP"),
                Trade(d.plusDays(2), "VUSA", false, 5.0, 130.0, 0.0, "GBP"),
            ),
        )
        val p = ps.single()
        assertEquals(15.0, p.qty, 0.001)
        assertEquals(100.0, p.realised, 0.001) // sold 5 at 130 vs average 110
        assertEquals(1650.0, p.cost, 0.001)
    }

    @Test fun incomeLadderFillsItselfIn() {
        val step = Wealth.ladder.first { it.id == "w_first" }
        assertTrue(!Wealth.stepDone(prefs, step, emptyList()))
        Wealth.addIncome(prefs, IncomeEntry(LocalDate.now(), 50.0, "First automation"))
        assertTrue(Wealth.stepDone(prefs, step, Wealth.income(prefs)))
    }

    @Test fun dailyPlanHasCareerAndWealth() {
        val zone = ZoneId.systemDefault()
        val sunday = LocalDate.of(2026, 11, 22)
        val items = Plan.items(prefs, DayLog(ctx), sunday, zone)
        assertTrue(items.any { it.id == "aws" && it.pillar == Pillar.CAREER })
        assertTrue(items.any { it.id == "code" })
        assertTrue(items.any { it.id == "money" })
        assertTrue(Plan.items(prefs, DayLog(ctx), sunday.plusDays(1), zone).none { it.id == "money" })
        DayLog(ctx).setValue(sunday, "gh", 3)
        assertEquals(1f, Plan.items(prefs, DayLog(ctx), sunday, zone).first { it.id == "code" }.progress)
    }
}
