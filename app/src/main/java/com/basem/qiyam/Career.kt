package com.basem.qiyam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One section of the AWS exam and how much of the exam it is (percent). */
data class ExamDomain(val id: String, val name: String, val weight: Int)

data class PracticeScore(val date: LocalDate, val score: Int, val source: String)

data class Topic(val id: String, val label: String)
data class Track(val id: String, val name: String, val topics: List<Topic>)

data class Milestone(val title: String, val done: Boolean)
data class Project(val name: String, val repo: String, val milestones: List<Milestone>) {
    val progress get() = if (milestones.isEmpty()) 0f else milestones.count { it.done }.toFloat() / milestones.size
}

/** Confidence in an exam section or interview topic. */
val ConfidenceLabels = listOf("New", "Learning", "Solid", "Strong")

/**
 * Career: getting ready for US cloud / data / digital-manufacturing roles.
 * Everything is stored on the phone in [Prefs]; GitHub activity is read from GitHub's API.
 */
object Career {
    const val EXAM = "AWS Solutions Architect – Associate"
    const val EXAM_CODE = "SAA-C03"
    const val PASS = 720

    /** Official SAA-C03 content domains and weightings. */
    val domains = listOf(
        ExamDomain("secure", "Design Secure Architectures", 30),
        ExamDomain("resilient", "Design Resilient Architectures", 26),
        ExamDomain("performing", "Design High-Performing Architectures", 24),
        ExamDomain("cost", "Design Cost-Optimized Architectures", 20),
    )

    /** What interviews for each kind of role tend to cover. */
    val tracks = listOf(
        Track(
            "cloud", "Cloud engineer",
            listOf(
                Topic("c_net", "VPC, subnets, routing, security groups"),
                Topic("c_iam", "IAM: roles, policies, least privilege"),
                Topic("c_compute", "EC2, Auto Scaling, load balancers"),
                Topic("c_serverless", "Lambda, API Gateway, event-driven design"),
                Topic("c_iac", "Terraform / CloudFormation"),
                Topic("c_containers", "Docker and Kubernetes (EKS)"),
                Topic("c_cicd", "CI/CD pipelines (GitHub Actions)"),
                Topic("c_obs", "Monitoring and logging (CloudWatch)"),
                Topic("c_linux", "Linux, networking basics, troubleshooting"),
            ),
        ),
        Track(
            "data", "Data engineer",
            listOf(
                Topic("d_sql", "SQL: joins, window functions, tuning"),
                Topic("d_python", "Python for data (pandas, testing)"),
                Topic("d_spark", "Spark / PySpark"),
                Topic("d_model", "Data modelling: star schema, SCDs"),
                Topic("d_stream", "Kafka and streaming"),
                Topic("d_orch", "Orchestration (Airflow)"),
                Topic("d_aws", "AWS data: S3, Glue, Athena, Redshift"),
                Topic("d_quality", "Data quality and lineage"),
            ),
        ),
        Track(
            "mfg", "Digital manufacturing",
            listOf(
                Topic("m_iiot", "IIoT protocols: OPC UA, MQTT"),
                Topic("m_aas", "Asset Administration Shell and digital twins"),
                Topic("m_isa", "ISA-95, MES and the automation pyramid"),
                Topic("m_edge", "Edge to cloud data pipelines"),
                Topic("m_vision", "Computer vision for quality inspection"),
                Topic("m_oee", "OEE, predictive maintenance, KPIs"),
                Topic("m_cases", "Industry 4.0 case studies you can talk through"),
            ),
        ),
        Track(
            "general", "Interviews",
            listOf(
                Topic("g_design", "System design: scale, trade-offs, diagrams"),
                Topic("g_coding", "Coding: data structures and algorithms"),
                Topic("g_star", "Six STAR stories (impact, conflict, failure…)"),
                Topic("g_pitch", "Two-minute \"tell me about yourself\""),
                Topic("g_mock", "A recorded mock interview"),
            ),
        ),
    )

    /** A strong profile before applying. */
    val profile = listOf(
        "p_cv" to "One-page US-style CV with measurable results",
        "p_li" to "LinkedIn headline, About and Featured updated",
        "p_cert" to "AWS certification added to LinkedIn",
        "p_gh" to "GitHub profile README",
        "p_pins" to "Three pinned portfolio projects",
        "p_refs" to "Two people ready to give references",
    )

