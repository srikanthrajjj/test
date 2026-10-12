package com.safeguard.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.safeguard.app.databinding.ActivityPlacesBinding
import java.util.UUID

/**
 * Add the restaurants, bars, cinemas, etc. you want alerts for.
 * You add a place by its coordinates (long-press the main map later gives
 * center; here we keep it simple with a name + current map centre handoff).
 */
class PlacesActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlacesBinding
    private val items = ArrayList<Place>()

    private val searchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data ?: return@registerForActivityResult
        val name = data.getStringExtra("name") ?: return@registerForActivityResult
        val lat = data.getDoubleExtra("lat", 0.0)
        val lon = data.getDoubleExtra("lon", 0.0)
        val radius = data.getIntExtra("radius", 300)
        askMode(name, lat, lon, radius)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Places I visit"

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter()

        b.fabAdd.setOnClickListener {
            searchLauncher.launch(Intent(this, SearchPlaceActivity::class.java))
        }
        reload()
    }

    private fun askMode(name: String, lat: Double, lon: Double, radius: Int) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Alert when I arrive", "Alert when I leave")) { _, which ->
                Store.addPlace(
                    this,
                    Place(
                        UUID.randomUUID().toString(),
                        name, lat, lon, radius,
                        if (which == 0) "arrive" else "leave"
                    )
                )
                reload()
            }
            .show()
    }

    private fun reload() {
        items.clear()
        items.addAll(Store.places(this))
        b.list.adapter?.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.title)
            val sub: TextView = v.findViewById(R.id.sub)
            val del: View = v.findViewById(R.id.delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_place, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val p = items[pos]
            h.title.text = p.name
            val m = if (p.mode == "arrive") "on arrival" else "on leaving"
            h.sub.text = "${p.radiusM} m • alert $m"
            h.del.setOnClickListener {
                Store.removePlace(this@PlacesActivity, p.id)
                reload()
            }
        }
    }
}
