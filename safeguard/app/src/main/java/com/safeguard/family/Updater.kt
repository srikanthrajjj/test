package com.safeguard.family

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks the cloud (GitHub Releases) for a newer build and updates over the air.
 * Android does NOT allow a sideloaded app to install updates fully silently, so
 * the user taps "Update" once, then "Install" once. After they allow updates from
 * SafeGuard the first time, it stays a single tap.
 *
 * For true zero-tap background updates, publish to Play Store internal testing.
 */
object Updater {
    // Public repo + release that the build workflow publishes to.
    private const val LATEST_API = "https://api.github.com/repos/srikanthrajjj/test/releases/latest"
    private const val CHECK_GAP_MS = 6 * 60 * 60_000L   // at most every 6 hours

    fun check(ctx: Context, manual: Boolean = false) {
        val sp = ctx.getSharedPreferences("safeguard", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - sp.getLong("upd_check", 0L) < CHECK_GAP_MS) return
        sp.edit().putLong("upd_check", now).apply()

        Executors.newSingleThreadExecutor().execute {
            val info = fetchLatest() ?: run {
                if (manual) toast(ctx, "Could not check for updates"); return@execute
            }
            val current = currentVersion(ctx)
            if (info.versionCode <= current) {
                if (manual) toast(ctx, "You're on the latest version"); return@execute
            }
            ctx.mainExecutor().execute { prompt(ctx, info) }
        }
    }

    private data class Release(val versionCode: Int, val versionName: String, val url: String)

    private fun fetchLatest(): Release? = try {
        val c = URL(LATEST_API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15000; c.readTimeout = 15000
        val body = c.inputStream.bufferedReader().readText()
        c.disconnect()
        val o = JSONObject(body)
        // tag looks like "safeguard-12"  ->  versionCode 12
        val tag = o.optString("tag_name")
        val code = tag.substringAfterLast('-').toIntOrNull() ?: return null
        var apk: String? = null
        val assets = o.optJSONArray("assets")
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
        }
        apk?.let { Release(code, o.optString("name").ifBlank { tag }, it) }
    } catch (e: Exception) { null }

    private fun currentVersion(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (e: Exception) { Int.MAX_VALUE }

    private fun prompt(ctx: Context, r: Release) {
        AlertDialog.Builder(ctx)
            .setTitle("Update available")
            .setMessage("A newer SafeGuard is ready. Update now? It downloads and installs in a few seconds.")
            .setPositiveButton("Update") { _, _ -> download(ctx, r) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(ctx: Context, r: Release) {
        // Android 8+: allow this app to install packages, else the install screen won't open.
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(ctx)
                .setTitle("Allow updates")
                .setMessage("Turn on \"Allow from this source\" on the next screen, then tap Update again.")
                .setPositiveButton("Open settings") { _, _ ->
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        toast(ctx, "Downloading update…")
        val file = File(ctx.getExternalFilesDir(null), "SafeGuard-update.apk")
        if (file.exists()) file.delete()

        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(r.url))
            .setTitle("SafeGuard update")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = dm.enqueue(req)

        ctx.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                c.applicationContext.unregisterReceiver(this)
                if (file.exists() && file.length() > 0) install(ctx, file)
                else toast(ctx, "Update download failed")
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0)
    }

    private fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { toast(ctx, "Could not open installer") }
    }

    private fun toast(ctx: Context, s: String) =
        ctx.mainExecutor().execute { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() }

    private fun Context.mainExecutor() = androidx.core.content.ContextCompat.getMainExecutor(this)
}
