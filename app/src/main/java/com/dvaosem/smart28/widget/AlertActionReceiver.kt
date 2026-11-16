package com.dvaosem.smart28.widget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** "Vypnúť" button on a notification: switches the device off without opening the app. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val code = i.getStringExtra("code") ?: return
        val nid = i.getIntExtra("nid", 0)
        val pending = goAsync()
        Thread {
            try {
                val cmds = JSONArray().put(JSONObject().put("code", code).put("value", false))
                val ok = S28Api.call(c, "command", JSONObject().put("id", id).put("commands", cmds)) != null
                if (ok) (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(nid)
            } finally { pending.finish() }
        }.start()
    }
}
