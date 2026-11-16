package com.dvaosem.smart28.widget

import android.content.Context
import org.json.JSONArray
import java.util.UUID

/** App-private storage: session for the widget, last devices snapshot, favourites. */
object S28Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("s28", Context.MODE_PRIVATE)

    fun saveSession(c: Context, apiKey: String, refreshToken: String, apiUrl: String) {
        sp(c).edit().putString("apiKey", apiKey).putString("refresh", refreshToken).putString("apiUrl", apiUrl)
            .remove("idToken").putLong("idExp", 0L).apply()
    }
    fun apiKey(c: Context) = sp(c).getString("apiKey", "") ?: ""
    fun refresh(c: Context) = sp(c).getString("refresh", "") ?: ""
    fun apiUrl(c: Context) = sp(c).getString("apiUrl", "") ?: ""
    fun idToken(c: Context) = sp(c).getString("idToken", "") ?: ""
    fun idExp(c: Context) = sp(c).getLong("idExp", 0L)
    fun saveTokens(c: Context, id: String, refresh: String, exp: Long) {
        val e = sp(c).edit().putString("idToken", id).putLong("idExp", exp)
        if (refresh.isNotEmpty()) e.putString("refresh", refresh)
        e.apply()
    }

    fun devices(c: Context) = sp(c).getString("devices", "[]") ?: "[]"
    fun saveDevices(c: Context, json: String) { sp(c).edit().putString("devices", json).apply() }

    fun alerts(c: Context) = sp(c).getString("alerts", "{}") ?: "{}"
    fun saveAlerts(c: Context, json: String) { sp(c).edit().putString("alerts", json).apply() }

    fun favorites(c: Context): List<String> {
        val a = try { JSONArray(sp(c).getString("favs", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }
    }
    fun saveFavorites(c: Context, json: String) { sp(c).edit().putString("favs", json).apply() }

    /** Random key carried by widget PendingIntents so other apps cannot trigger device commands. */
    @Synchronized fun cmdKey(c: Context): String {
        var k = sp(c).getString("cmdKey", null)
        if (k == null) { k = UUID.randomUUID().toString(); sp(c).edit().putString("cmdKey", k).apply() }
        return k
    }
}
