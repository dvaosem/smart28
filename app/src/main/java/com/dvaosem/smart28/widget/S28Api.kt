package com.dvaosem.smart28.widget

import android.content.Context
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Calls the smart28 Cloud Function with an ID token obtained from the refresh token the web app handed over. */
object S28Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    @Synchronized
    fun idToken(c: Context): String? {
        val now = System.currentTimeMillis()
        val cached = S28Prefs.idToken(c)
        if (cached.isNotEmpty() && now < S28Prefs.idExp(c) - 60_000) return cached
        val rt = S28Prefs.refresh(c); val key = S28Prefs.apiKey(c)
        if (rt.isEmpty() || key.isEmpty()) return null
        return try {
            val body = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", rt).build()
            val req = Request.Builder()
                .url("https://securetoken.googleapis.com/v1/token?key=$key")
                .header("Referer", "https://dvaosem.com/")
                .post(body).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val j = JSONObject(r.body?.string() ?: return null)
                val id = j.optString("id_token")
                if (id.isEmpty()) return null
                val ttl = j.optString("expires_in", "3600").toLongOrNull() ?: 3600L
                S28Prefs.saveTokens(c, id, j.optString("refresh_token"), now + ttl * 1000)
                id
            }
        } catch (e: Exception) { null }
    }

    /** POSTs {action, ...params}; returns the JSON answer or null on any failure. */
    fun call(c: Context, action: String, params: JSONObject = JSONObject()): JSONObject? {
        val token = idToken(c) ?: return null
        val url = S28Prefs.apiUrl(c)
        if (!url.startsWith("https://")) return null
        return try {
            params.put("action", action)
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .post(params.toString().toRequestBody(JSON)).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                JSONObject(r.body?.string() ?: return null)
            }
        } catch (e: Exception) { null }
    }
}
