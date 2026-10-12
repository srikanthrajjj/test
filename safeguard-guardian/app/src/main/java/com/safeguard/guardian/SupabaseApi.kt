package com.safeguard.guardian

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only REST client: fetches the paired phone's last location and new alerts. */
object SupabaseApi {
    data class Location(val lat: Double, val lon: Double, val updatedAt: String)
    data class Alert(val id: Long, val title: String, val body: String, val createdAt: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val configured get() = BuildConfig.SUPABASE_URL.isNotBlank()

    private fun headers(req: Request.Builder): Request.Builder = req
        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")

    /** True if a pairing row exists for this code (does the tracked phone know this code?). */
    fun codeExists(code: String): Boolean {
        if (!configured) return false
        return try {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/pairings?code=eq.$code&select=code"
            val req = headers(Request.Builder().url(url)).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return false
                resp.isSuccessful && JSONArray(body).length() > 0
            }
        } catch (e: Exception) {
            Log.w("SupabaseApi", "codeExists failed", e); false
        }
    }

    fun latestLocation(code: String): Location? {
        if (!configured || code.isBlank()) return null
        return try {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/locations?code=eq.$code&select=lat,lon,updated_at"
            val req = headers(Request.Builder().url(url)).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null
                if (!resp.isSuccessful) return null
                val arr = JSONArray(body)
                if (arr.length() == 0) return null
                val o = arr.getJSONObject(0)
                Location(o.getDouble("lat"), o.getDouble("lon"), o.optString("updated_at", ""))
            }
        } catch (e: Exception) {
            Log.w("SupabaseApi", "latestLocation failed", e); null
        }
    }

    /** Alerts newer than [afterId], oldest first. */
    fun newAlerts(code: String, afterId: Long): List<Alert> {
        if (!configured || code.isBlank()) return emptyList()
        return try {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/alerts" +
                "?code=eq.$code&id=gt.$afterId&select=id,title,body,created_at&order=id.asc"
            val req = headers(Request.Builder().url(url)).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                if (!resp.isSuccessful) return emptyList()
                val arr = JSONArray(body)
                val out = ArrayList<Alert>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(
                        Alert(
                            o.getLong("id"),
                            o.getString("title"),
                            o.getString("body"),
                            o.optString("created_at", "")
                        )
                    )
                }
                out
            }
        } catch (e: Exception) {
            Log.w("SupabaseApi", "newAlerts failed", e); emptyList()
        }
    }
}
