package com.replykey.ai

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("replykey", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = p.edit().putString("model", v).apply()

    var userName: String
        get() = p.getString("user_name", "") ?: ""
        set(v) = p.edit().putString("user_name", v.trim()).apply()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        val MODELS = listOf(
            "claude-opus-5-5" to "Opus 5.5 — best replies",
            "claude-sonnet-5-5" to "Sonnet 5.5 — faster",
            "claude-haiku-5-5" to "Haiku 5.5 — fastest, cheapest",
        )
    }
}
