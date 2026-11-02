package uz.anketayordamchi

import kotlin.math.abs

class Abort(msg: String) : RuntimeException(msg)

/** note: to'xtash sababi (yoki null). lines: har bir savol uchun natija ("OK ..." / "! ..." / "- ..."). */
class RunResult(val lines: List<String>, val note: String?)

/** Ishga tushirishlar orasida eslab qolinadigan holat (bir marta bosilgan "Tasdiqlash" qayta bosilmasligi uchun). */
class EngineState {
    val pressed = HashSet<String>()
}

private class Res(val lines: List<String>, val acted: Boolean = true, val abort: Boolean = false)

/**
 * Avtomatlashtirish mantiqi. Faqat UiDriver orqali ishlaydi (Android'ga bog'liq emas).
 * Qoidalar:
 *  - har bir amaldan keyin ekran QAYTA o'qiladi va natija shu yangi ekran bo'yicha tekshiriladi;
 *  - bosish bilan "OK" deyilmaydi: faqat holat haqiqatan o'zgargani ko'rilgandagina;
 *  - bitta variant/tugma bir ishga tushirishda ko'pi bilan bir marta bosiladi;
 *  - "Keyingisi", "Orqaga", "Yakunlash", GPS, kamera tugmalariga tegilmaydi.
 */
class Engine(private val ui: UiDriver, private val st: AutoSettings, private val state: EngineState) {

    // ---------------------------------------------------------------- umumiy
    private fun check() {
        if (ui.stopped()) throw Abort("STOP bosildi")
    }

    private fun fresh(): Snapshot =
        ui.snapshot() ?: throw Abort("Census ilovasi oldingi planda emas yoki ekran o'qilmadi - to'xtadi")

    private fun need(n: Int): Snapshot {
        check()
        val s = fresh()
        val p = s.page() ?: throw Abort("Sahifa raqami (N / 6) ko'rinmadi - to'xtadi")
        if (p != n) throw Abort("Sahifa o'zgardi ($p/6) - to'xtadi")
        return s
    }

    private fun scrollTop() {
        repeat(15) {
            if (ui.stopped()) return
            if (!ui.scroll(false)) return
            ui.sleep(300)
        }
    }

    /** Sahifani yuqoridan pastga aylantirib, body(snapshot, atEnd) ni chaqiradi. body true qaytarsa - tugadi. */
    private fun screens(n: Int, body: (Snapshot, Boolean) -> Boolean) {
        scrollTop()
        var prev: String? = null
        for (i in 0 until 25) {
            val s = need(n)
            val sig = s.signature()
            val atEnd = (sig == prev)
            if (body(s, atEnd) || atEnd) return
            prev = sig
            if (!ui.scroll(true)) {
                ui.sleep(300)
                body(need(n), true)
                return
            }
            ui.sleep(500)
        }
        body(need(n), true)
    }

    private fun labelFor(s: Snapshot, q: String, titleStart: String?, titleHas: String?): Int {
        var li = s.findLabel(q, titleStart)
        if (li < 0 && titleHas != null) {
            li = s.findLabelByTitle(titleHas)
            if (li >= 0) ui.log("Eslatma: $q kodi topilmadi, sarlavha bo'yicha topildi (kod: ${s.parsed(li).code})")
        }
        return li
    }

    private fun matches(o: OptionInfo, match: String, exact: Boolean): Boolean {
        val m = fz(match)
        return o.labels.any { val f = fz(it); if (exact) f == m else f.contains(m) }
    }

    private fun describeOpts(opts: List<OptionInfo>): String =
        if (opts.isEmpty()) "yo'q" else opts.joinToString("; ") { "${it.shortLabel().take(25)}[${if (it.on) "on" else "off"}]" }

    private fun show(v: String): String = if (v.length <= 4) "\"$v\"" else "<${v.length} belgi>"

