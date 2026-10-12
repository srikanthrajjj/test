package com.safeguard.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.safeguard.app.databinding.ActivitySearchPlaceBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Search a place, company or school by name (OpenStreetMap Nominatim, no API
 * key, no Google Maps), pick a result, then choose a radius. Returns the
 * chosen place to the caller, which decides whether it becomes the safe zone
 * or a place to watch.
 */
class SearchPlaceActivity : AppCompatActivity() {

    data class Result(val name: String, val lat: Double, val lon: Double)

    private lateinit var b: ActivitySearchPlaceBinding
    private val results = ArrayList<Result>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchPlaceBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Search a place"

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter()

        b.searchBtn.setOnClickListener { search() }
        b.query.setOnEditorActionListener { _, _, _ -> search(); true }
    }

    private fun search() {
        val q = b.query.text.toString().trim()
        if (q.isEmpty()) return
        b.empty.text = "Searching..."
        b.empty.visibility = View.VISIBLE
        Thread {
            val found = runSearch(q)
            main.post {
                results.clear()
                results.addAll(found)
                b.list.adapter?.notifyDataSetChanged()
                b.empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
                b.empty.text = "No results. Try a more specific name."
            }
        }.start()
    }

    private fun runSearch(q: String): List<Result> {
        return try {
            val url = "https://nominatim.openstreetmap.org/search?format=json&limit=8&q=" +
                java.net.URLEncoder.encode(q, "UTF-8")
            val req = Request.Builder().url(url)
                .addHeader("User-Agent", "SafeGuard-App/1.0")
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                val arr = JSONArray(body)
                val out = ArrayList<Result>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(
                        Result(
                            o.optString("display_name", q),
                            o.getString("lat").toDouble(),
                            o.getString("lon").toDouble()
                        )
                    )
                }
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun pickRadius(r: Result) {
        val radii = arrayOf("200 m", "300 m", "500 m", "1 km")
        val values = intArrayOf(200, 300, 500, 1000)
        AlertDialog.Builder(this)
            .setTitle("Radius for \"${r.name}\"")
            .setItems(radii) { _, which ->
                val data = Intent()
                    .putExtra("name", r.name)
                    .putExtra("lat", r.lat)
                    .putExtra("lon", r.lon)
                    .putExtra("radius", values[which])
                setResult(RESULT_OK, data)
                finish()
            }
            .show()
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return VH(v)
        }

        override fun getItemCount() = results.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = results[pos]
            h.text.text = r.name
            h.text.setOnClickListener { pickRadius(r) }
        }
    }
}
