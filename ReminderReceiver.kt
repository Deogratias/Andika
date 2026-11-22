package rw.andika.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Evening nudge, skipped on days you've already recorded something. Rescheduled after a restart. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminder.schedule(context)
            return
        }
        val p = Store.prefs(context)
        if (!p.getBoolean("reminder_on", true)) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (p.getString("last_recorded_day", "") == today) return
        Notif.show(
            context, 2001, Notif.CH_REMIND,
            "Did you record today's spending?",
            "Nothing recorded yet today. Add anything you paid in cash or outside Andika.",
            "open", "quick"
        )
    }
}

object Reminder {
    private fun intent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 3001,
        Intent(c, ReminderReceiver::class.java).setAction("rw.andika.app.REMIND"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = intent(c)
        am.cancel(pi)
        val p = Store.prefs(c)
        if (!p.getBoolean("reminder_on", true)) return
        val hour = p.getInt("reminder_hour", 20)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}
