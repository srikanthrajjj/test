package com.safeguard.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.app.databinding.ActivityPairingBinding
import kotlin.random.Random

/**
 * Shows (or creates) this device's 6-digit pairing code, and registers it
 * with Supabase so a SafeGuard Guardian app on another phone can connect by
 * entering the same code. Pairing is one-way viewing: the guardian only ever
 * reads this device's location + alerts.
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var b: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Pair guardian app"

        var code = Store.pairingCode(this)
        if (code.isBlank()) {
            code = (100000 + Random.nextInt(900000)).toString()
            Store.setPairingCode(this, code)
        }
        b.code.text = code
        b.hint.text = "Open SafeGuard Guardian on the guardian's phone and enter this code."

        b.regenerate.setOnClickListener {
            val fresh = (100000 + Random.nextInt(900000)).toString()
            Store.setPairingCode(this, fresh)
            b.code.text = fresh
            register(fresh)
        }

        register(code)
    }

    private fun register(code: String) {
        Thread {
            val ok = SupabaseApi.createPairing(code)
            Handler(Looper.getMainLooper()).post {
                if (!ok) {
                    Toast.makeText(
                        this,
                        "Could not reach the pairing server. Check it's configured.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }
}
