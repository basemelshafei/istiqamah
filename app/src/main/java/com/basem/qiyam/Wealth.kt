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

data class Platform(val id: String, val name: String, val currency: String, val holdings: List<Holding>, val syncedAt: Long) {
    val value get() = holdings.sumOf { it.value }
    val cost get() = holdings.sumOf { it.cost }
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

    internal fun save(p: Prefs, pl: Platform) = p.putStr("plat_${pl.id}", toJson(pl).toString())

    private fun toJson(pl: Platform) = JSONObject().put("id", pl.id).put("name", pl.name).put("ccy", pl.currency).put("at", pl.syncedAt)
        .put("h", JSONArray().apply { pl.holdings.forEach { put(JSONObject().put("s", it.symbol).put("n", it.name).put("q", it.quantity).put("c", it.cost).put("v", it.value)) } })

    private fun fromJson(o: JSONObject): Platform {
        val h = o.getJSONArray("h")
        return Platform(
            o.getString("id"), o.getString("name"), o.getString("ccy"),
            (0 until h.length()).map { i -> h.getJSONObject(i).let { Holding(it.getString("s"), it.optString("n"), it.getDouble("q"), it.getDouble("c"), it.getDouble("v")) } },
            o.optLong("at"),
        )
    }

    // --- Trading 212 (API key + secret; Invest and Stocks ISA accounts) -------------------------

    fun syncTrading212(ctx: Context): String {
        val p = Prefs(ctx)
        val key = p.getStr("t212Key"); val secret = p.getStr("t212Secret")
        if (key.isBlank()) return "Add your Trading 212 API key"
        val auth = if (secret.isNotBlank()) "Basic " + Base64.encodeToString("$key:$secret".toByteArray(), Base64.NO_WRAP) else key
        val base = "https://live.trading212.com/api/v0/equity"
        var (code, body) = Net.get("$base/positions", mapOf("Authorization" to auth))
        val holdings: List<Holding>
        var currency = "GBP"
        if (code in 200..299) {
            val r = parseTrading212Positions(body); holdings = r.first; currency = r.second ?: currency
        } else if (code == 404) {
            val r2 = Net.get("$base/portfolio", mapOf("Authorization" to auth)); code = r2.first; body = r2.second
            if (code !in 200..299) return t212Error(code)
            holdings = parseTrading212Portfolio(body)
        } else return t212Error(code)
        save(p, Platform("t212", "Trading 212", currency, holdings, System.currentTimeMillis()))
        return "Trading 212: ${holdings.size} position${if (holdings.size == 1) "" else "s"}"
    }

    private fun t212Error(code: Int) = when (code) {
        401, 403 -> "Trading 212 didn't accept the key. Check it has Portfolio permission."
        429 -> "Trading 212 asks to slow down. Try again in a minute."
        else -> "Trading 212 returned an error ($code)"
    }

    /** /equity/positions: values are in your account currency under walletImpact. */
    fun parseTrading212Positions(json: String): Pair<List<Holding>, String?> {
        val arr = JSONArray(json)
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
                cost = w?.optDouble("totalCost") ?: (o.optDouble("averagePricePaid", 0.0) * o.optDouble("quantity", 0.0)),
                value = w?.optDouble("currentValue") ?: (o.optDouble("currentPrice", 0.0) * o.optDouble("quantity", 0.0)),
            )
        }
        return list to ccy
    }

    /** Older /equity/portfolio shape: prices in the instrument's currency. */
    fun parseTrading212Portfolio(json: String): List<Holding> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val q = o.optDouble("quantity", 0.0)
            Holding(o.optString("ticker").substringBefore("_"), "", q, o.optDouble("averagePrice", 0.0) * q, o.optDouble("currentPrice", 0.0) * q)
        }
    }

    // --- eToro (Public API key + User key) --------------------------------------------------------

    fun syncEtoro(ctx: Context): String {
        val p = Prefs(ctx)
        val apiKey = p.getStr("etoroApiKey"); val userKey = p.getStr("etoroUserKey")
        if (apiKey.isBlank() || userKey.isBlank()) return "Add both eToro keys"
        fun headers() = mapOf("x-api-key" to apiKey, "x-user-key" to userKey, "x-request-id" to UUID.randomUUID().toString())
        val (code, body) = Net.get("https://public-api.etoro.com/api/v1/trading/info/real/pnl", headers())
        if (code == 401 || code == 403) return "eToro didn't accept the keys"
        if (code !in 200..299) return "eToro returned an error ($code)"
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
        return "eToro: ${holdings.size} position${if (holdings.size == 1) "" else "s"}"
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
