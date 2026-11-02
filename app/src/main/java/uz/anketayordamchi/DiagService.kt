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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * DIAG (diagnostika) + AVTO (javob kiritish).
 * "Keyingisi", "Orqaga", "Yakunlash", GPS va kamera tugmalariga HECH QACHON tegmaydi.
 * Panel: odatda kichik (mini) holatda; klaviatura ochilganda butunlay yashiriladi.
 */
class DiagService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var wm: WindowManager? = null
    private var button: TextView? = null

    // mini panel (kichik, surib yuriladigan)
    private var mini: LinearLayout? = null
    private var miniLabel: TextView? = null
    private var miniRun: TextView? = null

    // to'liq panel (faqat so'ralganda yoki natija chiqqanda)
    private var panel: LinearLayout? = null
    private var panelText: TextView? = null
    private var applyBtn: Button? = null
    private var staffBtn1: Button? = null
    private var staffBtn2: Button? = null
    private var confirmBtn: Button? = null
    private var houseRow: LinearLayout? = null

    private val main = Handler(Looper.getMainLooper())
    private val engineState = EngineState()
    private val driver by lazy { AccDriver(this) }

    @Volatile private var stopFlag = false
    @Volatile private var running = false
    @Volatile private var imeVisible = false
    private var fullOpen = false
    private var lastText = ""
    private var lastCanApply = false
    private var panelPage = -1
    private var lastPage = -1
    private var checkPending = false
    private var dialogCount = 0
    private var dialogLogged = 0
    private var lastDialogLog = ""

    // ---- AccDriver uchun ----
    fun targetPackage(): String = prefs.getString("target", "") ?: ""
    fun isStopped(): Boolean = stopFlag
    fun logLine(s: String) = AppLog.write(this, s)

    /** (x, y) nuqta bizning panel, mini panel yoki DIAG tugmasi ustiga tushadimi. */
    fun overlayContains(x: Int, y: Int): Boolean {
        for (v in listOf<View?>(panel, mini, button)) {
            if (v == null || v.visibility != View.VISIBLE) continue
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
        AppLog.write(this, "Xizmat ulandi (Anketa Yordamchi 1.1.0)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val type = event.eventType
            if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                checkKeyboard()
                return
            }
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) return
            val pkg = event.packageName?.toString() ?: return
            if (pkg.contains("inputmethod") || pkg.contains("keyboard")) {
                if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) checkKeyboard()
                return
            }
            if (pkg == packageName || pkg == "com.android.systemui") return
            val target = targetPackage()
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                remember(pkg)
                if (target.isNotEmpty() && pkg == target) {
                    showButton()
                } else {
                    hideButton()
                    hideAll()
                    panelPage = -1
                    lastPage = -1
                }
            }
            if (target.isNotEmpty() && pkg == target) checkKeyboard()
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
        hideAll()
        super.onDestroy()
    }

    private fun remember(pkg: String) {
        val list = (prefs.getString("recent", "") ?: "").split(",")
            .filter { it.isNotBlank() && it != pkg }.toMutableList()
        list.add(0, pkg)
        prefs.edit().putString("recent", list.take(8).joinToString(",")).apply()
    }

    // ---------------- sozlamalar ----------------

    private fun houseNext(): Int? = (prefs.getString("house_next", "") ?: "").trim().toIntOrNull()

    private fun settings(): AutoSettings {
        val hn = houseNext()
        return AutoSettings(
            prefs.getString("comment", "yaxshi") ?: "",
            prefs.getString("mahalla", "Ma'rifat") ?: "",
            prefs.getString("kocha", "I. Karimov") ?: "",
            prefs.getString("mahalla_key", "fat") ?: "",
            prefs.getString("kocha_key", "") ?: "",
            hn ?: -1,
            { if (hn != null) prefs.edit().putString("house_next", (hn + 1).toString()).apply() }
        )
    }

    private fun adjustHouse(d: Int) {
        val cur = houseNext() ?: return
        val nv = maxOf(0, cur + d)
        prefs.edit().putString("house_next", nv.toString()).apply()
        if (panelPage >= 1) showPlan(panelPage)
    }

    // ---------------- klaviatura: ochilganda panellar yashiriladi ----------------

    private fun checkKeyboard() {
        var ime = false
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) { ime = true; break }
            }
        } catch (_: Exception) {
        }
        if (ime != imeVisible) {
            imeVisible = ime
            main.post { applyVisibility() }
        }
    }

    private fun applyVisibility() {
        val v = if (imeVisible) View.GONE else View.VISIBLE
        button?.visibility = v
        mini?.visibility = v
        panel?.visibility = v
    }

    // ---------------- DIAG tugmasi ----------------

    private fun showButton() {
        main.post {
            if (button != null) return@post
            val b = TextView(this).apply {
                text = "DIAG"
                setTextColor(Color.WHITE)
                setBackgroundColor(0x991565C0.toInt())
                setPadding(20, 14, 20, 14)
                textSize = 11f
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
                applyVisibility()
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

    // ---------------- mini panel ----------------

    private fun small(b: Button) {
        b.textSize = 11f
        b.isAllCaps = false
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(10, 10, 10, 10)
    }

    private fun savePos(lp: WindowManager.LayoutParams) {
        prefs.edit().putInt("mini_x", lp.x).putInt("mini_y", lp.y).apply()
    }

    private fun makeDraggable(handle: View, target: View, lp: WindowManager.LayoutParams, onTap: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!moved && (abs(dx) > 12f || abs(dy) > 12f)) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { wm?.updateViewLayout(target, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap() else savePos(lp)
                    true
                }
                else -> true
            }
        }
    }

    private fun ensureMini() {
        if (mini != null) return
        val dm = resources.displayMetrics
        val dp = dm.density
        val sz = (46 * dp).toInt()
        val pd = (4 * dp).toInt()

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(0xB0212121.toInt())
        box.setPadding(pd, pd, pd, pd)

        val handle = TextView(this)
        handle.setTextColor(Color.WHITE)
        handle.textSize = 12f
        handle.gravity = Gravity.CENTER
        handle.setBackgroundColor(0xFF1565C0.toInt())
        box.addView(handle, LinearLayout.LayoutParams(sz, sz))

        val run = TextView(this)
        run.text = "\u25B6"
        run.textSize = 18f
        run.setTextColor(Color.WHITE)
        run.gravity = Gravity.CENTER
        run.setBackgroundColor(0xFF2E7D32.toInt())
        run.setOnClickListener {
            val pg = panelPage
            if (pg >= 1) startTask(pg, "kiritilmoqda...") { Engine(driver, settings(), engineState).apply(pg) }
        }
        val rlp = LinearLayout.LayoutParams(sz, sz)
        rlp.topMargin = pd
        box.addView(run, rlp)

        val stop = TextView(this)
        stop.text = "\u25A0"
        stop.textSize = 18f
        stop.setTextColor(Color.WHITE)
        stop.gravity = Gravity.CENTER
        stop.setBackgroundColor(0xFFC62828.toInt())
        stop.setOnClickListener { emergencyStop() }
        val slp = LinearLayout.LayoutParams(sz, sz)
        slp.topMargin = pd
        box.addView(stop, slp)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val defX = dm.widthPixels - sz - 3 * pd
        val defY = (dm.heightPixels * 0.30).toInt()
        lp.x = prefs.getInt("mini_x", defX).coerceIn(0, maxOf(0, dm.widthPixels - sz))
        lp.y = prefs.getInt("mini_y", defY).coerceIn(0, maxOf(0, dm.heightPixels - 3 * sz))
        makeDraggable(handle, box, lp) { toggleFull() }
        wm?.addView(box, lp)
        mini = box
        miniLabel = handle
        miniRun = run
    }

    private fun updateMini() {
        miniLabel?.text = if (running) "..." else if (panelPage >= 1) "$panelPage/6" else "AY"
        miniRun?.visibility = if (lastCanApply && !running) View.VISIBLE else View.GONE
    }

    private fun toggleFull() {
        if (fullOpen) closeFull() else {
            ensureFull()
            panelText?.text = lastText
            applyBtn?.visibility = if (lastCanApply) View.VISIBLE else View.GONE
            applyPageButtons()
            applyVisibility()
        }
    }

    // ---------------- to'liq panel ----------------

    private fun ensureFull() {
        if (panel != null) return
        val dm = resources.displayMetrics
        val dp = dm.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(0xEE212121.toInt())
        val p = (6 * dp).toInt()
        box.setPadding(p, p, p, p)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        val title = TextView(this)
        title.text = "Anketa Yordamchi"
        title.setTextColor(Color.WHITE)
        title.textSize = 12f
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val minBtn = Button(this)
        minBtn.text = "KICHRAYTIRISH"
        small(minBtn)
        minBtn.setOnClickListener { closeFull() }
        head.addView(minBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(head)

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        val sv = ScrollView(this)
        sv.addView(tv)
        box.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.28).toInt()))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val ok = Button(this)
        ok.text = "KIRITISH"
        ok.setOnClickListener { val pg = panelPage; startTask(pg, "kiritilmoqda...") { Engine(driver, settings(), engineState).apply(pg) } }
        val stop = Button(this)
        stop.text = "STOP"
        stop.setBackgroundColor(0xFFC62828.toInt())
        stop.setTextColor(Color.WHITE)
        stop.setOnClickListener { emergencyStop() }
        for (b in listOf(ok, stop)) {
            small(b)
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
        cf.text = "TASDIQLASH"
        cf.setOnClickListener { val pg = panelPage; startTask(pg, "tasdiqlanmoqda...") { Engine(driver, settings(), engineState).confirm(pg) } }
        for (b in listOf(s1, s2, cf)) {
            small(b)
            b.visibility = View.GONE
            row2.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row2)

        val hr = LinearLayout(this)
        hr.orientation = LinearLayout.HORIZONTAL
        val hm = Button(this)
        hm.text = "Uy raqami -1"
        hm.setOnClickListener { adjustHouse(-1) }
        val hp = Button(this)
        hp.text = "Uy raqami +1"
        hp.setOnClickListener { adjustHouse(1) }
        for (b in listOf(hm, hp)) {
            small(b)
            hr.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        hr.visibility = View.GONE
        box.addView(hr)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        val dg = Button(this)
        dg.text = "TASHXIS (hech narsa o'zgarmaydi)"
        small(dg)
        dg.setOnClickListener { val pg = panelPage; startTask(pg, "tashxis...") { traceRun(pg) } }
        row3.addView(dg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row3)

        staffBtn1 = s1
        staffBtn2 = s2
        confirmBtn = cf
        houseRow = hr

        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.94).toInt(),
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
        fullOpen = true
    }

    private fun closeFull() {
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel = null
        panelText = null
        applyBtn = null
        staffBtn1 = null
        staffBtn2 = null
        confirmBtn = null
        houseRow = null
        fullOpen = false
    }

    private fun applyPageButtons() {
        val n = panelPage
        staffBtn1?.visibility = if (n == 4) View.VISIBLE else View.GONE
        staffBtn2?.visibility = if (n == 4) View.VISIBLE else View.GONE
        confirmBtn?.visibility = if (n in Rules.confirmPages) View.VISIBLE else View.GONE
        houseRow?.visibility = if (n == 1 && houseNext() != null) View.VISIBLE else View.GONE
    }

    private fun hideAll() {
        main.post {
            closeFull()
            mini?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
            mini = null
            miniLabel = null
            miniRun = null
        }
    }

    /** open=true: to'liq panelni ochadi (natija); aks holda faqat mini panel (to'liq panel ochiq bo'lsa, uning matni yangilanadi). */
    private fun setState(text: String, canApply: Boolean, open: Boolean) {
        lastText = text
        lastCanApply = canApply
        main.post {
            try {
                ensureMini()
                if (open && !fullOpen) ensureFull()
                panelText?.text = lastText
                applyBtn?.visibility = if (canApply) View.VISIBLE else View.GONE
                applyPageButtons()
                updateMini()
                applyVisibility()
            } catch (e: Exception) {
                AppLog.write(this, "panel xato: $e")
            }
        }
    }

    private fun emergencyStop() {
        stopFlag = true
        prefs.edit().putBoolean("auto_on", false).apply()
        panelPage = -1
        lastPage = -1
        hideAll()
        AppLog.write(this, "FAVQULODDA TO'XTATISH (STOP)")
        toast("AVTO o'chirildi")
    }

    // ---------------- sahifani aniqlash ----------------

    /** Tasdiq oynasi chiqqan bo'lsa tasdiqlaydi (faqat 2/6 va 3/6 sahifalardan keyin). true = shu urinishda oyna bilan ishlandi. */
    private fun handleDialog(s: Snapshot): Boolean {
        running = true
        try {
            val r = Engine(driver, settings(), engineState).confirmDialog(s)
            if (r.lines.isEmpty()) return false
            val line = r.lines[0]
            if (line.startsWith("OK")) {
                dialogCount++
                AppLog.event(this, lastPage, line)
                toast("Avto-tasdiq: oyna tasdiqlandi")
                return true
            }
            if (line != lastDialogLog && dialogLogged < 3) {
                lastDialogLog = line
                dialogLogged++
                AppLog.event(this, lastPage, line)
            }
            return line.startsWith("!")
        } catch (e: Exception) {
            AppLog.write(this, "oyna tasdiqlash xato: $e")
            return false
        } finally {
            running = false
        }
    }

    private fun checkPage() {
        try {
            if (!prefs.getBoolean("auto_on", false) || running) return
            val s = driver.snapshot() ?: return
            val n = s.page()
            val autoConfirm = prefs.getBoolean("auto_confirm", true)

            if (autoConfirm && (n ?: lastPage) in 2..3 && dialogCount < 8) {
                if (handleDialog(s)) return
            }
            if (n == null) return
            lastPage = n
            if (n != panelPage) {
                panelPage = n
                engineState.pressed.clear()
                dialogCount = 0
                dialogLogged = 0
                showPlan(n)
            }
            if (autoConfirm && n in Rules.confirmPages) {
                val eng = Engine(driver, settings(), engineState)
                if (eng.hasNewConfirm(s, n)) {
                    startTask(n, "avto-tasdiq...", quiet = true) { eng.confirm(n, false) }
                }
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
        val hn = houseNext()
        if (n == 1 && hn != null) sb.append("Keyingi uy raqami: ").append(hn).append('\n')
        sb.append("Qo'lda: ").append(Rules.manual(n))
        setState(sb.toString(), rules.isNotEmpty(), fullOpen)
    }

    // ---------------- bajarish ----------------

    private fun startTask(n: Int, title: String, quiet: Boolean = false, body: () -> RunResult) {
        if (running || n < 1) return
        stopFlag = false
        running = true
        if (quiet) main.post { updateMini() } else setState("$n/6: $title (to'xtatish: STOP)", false, fullOpen)
        Thread {
            try {
                finish(n, body(), quiet)
            } catch (e: Exception) {
                AppLog.write(this, "task xato: $e")
                setState("Xato: log.txt ga yozildi (${e.javaClass.simpleName})", false, true)
            } finally {
                running = false
                main.post { updateMini() }
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

    private fun finish(n: Int, r: RunResult, quiet: Boolean) {
        if (r.note != null) AppLog.event(this, n, "! to'xtash sababi: ${r.note}")
        for (l in r.lines) AppLog.event(this, n, l)
        val bad = r.note != null || r.lines.any { it.startsWith("!") }
        if (quiet && !bad) {
            val ok = r.lines.count { it.startsWith("OK") }
            if (ok > 0) toast("Avto-tasdiq: $ok ta")
            return
        }
        val sb = StringBuilder("$n/6 natija:\n")
        if (r.note != null) sb.append(r.note).append('\n')
        val shown = r.lines.take(14)
        for (l in shown) sb.append(l).append('\n')
        if (r.lines.size > shown.size) sb.append("... yana ${r.lines.size - shown.size} qator (log.txt da)\n")
        sb.append("Qo'lda: ").append(Rules.manual(n))
        // Panel tugmalari saqlanib qoladi: sahifa o'zgarmaguncha qayta bosish mumkin.
        setState(sb.toString(), Rules.forPage(n, settings()).isNotEmpty(), true)
    }
}
