package com.basem.qiyam

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** One position, in the platform's account currency. */
data class Holding(val symbol: String, val name: String, val quantity: Double, val cost: Double, val value: Double) {
    val pnl get() = value - cost
    val pnlPct get() = if (cost > 0) pnl / cost * 100 else 0.0
}

/** A Trading 212 pie: value and what you've put in, in the account currency. */
data class Pie(val id: Long, val name: String, val value: Double, val invested: Double) {
    val pnl get() = value - invested
    val pnlPct get() = if (invested > 0) pnl / invested * 100 else 0.0
}

data class Platform(
    val id: String, val name: String, val currency: String, val holdings: List<Holding>, val syncedAt: Long,
    val cash: Double? = null, val pies: List<Pie> = emptyList(),
    /** The platform's own totals, when it reports them (Trading 212's account summary). */
    val reportedValue: Double? = null, val reportedCost: Double? = null, val realized: Double? = null,
) {
    val value get() = reportedValue ?: holdings.sumOf { it.value }
    val cost get() = reportedCost ?: holdings.sumOf { it.cost }
    val pnl get() = value - cost
}

data class Trade(val date: LocalDate, val symbol: String, val buy: Boolean, val qty: Double, val price: Double, val fees: Double, val currency: String)

data class IncomeEntry(val date: LocalDate, val amount: Double, val note: String)

/**
 * Wealth: your eToro and Trading 212 portfolios (read only, through their APIs, with keys you
 * create), a manual trade log for anything else, and your side income.
 */
object Wealth {

    // --- Stored platform snapshots ---------------------------------------------------------------

    fun platforms(p: Prefs): List<Platform> = listOf("t212", "etoro").mapNotNull { id ->
        runCatching { fromJson(JSONObject(p.getStr("plat_$id").ifBlank { return@mapNotNull null })) }.getOrNull()
    }

    /** Deletes a broker's keys and its stored portfolio from the phone. */
    fun disconnect(p: Prefs, id: String) {
        when (id) {
            "t212" -> { p.putStr("t212Key", ""); p.putStr("t212Secret", ""); p.putStr("t212Status", "") }
            "etoro" -> { p.putStr("etoroApiKey", ""); p.putStr("etoroUserKey", ""); p.putStr("etoroStatus", "") }
        }
        p.putStr("plat_$id", "")
    }

    internal fun save(p: Prefs, pl: Platform) {
        p.putStr("plat_${pl.id}", toJson(pl).toString())
        // One value per day, for the value-over-time chart.
        val hist = runCatching { JSONObject(p.getStr("hist_${pl.id}", "{}")) }.getOrDefault(JSONObject())
        hist.put(LocalDate.now().toString(), pl.value + (pl.cash ?: 0.0))
        val keys = hist.keys().asSequence().sorted().toList()
        keys.dropLast(400).forEach { hist.remove(it) }
        p.putStr("hist_${pl.id}", hist.toString())
    }

    /** Account value by day (oldest first), recorded each time the app syncs. */
    fun history(p: Prefs, id: String): List<Pair<LocalDate, Double>> = runCatching {
        val o = JSONObject(p.getStr("hist_$id", "{}"))
        o.keys().asSequence().map { LocalDate.parse(it) to o.getDouble(it) }.sortedBy { it.first }.toList()
    }.getOrDefault(emptyList())

    private fun toJson(pl: Platform) = JSONObject().put("id", pl.id).put("name", pl.name).put("ccy", pl.currency).put("at", pl.syncedAt)
        .put("cash", pl.cash ?: JSONObject.NULL)
        .put("rv", pl.reportedValue ?: JSONObject.NULL).put("rc", pl.reportedCost ?: JSONObject.NULL)
        .put("rp", pl.realized ?: JSONObject.NULL)
        .put("h", JSONArray().apply { pl.holdings.forEach { put(JSONObject().put("s", it.symbol).put("n", it.name).put("q", it.quantity).put("c", it.cost).put("v", it.value)) } })
        .put("pies", JSONArray().apply { pl.pies.forEach { put(JSONObject().put("id", it.id).put("n", it.name).put("v", it.value).put("i", it.invested)) } })