    // ---------------------------------------------------------------- xavfsiz bosish
    private val forbiddenKeys = listOf("keyingisi", "orqaga", "yakunlash", "rasmgaolish", "gpsnuqtasi", "gpsnuqta")

    /** Taqiqlangan tugmami: o'zi, ichidagi matnlar yoki 3 ta ota-ona yorlig'i bo'yicha. */
    private fun forbidden(n: UiNode): Boolean {
        fun bad(x: UiNode) = x.texts().any { t -> val f = fz(t); forbiddenKeys.any { f == it || (f.length < 25 && f.contains(it)) } }
        if (descendants(n).any { bad(it) }) return true
        var p = n.parent
        var k = 0
        while (p != null && k < 3) { if (bad(p)) return true; p = p.parent; k++ }
        return false
    }

    /** Hamma bosishlar shu yerdan o'tadi: avval ACTION_CLICK, bo'lmasa koordinata. Taqiqlangan tugmalar bosilmaydi. */
    private fun press(n: UiNode): Boolean {
        if (forbidden(n)) {
            ui.log("TAQIQLANGAN tugma bosilmadi: \"${n.label().take(30)}\"")
            return false
        }
        if (ui.click(n)) return true
        ui.log("ACTION_CLICK ishlamadi, koordinata bilan bosiladi: \"${n.label().take(30)}\"")
        return ui.tap(n)
    }

    // ---------------------------------------------------------------- ommaviy amallar
    /** KIRITISH: sahifadagi qoidalarni bajaradi. */
    fun apply(n: Int): RunResult = runRules(n, Rules.forPage(n, st), overwrite = false)

    /** 3.1 xodimlar soni (foydalanuvchi tugmasi bosilgandan keyin). */
    fun staff(n: Int, women: Int): RunResult {
        val r = runRules(n, Rules.staff(women), overwrite = true)
        return r
    }

    private fun runRules(n: Int, rules0: List<Rule>, overwrite: Boolean): RunResult {
        val rules = rules0.toMutableList()
        val out = ArrayList<String>()
        var note: String? = null
        try {
            screens(n) { s0, atEnd ->
                var cur = s0
                val it = rules.iterator()
                while (it.hasNext()) {
                    check()
                    val r = it.next()
                    val res = evalRule(n, r, cur, atEnd, overwrite) ?: continue
                    out.addAll(res.lines)
                    it.remove()
                    if (res.abort) throw Abort("Ochilgan ro'yxat/oyna yopilmagan - to'xtadi (qo'lda yoping)")
                    if (res.acted) cur = need(n)
                }
                rules.isEmpty()
            }
            for (r in rules) out.add("! ${r.label}: bajarilmadi (ekranda topilmadi) - o'zing tekshir")
        } catch (e: Abort) {
            note = e.message
            for (r in rules) out.add("! ${r.label}: bajarilmadi (${e.message})")
        }
        return RunResult(out, note)
    }

    private fun evalRule(n: Int, r: Rule, s: Snapshot, atEnd: Boolean, overwrite: Boolean): Res? = when (r) {
        is Choice -> doChoice(r, s, atEnd)
        is Verify -> doVerify(r, s, atEnd)
        is Select -> doSelect(r, s, atEnd)
        is SetText -> doSetText(r, s, atEnd)
        is SubField -> doSubField(r, s, atEnd, overwrite)
        is Status -> doStatus(r, s, atEnd)
    }

    private fun notFound(r: Rule, atEnd: Boolean, why: String = "savol ekranda topilmadi"): Res? =
        if (atEnd) Res(listOf("! ${r.label}: $why"), false) else null

    // ---------------------------------------------------------------- variant tanlash (radio / checkbox)
    private fun findOption(s: Snapshot, r: Choice): OptionInfo? {
        val li = labelFor(s, r.q, null, r.titleHas)
        if (li < 0) return null
        return s.options(li, s.blockEnd(li)).firstOrNull { matches(it, r.match, r.exact) }
    }

