package com.safeguard.family

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** First-time setup: pick "I share" or "I watch". */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        when (prefs.role) {
            "sharer" -> { go(SharerActivity::class.java); return }
            "viewer" -> { go(ViewerActivity::class.java); return }
        }
        setContentView(R.layout.activity_main)
        Ui.applyInsets(findViewById(R.id.root))

        val choose = findViewById<View>(R.id.chooseStep)
        val sharerStep = findViewById<View>(R.id.sharerStep)
        val viewerStep = findViewById<View>(R.id.viewerStep)

        findViewById<View>(R.id.cardShare).setOnClickListener {
            choose.visibility = View.GONE; sharerStep.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.cardWatch).setOnClickListener {
            choose.visibility = View.GONE; viewerStep.visibility = View.VISIBLE
        }
        listOf(R.id.backFromSharer, R.id.backFromViewer).forEach { id ->
            findViewById<TextView>(id).setOnClickListener {
                sharerStep.visibility = View.GONE; viewerStep.visibility = View.GONE
                choose.visibility = View.VISIBLE
            }
        }

        findViewById<Button>(R.id.btnStartSharing).setOnClickListener {
            val name = findViewById<EditText>(R.id.inputMyName).text.toString().trim()
            if (name.isEmpty()) { toast("Please enter your name"); return@setOnClickListener }
            prefs.name = name
            prefs.code = Prefs.newCode()
            prefs.role = "sharer"
            go(SharerActivity::class.java)
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val code = Prefs.normalize(findViewById<EditText>(R.id.inputCode).text.toString())
            val name = findViewById<EditText>(R.id.inputTheirName).text.toString().trim()
            if (code.length != 4) { toast("Family code has 4 digits, like 4821"); return@setOnClickListener }
            prefs.code = code
            prefs.name = name.ifEmpty { "Family" }
            prefs.role = "viewer"
            go(ViewerActivity::class.java)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun go(c: Class<*>) {
        startActivity(Intent(this, c))
        finish()
    }
}
