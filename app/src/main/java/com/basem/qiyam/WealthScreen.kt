package com.basem.qiyam

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.coerceAtLeast
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold = Color(0xFFFFB800)

fun money(v: Double, ccy: String): String {
    val sym = when (ccy) { "GBP" -> "£"; "USD" -> "$"; "EUR" -> "€"; else -> "" }
    val sign = if (v < 0) "−" else ""
    return sign + sym + String.format(Locale.US, "%,.2f", kotlin.math.abs(v)) + if (sym.isEmpty()) " $ccy" else ""
}

private fun signed(v: Double, ccy: String) = (if (v > 0) "+" else "") + money(v, ccy)

/** Wealth: portfolios (eToro, Trading 212, manual) and side income. */
@Composable
fun WealthScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val day = rememberNow().atZone(prefs.zone).toLocalDate()
    val platforms = Wealth.platforms(prefs)
    val income = Wealth.income(prefs)
    val thisMonth = Wealth.monthNet(income, YearMonth.from(day))

    Page("Wealth", "Side income this month ${money(thisMonth, "GBP")} · target ${money(Wealth.incomeTarget(prefs).toDouble(), "GBP")}") {
        PortfolioCard(platforms, prefs)
        SideIncomeCard(prefs, income, day)
        ManualCard(prefs, day)
        AccountsCard(prefs)
        Text(
            "Read only: Istiqamah can't place trades or move money. Not financial advice.",
            color = Night.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PortfolioCard(platforms: List<Platform>, prefs: Prefs) {
    val manual = Wealth.manualPositions(Wealth.trades(prefs)).filter { it.qty > 0 }
    Card {
        SectionTitle("Portfolio")
        if (platforms.isEmpty() && manual.isEmpty()) {
            val problem = listOf(prefs.getStr("t212Status"), prefs.getStr("etoroStatus")).firstOrNull { it.isNotBlank() && !it.startsWith("Synced") }
            Text(problem ?: "Connect eToro or Trading 212 below, or log trades by hand.", color = if (problem != null) Night.Warn else Night.Muted, fontSize = 14.sp)
        }
        platforms.forEach { pl ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(G.CHART, if (pl.id == "etoro") Color(0xFF13C636) else Color(0xFF0E7EE8), 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.name, color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${pl.holdings.size} positions · invested ${money(pl.cost, pl.currency)}", color = Night.Muted, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(pl.value, pl.currency), color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        signed(pl.pnl, pl.currency) + String.format(Locale.US, " (%+.1f%%)", if (pl.cost > 0) pl.pnl / pl.cost * 100 else 0.0),
                        color = if (pl.pnl >= 0) Night.Good else Night.Dawn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (manual.isNotEmpty()) {
            val byCcy = manual.groupBy { it.currency }
            byCcy.forEach { (ccy, ps) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(G.BOOK, Night.Muted, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Manual ($ccy)", color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${ps.size} positions at cost", color = Night.Muted, fontSize = 13.sp)
                    }
                    Text(money(ps.sumOf { it.cost }, ccy), color = Night.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val top = platforms.flatMap { pl -> pl.holdings.map { it to pl.currency } }.sortedByDescending { it.first.value }.take(8)
        if (top.isNotEmpty()) {
            HorizontalDivider(color = Night.CardEdge, thickness = 0.5.dp)
            Text("Largest holdings", color = Night.Muted, fontSize = 13.sp)
            top.forEach { (h, ccy) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.symbol, color = Night.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (h.name.isNotBlank()) Text(h.name, color = Night.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(h.value, ccy), color = Night.Text, fontSize = 15.sp)
                        Text(String.format(Locale.US, "%+.1f%%", h.pnlPct), color = if (h.pnl >= 0) Night.Good else Night.Dawn, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SideIncomeCard(prefs: Prefs, income: List<IncomeEntry>, day: LocalDate) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isCost by remember { mutableStateOf(false) }
    val target = Wealth.incomeTarget(prefs)
    val months = (5 downTo 0).map { YearMonth.from(day).minusMonths(it.toLong()) }
    val nets = months.map { Wealth.monthNet(income, it) }
    Card {
        SectionTitle("Side income · n8n")
        val now = nets.last()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(now, "GBP"), color = Night.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("  of ${money(target.toDouble(), "GBP")} this month", color = Night.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Bar((now / target).toFloat(), Gold)
        val max = (nets.maxOrNull() ?: 0.0).coerceAtLeast(target.toDouble())
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { i, m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height((44 * (nets[i].coerceAtLeast(0.0) / max)).toFloat().dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(4.dp)).background(if (i == months.lastIndex) Gold else Gold.copy(alpha = 0.35f)),
                    )
                    Text(m.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, color = Night.Muted, fontSize = 10.sp)
                }
            }
        }

        Text("The road to a second income", color = Night.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Wealth.ladder.forEach { step ->
            val done = Wealth.stepDone(prefs, step, income)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = step.auto == null) { prefs.putBool(step.id, !done) }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (done) Gold else Night.Fill), contentAlignment = Alignment.Center) {
                    if (done) Glyph(G.CHECK, Night.OnAccent, 14.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(step.title, color = if (done) Night.Muted else Night.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (step.auto != null) Text("auto", color = Night.Muted, fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = Night.CardEdge, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Income", !isCost, Night.Good, modifier = Modifier.weight(1f)) { isCost = false }
            StatusChip("Cost", isCost, Night.Dawn, modifier = Modifier.weight(1f)) { isCost = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it.replace(',', '.') }, label = { Text("£") }, singleLine = true,
                modifier = Modifier.weight(0.8f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("What for") }, singleLine = true,
                modifier = Modifier.weight(1.4f), colors = fieldColors(),
            )
        }
        val a = amount.toDoubleOrNull()
        TextButton(enabled = a != null && a > 0, onClick = {
            Wealth.addIncome(prefs, IncomeEntry(day, if (isCost) -a!! else a!!, note.trim())); amount = ""; note = ""
        }) { Text(if (isCost) "Add cost" else "Add income", color = Night.Gold, fontSize = 15.sp) }
        income.take(5).forEach { e ->
            Row {
                Text(e.date.format(DateTimeFormatter.ofPattern("d MMM")), color = Night.Muted, fontSize = 14.sp, modifier = Modifier.width(60.dp))
                Text(e.note.ifBlank { if (e.amount >= 0) "Income" else "Cost" }, color = Night.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(signed(e.amount, "GBP"), color = if (e.amount >= 0) Night.Good else Night.Dawn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        var targetText by remember { mutableStateOf(target.toString()) }
        OutlinedTextField(
            value = targetText, onValueChange = { targetText = it.filter(Char::isDigit).take(6) },
            label = { Text("Monthly target (£)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors(),
            trailingIcon = {
                val t = targetText.toIntOrNull()
                if (t != null && t > 0 && t != target) TextButton(onClick = { prefs.putInt("incomeTarget", t) }) { Text("Save", color = Night.Gold) }
            },
        )
    }
}

@Composable
private fun ManualCard(prefs: Prefs, day: LocalDate) {
    var symbol by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var buy by remember { mutableStateOf(true) }
    var ccy by remember { mutableStateOf("GBP") }
    val positions = Wealth.manualPositions(Wealth.trades(prefs))
    Card {
        SectionTitle("Manual trades")
        Text("For anything not connected. Profit or loss is worked out at average cost.", color = Night.Muted, fontSize = 13.sp)
        positions.forEach { p ->
            Row {
                Text(p.symbol, color = Night.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (p.qty > 0) "${fmtQty(p.qty)} @ ${money(p.cost / p.qty, p.currency)}" else "Closed", color = Night.Text, fontSize = 14.sp)
                    if (p.realised != 0.0) Text("Realised ${signed(p.realised, p.currency)}", color = if (p.realised >= 0) Night.Good else Night.Dawn, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Buy", buy, Night.Good, modifier = Modifier.weight(1f)) { buy = true }
            StatusChip("Sell", !buy, Night.Dawn, modifier = Modifier.weight(1f)) { buy = false }
            StatusChip("GBP", ccy == "GBP", Night.Gold, modifier = Modifier.weight(1f)) { ccy = "GBP" }
            StatusChip("USD", ccy == "USD", Night.Gold, modifier = Modifier.weight(1f)) { ccy = "USD" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(symbol, { symbol = it.uppercase().trim() }, label = { Text("Ticker") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
            OutlinedTextField(qty, { qty = it.replace(',', '.') }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.weight(0.8f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors())
            OutlinedTextField(price, { price = it.replace(',', '.') }, label = { Text("Price") }, singleLine = true, modifier = Modifier.weight(0.9f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors())
        }
        val q = qty.toDoubleOrNull(); val pr = price.toDoubleOrNull()
        TextButton(enabled = symbol.isNotBlank() && q != null && q > 0 && pr != null && pr > 0, onClick = {
            Wealth.addTrade(prefs, Trade(day, symbol, buy, q!!, pr!!, 0.0, ccy)); symbol = ""; qty = ""; price = ""
        }) { Text(if (buy) "Add buy" else "Add sell", color = Night.Gold, fontSize = 15.sp) }
    }
}

private fun fmtQty(q: Double) = if (q % 1.0 == 0.0) q.toLong().toString() else String.format(Locale.US, "%.4f", q).trimEnd('0')

@Composable
private fun AccountsCard(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var t212Key by remember { mutableStateOf("") }
    var t212Secret by remember { mutableStateOf("") }
    var etApi by remember { mutableStateOf("") }
    var etUser by remember { mutableStateOf("") }

    fun sync(block: (android.content.Context) -> String) = scope.launch {
        status = "Syncing…"
        status = withContext(Dispatchers.IO) { runCatching { block(ctx) }.getOrElse { "Couldn't connect: ${it.message}" } }
    }

    Card {
        SectionTitle("Connected accounts")

        // Trading 212
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(G.CHART, Color(0xFF0E7EE8), 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trading 212", color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (prefs.getStr("t212Key").isBlank()) "Invest or Stocks ISA account" else prefs.getStr("t212Status", "Key saved, not synced yet"),
                    color = statusColor(prefs.getStr("t212Status")), fontSize = 13.sp,
                )
            }
            if (prefs.getStr("t212Key").isNotBlank()) TextButton(onClick = { sync(Wealth::syncTrading212) }) { Text("Sync", color = Night.Gold) }
        }
        if (prefs.getStr("t212Key").isNotBlank()) {
            TextButton(onClick = { Wealth.disconnect(prefs, "t212") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Disconnect and delete key", color = Night.Dawn, fontSize = 14.sp)
            }
        }
        if (prefs.getStr("t212Key").isBlank()) {
            Text("In Trading 212: Settings › API › Generate key, with Portfolio permission. Paste the key and secret.", color = Night.Muted, fontSize = 13.sp)
            KeyField("API key", t212Key) { t212Key = it }
            KeyField("API secret", t212Secret) { t212Secret = it }
            TextButton(enabled = t212Key.isNotBlank(), onClick = {
                prefs.putStr("t212Key", t212Key); prefs.putStr("t212Secret", t212Secret); t212Key = ""; t212Secret = ""
                sync(Wealth::syncTrading212)
            }) { Text("Connect Trading 212", color = Night.Gold) }
        }

        HorizontalDivider(color = Night.CardEdge, thickness = 0.5.dp)

        // eToro
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(G.CHART, Color(0xFF13C636), 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("eToro", color = Night.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (prefs.getStr("etoroUserKey").isBlank()) "Real account, read only" else prefs.getStr("etoroStatus", "Keys saved, not synced yet"),
                    color = statusColor(prefs.getStr("etoroStatus")), fontSize = 13.sp,
                )
            }
            if (prefs.getStr("etoroUserKey").isNotBlank()) TextButton(onClick = { sync(Wealth::syncEtoro) }) { Text("Sync", color = Night.Gold) }
        }
        if (prefs.getStr("etoroUserKey").isNotBlank()) {
            TextButton(onClick = { Wealth.disconnect(prefs, "etoro") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Disconnect and delete keys", color = Night.Dawn, fontSize = 14.sp)
            }
        }
        if (prefs.getStr("etoroUserKey").isBlank()) {
            Text("eToro needs two keys: a Public API key and a User key (Settings › Trading › API). Give it read access only.", color = Night.Muted, fontSize = 13.sp)
            TextButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api-portal.etoro.com/getting-started")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("eToro's key guide", color = Night.Gold) }
            KeyField("Public API key", etApi) { etApi = it }
            KeyField("User key", etUser) { etUser = it }
            TextButton(enabled = etApi.isNotBlank() && etUser.isNotBlank(), onClick = {
                prefs.putStr("etoroApiKey", etApi); prefs.putStr("etoroUserKey", etUser); etApi = ""; etUser = ""
                sync(Wealth::syncEtoro)
            }) { Text("Connect eToro", color = Night.Gold) }
        }
        status?.let { Text(it, color = Night.Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun statusColor(status: String) = when {
    status.startsWith("Synced") -> Night.Good
    status.isBlank() -> Night.Muted
    else -> Night.Warn
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.trim()) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
    )
}
