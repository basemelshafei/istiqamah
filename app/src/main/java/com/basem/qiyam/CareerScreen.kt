package com.basem.qiyam

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.basem.qiyam.ui.Badge
import com.basem.qiyam.ui.G
import com.basem.qiyam.ui.Glyph
import com.basem.qiyam.ui.Night
import com.basem.qiyam.ui.Rings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Career: AWS certification, interview prep, profile, portfolio projects and GitHub. */
@Composable
fun CareerScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val log = remember { DayLog(ctx) }
    val day = rememberMinute().atZone(prefs.zone).toLocalDate()
    val readiness = Career.readiness(prefs)

    Page("Career", "${Career.EXAM_CODE} readiness $readiness% · cloud, data, digital manufacturing") {
        AwsCard(prefs, log, day, readiness)
        DomainsCard(prefs)
        PracticeCard(prefs, day)
        InterviewCard(prefs)
        ProjectsCard(prefs)
        GitHubCard(prefs, log, day)
        ProfileCard(prefs)
    }
}

@Composable
private fun AwsCard(prefs: Prefs, log: DayLog, day: LocalDate, readiness: Int) {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = (0L..6L).map { monday.plusDays(it) }
    val weekMinutes = week.sumOf { Career.studyMinutes(log, it) }
    val today = Career.studyMinutes(log, day)
    val days = Career.daysToExam(prefs, day)
    var dateText by remember { mutableStateOf(Career.examDate(prefs)?.toString() ?: "") }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(G.BRIEFCASE, Rings.Career, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Career.EXAM, color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        days == null -> "No exam date yet"
                        days > 0 -> "$days days to go · ${Career.examDate(prefs)!!.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
                        days == 0L -> "Exam day. Bismillah."
                        else -> "Exam was ${-days} days ago"
                    },
                    color = Night.Muted, fontSize = 13.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                listOf(RingValue(readiness / 100f, Rings.Career), RingValue(weekMinutes / (Career.studyTarget(prefs) * 7f), Rings.Deen)),
                size = 92.dp, stroke = 11.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelValue("Readiness", "$readiness%", Rings.Career)
                LabelValue("This week", "${weekMinutes / 60}h ${weekMinutes % 60}m", Rings.Deen)
                LabelValue("Today", "$today / ${Career.studyTarget(prefs)} min", Night.Text)
            }
        }
        SectionTitle("Log study")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 45, 60).forEach { m ->
                StatusChip("+$m min", false, Rings.Career, modifier = Modifier.weight(1f).testTag("study_$m")) { Career.addStudy(log, day, m) }
            }
        }
        if (today > 0) {
            TextButton(onClick = { Career.addStudy(log, day, -15) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Undo 15 min", color = Night.Muted, fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = dateText, onValueChange = { dateText = it.trim() },
            label = { Text("Exam date (YYYY-MM-DD)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            trailingIcon = {
                val parsed = runCatching { LocalDate.parse(dateText) }.getOrNull()
                if ((parsed != null || dateText.isBlank()) && parsed != Career.examDate(prefs)) {
                    TextButton(onClick = { Career.setExamDate(prefs, parsed) }) { Text("Save", color = Night.Gold) }
                }
            },
        )
    }
}

@Composable
private fun DomainsCard(prefs: Prefs) {
    Card {
        SectionTitle("Exam sections")
        Text("Rate yourself honestly. Bigger sections count more towards readiness.", color = Night.Muted, fontSize = 13.sp)
        Career.domains.forEach { d ->
            val level = Career.confidence(prefs, d.id)
            Column(Modifier.testTag("domain_${d.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name.removePrefix("Design "), color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${d.weight}%", color = Night.Muted, fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfidenceLabels.forEachIndexed { i, label ->
                        StatusChip(label, level == i, Rings.Career, modifier = Modifier.weight(1f), compact = true) { Career.setConfidence(prefs, d.id, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeCard(prefs: Prefs, day: LocalDate) {
    val scores = Career.scores(prefs)
    var score by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    Card {
        SectionTitle("Practice tests")
        Text(Career.verdict(scores), color = Night.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (scores.isNotEmpty()) {
            ScoreChart(
                scores.map { it.score },
                Modifier.fillMaxWidth().height(90.dp).semantics {
                    contentDescription = "Practice scores: " + scores.takeLast(5).joinToString(", ") { "${it.score}" } + ". Pass mark ${Career.PASS}."
                },
            )
            scores.takeLast(4).reversed().forEach { s ->
                Row {
                    Text(s.date.format(DateTimeFormatter.ofPattern("d MMM")), color = Night.Muted, fontSize = 14.sp, modifier = Modifier.width(64.dp))
                    Text(s.source.ifBlank { "Practice test" }, color = Night.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${s.score}", color = if (s.score >= Career.PASS) Night.Good else Night.Warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = score, onValueChange = { score = it.filter(Char::isDigit).take(4) },
                label = { Text("Score /1000") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors(),
            )
            OutlinedTextField(
                value = source, onValueChange = { source = it },
                label = { Text("Source") }, singleLine = true, modifier = Modifier.weight(1.3f), colors = fieldColors(),
            )
        }
        val n = score.toIntOrNull()
        TextButton(enabled = n != null && n in 100..1000, onClick = {
            Career.addScore(prefs, PracticeScore(day, n!!, source.trim())); score = ""; source = ""
        }) { Text("Add score", color = Night.Gold, fontSize = 15.sp) }
    }
}

/** Scores over time with the 720 pass line. */
@Composable
private fun ScoreChart(values: List<Int>, modifier: Modifier) {
    val line = Rings.Career
    val pass = Night.Muted
    Canvas(modifier) {
        val lo = 400f; val hi = 1000f
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        drawLine(pass, Offset(0f, y(Career.PASS.toFloat())), Offset(size.width, y(Career.PASS.toFloat())), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        if (values.size == 1) {
            drawCircle(line, 8f, Offset(size.width / 2, y(values[0].toFloat().coerceIn(lo, hi))))
            return@Canvas
        }
        val step = size.width / (values.size - 1)
        values.forEachIndexed { i, v ->
            val pt = Offset(i * step, y(v.toFloat().coerceIn(lo, hi)))
            if (i > 0) drawLine(line, Offset((i - 1) * step, y(values[i - 1].toFloat().coerceIn(lo, hi))), pt, 5f, StrokeCap.Round)
            drawCircle(line, 7f, pt)
        }
    }
}

@Composable
private fun InterviewCard(prefs: Prefs) {
    var trackId by remember { mutableStateOf(Career.tracks.first().id) }
    val track = Career.tracks.first { it.id == trackId }
    Card {
        SectionTitle("Interview prep")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Career.tracks.forEach { t ->
                StatusChip(t.name.substringBefore(" "), t.id == trackId, Night.Gold, modifier = Modifier.weight(1f), compact = true) { trackId = t.id }
            }
        }
        val prog = Career.trackProgress(prefs, track)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(track.name, color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(prog * 100).toInt()}%", color = Rings.Career, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Bar(prog, Rings.Career)
        track.topics.forEach { topic ->
            val level = Career.topicLevel(prefs, topic.id)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { Career.setTopicLevel(prefs, topic.id, (level + 1) % 3) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            when (level) { 2 -> Night.Good; 1 -> Night.Warn; else -> Night.Fill }
                        ),
                    contentAlignment = Alignment.Center,
                ) { if (level == 2) Glyph(G.CHECK, Night.OnAccent, 14.dp) }
                Spacer(Modifier.width(10.dp))
                Text(topic.label, color = Night.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(
                    listOf("New", "Learning", "Confident")[level], color = Night.Muted, fontSize = 12.sp,
                    modifier = Modifier.width(72.dp).padding(start = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        Text("Tap a topic to move it: new → learning → confident.", color = Night.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProjectsCard(prefs: Prefs) {
    val ctx = LocalContext.current
    val projects = Career.projects(prefs)
    var name by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    Card {
        SectionTitle("Portfolio projects")
        if (projects.isEmpty()) Text("Three strong, finished projects beat ten half-built ones. Add the ones you'll show.", color = Night.Muted, fontSize = 13.sp)
        projects.forEachIndexed { i, pr ->
            if (i > 0) HorizontalDivider(color = Night.CardEdge, thickness = 0.5.dp)
            Column(Modifier.fillMaxWidth().clickable { open = if (open == pr.name) null else pr.name }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pr.name, color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${pr.milestones.count { it.done }} of ${pr.milestones.size} milestones", color = Night.Muted, fontSize = 13.sp)
                    }
                    Text("${(pr.progress * 100).toInt()}%", color = Rings.Career, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Glyph(G.CHEVRON, Night.Muted, 16.dp, Modifier.graphicsLayer { rotationZ = if (open == pr.name) 90f else 0f })
                }
                Bar(pr.progress, Rings.Career)
            }
            if (open == pr.name) {
                pr.milestones.forEachIndexed { mi, m ->
                    Row(Modifier.fillMaxWidth().clickable { Career.toggleMilestone(prefs, pr.name, mi) }.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (m.done) Rings.Career else Night.Fill),
                            contentAlignment = Alignment.Center,
                        ) { if (m.done) Glyph(G.CHECK, Night.OnAccent, 14.dp) }
                        Spacer(Modifier.width(10.dp))
                        Text(m.title, color = Night.Text, fontSize = 15.sp)
                    }
                }
                Row {
                    if (pr.repo.isNotBlank()) TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pr.repo)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Open on GitHub", color = Night.Gold) }
                    TextButton(onClick = { Career.removeProject(prefs, pr.name); open = null }) { Text("Remove", color = Night.Dawn) }
                }
            }
        }
        val suggestions = GitHub.repos(prefs).filter { (n, _) -> projects.none { it.name.equals(n, true) } }.take(6)
        if (suggestions.isNotEmpty()) {
            Text("From your GitHub", color = Night.Muted, fontSize = 13.sp)
            suggestions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (n, url) -> StatusChip("+ $n", false, Night.Gold, modifier = Modifier.weight(1f), compact = true) { Career.addProject(prefs, n, url) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Add a project") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            trailingIcon = { TextButton(enabled = name.isNotBlank(), onClick = { Career.addProject(prefs, name, ""); name = "" }) { Text("Add", color = Night.Gold) } },
        )
    }
}

@Composable
private fun GitHubCard(prefs: Prefs, log: DayLog, day: LocalDate) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf(GitHub.user(prefs)) }
    var token by remember { mutableStateOf("") }
    val weeks = 15
    val start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    val last30 = (0L..29L).sumOf { GitHub.count(log, day.minusDays(it)) }
    var streak = 0
    run {
        var d = if (GitHub.count(log, day) > 0) day else day.minusDays(1)
        while (GitHub.count(log, d) > 0) { streak++; d = d.minusDays(1) }
    }
    val exact = prefs.getBool("ghExact")
    Card {
        SectionTitle("GitHub · ${GitHub.user(prefs)}")
        Row {
            LabelValue(if (exact) "Contributions, 30 days" else "Pushes, 30 days", "$last30", Rings.Career)
            Spacer(Modifier.width(24.dp))
            LabelValue("Streak", "$streak day${if (streak == 1) "" else "s"}", Night.Good)
        }
        // Contribution grid: columns are weeks, rows are Monday to Sunday.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0 until weeks) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (dow in 0 until 7) {
                        val d = start.plusWeeks(w.toLong()).plusDays(dow.toLong())
                        val n = GitHub.count(log, d)
                        val c = when {
                            d.isAfter(day) -> androidx.compose.ui.graphics.Color.Transparent
                            n == 0 -> Night.Fill
                            n <= 2 -> Night.Good.copy(alpha = 0.4f)
                            n <= 5 -> Night.Good.copy(alpha = 0.7f)
                            else -> Night.Good
                        }
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
            }
        }
        Text(
            if (exact) "Exact daily contributions, including private ones if your profile shows them."
            else "Without a token this counts pushes to public repos. Add a token for exact contributions.",
            color = Night.Muted, fontSize = 12.sp,
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it.trim() }, label = { Text("GitHub username") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it.trim() },
            label = { Text(if (GitHub.token(prefs).isBlank()) "Token (optional)" else "Token saved · paste to replace") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                prefs.putStr("ghUser", user)
                if (token.isNotBlank()) prefs.putStr("ghToken", token)
                token = ""
                scope.launch { status = withContext(Dispatchers.IO) { runCatching { GitHub.sync(ctx) }.getOrElse { "Couldn't reach GitHub" } } }
            }) { Text("Save and sync", color = Night.Gold, fontSize = 15.sp) }
            status?.let { Text(it, color = Night.Muted, fontSize = 13.sp) }
        }
        if (GitHub.token(prefs).isNotBlank()) {
            TextButton(onClick = { prefs.putStr("ghToken", ""); prefs.putBool("ghExact", false) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Remove token", color = Night.Dawn, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProfileCard(prefs: Prefs) {
    Card {
        SectionTitle("Profile before applying")
        Career.profile.forEach { (id, label) ->
            val done = prefs.getBool(id)
            Row(Modifier.fillMaxWidth().clickable { prefs.putBool(id, !done) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (done) Night.Gold else Night.Fill),
                    contentAlignment = Alignment.Center,
                ) { if (done) Glyph(G.CHECK, Night.OnAccent, 14.dp) }
                Spacer(Modifier.width(10.dp))
                Text(label, color = Night.Text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = Night.Muted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Bar(progress: Float, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Night.Fill)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}
