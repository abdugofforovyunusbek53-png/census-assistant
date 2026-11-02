package uz.anketayordamchi

import java.util.IdentityHashMap
import kotlin.math.abs

/** Android'dan mustaqil ekran elementi (sinash uchun). Haqiqiy ilovada AccNode, sinovda soxta tugun. */
interface UiNode {
    val cls: String
    val text: String
    val desc: String
    val hint: String
    val stateDesc: String
    val id: String
    val checkable: Boolean
    val checked: Boolean
    val selected: Boolean
    val clickable: Boolean
    val editable: Boolean
    val enabled: Boolean
    val visible: Boolean
    val showingHint: Boolean
    val scrollable: Boolean
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    val parent: UiNode?
    val children: List<UiNode>
}

/** Ekranni boshqarish: faqat shu interfeys orqali. Android'da AccDriver, sinovda FakeApp. */
interface UiDriver {
    /** Census oynalari (asosiy + ochiq ro'yxat/dialog) bitta daraxtda; Census oldingi planda bo'lmasa null. */
    fun snapshot(): Snapshot?
    fun click(n: UiNode): Boolean
    fun tap(n: UiNode): Boolean
    fun setText(n: UiNode, v: String): Boolean
    fun focus(n: UiNode): Boolean
    fun paste(n: UiNode, v: String): Boolean
    fun reveal(n: UiNode): Boolean
    fun scroll(forward: Boolean, prefer: ((UiNode) -> Boolean)? = null): Boolean
    fun sleep(ms: Long)
    fun stopped(): Boolean
    fun log(s: String)
}

val UiNode.cx: Int get() = (left + right) / 2
val UiNode.cy: Int get() = (top + bottom) / 2
val UiNode.h: Int get() = bottom - top

fun UiNode.texts(): List<String> = listOf(text, desc).map { norm(it) }.filter { it.isNotEmpty() }
fun UiNode.label(): String = texts().firstOrNull() ?: ""
fun UiNode.isControl(): Boolean = checkable || cls.endsWith("RadioButton") || cls.endsWith("CheckBox")
fun UiNode.isEdit(): Boolean = editable || cls.endsWith("EditText")
fun UiNode.isRadio(): Boolean = cls.endsWith("RadioButton")

/** Maydonning joriy qiymati ("hint" ko'rinib turgan bo'lsa - bo'sh). */
fun UiNode.value(): String = if (showingHint) "" else norm(text)

private val ON_STATES = setOf("tanlangan", "belgilangan", "selected", "checked", "on", "yoqilgan", "tanlandi")

/** stateDescription bo'yicha: true/false yoki null (noma'lum). */
fun UiNode.stateOn(): Boolean? {
    val s = fz(stateDesc)
    if (s.isEmpty()) return null
    if (s.startsWith("not") || s.startsWith("un") || s == "off" || s.startsWith("tanlanmagan") || s.startsWith("belgilanmagan")) return false
    if (s in ON_STATES) return true
    return null
}

fun descendants(n: UiNode, out: MutableList<UiNode> = ArrayList()): List<UiNode> {
    out.add(n)
    for (c in n.children) descendants(c, out)
    return out
}

fun isAncestor(a: UiNode, b: UiNode): Boolean {
    var p = b.parent
    while (p != null) {
        if (p === a) return true
        p = p.parent
    }
    return false
}

class OptionInfo(
    val node: UiNode,
    val labels: List<String>,
    val source: String,
    val on: Boolean,
    val onVia: String
) {
    fun shortLabel(): String = (labels.firstOrNull() ?: "?").take(40)
}

private val PAGE_RE = Regex("^(\\d{1,2}) ?/ ?6$")
private val NAV_KEYS = setOf("keyingisi", "orqaga", "yakunlash")

class Snapshot(val nodes: List<UiNode>) {
    private val pos = IdentityHashMap<UiNode, Int>().also { m -> nodes.forEachIndexed { i, n -> m[n] = i } }

    fun indexOf(n: UiNode): Int = pos[n] ?: -1

    fun page(): Int? {
        for (n in nodes) for (t in n.texts()) {
            val m = PAGE_RE.find(t)
            if (m != null) return m.groupValues[1].toInt()
        }
        return null
    }

    fun signature(): String =
        nodes.filter { it.visible && it.label().isNotEmpty() }.take(80)
            .joinToString("|") { fz(it.label()).take(12) + "@" + it.top }

    // ---------------------------------------------------------------- savol sarlavhalari
    fun parsedOf(n: UiNode): Parsed? {
        for (t in n.texts()) parseLabel(t)?.let { return it }
        return null
    }

