package verify

import uz.anketayordamchi.*

/** Sinov uchun soxta ekran tuguni. */
class FN(
    override val cls: String,
    override val text: String = "",
    override val desc: String = "",
    override val hint: String = "",
    override val stateDesc: String = "",
    override val checkable: Boolean = false,
    override val checked: Boolean = false,
    override val selected: Boolean = false,
    override val clickable: Boolean = false,
    override val editable: Boolean = false,
    override val enabled: Boolean = true,
    override val visible: Boolean = true,
    override val showingHint: Boolean = false,
    override val scrollable: Boolean = false,
    override val left: Int = 0,
    override val top: Int = 0,
    override val right: Int = 0,
    override val bottom: Int = 0,
    val ref: Any? = null
) : UiNode {
    override val id: String = ""
    override var parent: UiNode? = null
    val kids = ArrayList<UiNode>()
    override val children: List<UiNode> get() = kids
}

sealed class Row(val h: Int)
class RLabel(val text: String) : Row(70)
class ROpt(
    val group: String, val text: String, var on: Boolean, val enabled: Boolean = true,
    val multi: Boolean = false, val style: String = "own", val ignoresClick: Boolean = false
) : Row(70)
class REdit(
    val key: String, var value: String, val hint: String = "", val enabled: Boolean = true,
    val acceptSet: Boolean = true, val acceptPaste: Boolean = true, val acceptAny: Boolean = true
) : Row(90)
class RTwo(
    val labelA: String, val labelB: String, var a: String, var b: String,
    val acceptSet: Boolean = true, val acceptPaste: Boolean = true, val acceptAny: Boolean = true
) : Row(150)
class RSelect(val key: String, var value: String, val items: List<String>, val search: Boolean = true) : Row(80)
class RAuto(val key: String, var pending: Boolean, val btnWorks: Boolean = true, val markerOnly: Boolean = false) : Row(70)

class SubRef(val row: RTwo, val which: Int)

/** Soxta Census ilovasi: lazy daraxt, skroll, qidiruvli ro'yxat, tasdiqlash. UiDriver sifatida ishlaydi. */
class FakeApp(val page: Int, val rows: List<Row>) : UiDriver {
    val VT = 430
    val VB = 1290
    var scrollY = 0
    var foreground = true
    var dropdown: RSelect? = null
    var query = ""
    val clicks = ArrayList<String>()
    var taps = 0
    var pressCount = 0
    var openCount = 0
    val logs = ArrayList<String>()
    var sleeps = 0
    var stopAtSleep = -1
    var forceClickFalse = false

    private val ys: List<Int> = run {
        var y = 0
        rows.map { val r = y; y += it.h; r }
    }
    private val totalH = rows.sumOf { it.h }

    private fun yOf(row: Row): Int = ys[rows.indexOf(row)]

    private fun describe(ref: Any?): String = when (ref) {
        is ROpt -> "opt:${ref.group}:${ref.text}"
        is RSelect -> "select:${ref.key}"
        is RAuto -> "auto:${ref.key}"
        is String -> "item:$ref"
        else -> "other"
    }

    private fun add(parent: FN, n: FN): FN {
        n.parent = parent
        parent.kids.add(n)
        all.add(n)
        return n
    }

    private val all = ArrayList<UiNode>()

