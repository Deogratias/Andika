package rw.andika.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

/**
 * Andika hosts its screens (assets/index.html) in a WebView and adds the native parts:
 * direct USSD dialing, MoMo SMS detection, notifications, reminders and CSV export.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notif.channels(this)
        Reminder.schedule(this)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                js("window.onNativeResume && onNativeResume()")
                handleOpenIntent(intent)
            }

            // Keep any web link (e.g. a help page) out of the app's own view
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("file:///android_asset/")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (ignored: Exception) {}
                return true
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (pageReady) handleOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (pageReady) js("window.onNativeResume && onNativeResume()")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!pageReady) { finish(); return }
        web.evaluateJavascript("(window.onNativeBack && onNativeBack()) ? 'yes' : 'no'") { r ->
            if (r == null || !r.contains("yes")) finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        js("window.onNativeResume && onNativeResume()")
    }

    private fun handleOpenIntent(i: Intent?) {
        if (i == null) return
        val open = i.getStringExtra("open") ?: return
        i.removeExtra("open")
        val safe = if (open == "review" || open == "quick") open else return
        js("window.onNativeOpen && onNativeOpen('$safe')")
    }

    private fun js(code: String) {
        runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(code, null) }
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter { !granted(it) }
        if (missing.isEmpty()) return
        val p = Store.prefs(this)
        // After Android stops showing the prompt, open the app's settings page instead
        val asked = p.getInt("perm_asks", 0)
        if (asked >= 2 && missing.none { shouldShowRequestPermissionRationale(it) }) {
            openAppSettings()
            return
        }
        p.edit().putInt("perm_asks", asked + 1).apply()
        requestPermissions(missing.toTypedArray(), 42)
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (ignored: Exception) {}
    }

    private fun dial(code: String) {
        // Uri.fromParts encodes '#' correctly for USSD codes
        val uri = Uri.fromParts("tel", code, null)
        val action = if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        try {
            startActivity(Intent(action, uri))
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_DIAL, uri)) } catch (ignored: Exception) {
                Toast.makeText(this, "Couldn't open the phone app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCsv(name: String, csv: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: return false
                runOnUiThread { Toast.makeText(this, "Saved to Downloads: $name", Toast.LENGTH_LONG).show() }
                true
            } else {
                shareText(name, csv)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun shareText(subject: String, text: String) {
        runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            try { startActivity(Intent.createChooser(send, "Send your spending records")) } catch (ignored: Exception) {}
        }
    }

    /** Methods the screen (index.html) can call as AndroidBridge.xxx() */
    inner class Bridge {
        @JavascriptInterface
        fun dial(code: String) = runOnUiThread { this@MainActivity.dial(code) }

        @JavascriptInterface
        fun takeDetected(): String = Store.takeDetected(this@MainActivity)

        @JavascriptInterface
        fun expect(amount: String) {
            amount.toDoubleOrNull()?.let { Store.expect(this@MainActivity, Math.round(it)) }
        }

        @JavascriptInterface
        fun recordedOn(day: String) {
            Store.prefs(this@MainActivity).edit().putString("last_recorded_day", day).apply()
        }

        @JavascriptInterface
        fun permStatus(): String {
            val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
            return JSONObject()
                .put("sms", granted(Manifest.permission.RECEIVE_SMS))
                .put("call", granted(Manifest.permission.CALL_PHONE))
                .put("notif", notif)
                .toString()
        }

        @JavascriptInterface
        fun requestPerms() = runOnUiThread { askPermissions() }

        @JavascriptInterface
        fun openSettings() = runOnUiThread { openAppSettings() }

        @JavascriptInterface
        fun reminder(): String {
            val p = Store.prefs(this@MainActivity)
            return JSONObject().put("on", p.getBoolean("reminder_on", true)).put("hour", p.getInt("reminder_hour", 20)).toString()
        }

        @JavascriptInterface
        fun setReminder(on: Boolean, hour: Int) {
            Store.prefs(this@MainActivity).edit()
                .putBoolean("reminder_on", on)
                .putInt("reminder_hour", hour.coerceIn(0, 23))
                .apply()
            Reminder.schedule(this@MainActivity)
        }

        @JavascriptInterface
        fun saveCsv(name: String, csv: String): Boolean = this@MainActivity.saveCsv(name, csv)

        @JavascriptInterface
        fun share(subject: String, text: String) = shareText(subject, text)
    }
}
