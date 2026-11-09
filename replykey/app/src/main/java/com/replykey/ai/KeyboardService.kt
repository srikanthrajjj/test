package com.replykey.ai

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class KeyboardService : InputMethodService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs

    private lateinit var keysBox: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var replyText: TextView
    private lateinit var statusText: TextView
    private lateinit var aiButton: TextView

    private var symbols = false
    private var shift = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var draft = ""
    private var generation = 0
    private var lastStyle = STYLE_NATURAL

    private var density = 1f
    private fun px(v: Float) = (v * density).toInt()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        density = resources.displayMetrics.density
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(px(4f), px(6f), px(4f), px(8f))
        }
        root.addView(buildToolbar())
        panel = buildPanel()
        root.addView(panel)
        keysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysBox)
        renderKeys()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::keysBox.isInitialized) return
        symbols = false
        capsLock = false
        if (!restarting) hidePanel()
        updateAutoShift()
        renderKeys()
        val sensitive = isSensitiveField(info)
        aiButton.alpha = if (sensitive) 0.35f else 1f
        statusText.text = when {
            sensitive -> "AI off in password fields"
            prefs.apiKey.isBlank() -> "Tap ✨ to set up"
            ScreenReaderService.instance == null -> "Tip: turn on message reader"
            else -> "Tap ✨ to draft a reply"
        }
    }

    // ---------- toolbar + reply panel ----------

    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4f), 0, px(4f), px(6f))
        }
        aiButton = TextView(this).apply {
            text = "✨ AI reply"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(px(16f), px(8f), px(16f), px(8f))
            background = pill(ACCENT, ACCENT_PRESSED, 20f)
            contentDescription = "AI reply: read the message and draft a reply"
            setOnClickListener { haptic(it); onAiTapped(STYLE_NATURAL) }
        }
        bar.addView(aiButton)
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(MUTED)
            setPadding(px(12f), 0, px(8f), 0)
            maxLines = 1
        }
        bar.addView(statusText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val globe = TextView(this).apply {
            text = "🌐"
            textSize = 18f
            setPadding(px(10f), px(6f), px(10f), px(6f))
            contentDescription = "Switch keyboard"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        bar.addView(globe)
        return bar
    }

    private fun buildPanel(): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = round(PANEL, 14f)
            setPadding(px(12f), px(10f), px(12f), px(8f))
        }
        replyText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            maxHeight = px(150f)
            movementMethod = ScrollingMovementMethod()
            setLineSpacing(0f, 1.15f)
        }
        p.addView(replyText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(8f), 0, 0)
        }
        chips.addView(chip("Insert", primary = true) { insertDraft() })
        chips.addView(chip("↻ Retry") { onAiTapped(lastStyle) })
        chips.addView(chip("Shorter") { onAiTapped(STYLE_SHORT) })
        chips.addView(chip("Formal") { onAiTapped(STYLE_FORMAL) })
        chips.addView(chip("Friendly") { onAiTapped(STYLE_FRIENDLY) })
        chips.addView(chip("✕") { hidePanel() })
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        p.addView(scroller)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.setMargins(px(2f), 0, px(2f), px(8f))
        p.layoutParams = lp
        return p
    }

    private fun chip(label: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        if (primary) setTypeface(typeface, Typeface.BOLD)
        setPadding(px(14f), px(7f), px(14f), px(7f))
        background = if (primary) pill(ACCENT, ACCENT_PRESSED, 16f) else pill(KEY, KEY_PRESSED, 16f)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = px(8f) }
        setOnClickListener { haptic(it); onClick() }
    }

    private fun showPanel(text: String, ready: Boolean) {
        panel.visibility = View.VISIBLE
        replyText.text = text
        replyText.setTextColor(if (ready) Color.WHITE else MUTED)
        replyText.scrollTo(0, 0)
        if (panel.alpha < 1f) panel.animate().alpha(1f).setDuration(200).start()
    }

    private fun hidePanel() {
        generation++
        draft = ""
        if (::panel.isInitialized) {
            panel.visibility = View.GONE
            panel.alpha = 0f
        }
    }

    // ---------- AI ----------

    private fun isSensitiveField(info: EditorInfo?): Boolean {
        val t = info?.inputType ?: return false
        val variation = t and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun onAiTapped(style: String) {
        val info = currentInputEditorInfo
        if (isSensitiveField(info)) {
            statusText.text = "AI off in password fields"
            return
        }
        val apiKey = prefs.apiKey
        if (apiKey.isBlank()) {
            statusText.text = "Add your Claude API key"
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()

        val reader = ScreenReaderService.instance
        val (pkg, screen) = reader?.captureNow() ?: ((info?.packageName ?: "") to "")
        val earlier = reader?.earlierFor(pkg, screen).orEmpty()

        lastStyle = style
        val myGen = ++generation
        statusText.text = if (reader == null) "Reader off — using text box only" else "Reading message…"
        showPanel("Writing a reply…", ready = false)

        val req = ReplyRequest(
            appPackage = pkg.ifBlank { info?.packageName ?: "" },
            screenText = screen,
            earlierScreenText = earlier,
            textBeforeCursor = before,
            textAfterCursor = after,
            style = style,
            userName = prefs.userName,
        )
        val model = prefs.model
        worker.execute {
            val result = Claude.draft(apiKey, model, req)
            main.post {
                if (myGen != generation) return@post
                result.onSuccess {
                    draft = it
                    showPanel(it, ready = true)
                    statusText.text = "Tap Insert, or tweak it"
                }.onFailure {
                    draft = ""
                    showPanel(it.message ?: "Something went wrong. Check your connection.", ready = false)
                    statusText.text = "Couldn't draft"
                }
            }
        }
    }

    private fun insertDraft() {
        if (draft.isEmpty()) return
        currentInputConnection?.commitText(draft, 1)
        hidePanel()
        statusText.text = "Inserted"
    }

    // ---------- keys ----------

    private fun renderKeys() {
        keysBox.removeAllViews()
        val rows: List<List<String>> = if (!symbols) listOf(
            "1234567890".map { it.toString() },
            "qwertyuiop".map { it.toString() },
            listOf(HALF) + "asdfghjkl".map { it.toString() } + HALF,
            listOf(SHIFT) + "zxcvbnm".map { it.toString() } + DEL,
            listOf(SYM, ",", SPACE, ".", ENTER),
        ) else listOf(
            "1234567890".map { it.toString() },
            listOf("@", "#", "₹", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "%", "="),
            listOf("<", ">", "[", "]", "{", "}", "\\", "|", DEL),
            listOf(ABC, ",", SPACE, ".", ENTER),
        )
        rows.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { k -> line.addView(keyView(k)) }
            keysBox.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, px(54f)))
        }
    }

    private fun keyView(k: String): View {
        if (k == HALF) return View(this).apply { layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 0.5f) }
        val weight = when (k) {
            SPACE -> 4.4f
            SHIFT, DEL, SYM, ABC, ENTER -> 1.5f
            else -> 1f
        }
        val special = k in setOf(SHIFT, DEL, SYM, ABC)
        val label = when {
            k == SHIFT -> if (capsLock) "⇪" else "⇧"
            k == DEL -> "⌫"
            k == ENTER -> "↵"
            k == SPACE -> ""
            k.length == 1 && shift && !symbols -> k.uppercase()
            else -> k
        }
        val tv = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = if (k.length == 1) 22f else 20f
            background = when {
                k == ENTER -> pill(ENTER_BG, ENTER_PRESSED, 22f)
                k == SHIFT && shift -> pill(Color.parseColor("#6A7076"), KEY_PRESSED, 8f)
                special -> pill(KEY_SPECIAL, KEY_PRESSED, 8f)
                else -> pill(KEY, KEY_PRESSED, 8f)
            }
            isSoundEffectsEnabled = true
        }
        val lp = LinearLayout.LayoutParams(0, MATCH_PARENT, weight)
        lp.setMargins(px(3f), px(4f), px(3f), px(4f))
        tv.layoutParams = lp

        if (k == DEL) {
            tv.setOnTouchListener(RepeatListener { haptic(tv); deleteOne() })
        } else {
            tv.setOnClickListener { haptic(it); onKey(k) }
        }
        return tv
    }

    private fun onKey(k: String) {
        val ic = currentInputConnection ?: return
        when (k) {
            SHIFT -> {
                val now = System.currentTimeMillis()
                if (shift && now - lastShiftTap < 350) { capsLock = true } else if (shift) { shift = false; capsLock = false } else { shift = true }
                lastShiftTap = now
                renderKeys()
            }
            SYM -> { symbols = true; renderKeys() }
            ABC -> { symbols = false; renderKeys() }
            SPACE -> { ic.commitText(" ", 1); afterTyping() }
            ENTER -> { enter(); afterTyping() }
            else -> {
                val out = if (shift && !symbols) k.uppercase() else k
                ic.commitText(out, 1)
                if (shift && !capsLock) { shift = false; renderKeys() }
                if (k == "." || k == "?" || k == "!") afterTyping()
            }
        }
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val multiLine = ((ei?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (noAction || multiLine || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.commitText("\n", 1)
        } else {
            ic.performEditorAction(action)
        }
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) ic.commitText("", 1)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun afterTyping() {
        if (symbols || capsLock) return
        val was = shift
        updateAutoShift()
        if (was != shift) renderKeys()
    }

    private fun updateAutoShift() {
        if (capsLock) { shift = true; return }
        val ei = currentInputEditorInfo
        val caps = ei != null && ei.inputType != InputType.TYPE_NULL &&
            currentInputConnection?.getCursorCapsMode(ei.inputType)?.let { it != 0 } == true
        shift = caps
    }

    // ---------- visuals ----------

    private fun haptic(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun round(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radiusDp * density
    }

    private fun pill(color: Int, pressed: Int, radiusDp: Float) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), round(pressed, radiusDp))
        addState(intArrayOf(), round(color, radiusDp))
    }

    /** Delete key: repeat while held. */
    private inner class RepeatListener(val action: () -> Unit) : View.OnTouchListener {
        private val tick = object : Runnable {
            override fun run() { action(); main.postDelayed(this, 50) }
        }
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; action(); main.postDelayed(tick, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; main.removeCallbacks(tick) }
            }
            return true
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val SHIFT = "SHIFT"
        private const val DEL = "DEL"
        private const val SYM = "?123"
        private const val ABC = "ABC"
        private const val SPACE = "SPACE"
        private const val ENTER = "ENTER"
        private const val HALF = "HALF"

        const val STYLE_NATURAL = "natural and concise, matching the tone of the conversation"
        const val STYLE_SHORT = "very short — one or two sentences"
        const val STYLE_FORMAL = "formal and polished, suitable for work email"
        const val STYLE_FRIENDLY = "warm and friendly"

        private val BG = Color.parseColor("#202427")
        private val KEY = Color.parseColor("#464B50")
        private val KEY_SPECIAL = Color.parseColor("#363B3F")
        private val KEY_PRESSED = Color.parseColor("#5C6268")
        private val ENTER_BG = Color.parseColor("#4F8EF7")
        private val ENTER_PRESSED = Color.parseColor("#3B74D6")
        private val ACCENT = Color.parseColor("#8A5CF6")
        private val ACCENT_PRESSED = Color.parseColor("#7041E0")
        private val PANEL = Color.parseColor("#2E3337")
        private val MUTED = Color.parseColor("#A9B0B6")
    }
}