    val defaultMilestones = listOf("Clear README and problem statement", "Working demo", "Architecture diagram", "Deployed on AWS", "Write-up or LinkedIn post")

    // --- AWS -----------------------------------------------------------------------------------

    fun examDate(p: Prefs): LocalDate? = p.getStr("examDate").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun setExamDate(p: Prefs, d: LocalDate?) = p.putStr("examDate", d?.toString() ?: "")
    fun daysToExam(p: Prefs, today: LocalDate) = examDate(p)?.let { ChronoUnit.DAYS.between(today, it) }

    fun confidence(p: Prefs, id: String) = p.getInt("conf_$id", 0).coerceIn(0, 3)
    fun setConfidence(p: Prefs, id: String, v: Int) = p.putInt("conf_$id", v)

    /** 0–100: exam sections weighted by how much of the exam they are. */
    fun readiness(p: Prefs): Int = domains.sumOf { it.weight * confidence(p, it.id) } * 100 / (domains.sumOf { it.weight } * 3)

    fun studyTarget(p: Prefs) = p.getInt("studyTarget", 45)
    fun studyMinutes(log: DayLog, day: LocalDate) = log.value(day, "aws_min")
    fun addStudy(log: DayLog, day: LocalDate, minutes: Int) = log.setValue(day, "aws_min", (studyMinutes(log, day) + minutes).coerceAtLeast(0))

