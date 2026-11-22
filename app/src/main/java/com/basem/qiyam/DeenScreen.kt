package com.basem.qiyam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basem.qiyam.ui.Badge
import com.basem.qiyam.ui.G
import com.basem.qiyam.ui.Glyph
import com.basem.qiyam.ui.Night
import com.basem.qiyam.ui.Rings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun DeenScreen(onOpenQiyam: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val log = remember { DayLog(ctx) }
    val zone = prefs.zone
    val now = rememberMinute()
    // "Today" changes at Fajr, not midnight. Any earlier day can be picked and edited.
    val today = prefs.dayAt(now)
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val day = picked?.takeIf { !it.isAfter(today) } ?: today
    val times = NightCalculator.prayers(prefs.lat, prefs.lng, prefs.method, day, zone)
    val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val items = Plan.items(prefs, log, day, zone)
    val dayName = dayName(day, today)

    Page("Deen", "$dayName's score ${Plan.pillarScore(items, Pillar.DEEN)} · ${hijri(day)}") {

        DayPicker(day, today) { picked = it }

        // The day's prayers, with full status
        Card {
            SectionTitle(if (day == today) "Prayers today" else "Prayers · ${dayName(day, today, long = true)}")
            Prayer.entries.forEach { p ->
                val status = log.prayer(day, p)
                Column(Modifier.testTag("prayer_${p.id}")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(p.arabic, color = Night.Muted, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(fmt.format(p.time(times)), color = Night.Muted, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        listOf(PrayerStatus.ON_TIME, PrayerStatus.JAMAAH, PrayerStatus.LATE, PrayerStatus.MISSED).forEach { s ->
                            StatusChip(s.label, status == s, statusColor(s), modifier = Modifier.weight(if (s == PrayerStatus.LATE) 0.8f else 1f)) {
                                log.setPrayer(day, p, if (status == s) PrayerStatus.NONE else s)
                            }
                        }
                    }
                }
            }
            Text(
                "Prayed it after its time, even the next day? Mark it Late. It still counts.",
                color = Night.Muted, fontSize = 12.sp,
            )
        }

        // The week at a glance
        Card {
            SectionTitle("This week")
            Text("Tap a day to open it.", color = Night.Muted, fontSize = 12.sp)
            val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = (0L..6L).map { monday.plusDays(it) }
            Row {
                Spacer(Modifier.width(64.dp))
                week.forEach { d ->
                    Text(
                        d.dayOfWeek.name.take(1), color = if (d == day) Night.Gold else Night.Muted, fontSize = 12.sp,
                        fontWeight = if (d == day) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val pick: (LocalDate) -> Unit = { d -> if (!d.isAfter(today)) picked = d }
            Prayer.entries.forEach { p ->
                WeekRow(p.label, week, day, today, pick) { d -> statusColor(log.prayer(d, p)).takeIf { log.prayer(d, p) != PrayerStatus.NONE } }
            }
            WeekRow("Qiyam", week, day, today, pick) { d -> Rings.Deen.takeIf { prefs.woke(d) || log.done(d, "qiyam") } }
            WeekRow("Adhkar", week, day, today, pick) { d ->
                val n = listOf("adhkar_am", "adhkar_pm").count { log.done(d, it) }
                when (n) { 2 -> Rings.Deen; 1 -> Night.Warn; else -> null }
            }
            WeekRow("Quran", week, day, today, pick) { d ->
                val n = listOf("quran_new", "quran_rev").count { log.done(d, it) }
                when (n) { 2 -> Rings.Deen; 1 -> Night.Warn; else -> null }
            }
        }

        // Fasting
        Card {
            SectionTitle("Monday & Thursday fasts")
            val nextFast = generateSequence(today) { it.plusDays(1) }.first { Plan.isFastDay(it) }
            val month = day.withDayOfMonth(1)
            val fastDays = generateSequence(month) { it.plusDays(1) }.takeWhile { it.month == day.month }.filter { Plan.isFastDay(it) }.toList()
            val kept = fastDays.count { log.done(it, "fast") }
            Text(
                if (Plan.isFastDay(day) && day == today) "Today is a fast day." else "Next: ${nextFast.format(DateTimeFormatter.ofPattern("EEEE d MMM"))}. Your Qiyam alarm will remind you about suhoor.",
                color = Night.Text, fontSize = 14.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fastDays.forEach { d ->
                    val done = log.done(d, "fast")
                    val future = d.isAfter(today)
                    StatusChip(
                        d.format(DateTimeFormatter.ofPattern("d")), done,
                        Rings.Deen, enabled = !future, modifier = Modifier.weight(1f), compact = true,
                    ) { log.toggle(d, "fast") }
                }
            }
            Text("$kept of ${fastDays.count { !it.isAfter(today) }} so far this month", color = Night.Muted, fontSize = 12.sp)
        }

        // Adhkar
        Card {
            SectionTitle("Adhkar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Morning", log.done(day, "adhkar_am"), Rings.Deen) { log.toggle(day, "adhkar_am") }
                StatusChip("Evening", log.done(day, "adhkar_pm"), Rings.Deen) { log.toggle(day, "adhkar_pm") }
            }
            Text("Morning after Fajr until sunrise (${fmt.format(times.sunrise)}); evening after Asr until Maghrib.", color = Night.Muted, fontSize = 12.sp)
        }

        // Qiyam: a summary from the Qiyam alarm app
        QiyamCard(prefs, today, onOpenQiyam)

        // Quran
        Card {
            SectionTitle("Quran memorisation")
            var current by remember { mutableStateOf(prefs.quranCurrent) }
            OutlinedTextField(
                value = current, onValueChange = { current = it },
                label = { Text("Memorising now (e.g. Al-Mulk 1–10)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                trailingIcon = {
                    if (current.trim() != prefs.quranCurrent) {
                        TextButton(onClick = { prefs.quranCurrent = current }) { Text("Save", color = Night.Gold) }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(if (day == today) "Memorised today" else "Memorised", log.done(day, "quran_new"), Rings.Deen) { log.toggle(day, "quran_new") }
                StatusChip(if (day == today) "Revised today" else "Revised", log.done(day, "quran_rev"), Rings.Deen) { log.toggle(day, "quran_rev") }
            }
            val last30 = (0L..29L).map { day.minusDays(it) }
            Text(
                "Last 30 days: memorised on ${last30.count { log.done(it, "quran_new") }}, revised on ${last30.count { log.done(it, "quran_rev") }}",
                color = Night.Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WeekRow(
    label: String, week: List<LocalDate>, selected: LocalDate, today: LocalDate,
    onPick: (LocalDate) -> Unit, color: (LocalDate) -> Color?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Night.Text, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        week.forEach { d ->
            val c = color(d)
            Box(
                Modifier.weight(1f).heightIn(min = 30.dp)
                    .clickable(enabled = !d.isAfter(today), onClickLabel = "Open ${d.dayOfWeek.name.lowercase()}") { onPick(d) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(c ?: if (d.isAfter(today)) Color.Transparent else Night.CardEdge)
                        .border(1.5.dp, if (d == selected) Night.Gold else Color.Transparent, CircleShape),
                )
            }
        }
    }
}

/** "Today", "Yesterday", or the weekday and date. */
fun dayName(day: LocalDate, today: LocalDate, long: Boolean = false): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern(if (long) "EEEE d MMM" else "EEE d MMM"))
}

/** Step back through earlier days to edit them; forward stops at today. */
@Composable
private fun DayPicker(day: LocalDate, today: LocalDate, onPick: (LocalDate?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("day_picker"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPick(day.minusDays(1)) }, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("‹ Earlier", color = Night.Gold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayName(day, today), color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (day != today) Text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")), color = Night.Muted, fontSize = 12.sp)
        }
        TextButton(
            onClick = { onPick(if (day.plusDays(1) == today) null else day.plusDays(1)) },
            enabled = day.isBefore(today), modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Later ›", color = if (day.isBefore(today)) Night.Gold else Night.Muted, fontSize = 15.sp)
        }
    }
    if (day != today) {
        TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text("Back to today", color = Night.Gold, fontSize = 14.sp)
        }
    }
}

@Composable
fun StatusChip(
    label: String, on: Boolean, color: Color, enabled: Boolean = true,
    modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit,
) {
    // Tighter on small phones so a row of four still fits without clipping.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50))
            .background(if (on) color else Night.Fill.copy(alpha = if (enabled) Night.Fill.alpha else Night.Fill.alpha / 2))
            .selectable(selected = on, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = if (compact || narrow) 2.dp else 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, maxLines = 1, softWrap = false,
            color = if (on) Night.OnAccent else if (enabled) Night.Text else Night.Muted,
            fontSize = if (narrow) 12.sp else if (compact) 13.sp else 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

fun statusColor(s: PrayerStatus): Color = when (s) {
    PrayerStatus.JAMAAH -> Rings.Deen
    PrayerStatus.ON_TIME -> Night.Gold
    PrayerStatus.LATE -> Night.Warn
    PrayerStatus.MISSED -> Night.Dawn
    PrayerStatus.NONE -> Night.CardEdge
}

@Composable
private fun QiyamCard(prefs: Prefs, day: LocalDate, onOpen: () -> Unit) {
    val q = QiyamLink.last()
    val fmt = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(prefs.zone)
    val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(prefs.zone)
    Card {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Badge(G.MOON, Color(0xFF5E5CE6), 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Qiyam", color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        q == null -> "Install the Qiyam app to track night prayer here"
                        !q.enabled -> "Alarm off"
                        else -> "Alarm set for ${fmt.format(q.nextWake)}"
                    },
                    color = Night.Muted, fontSize = 13.sp,
                )
            }
            Text(if (q == null) "Get" else "Open", color = Night.Gold, fontSize = 15.sp)
            Glyph(G.CHEVRON, Night.Muted, 16.dp)
        }
        if (q != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Streak", color = Night.Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${q.streak}", color = Night.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(" nights", color = Night.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Best", color = Night.Muted, fontSize = 13.sp)
                    Text("${q.best}", color = Night.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Tonight", color = Night.Muted, fontSize = 13.sp)
                    Text(hm.format(q.lastThird), color = Night.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 13 downTo 0) {
                    val d = day.minusDays(i.toLong())
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .background(if (d in q.wokeNights || prefs.woke(d)) Color(0xFF5E5CE6) else Night.Fill),
                    )
                }
            }
            Text("Last 14 nights · alarm settings live in the Qiyam app", color = Night.Muted, fontSize = 12.sp)
        }
    }
}