    private fun doChoice(r: Choice, s: Snapshot, atEnd: Boolean): Res? {
        val li = labelFor(s, r.q, null, r.titleHas)
        if (li < 0) return notFound(r, atEnd)
        val end = s.blockEnd(li)
        val opts = s.options(li, end)
        val t = opts.firstOrNull { matches(it, r.match, r.exact) }
        if (t == null) {
            return if (s.blockClosed(li) || atEnd) {
                Res(listOf("! ${r.label}: variant topilmadi. Ko'ringanlar: ${describeOpts(opts)}"), false)
            } else null
        }
        if (t.on) return Res(listOf("OK ${r.label} (allaqachon tanlangan; holat: ${t.onVia})"), false)
        val others = opts.filter { it !== t && it.on }
        if (others.isNotEmpty() && (t.node.isRadio() || r.exclusive)) {
            return Res(listOf("! ${r.label}: boshqa variant tanlangan (${others.first().shortLabel()}) - o'zgartirilmadi"), false)
        }
        if (!t.node.enabled) return Res(listOf("! ${r.label}: tanlab bo'lmadi (variant bloklangan)"), false)

        if (!t.node.visible) { ui.reveal(t.node); ui.sleep(300) }
        val clicked = press(t.node)
        if (!clicked) return Res(listOf("! ${r.label}: bosilmadi (bosish amali bajarilmadi)"), false)

        // Bitta marta bosildi. Endi faqat HOLATNI tekshiramiz (qayta bosmaymiz).
        var last: OptionInfo? = null
        for (k in 0 until 5) {
            ui.sleep(350)
            val s2 = fresh()
            last = findOption(s2, r)
            if (last != null && last.on) return Res(listOf("OK ${r.label} (tasdiqlandi: ${last.onVia})"))
        }
        val hint = if (last == null) "variant qayta topilmadi" else "holat o'zgarmadi (checked=${last.node.checked}, selected=${last.node.selected}, stateDesc=\"${last.node.stateDesc}\")"
        return Res(listOf("! ${r.label}: bosildi, lekin tanlanganini tasdiqlab bo'lmadi - $hint. Qo'lda tekshir"))
    }

    // ---------------------------------------------------------------- faqat tekshirish (1.5, 5.5b)
    private fun doVerify(r: Verify, s: Snapshot, atEnd: Boolean): Res? {
        val li = labelFor(s, r.q, null, r.titleHas)
        if (li < 0) return notFound(r, atEnd)
        val end = s.blockEnd(li)
        val opts = s.options(li, end)
        val closed = s.blockClosed(li) || atEnd
        if (opts.isEmpty()) {
            return if (closed) Res(listOf("! ${r.label}: variantlar (RadioButton/CheckBox) daraxtda topilmadi - TASHXIS bosing"), false) else null
        }
        val on = opts.filter { it.on }
        if (on.isNotEmpty()) {
            val m = on.firstOrNull { o -> o.labels.any { fz(it).contains(fz(r.expect)) } }
            return Res(
                listOf(
                    if (m != null) "OK ${r.label}: mos (holat: ${m.onVia})"
                    else "! ${r.label}: boshqa variant tanlangan (${on[0].shortLabel()}) - tekshir"
                ), false
            )
        }
        if (!closed) return null // qolgan variantlar skroldan keyin ko'rinishi mumkin

        // Hech qaysi variant "yoqilgan" ko'rinmadi.
        val target = opts.firstOrNull { o -> o.labels.any { fz(it).contains(fz(r.expect)) } }
        if (r.setIfEmpty && target != null && opts.all { it.node.enabled }) {
            return doChoice(Choice(r.q, r.expect, false, false, r.label, r.titleHas), s, true)
        }
        val enabled = opts.filter { it.node.enabled }
        val extra = if (enabled.size == 1 && opts.size > 1)
            " Faqat '${enabled[0].shortLabel()}' yoqilgan, qolganlari o'chirilgan: ehtimol aynan shu tanlangan, ko'zda tekshir."
        else ""
        return Res(
            listOf(
                "! ${r.label}: tanlangan holat Accessibility orqali ko'rinmadi (variantlar: ${describeOpts(opts)}).$extra " +
                    "TASHXIS bosib trace yuboring"
            ), false
        )
    }

