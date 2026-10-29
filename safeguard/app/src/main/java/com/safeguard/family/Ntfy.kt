package com.safeguard.family

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to ntfy.sh, a free open-source push service. No account needed.
 * Two private channels per family code:
 *  - "...-loc"   : quiet location updates (the app reads these for the map)
 *  - "...-alert" : alerts like "Left office" (you subscribe to this in the ntfy app)
 * Must be called from a background thread.
 */
object Ntfy {
    private const val BASE = "https://ntfy.sh"

    fun locTopic(code: String) = "sg-${code.lowercase()}-loc"
    fun alertTopic(code: String) = "sg-${code.lowercase()}-alert"
    fun alertUrl(code: String) = "$BASE/${alertTopic(code)}"

    // Short-lived channel used only while pairing. Carries the real secret key.
    fun pairTopic(pin: String) = "sg-pair-$pin"

    fun publish(
        topic: String,
        body: String,
        title: String? = null,
        priority: Int = 3,
        tags: String? = null,
        click: String? = null,
        noCache: Boolean = false
    ): Boolean = try {
        val c = URL("$BASE/$topic").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 15000
        title?.let { c.setRequestProperty("Title", it) }
        c.setRequestProperty("Priority", priority.toString())
        tags?.let { c.setRequestProperty("Tags", it) }
        click?.let { c.setRequestProperty("Click", it) }
        if (noCache) { c.setRequestProperty("Cache", "no"); c.setRequestProperty("Firebase", "no") }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val ok = c.responseCode in 200..299
        c.disconnect()
        ok
    } catch (e: Exception) {
        false
    }

    /**
     * Listens live on the pairing topic for up to [timeoutMs] and returns the
     * secret the sharer is broadcasting, or null if none arrives. Nothing is
     * stored on the server (sharer sends with no-cache), so this must catch it live.
     */
    fun awaitSecret(pin: String, timeoutMs: Int): String? = try {
        val c = URL("$BASE/${pairTopic(pin)}/json").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = timeoutMs
        var found: String? = null
        val start = System.currentTimeMillis()
        c.inputStream.bufferedReader().use { r ->
            while (System.currentTimeMillis() - start < timeoutMs) {
                val line = r.readLine() ?: break
                if (line.isBlank()) continue
                val o = JSONObject(line)
                if (o.optString("event") == "message") {
                    val m = o.optString("message")
                    if (m.length in 8..40) { found = m; break }
                }
            }
        }
        c.disconnect()
        found
    } catch (e: Exception) {
        null
    }

    /** Returns the newest message body on a topic, or null. */
    fun latest(topic: String): String? = try {
        val c = URL("$BASE/$topic/json?poll=1&since=latest").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        var last: String? = null
        c.inputStream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val o = JSONObject(line)
                if (o.optString("event") == "message") last = o.optString("message")
            }
        }
        c.disconnect()
        last
    } catch (e: Exception) {
        null
    }
}