    fun scores(p: Prefs): List<PracticeScore> = runCatching {
        val arr = JSONArray(p.getStr("scores", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PracticeScore(LocalDate.parse(o.getString("d")), o.getInt("s"), o.optString("src"))
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    fun addScore(p: Prefs, s: PracticeScore) {
        val arr = JSONArray(p.getStr("scores", "[]"))
        arr.put(JSONObject().put("d", s.date.toString()).put("s", s.score).put("src", s.source))
        p.putStr("scores", arr.toString())
    }

    /** Plain-language verdict from the last three practice tests. */
    fun verdict(scores: List<PracticeScore>): String {
        if (scores.isEmpty()) return "Take a practice test to see where you stand"
        val recent = scores.takeLast(3).map { it.score }
        val avg = recent.average().toInt()
        return when {
            recent.size >= 2 && recent.all { it >= 800 } -> "Ready to book: last ${recent.size} scores all 800+"
            avg >= PASS -> "Borderline: averaging $avg. Aim for 800+ before booking"
            else -> "Keep going: averaging $avg, pass mark is $PASS"
        }
    }

    // --- Interview prep and profile ----------------------------------------------------------

    fun topicLevel(p: Prefs, id: String) = p.getInt("topic_$id", 0).coerceIn(0, 2)
    fun setTopicLevel(p: Prefs, id: String, v: Int) = p.putInt("topic_$id", v)
    fun trackProgress(p: Prefs, t: Track) = t.topics.sumOf { topicLevel(p, it.id) }.toFloat() / (t.topics.size * 2)

    // --- Projects ------------------------------------------------------------------------------

    fun projects(p: Prefs): List<Project> = runCatching {
        val arr = JSONArray(p.getStr("projects", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ms = o.getJSONArray("m")
            Project(o.getString("n"), o.optString("r"), (0 until ms.length()).map { j -> ms.getJSONObject(j).let { Milestone(it.getString("t"), it.getBoolean("d")) } })
        }
    }.getOrDefault(emptyList())

    fun saveProjects(p: Prefs, list: List<Project>) {
        val arr = JSONArray()
        list.forEach { pr ->
            arr.put(
                JSONObject().put("n", pr.name).put("r", pr.repo).put(
                    "m", JSONArray().apply { pr.milestones.forEach { put(JSONObject().put("t", it.title).put("d", it.done)) } },
                ),
            )
        }
        p.putStr("projects", arr.toString())
    }

    fun addProject(p: Prefs, name: String, repo: String) {
        if (name.isBlank() || projects(p).any { it.name.equals(name, true) }) return
        saveProjects(p, projects(p) + Project(name.trim(), repo.trim(), defaultMilestones.map { Milestone(it, false) }))
    }

    fun toggleMilestone(p: Prefs, project: String, index: Int) = saveProjects(
        p,
        projects(p).map { pr ->
            if (pr.name != project) pr
            else pr.copy(milestones = pr.milestones.mapIndexed { i, m -> if (i == index) m.copy(done = !m.done) else m })
        },
    )

    fun removeProject(p: Prefs, name: String) = saveProjects(p, projects(p).filterNot { it.name == name })
}

/**
 * GitHub activity: with a token, exact daily contributions (the green squares, private ones
 * included if your profile shows them); without, pushes from your public activity.
 */
object GitHub {
    fun user(p: Prefs) = p.getStr("ghUser", "basemelshafei")
    fun token(p: Prefs) = p.getStr("ghToken")

    fun count(log: DayLog, day: LocalDate) = log.value(day, "gh")

    /** Recently pushed repositories as (name, url), from the last sync. */
    fun repos(p: Prefs): List<Pair<String, String>> = p.getStr("ghRepos").lines().filter { "|" in it }.map { it.substringBefore("|") to it.substringAfter("|") }

    /** Blocking. Returns a short status. */
    fun sync(ctx: Context): String {
        val p = Prefs(ctx)
        val log = DayLog(ctx)
        val user = user(p).trim()
        if (user.isBlank()) return "Add your GitHub username"
        val token = token(p)
        val counts = mutableMapOf<LocalDate, Int>()
        if (token.isNotBlank()) {
            val query = "query(\$login:String!){user(login:\$login){contributionsCollection{contributionCalendar{weeks{contributionDays{date contributionCount}}}}}}"
            val body = JSONObject().put("query", query).put("variables", JSONObject().put("login", user)).toString()
            val (code, text) = Net.post("https://api.github.com/graphql", mapOf("Authorization" to "bearer $token"), body)
            if (code == 401) return "GitHub didn't accept that token"
            if (code !in 200..299) return "GitHub returned an error ($code)"
            counts.putAll(parseCalendar(text))
            p.putBool("ghExact", true)
        } else {
            for (page in 1..3) {
                val (code, text) = Net.get("https://api.github.com/users/$user/events/public?per_page=100&page=$page")
                if (code == 404) return "No GitHub user called $user"
                if (code !in 200..299) break
                val part = parseEvents(text, ZoneId.systemDefault())
                if (part.isEmpty()) break
                part.forEach { (d, n) -> counts[d] = (counts[d] ?: 0) + n }
            }
            p.putBool("ghExact", false)
        }
        // Recent repositories, offered as portfolio projects.
        runCatching {
            val (rc, rb) = Net.get("https://api.github.com/users/$user/repos?sort=pushed&per_page=15")
            if (rc in 200..299) {
                val arr = JSONArray(rb)
                val names = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("fork") }
                    .joinToString("\n") { it.getString("name") + "|" + it.getString("html_url") }
                p.putStr("ghRepos", names)
            }
        }
        val since = LocalDate.now().minusDays(120)
        counts.filterKeys { !it.isBefore(since) }.forEach { (d, n) -> log.setValue(d, "gh", n) }
        p.putLong("ghSynced", System.currentTimeMillis())
        return "GitHub synced"
    }

    fun parseCalendar(json: String): Map<LocalDate, Int> {
        val weeks = JSONObject(json).getJSONObject("data").getJSONObject("user").getJSONObject("contributionsCollection")
            .getJSONObject("contributionCalendar").getJSONArray("weeks")
        val out = mutableMapOf<LocalDate, Int>()
        for (i in 0 until weeks.length()) {
            val days = weeks.getJSONObject(i).getJSONArray("contributionDays")
            for (j in 0 until days.length()) {
                val d = days.getJSONObject(j)
                out[LocalDate.parse(d.getString("date"))] = d.getInt("contributionCount")
            }
        }
        return out
    }

    /** Counts pushes per local day from the public events feed. */
    fun parseEvents(json: String, zone: ZoneId): Map<LocalDate, Int> {
        val arr = JSONArray(json)
        val out = mutableMapOf<LocalDate, Int>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("type") != "PushEvent") continue
            val day = Instant.parse(e.getString("created_at")).atZone(zone).toLocalDate()
            out[day] = (out[day] ?: 0) + 1
        }
        return out
    }
}
