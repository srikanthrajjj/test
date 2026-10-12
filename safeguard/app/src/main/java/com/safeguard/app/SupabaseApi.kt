package com.safeguard.app

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal REST client for the Supabase project backing pairing + live
 * location. Uses plain PostgREST calls with the anon key -- no SDK needed.
 * Every call is a no-op if SUPABASE_URL is blank (not configured yet).
 */
object SupabaseApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val configured get() = BuildConfig.SUPABASE_URL.isNotBlank()

    private fun headers(req: Request.Builder): Request.Builder = req
        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        .addHeader("Content-Type", "application/json")

    /** Create (or confirm) a pairing row for this code. Call off the main thread. */
    fun createPairing(code: String): Boolean {
        if (!configured) return false
        return try {
            val body = JSONObject().put("code", code).toString()
                .toRequestBody("application/json".toMediaType())
            val req = headers(
                Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/rest/v1/pairings")
                    .post(body)
            ).addHeader("Prefer", "resolution=merge-duplicates").build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w("SupabaseApi", "createPairing failed", e); false
        }
    }

    /** Upsert this device's current location under its pairing code. */
    fun pushLocation(code: String, lat: Double, lon: Double) {
        if (!configured || code.isBlank()) return
        try {
            val body = JSONObject()
                .put("code", code)
                .put("lat", lat)
                .put("lon", lon)
                .put("updated_at", "now()")
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = headers(
                Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/rest/v1/locations")
                    .post(body)
            ).addHeader("Prefer", "resolution=merge-duplicates").build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w("SupabaseApi", "pushLocation failed", e)
        }
    }

    /** Record an alert so the paired guardian app can show it too. */
    fun pushAlert(code: String, title: String, body: String, lat: Double?, lon: Double?) {
        if (!configured || code.isBlank()) return
        try {
            val json = JSONObject()
                .put("code", code)
                .put("title", title)
                .put("body", body)
            if (lat != null) json.put("lat", lat)
            if (lon != null) json.put("lon", lon)
            val reqBody = json.toString().toRequestBody("application/json".toMediaType())
            val req = headers(
                Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/rest/v1/alerts")
                    .post(reqBody)
            ).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w("SupabaseApi", "pushAlert failed", e)
        }
    }
}