    // ---------------------------------------------------------------- 2.1 holati
    private fun doStatus(r: Status, s: Snapshot, atEnd: Boolean): Res? {
        val li = s.findLabel(r.q)
        if (li < 0) return notFound(r, atEnd)
        val end = s.blockEnd(li)
        val e = s.editFor(li, end)
        if (e == null) {
            return if (s.blockClosed(li) || atEnd) Res(listOf("! ${r.label}: kiritish maydoni topilmadi"), false) else null
        }
        val v = e.value()
        val pending = s.markers(li + 1, end).any { s.confirmButtonFor(it) != null }
        val line = when {
            v.isEmpty() -> "! ${r.label}: bo'sh - o'zing kiriting"
            pending -> "! ${r.label}: to'ldirilgan (${show(v)}), lekin tasdiqlanmagan - tekshirib TASDIQLASH ni bos"
            else -> "OK ${r.label}: to'ldirilgan (${show(v)}) va tasdiqlangan/tasdiq talab qilinmaydi"
        }
        return Res(listOf(line), false)
    }

    // ---------------------------------------------------------------- matn kiritish
    /**
     * Maydonga qiymat yozadi: to'g'ri qiymat bo'lsa tegmaydi; bo'sh bo'lsa bir marta yozadi; noto'g'ri qiymat
     * bo'lsa faqat overwrite=true (foydalanuvchi tugmasi) bo'lganda tuzatadi. Yakuniy tekshiruv: ekranni qayta o'qib.
     */
    private fun fillEdit(label: String, e: UiNode, value: String, overwrite: Boolean, locate: (Snapshot) -> UiNode?): String {
        val cur = e.value()
        if (fz(cur) == fz(value)) return "OK $label (allaqachon to'g'ri: ${show(cur)})"
        if (cur.isNotEmpty() && !overwrite) return "! $label: maydon bo'sh emas (${show(cur)}) - tegilmadi"
        if (!e.enabled) return "! $label: maydon o'chirilgan (bloklangan)"
        if (!e.visible) { ui.reveal(e); ui.sleep(300) }

        var lastSeen = cur
        for (attempt in 0 until 3) {
            check()
            val node = if (attempt == 0) e else (locate(fresh()) ?: return "! $label: maydon qayta topilmadi")
            val how: String
            val done: Boolean
            when (attempt) {
                0 -> { how = "set"; done = ui.setText(node, value) }
                1 -> { how = "fokus+set"; ui.click(node); ui.focus(node); done = ui.setText(node, value) }
                else -> { how = "paste"; done = ui.paste(node, value) }
            }
            ui.sleep(400)
            val s2 = fresh()
            val e2 = locate(s2) ?: return "! $label: yozildi, lekin maydon qayta topilmadi - tekshir"
            lastSeen = e2.value()
            if (fz(lastSeen) == fz(value)) {
                return "OK $label" + (if (attempt > 0) " ($how)" else "")
            }
            ui.log("$label: urinish ${attempt + 1} ($how) natija=$done, ko'rilgan qiymat ${show(lastSeen)}")
        }
        return "! $label: yozilganini tasdiqlab bo'lmadi (maydonda: ${show(lastSeen)}) - o'zing kirit"
    }

    private fun doSetText(r: SetText, s: Snapshot, atEnd: Boolean): Res? {
        val li = labelFor(s, r.q, null, r.titleHas)
        if (li < 0) return notFound(r, atEnd)
        val end = s.blockEnd(li)
        val e = s.editFor(li, end)
        if (e == null) {
            return if (s.blockClosed(li) || atEnd) Res(listOf("! ${r.label}: kiritish maydoni (EditText) topilmadi - TASHXIS bosing"), false) else null
        }
        val line = fillEdit(r.label, e, r.value, false) { s2 ->
            val l2 = labelFor(s2, r.q, null, r.titleHas)
            if (l2 < 0) null else s2.editFor(l2, s2.blockEnd(l2))
        }
        return Res(listOf(line))
    }