    override fun snapshot(): Snapshot? {
        if (!foreground) return null
        all.clear()
        val win = FN("android.widget.FrameLayout", left = 0, top = 0, right = 720, bottom = 1600)
        all.add(win)
        add(win, FN("android.view.View", desc = "$page / 6", left = 560, top = 340, right = 700, bottom = 380))
        val sv = add(win, FN("android.widget.ScrollView", scrollable = true, left = 0, top = VT, right = 720, bottom = VB))
        for ((i, row) in rows.withIndex()) {
            val top = VT + ys[i] - scrollY
            val bottom = top + row.h
            if (bottom <= VT || top >= VB) continue // lazy: ko'rinmaydigan qatorlar daraxtda yo'q
            when (row) {
                is RLabel -> add(sv, FN("android.view.View", desc = row.text, left = 70, top = top, right = 650, bottom = bottom))
                is ROpt -> optNodes(sv, row, top, bottom)
                is REdit -> add(
                    sv, FN(
                        "android.widget.EditText",
                        text = if (row.value.isEmpty()) row.hint else row.value, hint = row.hint,
                        showingHint = row.value.isEmpty() && row.hint.isNotEmpty(),
                        editable = true, enabled = row.enabled, clickable = true,
                        left = 75, top = top + 10, right = 665, bottom = bottom - 5, ref = row
                    )
                )
                is RTwo -> {
                    add(sv, FN("android.view.View", desc = row.labelA, left = 75, top = top, right = 200, bottom = top + 40))
                    add(sv, FN("android.view.View", desc = row.labelB, left = 392, top = top, right = 640, bottom = top + 40))
                    add(sv, FN("android.widget.EditText", text = row.a, editable = true, clickable = true,
                        left = 75, top = top + 50, right = 348, bottom = top + 140, ref = SubRef(row, 0)))
                    add(sv, FN("android.widget.EditText", text = row.b, editable = true, clickable = true,
                        left = 392, top = top + 50, right = 665, bottom = top + 140, ref = SubRef(row, 1)))
                }
                is RSelect -> add(
                    sv, FN("android.view.View", desc = if (row.value.isEmpty()) "Tanlang" else row.value,
                        clickable = true, left = 75, top = top + 5, right = 665, bottom = bottom - 5, ref = row)
                )
                is RAuto -> {
                    if (row.pending || row.markerOnly) {
                        add(sv, FN("android.view.View", desc = "Avtomatik to'ldirildi", left = 110, top = top, right = 400, bottom = bottom))
                    }
                    if (row.pending && !row.markerOnly) {
                        add(sv, FN("android.widget.Button", desc = "Tasdiqlash", clickable = true,
                            left = 528, top = top, right = 650, bottom = bottom, ref = row))
                    }
                }
            }
        }
        add(win, FN("android.widget.Button", desc = "Keyingisi", clickable = true, left = 380, top = 1365, right = 680, bottom = 1475))
        val dd = dropdown
        if (dd != null) {
            val dw = FN("android.widget.FrameLayout", left = 0, top = 200, right = 720, bottom = 1200)
            all.add(dw)
            var y = 210
            if (dd.search) {
                add(dw, FN("android.widget.EditText", text = query, hint = "Qidirish", editable = true, clickable = true,
                    left = 40, top = y, right = 680, bottom = y + 90, ref = "search"))
                y += 100
            }
            val shown = dd.items.filter { query.isEmpty() || it.lowercase().contains(query.lowercase()) }
            for (it in shown) {
                add(dw, FN("android.view.View", desc = it, clickable = true, left = 40, top = y, right = 680, bottom = y + 80, ref = it))
                y += 90
            }
        }
        return Snapshot(ArrayList(all))
    }

    private fun optNodes(sv: FN, row: ROpt, top: Int, bottom: Int) {
        val cls = if (row.multi) "android.widget.CheckBox" else "android.widget.RadioButton"
        when (row.style) {
            "own" -> add(sv, FN(cls, desc = row.text, checkable = true, checked = row.on, clickable = true,
                enabled = row.enabled, left = 90, top = top, right = 660, bottom = bottom, ref = row))
            "stateDesc" -> add(sv, FN(cls, desc = row.text, stateDesc = if (row.on) "Tanlangan" else "Tanlanmagan",
                clickable = true, enabled = row.enabled, left = 90, top = top, right = 660, bottom = bottom, ref = row))
            "selectedOnly" -> add(sv, FN(cls, desc = row.text, selected = row.on, clickable = true,
                enabled = row.enabled, left = 90, top = top, right = 660, bottom = bottom, ref = row))
            "nosignal" -> add(sv, FN(cls, desc = row.text, checkable = true, checked = false, clickable = true,
                enabled = row.enabled, left = 90, top = top, right = 660, bottom = bottom, ref = row))
            "parent" -> {
                val p = add(sv, FN("android.view.View", clickable = true, left = 90, top = top, right = 660, bottom = bottom, ref = row))
                add(p, FN(cls, checkable = true, checked = row.on, enabled = row.enabled, left = 90, top = top, right = 130, bottom = bottom, ref = row))
                add(p, FN("android.widget.TextView", text = row.text, left = 180, top = top, right = 660, bottom = bottom))
            }
            "sibling" -> {
                add(sv, FN(cls, checkable = true, checked = row.on, clickable = true, enabled = row.enabled,
                    left = 90, top = top, right = 130, bottom = bottom, ref = row))
                add(sv, FN("android.view.View", desc = row.text, left = 180, top = top, right = 660, bottom = bottom))
            }
        }
    }

