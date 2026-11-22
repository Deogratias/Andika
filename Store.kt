package rw.andika.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Small native store used to pass SMS-detected payments to the app screen. */
object Store {
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("andika", Context.MODE_PRIVATE)

    @Synchronized
    fun addDetected(c: Context, item: JSONObject) {
        val p = prefs(c)
        val arr = try { JSONArray(p.getString("detected", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(item)
        p.edit().putString("detected", arr.toString()).apply()
    }

    @Synchronized
    fun takeDetected(c: Context): String {
        val p = prefs(c)
        val s = p.getString("detected", "[]") ?: "[]"
        p.edit().putString("detected", "[]").apply()
        return s
    }

    /** Andika itself just started a payment of this amount. */
    fun expect(c: Context, amount: Long) {
        prefs(c).edit().putLong("exp_amount", amount).putLong("exp_ts", System.currentTimeMillis()).apply()
    }

    /** True (and cleared) when an SMS amount matches a payment Andika started in the last 20 minutes. */
    @Synchronized
    fun consumeExpected(c: Context, amount: Long): Boolean {
        val p = prefs(c)
        val a = p.getLong("exp_amount", -1)
        val t = p.getLong("exp_ts", 0)
        val ok = a == amount && System.currentTimeMillis() - t < 20 * 60 * 1000
        if (ok) p.edit().remove("exp_amount").remove("exp_ts").apply()
        return ok
    }
}