    private fun findSub(s: Snapshot, li: Int, end: Int, sub: String): UiNode? {
        val k = fz(sub)
        for (i in li + 1 until end) {
            val n = s.nodes[i]
            if (n.isControl() || n.isEdit()) continue
            if (n.texts().any { fz(it) == k }) return n
        }
        return null
    }

    private fun locateSub(s: Snapshot, r: SubField): UiNode? {
        val li = s.findLabel(r.q)
        if (li < 0) return null
        val end = s.blockEnd(li)
        val a = findSub(s, li, end, r.sub) ?: return null
        return s.editFor(li, end, a)
    }

    private fun doSubField(r: SubField, s: Snapshot, atEnd: Boolean, overwrite: Boolean): Res? {
        val e = locateSub(s, r)
        if (e == null) return notFound(r, atEnd, "maydon (\"${r.sub}\") topilmadi")
        return Res(listOf(fillEdit(r.label, e, r.value, overwrite) { s2 -> locateSub(s2, r) }))
    }

    // ---------------------------------------------------------------- ro'yxatdan nom tanlash (mahalla / ko'cha)
    private fun key(n: UiNode) = "${n.cls}|${n.left},${n.top},${n.right},${n.bottom}|${n.label()}"

    private fun pickOpener(s: Snapshot, li: Int, end: Int): UiNode? {
        val label = s.nodes[li]
        val inBlock = (li + 1 until end).map { s.nodes[it] }
        val ok = inBlock.filter { !s.isNav(it) && it.top >= label.bottom - 6 }
        ok.firstOrNull { it.clickable && it.enabled && !it.isControl() }?.let { return it }
        ok.firstOrNull { it.isEdit() }?.let { return it }
        return ok.firstOrNull { it.label().isNotEmpty() }
    }

    private fun currentValueLine(s: Snapshot, r: Select): Pair<Boolean, String>? {
        val li = s.findLabel(r.q, r.titleStart)
        if (li < 0) return null
        val vals = s.valueTexts(li, s.blockEnd(li))
        val hit = vals.firstOrNull { nameMatch(it, r.target) == 2 }
        return if (hit != null) true to hit else false to (vals.firstOrNull() ?: "")
    }

