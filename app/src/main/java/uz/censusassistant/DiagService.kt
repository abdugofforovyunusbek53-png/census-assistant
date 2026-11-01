package uz.censusassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1-bosqich: DIAGNOSTIKA. Hech narsani bosmaydi va kiritmaydi.
 * Tanlangan (target) ilova ochiq bo'lganda ekran chetida "DIAG" tugmasi chiqadi.
 * Bosilganda ekrandagi elementlar (matn, id, turi, holati) lokal faylga yoziladi.
 * Kiritish maydonlari (editable/parol) matni maskalanadi.
 */
class DiagService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var wm: WindowManager? = null
    private var button: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("ca", Context.MODE_PRIVATE)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AppLog.write(this, "Xizmat ulandi")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName || pkg == "com.android.systemui" ||
                pkg.contains("inputmethod") || pkg.contains("keyboard")
            ) return
            remember(pkg)
            val target = prefs.getString("target", "") ?: ""
            if (target.isNotEmpty() && pkg == target) showButton() else hideButton()
        } catch (e: Exception) {
            AppLog.write(this, "onAccessibilityEvent xato: $e")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideButton()
        super.onDestroy()
    }

    /** Oxirgi 8 ta ochilgan ilova paketi nomi (faqat nomlar, lokal) — Census paketini topish uchun. */
    private fun remember(pkg: String) {
        val list = (prefs.getString("recent", "") ?: "").split(",")
            .filter { it.isNotBlank() && it != pkg }.toMutableList()
        list.add(0, pkg)
        prefs.edit().putString("recent", list.take(8).joinToString(",")).apply()
    }

    private fun showButton() {
        if (button != null) return
        val b = TextView(this).apply {
            text = "DIAG"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1565C0.toInt())
            setPadding(32, 24, 32, 24)
            textSize = 14f
            setOnClickListener { dump() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        try {
            wm?.addView(b, lp)
            button = b
        } catch (e: Exception) {
            AppLog.write(this, "Tugma qo'shilmadi: $e")
        }
    }

    private fun hideButton() {
        button?.let {
            try { wm?.removeView(it) } catch (_: Exception) {}
        }
        button = null
    }

    private fun dump() {
        try {
            val root = rootInActiveWindow
            if (root == null) {
                Toast.makeText(this, "Ekran o'qilmadi", Toast.LENGTH_SHORT).show()
                return
            }
            val sb = StringBuilder()
            sb.append("Paket: ").append(root.packageName).append('\n')
            sb.append("Vaqt: ").append(Date()).append("\n\n")
            walk(root, 0, sb)
            val name = "dump_" + SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
            File(getExternalFilesDir(null), name).writeText(sb.toString())
            Toast.makeText(this, "Saqlandi: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLog.write(this, "dump xato: $e")
            Toast.makeText(this, "Xato: log.txt ga yozildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        if (depth > 40) return
        val masked = n.isEditable || n.isPassword
        val raw = n.text?.toString() ?: ""
        val text = if (masked && raw.isNotEmpty()) "<maskalangan:${raw.length} belgi>" else raw
        val r = Rect()
        n.getBoundsInScreen(r)
        sb.append("  ".repeat(depth))
            .append(n.className?.toString()?.substringAfterLast('.') ?: "?")
            .append(" id=").append(n.viewIdResourceName ?: "-")
            .append(" text=\"").append(text.take(120).replace("\n", " ")).append('"')
            .append(" desc=\"").append((n.contentDescription?.toString() ?: "").take(120).replace("\n", " ")).append('"')
            .append(" hint=\"").append((n.hintText?.toString() ?: "").take(80)).append('"')
            .append(" click=").append(n.isClickable)
            .append(" edit=").append(n.isEditable)
            .append(" checkable=").append(n.isCheckable)
            .append(" checked=").append(n.isChecked)
            .append(" enabled=").append(n.isEnabled)
            .append(" bounds=").append(r.toShortString())
            .append('\n')
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { walk(it, depth + 1, sb) }
        }
    }
}