    private fun fromJson(o: JSONObject): Platform {
        val h = o.getJSONArray("h")
        return Platform(
            o.getString("id"), o.getString("name"), o.getString("ccy"),
            (0 until h.length()).map { i -> h.getJSONObject(i).let { Holding(it.getString("s"), it.optString("n"), it.getDouble("q"), it.getDouble("c"), it.getDouble("v")) } },
            o.optLong("at"),
            cash = if (o.isNull("cash")) null else o.optDouble("cash"),
            reportedValue = if (!o.has("rv") || o.isNull("rv")) null else o.optDouble("rv"),
            reportedCost = if (!o.has("rc") || o.isNull("rc")) null else o.optDouble("rc"),
            realized = if (!o.has("rp") || o.isNull("rp")) null else o.optDouble("rp"),
            pies = o.optJSONArray("pies")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { Pie(it.getLong("id"), it.optString("n"), it.getDouble("v"), it.getDouble("i")) } } } ?: emptyList(),
        )
    }

    // --- Trading 212 (API key + secret; Invest and Stocks ISA accounts) -------------------------

    fun syncTrading212(ctx: Context): String {
        val p = Prefs(ctx)
        val msg = runCatching { fetchTrading212(p) }.getOrElse { "Couldn't read Trading 212's reply: ${it.message?.take(120)}" }
        p.putStr("t212Status", msg)
        return msg
    }

    private fun fetchTrading212(p: Prefs): String {
        val key = p.getStr("t212Key"); val secret = p.getStr("t212Secret")
        if (key.isBlank()) return "Add your Trading 212 API key"
        val base = "https://live.trading212.com/api/v0/equity"
        // New keys sign in with key + secret (Basic auth); older keys send the key on its own.
        val auths = buildList {
            if (secret.isNotBlank()) add("Basic " + Base64.encodeToString("$key:$secret".toByteArray(), Base64.NO_WRAP))
            add(key)
        }
        var last = 0 to ""
        for (auth in auths) {
            for (path in listOf("positions", "portfolio")) {
                val r = Net.get("$base/$path", mapOf("Authorization" to auth))
                last = r
                if (r.first in 200..299) {
                    val (holdings, ccy) = if (path == "positions") parseTrading212Positions(r.second)
                    else parseTrading212Portfolio(r.second) to null
                    // Headline numbers come from Trading 212's own account summary, so they match its app.
                    val sum = runCatching { trading212Summary(base, auth) }.getOrNull()
                    val pies = runCatching { trading212Pies(p, base, auth) }.getOrDefault(emptyList())
                    save(p, Platform(
                        "t212", "Trading 212", sum?.currency ?: ccy ?: "GBP", holdings, System.currentTimeMillis(),
                        sum?.cash, pies, sum?.value, sum?.cost, sum?.realized,
                    ))
                    return if (holdings.isEmpty()) "Connected, but Trading 212 returned no open positions. Is this key for the account you invest in (Invest or ISA)?"
                    else "Synced ${holdings.size} position${if (holdings.size == 1) "" else "s"}"
                }
                if (r.first == 401) break // wrong sign-in style: try the next one
                if (r.first == 429) { Thread.sleep(1500) }
            }
        }
        return t212Error(last.first) + detail(last.second)
    }

    data class T212Summary(val currency: String?, val value: Double?, val cost: Double?, val cash: Double?, val realized: Double?)

    /** Account totals: /account/summary, or the older /account/cash for keys that only have that. */
    private fun trading212Summary(base: String, auth: String): T212Summary? {
        Thread.sleep(1100)
        val (c, b) = Net.get("$base/account/summary", mapOf("Authorization" to auth))
        if (c in 200..299) return parseTrading212Summary(b)
        Thread.sleep(1100)
        val (c2, b2) = Net.get("$base/account/cash", mapOf("Authorization" to auth))
        return if (c2 in 200..299) parseTrading212Cash(b2) else null
    }

    private fun JSONObject?.num(k: String): Double? =
        this?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.takeIf { !it.isNaN() }

    /** /equity/account/summary. Cash counts what's free, what's waiting in pies and what's held for orders. */
    fun parseTrading212Summary(json: String): T212Summary {
        val o = JSONObject(json)
        val cash = o.optJSONObject("cash")
        val inv = o.optJSONObject("investments")
        val parts = listOf("availableToTrade", "inPies", "reservedForOrders").mapNotNull { cash.num(it) }
        return T212Summary(
            currency = o.optString("currency").ifBlank { null },
            value = inv.num("currentValue"), cost = inv.num("totalCost"),
            cash = if (parts.isEmpty()) null else parts.sum(),
            realized = inv.num("realizedProfitLoss"),
        )
    }

    /** The older /equity/account/cash shape. */
    fun parseTrading212Cash(json: String): T212Summary {
        val o = JSONObject(json)
        val parts = listOf("free", "pieCash", "blocked").mapNotNull { o.num(it) }
        val invested = o.num("invested"); val ppl = o.num("ppl")
        return T212Summary(
            currency = null,
            value = if (invested != null && ppl != null) invested + ppl else null, cost = invested,
            cash = if (parts.isEmpty()) null else parts.sum(), realized = o.num("result"),
        )
    }

    /** Your pies with value and invested amounts. Names are fetched once and remembered. */
    private fun trading212Pies(p: Prefs, base: String, auth: String): List<Pie> {
        Thread.sleep(1100)
        val (c, b) = Net.get("$base/pies", mapOf("Authorization" to auth))
        if (c !in 200..299) return emptyList()
        return parseTrading212Pies(b).map { pie ->
            var name = p.getStr("pieName_${pie.id}")
            if (name.isBlank()) {
                Thread.sleep(5100) // Trading 212 allows one pie lookup every 5 seconds
                val (c2, b2) = Net.get("$base/pies/${pie.id}", mapOf("Authorization" to auth))
                if (c2 in 200..299) name = runCatching { JSONObject(b2).getJSONObject("settings").optString("name") }.getOrDefault("")
                if (name.isNotBlank()) p.putStr("pieName_${pie.id}", name)
            }
            pie.copy(name = name.ifBlank { "Pie ${pie.id}" })
        }
    }

    fun parseTrading212Pies(json: String): List<Pie> {
        val arr = asArray(json, "items", "pies")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val r = o.optJSONObject("result")
            Pie(
                id = o.optLong("id"), name = "",
                value = r?.optDouble("priceAvgValue", 0.0) ?: 0.0,
                invested = r?.optDouble("priceAvgInvestedValue", 0.0) ?: 0.0,
            )
        }.sortedByDescending { it.value }
    }

    /** A short piece of the server's own error text, so problems are diagnosable from the screen. */
    private fun detail(body: String): String {
        val text = runCatching { org.json.JSONObject(body).let { it.optString("message").ifBlank { it.optString("code") }.ifBlank { it.toString() } } }
            .getOrDefault(body).replace("\n", " ").trim().take(140)
        return if (text.isBlank()) "" else " ($text)"
    }

    private fun t212Error(code: Int) = when (code) {
        0 -> "Couldn't reach Trading 212. Check your internet connection."
        401 -> "Trading 212 didn't accept the key and secret. Paste them again, without spaces."
        403 -> "The key works but lacks permission. Recreate it with Portfolio (and Account data) ticked."
        429 -> "Trading 212 asks to slow down. Try again in a minute."
        else -> "Trading 212 returned error $code"
    }

    /** /equity/positions: values are in your account currency under walletImpact. */
    fun parseTrading212Positions(json: String): Pair<List<Holding>, String?> {
        val arr = asArray(json, "items", "positions")
        var ccy: String? = null
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val inst = o.optJSONObject("instrument")
            val w = o.optJSONObject("walletImpact")
            ccy = ccy ?: w?.optString("currency")?.takeIf { it.isNotBlank() }
            val ticker = inst?.optString("ticker") ?: o.optString("ticker")
            Holding(
                symbol = ticker.substringBefore("_"),
                name = inst?.optString("name").orEmpty(),
                quantity = o.optDouble("quantity", 0.0),
                cost = w.num("totalCost") ?: (o.optDouble("averagePricePaid", 0.0) * o.optDouble("quantity", 0.0)),
                value = w.num("currentValue") ?: (o.optDouble("currentPrice", 0.0) * o.optDouble("quantity", 0.0)),
            )
        }
        return list to ccy
    }

    /** Older /equity/portfolio shape: prices in the instrument's currency. */
    fun parseTrading212Portfolio(json: String): List<Holding> {
        val arr = asArray(json, "items", "positions")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val q = o.optDouble("quantity", 0.0)
            Holding(o.optString("ticker").substringBefore("_"), "", q, o.optDouble("averagePrice", 0.0) * q, o.optDouble("currentPrice", 0.0) * q)
        }
    }

    private fun asArray(json: String, vararg keys: String): JSONArray {
        val t = json.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        keys.forEach { k -> o.optJSONArray(k)?.let { return it } }
        throw IllegalStateException("unexpected reply: ${t.take(80)}")
    }

    // --- eToro (Public API key + User key) --------------------------------------------------------

    fun syncEtoro(ctx: Context): String {
        val p = Prefs(ctx)
        val msg = runCatching { fetchEtoro(p) }.getOrElse { "Couldn't read eToro's reply: ${it.message?.take(120)}" }
        p.putStr("etoroStatus", msg)
        return msg
    }

    private fun fetchEtoro(p: Prefs): String {
        val apiKey = p.getStr("etoroApiKey"); val userKey = p.getStr("etoroUserKey")
        if (apiKey.isBlank() || userKey.isBlank()) return "Add both eToro keys"
        fun headers() = mapOf("x-api-key" to apiKey, "x-user-key" to userKey, "x-request-id" to UUID.randomUUID().toString())
        val (code, body) = Net.get("https://public-api.etoro.com/api/v1/trading/info/real/pnl", headers())
        if (code == 0) return "Couldn't reach eToro. Check your internet connection."
        if (code == 401 || code == 403) return "eToro didn't accept the keys" + detail(body)
        if (code !in 200..299) return "eToro returned error $code" + detail(body)
        val positions = parseEtoro(body)
        // Turn instrument IDs into names.
        val names = mutableMapOf<Int, Pair<String, String>>()
        val ids = positions.map { it.first }.distinct()
        if (ids.isNotEmpty()) runCatching {
            val (c2, b2) = Net.get("https://public-api.etoro.com/api/v1/market-data/instruments?instrumentIds=${ids.joinToString(",")}", headers())
            if (c2 in 200..299) names.putAll(parseEtoroInstruments(b2))
        }
        val holdings = positions.groupBy { it.first }.map { (id, ps) ->
            val (sym, name) = names[id] ?: ("#$id" to "")
            Holding(sym, name, ps.sumOf { it.second.quantity }, ps.sumOf { it.second.cost }, ps.sumOf { it.second.value })
        }
        save(p, Platform("etoro", "eToro", "USD", holdings, System.currentTimeMillis()))
        return if (holdings.isEmpty()) "Connected, but eToro returned no open positions" else "Synced ${holdings.size} position${if (holdings.size == 1) "" else "s"}"
    }

    /** Positions (including copy-trading) as (instrumentId, holding with USD cost/value). */
    fun parseEtoro(json: String): List<Pair<Int, Holding>> {
        val root = JSONObject(json)
        val portfolio = root.optJSONObject("clientPortfolio") ?: root
        val all = mutableListOf<JSONObject>()
        portfolio.optJSONArray("positions")?.let { a -> for (i in 0 until a.length()) all += a.getJSONObject(i) }
        portfolio.optJSONArray("mirrors")?.let { m ->
            for (i in 0 until m.length()) m.getJSONObject(i).optJSONArray("positions")?.let { a -> for (j in 0 until a.length()) all += a.getJSONObject(j) }
        }
        return all.map { o ->
            val id = if (o.has("instrumentID")) o.optInt("instrumentID") else o.optInt("instrumentId")
            val invested = if (o.has("amount")) o.optDouble("amount", 0.0) else o.optDouble("initialAmountInDollars", 0.0)
            val pnl = o.optJSONObject("unrealizedPnL")?.optDouble("pnL", 0.0) ?: o.optDouble("pnL", 0.0)
            id to Holding("#$id", "", o.optDouble("units", 0.0), invested, invested + (if (pnl.isNaN()) 0.0 else pnl))
        }
    }

    fun parseEtoroInstruments(json: String): Map<Int, Pair<String, String>> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("instrumentDisplayDatas") ?: root.optJSONArray("instruments") ?: JSONArray()
        return (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val id = if (o.has("instrumentID")) o.optInt("instrumentID") else o.optInt("instrumentId")
            val sym = o.optString("symbolFull").ifBlank { o.optString("symbol") }.ifBlank { "#$id" }
            id to (sym to o.optString("instrumentDisplayName").ifBlank { o.optString("displayName") })
        }
    }

    // --- Manual trades (average-cost method) -------------------------------------------------------

    fun trades(p: Prefs): List<Trade> = runCatching {
        val arr = JSONArray(p.getStr("trades", "[]"))
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let {
                Trade(LocalDate.parse(it.getString("d")), it.getString("s"), it.getBoolean("b"), it.getDouble("q"), it.getDouble("p"), it.optDouble("f", 0.0), it.optString("c", "GBP"))
            }
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    fun addTrade(p: Prefs, t: Trade) {
        val arr = JSONArray(p.getStr("trades", "[]"))
        arr.put(JSONObject().put("d", t.date.toString()).put("s", t.symbol.uppercase()).put("b", t.buy).put("q", t.qty).put("p", t.price).put("f", t.fees).put("c", t.currency))
        p.putStr("trades", arr.toString())
    }

    data class ManualPosition(val symbol: String, val currency: String, val qty: Double, val cost: Double, val realised: Double)

    /** Holdings at average cost, and profit or loss already locked in by sales. */
    fun manualPositions(trades: List<Trade>): List<ManualPosition> =
        trades.groupBy { it.symbol to it.currency }.map { (k, ts) ->
            var qty = 0.0; var cost = 0.0; var realised = 0.0
            ts.sortedBy { it.date }.forEach { t ->
                if (t.buy) { qty += t.qty; cost += t.qty * t.price + t.fees }
                else if (qty > 0) {
                    val sold = minOf(t.qty, qty)
                    val avg = cost / qty
                    realised += sold * t.price - t.fees - sold * avg
                    cost -= sold * avg; qty -= sold
                }
            }
            ManualPosition(k.first, k.second, qty, cost, realised)
        }.sortedByDescending { it.cost }

    // --- Side income ---------------------------------------------------------------------------------

    fun income(p: Prefs): List<IncomeEntry> = runCatching {
        val arr = JSONArray(p.getStr("income", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { IncomeEntry(LocalDate.parse(it.getString("d")), it.getDouble("a"), it.optString("n")) } }
            .sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    fun addIncome(p: Prefs, e: IncomeEntry) {
        val arr = JSONArray(p.getStr("income", "[]"))
        arr.put(JSONObject().put("d", e.date.toString()).put("a", e.amount).put("n", e.note))
        p.putStr("income", arr.toString())
    }

    fun monthNet(entries: List<IncomeEntry>, m: YearMonth) = entries.filter { YearMonth.from(it.date) == m }.sumOf { it.amount }

    fun incomeTarget(p: Prefs) = p.getInt("incomeTarget", 500)

    /** The path from nothing to a real second income. Steps marked auto fill in from your log. */
    data class Step(val id: String, val title: String, val auto: ((List<IncomeEntry>, Int) -> Boolean)? = null)

    val ladder = listOf(
        Step("w_niche", "Pick a niche and one clear offer"),
        Step("w_demo", "Build one showcase n8n automation"),
        Step("w_publish", "Publish it: portfolio, LinkedIn or n8n templates"),
        Step("w_talk", "First conversation with a potential client"),
        Step("w_first", "First money earned") { e, _ -> e.any { it.amount > 0 } },
        Step("w_100", "£100 earned in total") { e, _ -> e.filter { it.amount > 0 }.sumOf { it.amount } >= 100 },
        Step("w_500m", "£500 profit in a single month") { e, _ -> e.groupBy { YearMonth.from(it.date) }.values.any { m -> m.sumOf { it.amount } >= 500 } },
        Step("w_target", "Hit your monthly target") { e, t -> e.groupBy { YearMonth.from(it.date) }.values.any { m -> m.sumOf { it.amount } >= t } },
    )

    fun stepDone(p: Prefs, s: Step, entries: List<IncomeEntry>) = s.auto?.invoke(entries, incomeTarget(p)) ?: p.getBool(s.id)
}