    private fun doSelect(r: Select, s0: Snapshot, atEnd: Boolean): Res? {
        val li = s0.findLabel(r.q, r.titleStart)
        if (li < 0) return notFound(r, atEnd)
        val end = s0.blockEnd(li)
        if (!s0.blockClosed(li) && !atEnd) return null // tanlov qatori hali to'liq ko'rinmagan: skroldan keyin
        val cur = currentValueLine(s0, r)!!
        if (cur.first) return Res(listOf("OK ${r.label} (allaqachon tanlangan: ${cur.second.take(40)})"), false)

        val opener = pickOpener(s0, li, end)
            ?: return Res(listOf("! ${r.label}: ochuvchi element topilmadi - TASHXIS bosing"), false)
        val before = s0.nodes.map { key(it) }.toHashSet()
        if (!opener.visible) { ui.reveal(opener); ui.sleep(300) }
        if (!press(opener)) return Res(listOf("! ${r.label}: ro'yxatni ochib bo'lmadi"), false)

        // Ro'yxat ochilishini kutamiz: daraxtda yangi tugunlar paydo bo'lishi kerak.
        var s1: Snapshot? = null
        for (k in 0 until 8) {
            ui.sleep(350)
            check()
            val t = fresh()
            if (t.nodes.any { key(it) !in before }) { s1 = t; break }
        }
        if (s1 == null) return Res(listOf("! ${r.label}: ro'yxat ochilmadi (ekran o'zgarmadi)"), false)

        fun newNodes(t: Snapshot) = t.nodes.filter { key(it) !in before }
        val searchBox = newNodes(s1).firstOrNull { it.isEdit() && it.enabled }
        val seenTexts = LinkedHashSet<String>()
        var picked: UiNode? = null
        var pickedText = ""
        var similar: List<String> = emptyList()
        var ambiguous: List<String> = emptyList()

        fun evaluate(t: Snapshot): Boolean {
            val cands = newNodes(t).filter { !it.isEdit() && it.label().isNotEmpty() && parseLabel(it.label()) == null }
            val byText = LinkedHashMap<String, UiNode>()
            for (c in cands) { seenTexts.add(c.label()); byText.putIfAbsent(c.label(), c) }
            val exact = byText.filter { nameMatch(it.key, r.target) == 2 }
            val near = byText.filter { nameMatch(it.key, r.target) == 1 }
            if (exact.size == 1) { picked = exact.values.first(); pickedText = exact.keys.first(); return true }
            if (exact.size > 1) { ambiguous = exact.keys.toList(); return true }
            similar = near.keys.toList()
            return false
        }

        var decided = false
        if (searchBox != null) {
            for (q in searchQueries(r.target)) {
                check()
                val node = newNodes(fresh()).firstOrNull { it.isEdit() } ?: searchBox
                ui.setText(node, q)
                ui.sleep(900)
                val t = fresh()
                if (evaluate(t)) { decided = true; break }
            }
        } else {
            // Qidiruv maydoni yo'q: oddiy ro'yxat. Ro'yxatni aylantirib ko'ramiz.
            if (evaluate(s1)) decided = true
            var tries = 0
            while (!decided && tries < 12) {
                check()
                val before2 = fresh().signature()
                if (!ui.scroll(true) { it.visible && key(it) !in before }) break
                ui.sleep(500)
                val t = fresh()
                if (t.signature() == before2) break
                if (evaluate(t)) decided = true
                tries++
            }
        }

        if (ambiguous.isNotEmpty()) {
            return Res(listOf("! ${r.label}: bir nechta aynan mos natija (${ambiguous.take(4).joinToString(" | ") { it.take(30) }}) - TANLANMADI, o'zing tanla"), false, abort = true)
        }
        val pk = picked
        if (pk == null) {
            val tail = if (similar.isNotEmpty()) " O'xshash (avtomatik tanlanmadi): ${similar.take(4).joinToString(" | ") { it.take(30) }}." else ""
            ui.log("${r.label}: ro'yxatda ko'rilgan matnlar: ${seenTexts.take(25).joinToString(" | ") { it.take(30) }}")
            return Res(listOf("! ${r.label}: ro'yxatda aynan mos natija topilmadi.$tail Ro'yxat ochiq qoldi"), false, abort = true)
        }

        // Tanlash: nomzodni yoki uning bosiladigan otasini bosamiz.
        if (!press(pk)) return Res(listOf("! ${r.label}: '${pickedText.take(30)}' topildi, lekin bosilmadi. Ro'yxat ochiq qoldi"), false, abort = true)

        // To'g'riligini tekshirish: ro'yxat yopilib, maydonda aynan shu nom ko'rinishi kerak.
        for (k in 0 until 8) {
            ui.sleep(400)
            check()
            val t = fresh()
            val now = currentValueLine(t, r)
            if (now != null && now.first) {
                val still = t.nodes.any { it.isEdit() && key(it) !in before && it.enabled }
                if (!still) return Res(listOf("OK ${r.label} (tanlandi: ${now.second.take(40)})"))
            }
        }
        return Res(
            listOf("! ${r.label}: '${pickedText.take(30)}' bosildi, lekin maydonda aynan shu nom ko'rinmadi - tekshir"),
            true, abort = true
        )
    }