    // ---------------------------------------------------------------- UiDriver
    private fun perform(n: FN): Boolean {
        if (forceClickFalse) return false
        val ref = n.ref
        clicks.add(describe(ref))
        when (ref) {
            is ROpt -> {
                if (!ref.enabled || ref.ignoresClick) return true
                if (ref.multi) ref.on = !ref.on
                else {
                    rows.filterIsInstance<ROpt>().filter { it.group == ref.group }.forEach { it.on = false }
                    ref.on = true
                }
            }
            is RSelect -> { dropdown = ref; query = ""; openCount++ }
            is RAuto -> { pressCount++; if (ref.btnWorks) ref.pending = false }
            is String -> { dropdown!!.value = ref; dropdown = null }
        }
        return true
    }

    override fun click(n: UiNode): Boolean {
        var p: UiNode? = n
        var hops = 0
        while (p != null && hops < 4) {
            if (p.clickable) return perform(p as FN)
            p = p.parent
            hops++
        }
        return false
    }

    override fun tap(n: UiNode): Boolean { taps++; val f = n as? FN ?: return false; return perform(f) }

    override fun setText(n: UiNode, v: String): Boolean {
        val ref = (n as FN).ref
        when (ref) {
            "search" -> { query = v; return true }
            is REdit -> { if (!ref.enabled) return false; if (ref.acceptSet && ref.acceptAny) ref.value = v; return true }
            is SubRef -> {
                if (!ref.row.acceptSet || !ref.row.acceptAny) return true
                if (ref.which == 0) ref.row.a = v else ref.row.b = v
                return true
            }
        }
        return false
    }

    override fun focus(n: UiNode): Boolean = true

    override fun paste(n: UiNode, v: String): Boolean {
        val ref = (n as FN).ref
        when (ref) {
            is REdit -> { if (ref.acceptPaste && ref.acceptAny) ref.value = v; return true }
            is SubRef -> {
                if (!ref.row.acceptPaste || !ref.row.acceptAny) return true
                if (ref.which == 0) ref.row.a = v else ref.row.b = v
                return true
            }
        }
        return false
    }

    override fun reveal(n: UiNode): Boolean {
        val ref = (n as FN).ref
        val row = when (ref) {
            is Row -> ref
            is SubRef -> ref.row
            else -> null
        } ?: return false
        scrollY = (yOf(row) - 100).coerceIn(0, maxOf(0, totalH - (VB - VT)))
        return true
    }

    override fun scroll(forward: Boolean, prefer: ((UiNode) -> Boolean)?): Boolean {
        if (dropdown != null && prefer != null) return false
        val maxY = maxOf(0, totalH - (VB - VT))
        return if (forward) {
            if (scrollY >= maxY) false else { scrollY = minOf(maxY, scrollY + 400); true }
        } else {
            if (scrollY <= 0) false else { scrollY = maxOf(0, scrollY - 400); true }
        }
    }

    override fun sleep(ms: Long) { sleeps++ }
    override fun stopped(): Boolean = stopAtSleep in 0..sleeps
    override fun log(s: String) { logs.add(s) }

    fun opt(group: String, text: String): ROpt = rows.filterIsInstance<ROpt>().first { it.group == group && it.text == text }
}
