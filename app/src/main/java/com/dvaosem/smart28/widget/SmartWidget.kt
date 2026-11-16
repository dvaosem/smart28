package com.dvaosem.smart28.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dvaosem.smart28.R
import com.dvaosem.smart28.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Home-screen widget: favourite devices (heating −/+, switches ZAP/VYP). */
class SmartWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_CMD = "com.dvaosem.smart28.CMD"
        const val ACTION_REFRESH = "com.dvaosem.smart28.REFRESH"
        private const val MAX_ROWS = 8
        private val SW_CODES = listOf("switch", "switch_1", "switch_led")

        fun ids(c: Context): IntArray =
            AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, SmartWidget::class.java))

        fun hasWidgets(c: Context) = ids(c).isNotEmpty()

        fun refreshAll(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            for (id in ids(c)) render(c, mgr, id)
        }

        /** Downloads fresh states of all devices and re-renders. Safe to call from a worker/background thread. */
        fun fetchAndRender(c: Context): Boolean {
            val res = S28Api.call(c, "devices") ?: return false
            val arr = res.optJSONArray("devices") ?: return false
            S28Prefs.saveDevices(c, arr.toString())
            refreshAll(c)
            return true
        }

        private fun statusOf(dev: JSONObject, code: String): Any? {
            val st = dev.optJSONArray("status") ?: return null
            for (i in 0 until st.length()) {
                val s = st.getJSONObject(i)
                if (s.optString("code") == code) return s.opt("value")
            }
            return null
        }

        private fun setStatus(dev: JSONObject, code: String, value: Any) {
            val st = dev.optJSONArray("status") ?: JSONArray().also { dev.put("status", it) }
            for (i in 0 until st.length()) {
                val s = st.getJSONObject(i)
                if (s.optString("code") == code) { s.put("value", value); return }
            }
            st.put(JSONObject().put("code", code).put("value", value))
        }

        private fun cmdIntent(c: Context, req: Int, id: String, code: String, type: String, value: String): PendingIntent {
            val i = Intent(c, SmartWidget::class.java).setAction(ACTION_CMD)
                .putExtra("k", S28Prefs.cmdKey(c)).putExtra("id", id).putExtra("code", code)
                .putExtra("t", type).putExtra("v", value)
            return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun render(c: Context, mgr: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(c.packageName, R.layout.widget_main)
            rv.removeAllViews(R.id.rows)
            rv.setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(
                c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            rv.setOnClickPendingIntent(R.id.btn_refresh, PendingIntent.getBroadcast(
                c, 2, Intent(c, SmartWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            val devs = try { JSONArray(S28Prefs.devices(c)) } catch (e: Exception) { JSONArray() }
            val byId = HashMap<String, JSONObject>()
            for (i in 0 until devs.length()) devs.getJSONObject(i).let { byId[it.optString("id")] = it }
            val favs = S28Prefs.favorites(c).mapNotNull { byId[it] }.take(MAX_ROWS)

            rv.setViewVisibility(R.id.empty, if (favs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            favs.forEachIndexed { i, d ->
                val id = d.optString("id"); val online = d.optBoolean("online")
                val heat = d.optString("category") == "wk"
                val name = d.optString("name").trim()
                val base = (widgetId and 0xFFF) * 100 + i * 10
                if (heat) {
                    val row = RemoteViews(c.packageName, R.layout.row_heat)
                    val cur = (statusOf(d, "temp_current") as? Number)?.toInt()
                    val set = (statusOf(d, "temp_set") as? Number)?.toInt()
                    row.setTextViewText(R.id.name, name)
                    row.setTextViewText(R.id.value, (if (cur != null) "$cur°" else "–") + " → " + (if (set != null) "$set°" else "–"))
                    if (online && set != null) {
                        row.setOnClickPendingIntent(R.id.btn_minus, cmdIntent(c, base + 1, id, "temp_set", "i", (maxOf(5, set - 1)).toString()))
                        row.setOnClickPendingIntent(R.id.btn_plus, cmdIntent(c, base + 2, id, "temp_set", "i", (minOf(60, set + 1)).toString()))
                    }
                    rv.addView(R.id.rows, row)
                } else {
                    val row = RemoteViews(c.packageName, R.layout.row_sw)
                    val code = SW_CODES.firstOrNull { statusOf(d, it) is Boolean }
                    val on = code?.let { statusOf(d, it) as? Boolean } ?: false
                    row.setTextViewText(R.id.name, name + if (online) "" else " (offline)")
                    row.setTextViewText(R.id.btn_toggle, if (on) "ZAP" else "VYP")
                    row.setInt(R.id.btn_toggle, "setBackgroundResource", if (on) R.drawable.widget_btn_on else R.drawable.widget_btn)
                    row.setTextColor(R.id.btn_toggle, if (on) 0xFF22201D.toInt() else 0xFFECE6DD.toInt())
                    if (online && code != null) {
                        row.setOnClickPendingIntent(R.id.btn_toggle, cmdIntent(c, base + 3, id, code, "b", (!on).toString()))
                    }
                    rv.addView(R.id.rows, row)
                }
            }
            mgr.updateAppWidget(widgetId, rv)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, mgr, id)
        val pending = goAsync()
        Thread { try { fetchAndRender(context) } finally { pending.finish() } }.start()
    }

    override fun onEnabled(context: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("s28-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("s28-refresh")
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                val pending = goAsync()
                Thread {
                    try { if (!fetchAndRender(context)) toast(context, "Obnovenie zlyhalo – otvor smart28") }
                    finally { pending.finish() }
                }.start()
            }
            ACTION_CMD -> {
                if (intent.getStringExtra("k") != S28Prefs.cmdKey(context)) return
                val id = intent.getStringExtra("id") ?: return
                val code = intent.getStringExtra("code") ?: return
                val type = intent.getStringExtra("t") ?: return
                val raw = intent.getStringExtra("v") ?: return
                val value: Any = if (type == "b") raw.toBoolean() else (raw.toIntOrNull() ?: return)
                val pending = goAsync()
                Thread {
                    try { sendCommand(context, id, code, value) } finally { pending.finish() }
                }.start()
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun sendCommand(c: Context, id: String, code: String, value: Any) {
        // optimistic update so the widget reacts immediately
        try {
            val devs = JSONArray(S28Prefs.devices(c))
            for (i in 0 until devs.length()) {
                val d = devs.getJSONObject(i)
                if (d.optString("id") == id) setStatus(d, code, value)
            }
            S28Prefs.saveDevices(c, devs.toString())
            refreshAll(c)
        } catch (e: Exception) { }
        val cmd = JSONObject().put("id", id).put("commands", JSONArray().put(JSONObject().put("code", code).put("value", value)))
        val res = S28Api.call(c, "command", cmd)
        if (res == null) {
            toast(c, "Príkaz zlyhal – otvor smart28")
            fetchAndRender(c)
        }
    }

    private fun toast(c: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show() }
    }
}