    private val labelIdx: List<Int> by lazy {
        val out = ArrayList<Int>()
        val seen = IdentityHashMap<UiNode, String>()
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.isControl() || n.isEdit()) continue
            val p = parsedOf(n) ?: continue
            var a = n.parent
            var dup = false
            while (a != null) {
                if (seen[a] == p.code) { dup = true; break }
                a = a.parent
            }
            if (dup) continue
            seen[n] = p.code
            out.add(i)
        }
        out
    }

    fun labels(): List<Int> = labelIdx

    fun parsed(i: Int): Parsed = parsedOf(nodes[i])!!

    fun findLabel(q: String, titleStart: String? = null): Int {
        for (i in labelIdx) {
            val p = parsed(i)
            if (p.code != q) continue
            if (titleStart != null && !fz(p.title).startsWith(fz(titleStart))) continue
            return i
        }
        return -1
    }

    /** Sarlavhada so'z bor va bunday sarlavha YAGONA bo'lsa. */
    fun findLabelByTitle(has: String): Int {
        val hits = labelIdx.filter { fz(parsed(it).title).contains(fz(has)) }
        return if (hits.size == 1) hits[0] else -1
    }

    /** Navigatsiya tugmalari ("Keyingisi", "Orqaga", "Yakunlash"): hech qachon savol blokiga kirmaydi. */
    fun isNav(n: UiNode): Boolean = !n.isEdit() && !n.isControl() && n.texts().any { fz(it) in NAV_KEYS }

    /** Blok keyingi savol sarlavhasida yoki birinchi navigatsiya tugmasida tugaydi. */
    fun blockEnd(li: Int): Int {
        var e = nodes.size
        for (j in labelIdx) if (j > li) { e = j; break }
        for (i in li + 1 until e) if (isNav(nodes[i])) return i
        return e
    }

    /** Keyingi savol sarlavhasi daraxtda bor = bu savolning hamma variantlari daraxtda. */
    fun blockClosed(li: Int): Boolean = labelIdx.any { it > li }

    fun labelBefore(i: Int): Int {
        var best = -1
        for (j in labelIdx) if (j < i) best = j else break
        return best
    }

    // ---------------------------------------------------------------- variantlar
    private fun stateOf(c: UiNode): Pair<Boolean, String> {
        if (c.checked) return true to "checked"
        if (c.stateOn() == true) return true to "stateDesc"
        var p = c.parent
        var k = 0
        while (p != null && k < 2) {
            if (p.checkable && p.checked) return true to "ancestor-checked"
            p = p.parent
            k++
        }
        if (!c.checkable && c.selected) return true to "selected"
        return false to "-"
    }

    /** Variant matni: o'zining text/desc'i; bo'lmasa ota-element ichidagi matn; bo'lmasa yonidagi matn. */
    private fun labelsOf(c: UiNode, li: Int, end: Int): Pair<List<String>, String> {
        val own = c.texts()
        if (own.isNotEmpty()) return own to "own"
        var p = c.parent
        var hop = 0
        while (p != null && hop < 3) {
            val ds = descendants(p)
            if (ds.count { it.isControl() } != 1) break
            val ts = ArrayList<String>()
            ts.addAll(p.texts())
            for (d in ds) if (d !== p && !d.isControl() && !d.isEdit()) ts.addAll(d.texts())
            if (ts.isNotEmpty()) return (listOf(ts.joinToString(" ")) + ts) to "parent"
            p = p.parent
            hop++
        }
        val i = indexOf(c)
        for (j in i + 1 until minOf(end, i + 6)) {
            val n = nodes[j]
            if (n.isControl()) break
            if (n.isEdit()) continue
            val ts = n.texts()
            if (ts.isNotEmpty() && abs(n.cy - c.cy) <= maxOf(c.h, 60)) return ts to "sibling"
        }
        for (j in i - 1 downTo maxOf(li + 1, i - 3)) {
            val n = nodes[j]
            if (n.isControl()) break
            if (n.isEdit()) continue
            val ts = n.texts()
            if (ts.isNotEmpty() && abs(n.cy - c.cy) <= maxOf(c.h, 60)) return ts to "sibling-before"
        }
        return emptyList<String>() to "none"
    }

    fun options(li: Int, end: Int): List<OptionInfo> {
        val raw = ArrayList<UiNode>()
        for (i in li + 1 until end) if (nodes[i].isControl()) raw.add(nodes[i])
        val leaf = raw.filter { c -> raw.none { o -> o !== c && isAncestor(c, o) } }
        return leaf.map { c ->
            val (lbls, src) = labelsOf(c, li, end)
            val (on, via) = stateOf(c)
            OptionInfo(c, lbls, src, on, via)
        }
    }

    // ---------------------------------------------------------------- matn maydonlari
    fun edits(li: Int, end: Int): List<UiNode> {
        val out = ArrayList<UiNode>()
        for (i in li + 1 until end) if (nodes[i].isEdit()) out.add(nodes[i])
        return out
    }

    private fun hOverlap(a: UiNode, b: UiNode): Int = minOf(a.right, b.right) - maxOf(a.left, b.left)

    /** Sarlavha (yoki ichki yorliq) tagidagi maydon: avval geometriya (ustun bo'yicha), keyin DFS tartibi. */
    fun editFor(li: Int, end: Int, anchor: UiNode = nodes[li]): UiNode? {
        val es = edits(li, end)
        if (es.isEmpty()) return null
        val geo = es.filter { it.top >= anchor.top - 4 && hOverlap(it, anchor) > 0 }
            .minWithOrNull(compareBy({ abs(it.top - anchor.bottom) }, { -hOverlap(it, anchor) }))
        if (geo != null) return geo
        val ai = indexOf(anchor)
        return es.firstOrNull { indexOf(it) > ai } ?: es.first()
    }

    /** Savol blokidagi qiymat matnlari (yorliqlardan tashqari; hint ko'rinib turgan maydonlar hisobga olinmaydi). */
    fun valueTexts(li: Int, end: Int): List<String> {
        val out = ArrayList<String>()
        for (i in li + 1 until end) {
            val n = nodes[i]
            if (n.isControl()) continue
            if (n.isEdit()) { val v = n.value(); if (v.isNotEmpty()) out.add(v); continue }
            for (t in n.texts()) if (parseLabel(t) == null) out.add(t)
        }
        return out
    }

    // ---------------------------------------------------------------- "Avtomatik to'ldirildi" / "Tasdiqlash"
    fun isMarker(n: UiNode): Boolean {
        if (n.isControl() || n.isEdit()) return false
        val f = fz(n.label())
        return f.contains("avtomatik") && f.contains("toldirildi")
    }

    fun markers(from: Int = 0, to: Int = nodes.size): List<UiNode> {
        val out = ArrayList<UiNode>()
        for (i in from until to) if (isMarker(nodes[i])) out.add(nodes[i])
        return out
    }

    private fun clickableSelfOrAncestor(n: UiNode): Boolean {
        var p: UiNode? = n
        var k = 0
        while (p != null && k < 3) {
            if (p.clickable) return true
            p = p.parent
            k++
        }
        return false
    }

    /** Marker yonidagi "Tasdiqlash" tugmasi: bir xil ota, yoki bir qatorda o'ng tomonda. */
    fun confirmButtonFor(marker: UiNode): UiNode? {
        val cands = nodes.filter { fz(it.label()) == "tasdiqlash" && !it.isEdit() && clickableSelfOrAncestor(it) }
        if (cands.isEmpty()) return null
        val sib = cands.firstOrNull { c -> marker.parent != null && c.parent === marker.parent }
        if (sib != null) return sib
        val row = cands.filter { abs(it.cy - marker.cy) <= maxOf(60, marker.h * 2) && it.cx >= marker.cx }
        return row.minByOrNull { abs(it.cy - marker.cy) * 10000 + (it.cx - marker.cx) }
    }

    // ---------------------------------------------------------------- tashxis (trace)
    fun describe(): List<String> {
        val out = ArrayList<String>()
        out.add("Sahifa: ${page()}  tugunlar: ${nodes.size}  savollar: ${labelIdx.size}")
        for (li in labelIdx) {
            val p = parsed(li)
            val end = blockEnd(li)
            val opts = options(li, end)
            val es = edits(li, end)
            out.add("SAVOL ${p.code} \"${mask(p.title).take(50)}\" yopiq=${blockClosed(li)} variant=${opts.size} maydon=${es.size}")
            for (o in opts) {
                val n = o.node
                out.add(
                    "   VARIANT yoqilgan=${o.on}(${o.onVia}) enabled=${n.enabled} visible=${n.visible} " +
                        "manba=${o.source} cls=${n.cls.substringAfterLast('.')} checkable=${n.checkable} checked=${n.checked} " +
                        "selected=${n.selected} stateDesc=\"${n.stateDesc}\" clickable=${n.clickable} matn=\"${o.shortLabel()}\""
                )
            }
            for (e in es) {
                out.add(
                    "   MAYDON enabled=${e.enabled} visible=${e.visible} hintKorinyapti=${e.showingHint} " +
                        "uzunlik=${e.value().length} hint=\"${e.hint.take(30)}\" bounds=[${e.left},${e.top}][${e.right},${e.bottom}]"
                )
            }
            for (m in markers(li + 1, end)) {
                val b = confirmButtonFor(m)
                out.add("   MARKER \"${m.label().take(30)}\" tasdiqlashTugmasi=${b != null}")
            }
        }
        return out
    }
}
