package com.safeguard.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Places I visit"

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = Adapter()

        b.fabAdd.setOnClickListener { addDialog() }
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(Store.places(this))
        b.list.adapter?.notifyDataSetChanged()
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_place, null)
        val name = view.findViewById<EditText>(R.id.name)
        val lat = view.findViewById<EditText>(R.id.lat)
        val lon = view.findViewById<EditText>(R.id.lon)
        val radius = view.findViewById<EditText>(R.id.radius)
        val mode = view.findViewById<Spinner>(R.id.mode)
        mode.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Alert when I arrive", "Alert when I leave")
        )

        AlertDialog.Builder(this)
            .setTitle("Add a place")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val la = lat.text.toString().toDoubleOrNull()
                val lo = lon.text.toString().toDoubleOrNull()
                val r = radius.text.toString().toIntOrNull() ?: 150
                if (name.text.isBlank() || la == null || lo == null) {
                    Toast.makeText(this, "Fill name, lat, lon", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Store.addPlace(
                    this,
                    Place(
                        UUID.randomUUID().toString(),
                        name.text.toString().trim(),
                        la, lo, r,
                        if (mode.selectedItemPosition == 0) "arrive" else "leave"
                    )
                )
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
