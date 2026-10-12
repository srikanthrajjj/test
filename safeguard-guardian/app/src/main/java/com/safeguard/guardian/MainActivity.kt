package com.safeguard.guardian

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.guardian.databinding.ActivityMainBinding

/** Enter the 6-digit code shown on the tracked phone's "Pair guardian app" screen. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val saved = Store.pairingCode(this)
        if (saved.isNotBlank()) {
            b.code.setText(saved)
        }

        b.connect.setOnClickListener { connect() }
    }

    private fun connect() {
        val code = b.code.text.toString().trim()
        if (code.length != 6 || code.toIntOrNull() == null) {
            Toast.makeText(this, "Enter the 6-digit code", Toast.LENGTH_SHORT).show()
            return
        }
        if (!SupabaseApi.configured) {
            Toast.makeText(this, "App is not configured with a server yet.", Toast.LENGTH_LONG).show()
            return
        }
        b.connect.isEnabled = false
        b.connect.text = "Connecting..."
        Thread {
            val exists = SupabaseApi.codeExists(code)
            main.post {
                b.connect.isEnabled = true
                b.connect.text = "Connect"
                if (exists) {
                    Store.setPairingCode(this, code)
                    startActivity(Intent(this, LiveActivity::class.java))
                } else {
                    Toast.makeText(this, "That code wasn't found. Check it and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        // If already paired, go straight to the live view.
        if (Store.pairingCode(this).isNotBlank() && SupabaseApi.configured) {
            startActivity(Intent(this, LiveActivity::class.java))
        }
    }
}
