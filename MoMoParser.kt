package rw.andika.app

/** A payment noticed in an SMS. */
data class Detected(val amount: Long, val payee: String)

/**
 * Best-effort reader for MoMo / Airtel Money / bank confirmation SMS.
 * Only outgoing money (payments, transfers, purchases) counts. Money received,
 * deposits and cash withdrawals are ignored, so nothing is counted twice.
 */
object MoMoParser {
    private val KNOWN = listOf("m-money", "momo", "mtn", "airtel", "mobile money", "mobilemoney")
    private val OUT = listOf(
        "payment of", "you have paid", "you paid", "transferred", "you have sent", "sent to",
        "purchase of", "you have bought", "you bought", "transaction of", "debited",
        "has been paid", "paid to"
    )
    private val IN = listOf(
        "you have received", "received from", "deposit of", "you have deposited",
        "credited", "withdrawn", "withdrawal", "cash out"
    )
    private val AMT = Regex("""([0-9][0-9,]*(?:\.[0-9]+)?)\s*RWF|RWF\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val TO = Regex("""\bto\s+([A-Za-z][^\n(]{1,40}?)(?=\s*\(|\s+[0-9]|\s+at\b|\s+has\b|\s+was\b|\s+on\b|\s+with\b|[.,;]|$)""", RegexOption.IGNORE_CASE)
    private val BY = Regex("""\bby\s+([A-Za-z][^\n(]{1,40}?)(?=\s+on\b|\s*\(|\s+[0-9]|[.,;]|$)""", RegexOption.IGNORE_CASE)
    private val AT = Regex("""\b(?:purchase|payment)\s+at\s+([A-Za-z][^\n(]{1,40}?)(?=\s+on\b|\s*\(|\s+[0-9]|[.,;]|$)""", RegexOption.IGNORE_CASE)

    private fun firstIndex(text: String, words: List<String>): Int {
        var best = -1
        for (w in words) {
            val i = text.indexOf(w)
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        return best
    }

    fun parse(sender: String, body: String): Detected? {
        val b = body.lowercase()
        if (!b.contains("rwf")) return null
        val known = KNOWN.any { sender.lowercase().contains(it) }
        if (!known && !b.contains("balance")) return null
        val out = firstIndex(b, OUT)
        val inc = firstIndex(b, IN)
        if (out < 0) return null
        if (inc in 0 until out) return null
        val m = AMT.find(body) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }.replace(",", "")
        val amount = raw.toDoubleOrNull()?.let { Math.round(it) } ?: return null
        if (amount <= 0) return null
        val payee = (TO.find(body) ?: BY.find(body) ?: AT.find(body))?.groupValues?.get(1)?.trim() ?: ""
        return Detected(amount, payee)
    }
}
