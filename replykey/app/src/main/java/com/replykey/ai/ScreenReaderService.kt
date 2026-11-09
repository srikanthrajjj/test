package com.replykey.ai

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Reads visible text of the app behind the keyboard. A keyboard alone can only see its own
 * text field, so this service supplies the message being replied to.
 *
 * It also keeps the last distinct screen per app in memory, so when you open an email and tap
 * Reply, the email you were just reading is still available as context.
 * Nothing is stored on disk; nothing is sent anywhere until the AI key is tapped.
 */
class ScreenReaderService : AccessibilityService() {

    private val lastSnapshot = HashMap<String, String>()     // pkg -> latest screen text
    private val previousSnapshot = HashMap<String, String>() // pkg -> the distinct screen before that
    private var lastCaptureAt = 0L

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val now = SystemClock.uptimeMillis()
        if (now - lastCaptureAt < 700) return
        lastCaptureAt = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val text = collect(root)
        if (text.length < 20) return
        val last = lastSnapshot[pkg]
        if (last != null && last != text && !similar(last, text)) previousSnapshot[pkg] = last
        lastSnapshot[pkg] = text
    }

    /** Text of the app window under the keyboard right now. */
    fun captureNow(): Pair<String, String> {
        var best: AccessibilityNodeInfo? = null
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == packageName) continue
                if (best == null || w.isActive || w.isFocused) best = r
            }
        }
        val root = best ?: rootInActiveWindow ?: return "" to ""
        val pkg = root.packageName?.toString() ?: ""
        return pkg to collect(root)
    }

    fun earlierFor(pkg: String, current: String): String {
        val candidates = listOfNotNull(lastSnapshot[pkg], previousSnapshot[pkg])
        return candidates.firstOrNull { it != current && !similar(it, current) } ?: ""
    }

    private fun collect(root: AccessibilityNodeInfo): String {
        val seen = LinkedHashSet<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 60 || seen.size > 400) return
            if (n.isPassword) return
            if (n.isVisibleToUser && !n.isEditable) {
                val t = n.text?.toString()?.trim().orEmpty()
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) seen.add(t)
                else if (d.length > 3 && d.contains(' ')) seen.add(d)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val joined = seen.joinToString("\n")
        return if (joined.length > MAX_CHARS) joined.takeLast(MAX_CHARS) else joined
    }

    private fun similar(a: String, b: String): Boolean {
        val la = a.lines().toSet()
        val lb = b.lines().toSet()
        val inter = la.intersect(lb).size.toDouble()
        return inter / maxOf(1, minOf(la.size, lb.size)) > 0.85
    }

    companion object {
        const val MAX_CHARS = 8000
        @Volatile var instance: ScreenReaderService? = null
    }
}