    // ---------------------------------------------------------------- TASDIQLASH
    private fun pendingConfirms(s: Snapshot): List<Triple<String, UiNode, UiNode?>> {
        val out = ArrayList<Triple<String, UiNode, UiNode?>>()
        for (m in s.markers()) {
            val qi = s.labelBefore(s.indexOf(m))
            val code = if (qi >= 0) s.parsed(qi).code else "?"
            out.add(Triple(code, m, s.confirmButtonFor(m)))
        }
        return out
    }

    /**
     * Faqat foydalanuvchi TASDIQLASH tugmasini bosgandan keyin: "Avtomatik to'ldirildi" yozuvi va yonida
     * "Tasdiqlash" tugmasi bor savollarni BIR MARTA tasdiqlaydi va natijani tekshiradi.
     */
    fun confirm(n: Int): RunResult {
        val handled = LinkedHashMap<String, String>()
        var note: String? = null
        try {
            scrollTop()
            var prev: String? = null
            for (guard in 0 until 80) {
                val s = need(n)
                var pressedOne = false
                for ((code, _, btn) in pendingConfirms(s)) {
                    val key = "$n|$code"
                    if (handled.containsKey(key)) continue
                    if (btn == null) {
                        handled[key] = "- $code: Avtomatik to'ldirildi yozuvi bor, lekin Tasdiqlash tugmasi yo'q (allaqachon tasdiqlangan bo'lishi mumkin)"
                        continue
                    }
                    if (key in state.pressed) {
                        handled[key] = "! $code: avval bosilgan, lekin hamon tasdiqlanmagan ko'rinadi - qayta bosilmadi, o'zing tekshir"
                        continue
                    }
                    if (!btn.enabled) {
                        handled[key] = "! $code: Tasdiqlash tugmasi o'chirilgan (bloklangan) - sababi: shart bajarilmagan bo'lishi mumkin"
                        continue
                    }
                    state.pressed.add(key)
                    if (!btn.visible) { ui.reveal(btn); ui.sleep(300) }
                    if (!press(btn)) {
                        handled[key] = "! $code: Tasdiqlash bosilmadi (bosish amali bajarilmadi)"
                        pressedOne = true
                        break
                    }
                    ui.sleep(700)
                    val s2 = fresh()
                    val still = pendingConfirms(s2).any { it.first == code && it.third != null }
                    handled[key] = if (!still) "OK $code: tasdiqlandi (Tasdiqlash tugmasi yo'qoldi)"
                    else "! $code: bosildi, lekin Tasdiqlash tugmasi hamon ko'rinadi - ilova qabul qilmagan bo'lishi mumkin, o'zing tekshir"
                    pressedOne = true
                    break // ekran o'zgardi: qaytadan o'qiymiz
                }
                if (pressedOne) continue
                val sig = s.signature()
                if (sig == prev) break
                prev = sig
                if (!ui.scroll(true)) break
                ui.sleep(500)
            }
        } catch (e: Abort) {
            note = e.message
        }
        val lines = ArrayList(handled.values)
        if (lines.isEmpty() && note == null) {
            lines.add("- Bu sahifada tasdiqlanadigan \"Avtomatik to'ldirildi\" maydon yo'q (xato emas: hammasi tasdiqlangan yoki talab qilinmaydi)")
        }
        return RunResult(lines, note)
    }

    // ---------------------------------------------------------------- TASHXIS
    /** Hech narsani o'zgartirmaydi: ekrandagi savollar, variantlar, ularning holati va qoidalar nimani ko'rishi. */
    fun explain(n: Int): RunResult {
        val out = ArrayList<String>()
        var note: String? = null
        try {
            scrollTop()
            val seen = LinkedHashSet<String>()
            var prev: String? = null
            for (i in 0 until 25) {
                val s = need(n)
                for (l in s.describe()) if (seen.add(l)) out.add(l)
                val sig = s.signature()
                if (sig == prev) break
                prev = sig
                if (!ui.scroll(true)) break
                ui.sleep(500)
            }
        } catch (e: Abort) {
            note = e.message
        }
        return RunResult(out, note)
    }
}
