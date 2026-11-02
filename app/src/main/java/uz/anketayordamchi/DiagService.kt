package uz.anketayordamchi

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DIAG (diagnostika) + AVTO (javob kiritish).
 * "Keyingisi", "Orqaga", "Yakunlash", GPS va kamera tugmalariga HECH QACHON tegmaydi.
 * Faqat foydalanuvchi panelda tugma bosgandagina ishlaydi; STOP istalgan paytda to'xtatadi.
 */
class DiagService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var wm: WindowManager? = null
    private var button: TextView? = null
    private var panel: LinearLayout? = null
    private var panelText: TextView? = null
    private var applyBtn: Button? = null
    private var staffBtn1: Button? = null
    private var staffBtn2: Button? = null
    private var confirmBtn: Button? = null
    private val main = Handler(Looper.getMainLooper())
    private val engineState = EngineState()
    private val driver by lazy { AccDriver(this) }

    @Volatile private var stopFlag = false
    @Volatile private var running = false
    private var panelPage = -1
    private var checkPending = false

    // ---- AccDriver uchun ----
    fun targetPackage(): String = prefs.getString("target", "") ?: ""
    fun isStopped(): Boolean = stopFlag
    fun logLine(s: String) = AppLog.write(this, s)

    /** (x, y) nuqta bizning panel yoki DIAG tugmasi ustiga tushadimi. */
    fun overlayContains(x: Int, y: Int): Boolean {
        for (v in listOf<View?>(panel, button)) {
            if (v == null) continue
            try {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                if (x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("ay", Context.MODE_PRIVATE)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AppLog.write(this, "Xizmat ulandi (Anketa Yordamchi 1.0.0)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val type = event.eventType
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) return
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName || pkg == "com.android.systemui" ||
                pkg.contains("inputmethod") || pkg.contains("keyboard")
            ) return
            val target = targetPackage()
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                remember(pkg)
                if (target.isNotEmpty() && pkg == target) {
                    showButton()
                } else {
                    hideButton()
                    hidePanel()
                    panelPage = -1
                }
            }
            if (target.isNotEmpty() && pkg == target &&
                prefs.getBoolean("auto_on", false) && !checkPending && !running
            ) {
                checkPending = true
                main.postDelayed({
                    checkPending = false
                    Thread { checkPage() }.start()
                }, 700)
            }
        } catch (e: Exception) {
            AppLog.write(this, "onAccessibilityEvent xato: $e")
        }
    }

    override fun onInterrupt() {
        stopFlag = true
    }

    override fun onDestroy() {
        stopFlag = true
        hideButton()
        hidePanel()
        super.onDestroy()
    }

    private fun remember(pkg: String) {
        val list = (prefs.getString("recent", "") ?: "").split(",")
            .filter { it.isNotBlank() && it != pkg }.toMutableList()
        list.add(0, pkg)
        prefs.edit().putString("recent", list.take(8).joinToString(",")).apply()
    }

    private fun settings(): AutoSettings = AutoSettings(
        prefs.getString("comment", "yaxshi") ?: "",
        prefs.getString("mahalla", "Ma'rifat") ?: "",
        prefs.getString("kocha", "I. Karimov") ?: ""
    )

    // ---------------- DIAG tugmasi ----------------

    private fun showButton() {
        main.post {
            if (button != null) return@post
            val b = TextView(this).apply {
                text = "DIAG"
                setTextColor(Color.WHITE)
                setBackgroundColor(0xCC1565C0.toInt())
                setPadding(32, 24, 32, 24)
                textSize = 14f
                setOnClickListener { Thread { dump() }.start() }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.END or Gravity.BOTTOM
            lp.y = 260
            try {
                wm?.addView(b, lp)
                button = b
            } catch (e: Exception) {
                AppLog.write(this, "Tugma qo'shilmadi: $e")
            }
        }
    }

    private fun hideButton() {
        main.post {
            button?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
            button = null
        }
    }

    private fun toast(t: String) = main.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }

    /** Xom dump (Census ning barcha oynalari). JShShIR/telefon/koordinatalar va kiritish maydonlari maskalanadi. */
    private fun dump() {
        try {
            val target = targetPackage()
            val roots = ArrayList<AccessibilityNodeInfo>()
            try {
                for (w in windows.reversed()) {
                    val r = w.root ?: continue
                    if (r.packageName?.toString() == target) roots.add(r)
                }
            } catch (_: Exception) {
            }
            if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }
            if (roots.isEmpty()) {
                toast("Ekran o'qilmadi")
                return
            }
            val sb = StringBuilder()
            sb.append("Paket: ").append(roots[0].packageName).append('\n')
            sb.append("Vaqt: ").append(Date()).append('\n')
            sb.append("Oynalar: ").append(roots.size).append("\n\n")
            for ((wi, root) in roots.withIndex()) {
                sb.append("=== OYNA ").append(wi + 1).append(" ===\n")
                walk(root, 0, sb)
            }
            val name = "dump_" + SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
            File(getExternalFilesDir(null) ?: filesDir, name).writeText(sb.toString())
            toast("Saqlandi: $name")
        } catch (e: Exception) {
            AppLog.write(this, "dump xato: $e")
            toast("Xato: log.txt ga yozildi")
        }
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        if (depth > 40) return
        val masked = n.isEditable || n.isPassword
        val raw = n.text?.toString() ?: ""
        val text = if (masked && raw.isNotEmpty()) "<maskalangan:${raw.length} belgi>" else mask(raw)
        val r = Rect()
        n.getBoundsInScreen(r)
        sb.append("  ".repeat(depth))
            .append(n.className?.toString()?.substringAfterLast('.') ?: "?")
            .append(" id=").append(n.viewIdResourceName ?: "-")
            .append(" text=\"").append(text.take(120).replace("\n", " ")).append('"')
            .append(" desc=\"").append(mask(n.contentDescription?.toString() ?: "").take(120).replace("\n", " ")).append('"')
            .append(" hint=\"").append((n.hintText?.toString() ?: "").take(80)).append('"')
            .append(" state=\"").append(if (android.os.Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString() ?: "" else "").append('"')
            .append(" click=").append(n.isClickable)
            .append(" edit=").append(n.isEditable)
            .append(" checkable=").append(n.isCheckable)
            .append(" checked=").append(n.isChecked)
            .append(" selected=").append(n.isSelected)
            .append(" enabled=").append(n.isEnabled)
            .append(" visible=").append(n.isVisibleToUser)
            .append(" scrollable=").append(n.isScrollable)
            .append(" bounds=").append(r.toShortString())
            .append('\n')
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { walk(it, depth + 1, sb) }
        }
    }

    // ---------------- panel ----------------

    private fun buildPanel() {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(0xEE212121.toInt())
        val p = (8 * dp).toInt()
        box.setPadding(p, p, p, p)

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        box.addView(tv)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val ok = Button(this)
        ok.text = "KIRITISH"
        ok.setOnClickListener { val pg = panelPage; startTask(pg, "kiritilmoqda...") { Engine(driver, settings(), engineState).apply(pg) } }
        val close = Button(this)
        close.text = "YOPISH"
        close.setOnClickListener { hidePanel() }
        val stop = Button(this)
        stop.text = "STOP"
        stop.setBackgroundColor(0xFFC62828.toInt())
        stop.setTextColor(Color.WHITE)
        stop.setOnClickListener { emergencyStop() }
        for (b in listOf(ok, close, stop)) {
            b.textSize = 12f
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val s1 = Button(this)
        s1.text = "1 xodim, ayol 0"
        s1.setOnClickListener { val pg = panelPage; startTask(pg, "xodimlar soni kiritilmoqda...") { Engine(driver, settings(), engineState).staff(pg, 0) } }
        val s2 = Button(this)
        s2.text = "1 xodim, ayol 1"
        s2.setOnClickListener { val pg = panelPage; startTask(pg, "xodimlar soni kiritilmoqda...") { Engine(driver, settings(), engineState).staff(pg, 1) } }
        val cf = Button(this)
        cf.text = "TASDIQLASH (tekshirdim)"
        cf.setOnClickListener { val pg = panelPage; startTask(pg, "tasdiqlanmoqda...") { Engine(driver, settings(), engineState).confirm(pg) } }
        for (b in listOf(s1, s2, cf)) {
            b.textSize = 11f
            b.visibility = View.GONE
            row2.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row2)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        val dg = Button(this)
        dg.text = "TASHXIS (hech narsa o'zgarmaydi)"
        dg.textSize = 11f
        dg.setOnClickListener { val pg = panelPage; startTask(pg, "tashxis...") { traceRun(pg) } }
        row3.addView(dg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row3)

        staffBtn1 = s1
        staffBtn2 = s2
        confirmBtn = cf

        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.96).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (24 * dp).toInt()
        wm?.addView(box, lp)
        panel = box
        panelText = tv
        applyBtn = ok
    }

    private fun showPanel(text: String, canApply: Boolean) {
        main.post {
            try {
                if (panel == null) buildPanel()
                panelText?.text = text
                applyBtn?.visibility = if (canApply) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                AppLog.write(this, "panel xato: $e")
            }
        }
    }

    private fun hidePanel() {
        main.post {
            panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
            panel = null
            panelText = null
            applyBtn = null
            staffBtn1 = null
            staffBtn2 = null
            confirmBtn = null
        }
    }

    private fun emergencyStop() {
        stopFlag = true
        prefs.edit().putBoolean("auto_on", false).apply()
        panelPage = -1
        hidePanel()
        AppLog.write(this, "FAVQULODDA TO'XTATISH (STOP)")
        toast("AVTO o'chirildi")
    }

    // ---------------- sahifani aniqlash ----------------

    private fun checkPage() {
        try {
            if (!prefs.getBoolean("auto_on", false) || running) return
            val s = driver.snapshot() ?: return
            val n = s.page() ?: return
            if (n != panelPage) {
                panelPage = n
                showPlan(n)
            }
        } catch (e: Exception) {
            AppLog.write(this, "checkPage xato: $e")
        }
    }

    private fun showPlan(n: Int) {
        val rules = Rules.forPage(n, settings())
        val sb = StringBuilder("$n/6 sahifa\n")
        if (rules.isEmpty()) {
            sb.append("Avtomatik kiritiladigan javob yo'q.\n")
        } else {
            sb.append("Kiritiladi / tekshiriladi:\n")
            for (r in rules) sb.append("- ").append(r.label).append('\n')
        }
        sb.append("Qo'lda: ").append(Rules.manual(n))
        showPanel(sb.toString(), rules.isNotEmpty())
        main.post {
            staffBtn1?.visibility = if (n == 4) View.VISIBLE else View.GONE
            staffBtn2?.visibility = if (n == 4) View.VISIBLE else View.GONE
            confirmBtn?.visibility = if (n in Rules.confirmPages) View.VISIBLE else View.GONE
        }
    }

    // ---------------- bajarish ----------------

    private fun startTask(n: Int, title: String, body: () -> RunResult) {
        if (running || n < 1) return
        stopFlag = false
        running = true
        showPanel("$n/6: $title (to'xtatish: STOP)", false)
        Thread {
            try {
                finish(n, body())
            } catch (e: Exception) {
                AppLog.write(this, "task xato: $e")
                showPanel("Xato: log.txt ga yozildi (${e.javaClass.simpleName})", false)
            } finally {
                running = false
            }
        }.start()
    }

    private fun traceRun(n: Int): RunResult {
        val r = Engine(driver, settings(), engineState).explain(n)
        val name = "dump_trace_" + SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        try {
            File(getExternalFilesDir(null) ?: filesDir, name)
                .writeText("Sahifa: $n/6\n" + r.lines.joinToString("\n") { mask(it) } + "\n" + (r.note ?: ""))
        } catch (e: Exception) {
            AppLog.write(this, "trace yozilmadi: $e")
        }
        return RunResult(listOf("- Tashxis saqlandi: $name (ilovada 'Oxirgi dump')") + r.lines.take(6), r.note)
    }

    private fun finish(n: Int, r: RunResult) {
        val sb = StringBuilder("$n/6 natija:\n")
        if (r.note != null) sb.append(r.note).append('\n')
        if (r.note != null) AppLog.event(this, n, "! to'xtash sababi: ${r.note}")
        for (l in r.lines) AppLog.event(this, n, l)
        val shown = r.lines.take(14)
        for (l in shown) sb.append(l).append('\n')
        if (r.lines.size > shown.size) sb.append("... yana ${r.lines.size - shown.size} qator (log.txt da)\n")
        sb.append("Qo'lda: ").append(Rules.manual(n))
        showPanel(sb.toString(), false)
        // Panel tugmalari saqlanib qoladi: sahifa o'zgarmaguncha qayta bosish mumkin.
        main.post { applyBtn?.visibility = View.VISIBLE }
    }
}
