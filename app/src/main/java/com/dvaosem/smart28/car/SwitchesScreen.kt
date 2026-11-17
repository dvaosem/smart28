package com.dvaosem.smart28.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import com.dvaosem.smart28.widget.S28Api
import com.dvaosem.smart28.widget.S28Prefs
import org.json.JSONArray
import org.json.JSONObject

/** Lists favourite switch devices (★ in the app) with a toggle each. */
class SwitchesScreen(c: CarContext) : Screen(c) {
    private val main = Handler(Looper.getMainLooper())
    private val codes = listOf("switch", "switch_1", "switch_led")
    private var devices = JSONArray()
    private var loading = true

    init { reload() }

    private fun statusOf(d: JSONObject, code: String): Any? {
        val st = d.optJSONArray("status") ?: return null
        for (i in 0 until st.length()) {
            val s = st.getJSONObject(i)
            if (s.optString("code") == code) return s.opt("value")
        }
        return null
    }

    private fun setLocal(id: String, code: String, v: Boolean) {
        for (i in 0 until devices.length()) {
            val d = devices.getJSONObject(i)
            if (d.optString("id") != id) continue
            val st = d.optJSONArray("status") ?: JSONArray().also { d.put("status", it) }
            for (j in 0 until st.length()) {
                val s = st.getJSONObject(j)
                if (s.optString("code") == code) { s.put("value", v); return }
            }
            st.put(JSONObject().put("code", code).put("value", v))
        }
    }

    private fun reload() {
        loading = true
        Thread {
            val res = S28Api.call(carContext, "devices")
            val arr = res?.optJSONArray("devices")
            if (arr != null) S28Prefs.saveDevices(carContext, arr.toString())
            devices = try { JSONArray(S28Prefs.devices(carContext)) } catch (e: Exception) { JSONArray() }
            loading = false
            main.post { invalidate() }
        }.start()
    }

    private fun send(id: String, code: String, on: Boolean) {
        setLocal(id, code, on)
        invalidate()
        Thread {
            val cmd = JSONObject().put("id", id)
                .put("commands", JSONArray().put(JSONObject().put("code", code).put("value", on)))
            val ok = S28Api.call(carContext, "command", cmd) != null
            main.post {
                if (!ok) {
                    setLocal(id, code, !on)
                    CarToast.makeText(carContext, "Príkaz zlyhal", CarToast.LENGTH_SHORT).show()
                }
                invalidate()
            }
        }.start()
    }

    override fun onGetTemplate(): Template {
        val byId = HashMap<String, JSONObject>()
        for (i in 0 until devices.length()) devices.getJSONObject(i).let { byId[it.optString("id")] = it }
        val list = ItemList.Builder()
        var n = 0
        for (fid in S28Prefs.favorites(carContext)) {
            val d = byId[fid] ?: continue
            val code = codes.firstOrNull { statusOf(d, it) is Boolean } ?: continue
            val on = statusOf(d, code) as Boolean
            val online = d.optBoolean("online")
            val title = d.optString("name").trim() + if (online) "" else " (offline)"
            val row = Row.Builder().setTitle(title)
            if (online) {
                row.setToggle(Toggle.Builder { checked -> send(fid, code, checked) }.setChecked(on).build())
            }
            list.addItem(row.build())
            if (++n >= 6) break
        }
        if (n == 0) list.setNoItemsMessage(if (loading) "Načítavam…" else "Žiadne obľúbené spínače – označ ★ v appke")
        val t = ListTemplate.Builder().setTitle("smart28").setHeaderAction(Action.APP_ICON)
        return if (loading && n == 0) t.setLoading(true).build() else t.setSingleList(list.build()).build()
    }
}
