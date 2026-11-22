package rw.andika.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notif {
    const val CH_PAY = "payments"
    const val CH_REMIND = "reminders"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_PAY, "Payments to record", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks you to record a MoMo payment it noticed"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Evening nudge to record the day's spending"
            }
        )
    }

    fun show(c: Context, id: Int, channel: String, title: String, text: String, openKey: String, openValue: String) {
        channels(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(openKey, openValue)
        }
        val pi = PendingIntent.getActivity(
            c, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF1F5E46.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }
}
