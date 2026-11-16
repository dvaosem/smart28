package com.dvaosem.smart28.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dvaosem.smart28.R
import com.dvaosem.smart28.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Phone notifications evaluated from the device snapshot:
 *  - low battery, - light on late at night, - device went offline for a while.
 * Settings JSON (S28Prefs.alerts): {on, bat:{on,lim}, light:{on,from,to,skip:[ids]}, off:{on,min}}.
 */
object Notifier {
    private const val CH = "s28-alerts"
    private val LIGHT_CATS = setOf("dj", "dd", "tgkg")
    private val LIGHT_NAME = Regex("svetl|light|žiarov|lamp", RegexOption.IGNORE_CASE)
    private val SW_CODES = listOf("switch", "switch_1", "switch_led")

    fun settings(c: Context): JSONObject = try { JSONObject(S28Prefs.alerts(c)) } catch (e: Exception) { JSONObject() }

    fun schedule(c: Context, on: Boolean) {
        val wm = WorkManager.getInstance(c)
        if (on) {
            val req = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("s28-alerts", ExistingPeriodicWorkPolicy.KEEP, req)
        } else wm.cancelUniqueWork("s28-alerts")
    }

    private fun canPost(c: Context) =
        Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(c: Context, key: String, title: String, text: String, action: PendingIntent? = null) {
        if (!canPost(c)) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(NotificationChannel(CH, "Upozornenia smart28", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true)
        if (action != null) n.addAction(0, "Vypnúť", action)
        nm.notify(key.hashCode(), n.build())
    }

    fun test(c: Context) = post(c, "test", "smart28", "Notifikácie fungujú.")

    private fun status(d: JSONObject, code: String): Any? {
        val st = d.optJSONArray("status") ?: return null
        for (i in 0 until st.length()) {
            val s = st.optJSONObject(i) ?: continue
            if (s.optString("code") == code) return s.opt("value")
        }
        return null
    }

    fun check(c: Context, devices: JSONArray) {
        val s = settings(c)
        if (!s.optBoolean("on", false)) return
        val sp = c.getSharedPreferences("s28alerts", Context.MODE_PRIVATE)
        val ed = sp.edit()
        val first = !sp.getBoolean("init", false)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val bat = s.optJSONObject("bat"); val light = s.optJSONObject("light"); val off = s.optJSONObject("off")

        for (i in 0 until devices.length()) {
            val d = devices.optJSONObject(i) ?: continue
            val id = d.optString("id"); if (id.isEmpty()) continue
            val name = d.optString("name").trim()
            val online = d.optBoolean("online", false)

            // battery
            if (bat != null && bat.optBoolean("on", true)) {
                val lim = bat.optInt("lim", 30)
                val b = ((status(d, "battery_percentage") ?: status(d, "residual_electricity")) as? Number)?.toInt()
                if (b != null) {
                    val flagged = sp.getBoolean("bat_$id", false)
                    if (b < lim && !flagged) { post(c, "bat_$id", "Slabá batéria", "$name: $b %"); ed.putBoolean("bat_$id", true) }
                    else if (b >= lim + 10 && flagged) ed.remove("bat_$id")
                }
            }

            // light on at night
            if (light != null && light.optBoolean("on", true) && online) {
                val isLight = d.optString("category") in LIGHT_CATS || LIGHT_NAME.containsMatchIn(name)
                val sc = SW_CODES.firstOrNull { status(d, it) is Boolean }
                val skip = light.optJSONArray("skip")
                var skipped = false
                if (skip != null) for (k in 0 until skip.length()) if (skip.optString(k) == id) skipped = true
                val from = light.optInt("from", 22); val to = light.optInt("to", 5)
                val night = if (from > to) (hour >= from || hour < to) else (hour in from until to)
                if (isLight && sc != null && !skipped && night && status(d, sc) == true) {
                    val nightKey = Calendar.getInstance().apply { if (hour < to) add(Calendar.DAY_OF_YEAR, -1) }
                        .let { "${it.get(Calendar.YEAR)}${it.get(Calendar.DAY_OF_YEAR)}" }
                    if (sp.getString("lt_$id", "") != nightKey) {
                        val nid = "lt_$id".hashCode()
                        val act = PendingIntent.getBroadcast(c, nid,
                            Intent(c, AlertActionReceiver::class.java).putExtra("id", id).putExtra("code", sc).putExtra("nid", nid),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        post(c, "lt_$id", "Svetlo svieti", "$name svieti po ${from}:00", act)
                        ed.putString("lt_$id", nightKey)
                    }
                }
            }

            // offline for a while (only devices seen online before)
            if (off != null && off.optBoolean("on", true)) {
                if (online) { ed.remove("off_$id"); ed.remove("offA_$id") }
                else {
                    var began = sp.getLong("off_$id", 0L)
                    if (began == 0L) { began = if (first) -1L else now; ed.putLong("off_$id", began) }
                    val mins = off.optInt("min", 30)
                    if (began > 0 && now - began >= mins * 60_000L && !sp.getBoolean("offA_$id", false)) {
                        post(c, "off_$id", "Zariadenie offline", "$name nie je dostupné")
                        ed.putBoolean("offA_$id", true)
                    }
                }
            }
        }
        ed.putBoolean("init", true).apply()
    }
}
