package uz.censusassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
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

private val labelRe = Regex("^\\d+\\.\\d+[a-z]?(\\.\\d+)?\\s.*")
private val pageRe = Regex("^(\\d) / 6$")

internal class N(val info: AccessibilityNodeInfo) {
    val cls: String = info.className?.toString() ?: ""
    val desc: String = norm(info.contentDescription?.toString() ?: "")
    val text: String = norm(info.text?.toString() ?: "")
    val isOption: Boolean = cls.endsWith("RadioButton") || cls.endsWith("CheckBox")
    val isEdit: Boolean = cls.endsWith("EditText")
}

/**
 * DIAG (1-bosqich) + AVTO (2-bosqich).
 * AVTO faqat RadioButton/CheckBox tanlaydi va (ixtiyoriy) bo'sh izoh maydoniga matn yozadi.
 * "Keyingisi", "Tasdiqlash", "Orqaga", "Yakunlash" va GPS/kamera tugmalariga HECH QACHON tegmaydi.
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

    @Volatile private var stopFlag = false
    @Volatile private var running = false
    private var panelPage = -1
    private var checkPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("ca", Context.MODE_PRIVATE)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AppLog.write(this, "Xizmat ulandi")
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
            val target = prefs.getString("target", "") ?: ""
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
                prefs.getBoolean("auto_on", false) && !checkPending
            ) {
                checkPending = true
                main.postDelayed({
                    checkPending = false
                    checkPage()
                }, 700)
            }
        } catch (e: Exception) {
            AppLog.write(this, "onAccessibilityEvent xato: $e")
        }
    }

    override fun onInterrupt() {}

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

    // ---------------- DIAG tugmasi ----------------

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
        lp.gravity = Gravity.END or Gravity.BOTTOM
        lp.y = 260
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

    private fun mask(s: String): String =
        s.replace(Regex("\\d{9,}")) { "<raqam:${it.value.length}>" }
            .replace(Regex("\\d+\\.\\d{5,}"), "<koordinata>")

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
        val text = if (masked && raw.isNotEmpty()) "<maskalangan:${raw.length} belgi>" else mask(raw)
        val r = Rect()
        n.getBoundsInScreen(r)
        sb.append("  ".repeat(depth))
            .append(n.className?.toString()?.substringAfterLast('.') ?: "?")
            .append(" id=").append(n.viewIdResourceName ?: "-")
            .append(" text=\"").append(text.take(120).replace("\n", " ")).append('"')
            .append(" desc=\"").append(mask(n.contentDescription?.toString() ?: "").take(120).replace("\n", " ")).append('"')
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

    // ---------------- AVTO: panel ----------------

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
        ok.setOnClickListener { startApply(panelPage) }
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
        s1.setOnClickListener { val pg = panelPage; startTask(pg, "xodimlar soni kiritilmoqda...") { runStaff(pg, 0) } }
        val s2 = Button(this)
        s2.text = "1 xodim, ayol 1"
        s2.setOnClickListener { val pg = panelPage; startTask(pg, "xodimlar soni kiritilmoqda...") { runStaff(pg, 1) } }
        val cf = Button(this)
        cf.text = "TASDIQLASH (tekshirdim)"
        cf.setOnClickListener { val pg = panelPage; startTask(pg, "tasdiqlanmoqda...") { runConfirm(pg) } }
        for (b in listOf(s1, s2, cf)) {
            b.textSize = 11f
            b.visibility = View.GONE
            row2.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row2)
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
            panel?.let {
                try { wm?.removeView(it) } catch (_: Exception) {}
            }
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
        Toast.makeText(this, "AVTO o'chirildi", Toast.LENGTH_SHORT).show()
    }

    // ---------------- AVTO: sahifani aniqlash ----------------

    private fun flatten(root: AccessibilityNodeInfo): List<N> {
        val out = ArrayList<N>()
        fun go(n: AccessibilityNodeInfo, d: Int) {
            if (d > 40) return
            out.add(N(n))
            for (i in 0 until n.childCount) n.getChild(i)?.let { go(it, d + 1) }
        }
        go(root, 0)
        return out
    }

    private fun currentPage(nodes: List<N>): Int? =
        nodes.firstNotNullOfOrNull { pageRe.find(it.desc)?.groupValues?.get(1)?.toIntOrNull() }

    private fun checkPage() {
        try {
            if (!prefs.getBoolean("auto_on", false) || running) return
            val root = rootInActiveWindow ?: return
            val target = prefs.getString("target", "") ?: ""
            if (root.packageName?.toString() != target) return
            val n = currentPage(flatten(root)) ?: return
            if (n != panelPage) {
                panelPage = n
                showPlan(n)
            }
        } catch (e: Exception) {
            AppLog.write(this, "checkPage xato: $e")
        }
    }

    private fun showPlan(n: Int) {
        val rules = Rules.forPage(n, prefs.getString("comment", "") ?: "")
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
            confirmBtn?.visibility = if (n == 2 || n == 3) View.VISIBLE else View.GONE
        }
    }

    // ---------------- AVTO: bajarish ----------------

    private fun startApply(n: Int) {
        startTask(n, "kiritilmoqda...") { runApply(n) }
    }

    private fun startTask(n: Int, title: String, body: () -> Unit) {
        if (running || n < 1) return
        stopFlag = false
        running = true
        showPanel("$n/6: $title (to'xtatish: STOP)", false)
        Thread {
            try {
                body()
            } catch (e: Exception) {
                AppLog.write(this, "task xato: $e")
                showPanel("Xato: log.txt ga yozildi", false)
            } finally {
                running = false
            }
        }.start()
    }

    // ---- Xodimlar soni: foydalanuvchi bosgan tugma bo'yicha (1 kishi; ayollar 0 yoki 1) ----
    private fun fillAfter(nodes: List<N>, labelDesc: String, value: String, label: String): String {
        val li = nodes.indexOfFirst { !it.isEdit && !it.isOption && it.desc.lowercase() == labelDesc }
        if (li < 0) return "! $label: maydon topilmadi"
        var edit: N? = null
        for (i in li + 1 until minOf(nodes.size, li + 7)) {
            if (nodes[i].isEdit) { edit = nodes[i]; break }
        }
        if (edit == null) return "! $label: kiritish maydoni topilmadi"
        val empty = edit.info.text.isNullOrEmpty() || edit.info.isShowingHintText
        if (!empty) return "! $label: maydon bo'sh emas - tegilmadi"
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        if (!edit.info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "! $label: yozilmadi"
        Thread.sleep(300)
        edit.info.refresh()
        val now = edit.info.text?.toString() ?: ""
        return if (now == value) "OK $label" else "! $label: yozilganini tasdiqlab bo'lmadi - tekshir"
    }

    private fun runStaff(n: Int, women: Int) {
        val target = prefs.getString("target", "") ?: ""
        val report = ArrayList<String>()
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != target) {
            finish(n, report, "Census ilovasi oldingi planda emas - to'xtadi")
            return
        }
        val nodes = flatten(root)
        if (currentPage(nodes) != n) {
            finish(n, report, "Sahifa o'zgardi - to'xtadi")
            return
        }
        report.add(fillAfter(nodes, "jami", "1", "3.1 Jami -> 1"))
        report.add(fillAfter(nodes, "shundan, ayollar", women.toString(), "3.1 Ayollar -> $women"))
        finish(n, report, null)
    }

    // ---- "Avtomatik to'ldirildi" maydonlarini TASDIQLASH: faqat foydalanuvchi tugmani bosgandan keyin ----
    private fun runConfirm(n: Int) {
        val target = prefs.getString("target", "") ?: ""
        val report = ArrayList<String>()
        val pressedAt = HashSet<String>()
        var pressed = 0

        for (i in 0 until 15) {
            if (stopFlag) { finish(n, report, "STOP bosildi"); return }
            val r0 = rootInActiveWindow ?: break
            val sc = findScroll(r0) ?: break
            if (!sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
            Thread.sleep(350)
        }

        var step = 0
        while (step < 30 && pressed < 8) {
            step++
            if (stopFlag) { finish(n, report, "STOP bosildi"); return }
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != target) {
                finish(n, report, "Census ilovasi oldingi planda emas - to'xtadi")
                return
            }
            val nodes = flatten(root)
            if (currentPage(nodes) != n) {
                finish(n, report, "Sahifa o'zgardi - to'xtadi")
                return
            }
            var pressedNow = false
            for (i in nodes.indices) {
                val x = nodes[i]
                if (x.isEdit || x.isOption || x.desc.lowercase() != "avtomatik to'ldirildi") continue
                for (j in i + 1..minOf(i + 3, nodes.size - 1)) {
                    val b = nodes[j]
                    if (b.cls.endsWith("Button") && b.desc.lowercase() == "tasdiqlash" &&
                        b.info.isEnabled && b.info.isClickable
                    ) {
                        val r = Rect()
                        b.info.getBoundsInScreen(r)
                        val key = r.toShortString()
                        if (pressedAt.add(key) && b.info.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            pressed++
                            report.add("OK Tasdiqlash bosildi ($pressed)")
                            pressedNow = true
                        }
                        break
                    }
                }
                if (pressedNow) break
            }
            if (pressedNow) {
                Thread.sleep(600)
                continue
            }
            val sc = findScroll(root) ?: break
            if (!sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break
            Thread.sleep(500)
        }
        if (pressed == 0) report.add("Tasdiqlanadigan \"Avtomatik to'ldirildi\" maydon topilmadi")
        finish(n, report, null)
    }

    private fun finish(n: Int, report: List<String>, note: String?) {
        val sb = StringBuilder("$n/6 natija:\n")
        if (note != null) sb.append(note).append('\n')
        for (l in report) {
            sb.append(l).append('\n')
            AppLog.write(this, "[$n/6] $l")
        }
        sb.append("Qo'lda: ").append(Rules.manual(n))
        showPanel(sb.toString(), false)
    }

    private fun findScroll(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.className?.toString()?.endsWith("ScrollView") == true) return root
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            val f = findScroll(c)
            if (f != null) return f
        }
        return null
    }

    private fun runApply(n: Int) {
        val target = prefs.getString("target", "") ?: ""
        val remaining = Rules.forPage(n, prefs.getString("comment", "") ?: "").toMutableList()
        val report = ArrayList<String>()

        // Avval ro'yxat tepasiga chiqamiz (savol sarlavhalari ko'rinishi uchun)
        for (i in 0 until 15) {
            if (stopFlag) { finish(n, report, "STOP bosildi"); return }
            val r0 = rootInActiveWindow ?: break
            val sc = findScroll(r0) ?: break
            if (!sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
            Thread.sleep(350)
        }

        for (step in 0 until 25) {
            if (stopFlag) { finish(n, report, "STOP bosildi"); return }
            val root = rootInActiveWindow
            if (root == null || root.packageName?.toString() != target) {
                finish(n, report, "Census ilovasi oldingi planda emas - to'xtadi")
                return
            }
            val nodes = flatten(root)
            if (currentPage(nodes) != n) {
                finish(n, report, "Sahifa o'zgardi - to'xtadi")
                return
            }
            processRules(nodes, remaining, report)
            if (remaining.isEmpty()) break
            val sc = findScroll(root) ?: break
            if (!sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break
            Thread.sleep(500)
        }
        for (r in remaining) report.add("! ${r.label}: ekranda topilmadi - o'zing tekshir")
        finish(n, report, null)
    }

    private fun processRules(nodes: List<N>, remaining: MutableList<Rule>, report: MutableList<String>) {
        val iter = remaining.iterator()
        while (iter.hasNext()) {
            if (stopFlag) return
            val r = iter.next()
            val done = when (r) {
                is Choice -> doChoice(nodes, r, report)
                is Verify -> doVerify(nodes, r, report)
                is Present -> doPresent(nodes, r, report)
                is SetText -> doSetText(nodes, r, report)
            }
            if (done) iter.remove()
        }
    }

    private fun labelIndex(nodes: List<N>, q: String): Int =
        nodes.indexOfFirst { !it.isOption && !it.isEdit && it.desc.lowercase().startsWith("$q ") }

    private fun optionsAfter(nodes: List<N>, li: Int): List<N> {
        val out = ArrayList<N>()
        for (i in li + 1 until nodes.size) {
            val n = nodes[i]
            if (n.isOption) out.add(n)
            else if (n.cls.endsWith("View") && labelRe.matches(n.desc)) break
        }
        return out
    }

    private fun safeClick(n: N): Boolean {
        if (!n.isOption) return false
        val d = n.desc.lowercase()
        if (d.contains("yakunlash") || d.contains("keyingisi") ||
            d.contains("tasdiqlash") || d.contains("orqaga")
        ) return false
        return n.info.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun doChoice(nodes: List<N>, r: Choice, report: MutableList<String>): Boolean {
        val li = labelIndex(nodes, r.q)
        if (li < 0) return false
        val opts = optionsAfter(nodes, li)
        val t = opts.firstOrNull {
            val d = it.desc.lowercase()
            if (r.exact) d == r.match else d.contains(r.match)
        } ?: return false
        if (t.info.isChecked) {
            report.add("OK ${r.label} (allaqachon tanlangan)")
            return true
        }
        val others = opts.filter { it !== t && it.info.isChecked }
        val isRadio = t.cls.endsWith("RadioButton")
        if (others.isNotEmpty() && (isRadio || r.exclusive)) {
            report.add("! ${r.label}: boshqa variant tanlangan (${others.first().desc.take(40)}) - o'zgartirilmadi")
            return true
        }
        if (!t.info.isEnabled || !t.info.isClickable) {
            report.add("! ${r.label}: tanlab bo'lmadi (bloklangan)")
            return true
        }
        if (!safeClick(t)) {
            report.add("! ${r.label}: bosilmadi")
            return true
        }
        Thread.sleep(350)
        t.info.refresh()
        report.add(
            if (t.info.isChecked) "OK ${r.label}"
            else "! ${r.label}: tanlanganini tasdiqlab bo'lmadi - tekshir"
        )
        return true
    }

    private fun doVerify(nodes: List<N>, r: Verify, report: MutableList<String>): Boolean {
        val li = labelIndex(nodes, r.q)
        if (li < 0) return false
        val opts = optionsAfter(nodes, li)
        if (opts.isEmpty()) return false
        val checked = opts.firstOrNull { it.info.isChecked }
        if (checked == null) {
            report.add("! ${r.label}: javob tanlanmagan - tekshir")
        } else if (checked.desc.lowercase().contains(r.expect)) {
            report.add("OK ${r.label}: mos")
        } else {
            report.add("! ${r.label}: boshqa variant (${checked.desc.take(40)}) - tekshir")
        }
        return true
    }

    private fun doPresent(nodes: List<N>, r: Present, report: MutableList<String>): Boolean {
        val found = nodes.any {
            it.text.lowercase().contains(r.token) || it.desc.lowercase().contains(r.token)
        }
        if (found) report.add("OK ${r.label}: ko'rinmoqda")
        return found
    }

    private fun doSetText(nodes: List<N>, r: SetText, report: MutableList<String>): Boolean {
        val li = labelIndex(nodes, r.q)
        if (li < 0) return false
        var edit: N? = null
        for (i in li + 1 until nodes.size) {
            if (nodes[i].isEdit) { edit = nodes[i]; break }
            if (nodes[i].cls.endsWith("View") && labelRe.matches(nodes[i].desc)) break
        }
        if (edit == null) return false
        val empty = edit.info.text.isNullOrEmpty() || edit.info.isShowingHintText
        if (!empty) {
            report.add("! ${r.label}: maydon bo'sh emas - tegilmadi")
            return true
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, r.value)
        val ok = edit.info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        report.add(if (ok) "OK ${r.label}" else "! ${r.label}: yozilmadi")
        return true
    }
}
