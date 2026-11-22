package rw.andika.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONObject
import java.util.Locale

/** Notices MoMo confirmation SMS and asks you to record the payment. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val sender = msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val d = MoMoParser.parse(sender, body) ?: return

        val now = System.currentTimeMillis()
        val matched = Store.consumeExpected(context, d.amount)
        val id = "sms" + now.toString(36)
        Store.addDetected(
            context,
            JSONObject()
                .put("id", id)
                .put("amount", d.amount)
                .put("payee", d.payee)
                .put("ts", now)
                .put("matched", matched)
        )
        // A payment Andika started itself is already recorded: no need to ask again.
        if (matched) return

        val amountText = String.format(Locale.US, "RWF %,d", d.amount)
        val who = if (d.payee.isNotBlank()) " to ${d.payee}" else ""
        Notif.show(
            context,
            (now % 1_000_000).toInt(),
            Notif.CH_PAY,
            "You paid $amountText$who. Record it?",
            "Tap to pick a category. It takes 3 seconds.",
            "open",
            "review"
        )
    }
}
