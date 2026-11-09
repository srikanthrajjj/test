package com.replykey.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(28), px(20), px(28))
            setBackgroundColor(Color.parseColor("#1B1416"))
        }
        fun label(t: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, px(14), 0, px(6))
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }

        col.addView(label("✨ ReplyKey", 26f, true))
        col.addView(label("A keyboard with an AI key. Tap ✨ and it reads the message on screen and writes a reply at your cursor."))

        status = label("")
        col.addView(status)

        col.addView(label("1. Turn on the keyboard", 17f, true))
        col.addView(button("Open keyboard settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(button("Switch to ReplyKey") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        col.addView(label("2. Let it read the message", 17f, true))
        col.addView(label("Android keyboards can only see their own text box. To read the email or chat you're replying to, enable \"ReplyKey message reader\" under Accessibility → Installed apps.", 13f))
        col.addView(button("Open accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        col.addView(label("3. Claude API key", 17f, true))
        val key = EditText(this).apply {
            hint = "sk-ant-…"; setText(prefs.apiKey); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        col.addView(key)
        col.addView(label("Get one at console.anthropic.com. Stored only on this phone.", 12f))

        col.addView(label("Your name (for sign-offs)", 15f, true))
        val name = EditText(this).apply { setText(prefs.userName); setTextColor(Color.WHITE) }
        col.addView(name)

        col.addView(label("Model", 15f, true))
        val group = RadioGroup(this)
        Prefs.MODELS.forEachIndexed { i, (id, title) ->
            group.addView(RadioButton(this).apply {
                this.id = 1000 + i; text = title; setTextColor(Color.WHITE); isChecked = prefs.model == id
            })
        }
        col.addView(group)

        col.addView(button("Save") {
            prefs.apiKey = key.text.toString()
            prefs.userName = name.text.toString()
            val idx = group.checkedRadioButtonId - 1000
            if (idx in Prefs.MODELS.indices) prefs.model = Prefs.MODELS[idx].first
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            refresh()
        })

        col.addView(label("Privacy: screen text is kept in memory only and sent to Claude only when you tap ✨. Password fields are never read.", 12f))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B1416"))
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        col.gravity = Gravity.TOP
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val kb = imm.enabledInputMethodList.any { it.packageName == packageName }
        val reader = ScreenReaderService.instance != null
        val key = prefs.apiKey.isNotBlank()
        fun mark(ok: Boolean) = if (ok) "✅" else "⬜"
        status.text = "${mark(kb)} Keyboard enabled\n${mark(reader)} Message reader on\n${mark(key)} API key set"
    }
}
