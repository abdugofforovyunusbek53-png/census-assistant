package verify

import uz.anketayordamchi.*

var failures = 0
var passes = 0
var current = ""

fun check(what: String, cond: Boolean, extra: () -> String = { "" }) {
    if (cond) { passes++ } else { failures++; println("  FAIL [$current] $what ${extra()}") }
}

fun test(name: String, body: () -> Unit) {
    current = name
    println("== $name")
    try { body() } catch (e: Throwable) { failures++; println("  EXCEPTION [$name] $e"); e.printStackTrace(System.out) }
}

fun RunResult.line(key: String): String? = lines.firstOrNull { it.contains(key) }
fun RunResult.dump(): String = lines.joinToString("\n    ", "\n    ") + (if (note != null) "\n    NOTE: $note" else "")

val ST = AutoSettings("yaxshi", "Ma'rifat", "I. Karimov")

fun radios(group: String, vararg texts: String, sel: Int = -1, style: String = "own", multi: Boolean = false, othersEnabled: Boolean = true): List<ROpt> =
    texts.mapIndexed { i, t -> ROpt(group, t, i == sel, enabled = othersEnabled || i == sel, multi = multi, style = style) }

// ---------------------------------------------------------------- sahifalar
fun page1(mahalla: List<String>, kocha: List<String>, mValue: String = "", kValue: String = "", uy: String = ""): FakeApp {
    val rows = ArrayList<Row>()
    rows.add(RLabel("0.0 Tashrif buyurilgan obyektning joylashuv turini ko'rsating. *"))
    rows.addAll(radios("0.0", "Bozor", "Savdo majmuasi", "Boshqa joy"))
    rows.add(RLabel("0.1 Joylashuv joyining xususiyatlari (subyektning tuzilishi) *"))
    rows.addAll(radios("0.1", "Bino:\ng'ishtdan qurilgan inshoot yoki mustahkam devor bilan o'ralgan hudud", "Kiosk / konteyner / yengil konstruksiya", "Bozor rastasi / savdo stoli"))
    rows.add(RLabel("0.2 Subyekt hududiga kirish qanday tashkil qilingan? *"))
    rows.addAll(radios("0.2", "Mijozlar uchun alohida kirish joyi (eshik/darvoza)", "Mijozlar uchun umumiy kirish joyi (eshik/darvoza)", "Mijozlar uchun kirish joyi mavjud emas"))
    rows.add(RLabel("0.3 Mahalla *"))
    rows.add(RSelect("mahalla", mValue, mahalla))
    rows.add(RLabel("0.3 Ko'cha"))
    rows.add(RSelect("kocha", kValue, kocha))
    rows.add(RLabel("0.3 Uy raqami yoki mo'ljal (ko'cha/uy raqami noma'lum bo'lsa - bozor nomi, rasta raqami) *"))
    rows.add(REdit("uy", uy))
    rows.add(RLabel("0.4 Tashrifingiz vaqtida subyekt ochiq edimi? *"))
    rows.addAll(radios("0.4", "Ha, ochiq", "Yo'q, tashrif vaqtida yopiq", "Ha, ochiq, lekin javob berishdan bosh tortdi"))
    return FakeApp(1, rows)
}

val HY = "‑" // buzilmaydigan chiziqcha: "veb-sayt" ning boshqa ko'rinishi

fun page2(style15: String = "own", s15sel: Int = 1, s19: String = "sibling", other19On: Int = -1, auto: Boolean = true,
          autoWorks: Boolean = true, markerOnly: Boolean = false): FakeApp {
    val rows = ArrayList<Row>()
    rows.add(RLabel("1.1 Subyektning (yoki u tegishli bo'lgan korxonaning) yuridik nomini ko'rsating. *"))
    rows.add(RAuto("1.1", auto, autoWorks, markerOnly))
    rows.add(RLabel("1.4 Ushbu subyekt qaysi turga kiradi? *"))
    rows.addAll(radios("1.4", "Alohida mustaqil korxona yoki tashkilot\nyoki mustaqil faoliyat yurituvchi jismoniy shaxs", "Korxonaning asosiy bosh ofisi", "Korxona/tashkilotning filiali yoki bo'linmasi"))
    rows.add(RLabel("1.5 Subyektning huquqiy maqomi qanday? *"))
    val st = if (style15 == "nosignal") "nosignal" else style15
    val o15 = listOf("Mas'uliyati cheklangan jamiyat", "O'zini o'zi band qilgan shaxs", "Norasmiy (ro'yxatdan o'tgan huquqiy maqomi yo'q)")
    rows.addAll(o15.mapIndexed { i, t ->
        ROpt("1.5", t, i == s15sel, enabled = (i == s15sel) || style15 == "enabled", style = if (style15 == "enabled") "own" else st)
    })
    rows.add(RAuto("1.5", auto, autoWorks, markerOnly))
    rows.add(RLabel("1.5d Subyekt o'z faoliyatida qanday to'lov usullaridan foydalanadi? (bir nechta javob) *"))
    rows.addAll(radios("1.5d", "Naqd pul orqali", "Plastik karta orqali (POS terminal)", "Plastik karta orqali (P2P, to'lov ilovalari, QR-kod)", "Hisob raqamiga pul o'tkazish orqali (bank o'tkazmasi)", "Boshqa", multi = true))
    rows.add(RLabel("1.9 Subyekt internetdan foydalanadimi? (bir nechta javob) *"))
    val t19 = listOf(
        "Ha, ijtimoiy tarmoqlarda muloqot uchun foydalanadi (Facebook, Telegram, Instagram, WhatsApp va h.k.)",
        "Ha, ijtimoiy tarmoqlarda savdo uchun foydalanadi (Facebook, Telegram, Instagram, WhatsApp va h.k.)",
        "Ha, ma'lumotlar joylashtirilgan veb${HY}sahifadan foydalanadi",
        "Ha, internet orqali savdo qilishga mo'ljallangan veb${HY}sahifa yoki platformadan foydalanadi (Uzum, Yandex, OLX va h.k.)",
        "Yo'q, veb${HY}sayt va ijtimoiy tarmoqlar mavjud emas"
    )
    rows.addAll(t19.mapIndexed { i, t -> ROpt("1.9", t, i == other19On, multi = true, style = s19) })
    rows.add(RLabel("1.10 Subyektning amaldagi telefon raqamini kiriting. *"))
    rows.add(REdit("tel", "", hint = "+998 (99) 999-99-99"))
    return FakeApp(2, rows)
}

fun page3(y21: String, pending21: Boolean, other26On: Boolean = false): FakeApp {
    val rows = ArrayList<Row>()
    rows.add(RLabel("2.1 Subyekt qachon faoliyat boshlaganini ko'rsating. *"))
    rows.add(REdit("yil", y21))
    rows.add(RAuto("2.1", pending21))
    rows.add(RLabel("2.3a Subyekt amalga oshirayotgan asosiy faoliyat turi. *"))
    rows.add(REdit("faoliyat", "Yengil avtotransport"))
    rows.add(RLabel("2.4 Ushbu subyekt asosan qaysi toifadagi mijozlarga xizmat ko'rsatadi? *"))
    rows.addAll(radios("2.4", "Asosan aholi / uy xo'jaliklari", "Asosan boshqa korxonalar (shu jumladan davlat va boshqa tashkilotlar)", "Aholi / uy xo'jaliklari va boshqa korxonalar (deyarli teng ulushda)"))
    rows.add(RLabel("2.6 Subyekt bevosita qaysi tashqi savdo faoliyatlarini muntazam amalga oshiradi? (bir nechta javobni tanlash mumkin) *"))
    val o26 = listOf("Tovarlar importi", "Xizmatlar importi", "Tovarlar eksporti", "Xizmatlar eksporti", "Import ham, eksport ham yo'q")
    rows.addAll(o26.mapIndexed { i, t -> ROpt("2.6", t, other26On && i == 2, multi = true) })
    return FakeApp(3, rows)
}

fun page4(a: String = "", b: String = "", acceptSet: Boolean = true, acceptPaste: Boolean = true, acceptAny: Boolean = true): FakeApp {
    val rows = ArrayList<Row>()
    rows.add(RLabel("3.1 2025 yilda ushbu subyektdagi barcha yollangan va yollanmagan ishlovchilarning o'rtacha soni qancha bo'lgan? *"))
    rows.add(RTwo("Jami *", "shundan,\nayollar *", a, b, acceptSet, acceptPaste, acceptAny))
    return FakeApp(4, rows)
}

fun page6(commentCode: String = "5.5d", withEdit: Boolean = true, commentValue: String = ""): FakeApp {
    val rows = ArrayList<Row>()
    rows.add(RLabel("5.4c Subyekt (yoki u tegishli bo'lgan korxona) O'zbekiston Respublikasining ro'yxatga olish organlari yoki soliq organlarida rasman ro'yxatdan o'tganmi? *"))
    rows.addAll(radios("5.4c", "Ha", "Yo'q", "Bilmayman (yoki umuman ma'lum emas)"))
    rows.add(RLabel("5.5 Kelgusida subyekt qaysi usulda so'rovdan o'tishni afzal ko'radi? *"))
    rows.addAll(radios("5.5", "Onlayn: elektron pochta havolasi orqali internet savolnoma", "Telefon orqali", "Yuzma${HY}yuz intervyu"))
    rows.add(RLabel("5.5a Respondentning familiyasi, ismi va otasining ismi. *"))
    rows.add(REdit("fio", "FAMILIYA ISM OTASI"))
    rows.add(RLabel("5.5b Respondentning lavozimi. *"))
    rows.addAll(radios("5.5b", "Egasi / direktor / rahbar / eng yuqori lavozimli xodim", "O'rta bo'g'in rahbari / bosh hisobchi / boshqaruvchi", "Oddiy yoki kichik lavozimli xodim (yordamchi hisobchi)", "Yuqoridagi variantlardan hech biri", sel = 0, othersEnabled = false))
    rows.add(RLabel("5.5c Respondentning telefon raqami *"))
    rows.add(REdit("tel", "", hint = "+998 (99) 999-99-99"))
    rows.add(RLabel("$commentCode Izoh"))
    if (withEdit) rows.add(REdit("izoh", commentValue))
    return FakeApp(6, rows)
}

fun eng(app: FakeApp, st: AutoSettings = ST, state: EngineState = EngineState()) = Engine(app, st, state)

fun edit(app: FakeApp, key: String) = app.rows.filterIsInstance<REdit>().first { it.key == key }
fun sel(app: FakeApp, key: String) = app.rows.filterIsInstance<RSelect>().first { it.key == key }

// ---------------------------------------------------------------- sinovlar
fun main() {
    test("nameMatch / searchQueries") {
        check("m.f.y. Ma'rifat aniq", nameMatch("m.f.y. Ma'rifat", "Ma'rifat") == 2)
        check("curly apostrof aniq", nameMatch("Ma’rifat", "Ma'rifat") == 2)
        check("ʻ va bosh harf", nameMatch("MAʻRIFAT", "Ma'rifat") == 2)
        check("qavs ichida", nameMatch("Ma'rifat (Andijon sh.)", "Ma'rifat") == 2)
        check("Ma'rifat-2 faqat o'xshash", nameMatch("Ma'rifat-2", "Ma'rifat") == 1)
        check("Ma'rifatchi mos emas", nameMatch("Ma'rifatchi", "Ma'rifat") == 0)
        check("I.Karimov aniq", nameMatch("I.Karimov", "I. Karimov") == 2)
        check("I. Karimov ko'chasi aniq", nameMatch("I. Karimov ko'chasi", "I. Karimov") == 2)
        check("Karimov I. aniq", nameMatch("Karimov I.", "I. Karimov") == 2)
        check("Islom Karimov mos emas", nameMatch("Islom Karimov", "I. Karimov") == 0)
        check("A.Karimov mos emas", nameMatch("A.Karimov", "I. Karimov") == 0)
        check("I.Karimov-2 faqat o'xshash", nameMatch("I.Karimov-2", "I. Karimov") == 1)
        val q1 = searchQueries("Ma'rifat")
        check("so'rov: rifat", "rifat" in q1, { q1.toString() })
        check("so'rov: curly", "Ma’rifat" in q1, { q1.toString() })
        check("so'rov: Marifat", "Marifat" in q1, { q1.toString() })
        val q2 = searchQueries("I. Karimov")
        check("so'rov: I.Karimov", "I.Karimov" in q2, { q2.toString() })
        check("so'rov: Karimov", "Karimov" in q2, { q2.toString() })
        check("mask raqam", mask("32205771230044") == "<raqam:14>")
    }

    test("1-bet: mahalla + ko'cha qidiruv (ilova xom qidiruvi apostrofga sezgir), tanlash va tekshirish") {
        val app = page1(
            listOf("Navbahor", "Ma’rifat-2", "Ma’rifat", "Ma’rifatchi"),
            listOf("A.Karimov", "I.Karimov", "Islom Karimov", "Yodgorov")
        )
        val r = eng(app).apply(1)
        println(r.dump())
        check("mahalla tanlandi", sel(app, "mahalla").value == "Ma’rifat", { sel(app, "mahalla").value })
        check("ko'cha tanlandi", sel(app, "kocha").value == "I.Karimov", { sel(app, "kocha").value })
        check("mahalla OK", r.line("0.3 Mahalla")?.startsWith("OK") == true)
        check("ko'cha OK", r.line("0.3 Ko'cha")?.startsWith("OK") == true)
        check("0.0 boshqa joy", app.opt("0.0", "Boshqa joy").on)
        check("0.2 umumiy", app.opt("0.2", "Mijozlar uchun umumiy kirish joyi (eshik/darvoza)").on)
        check("0.4 ha, ochiq", app.opt("0.4", "Ha, ochiq").on)
        check("0.1 bino", app.opt("0.1", app.rows.filterIsInstance<ROpt>().first { it.group == "0.1" }.text).on)
        check("uy raqamiga tegilmadi", edit(app, "uy").value == "")
        check("noto'g'ri nomzod bosilmadi", app.clicks.none { it.contains("Ma’rifatchi") || it.contains("Ma’rifat-2") || it.contains("A.Karimov") || it.contains("Islom") })
        check("natija xatosiz", r.note == null, { r.note ?: "" })
    }

    test("1-bet: faqat o'xshash natija bo'lsa TANLANMAYDI") {
        val app = page1(listOf("Ma’rifat-2", "Ma’rifatchi", "Navbahor"), listOf("I.Karimov"))
        val r = eng(app).apply(1)
        println(r.dump())
        check("mahalla bo'sh qoldi", sel(app, "mahalla").value == "", { sel(app, "mahalla").value })
        check("xabar: topilmadi", r.line("0.3 Mahalla")?.contains("topilmadi") == true)
        check("xabar: o'xshash ko'rsatildi", r.line("0.3 Mahalla")?.contains("O'xshash") == true)
        check("ro'yxat ochiq qolgani aytildi", r.note != null)
        check("ko'chaga o'tilmadi (adashmaslik uchun)", sel(app, "kocha").value == "")
    }

    test("1-bet: ikki aniq natija - tanlanmaydi") {
        val app = page1(listOf("Ma’rifat"), listOf("I. Karimov", "I. Karimov ko'chasi"))
        val r = eng(app).apply(1)
        println(r.dump())
        check("ko'cha bo'sh", sel(app, "kocha").value == "", { sel(app, "kocha").value })
        check("noaniqlik xabari", r.line("0.3 Ko'cha")?.contains("bir nechta") == true)
    }

    test("1-bet: mahalla allaqachon tanlangan - ro'yxat ochilmaydi") {
        val app = page1(listOf("Ma’rifat"), listOf("I.Karimov"), mValue = "m.f.y. Ma'rifat")
        val r = eng(app).apply(1)
        println(r.dump())
        check("ochilish soni 1 (faqat ko'cha)", app.openCount == 1, { app.openCount.toString() })
        check("mahalla OK allaqachon", r.line("0.3 Mahalla")?.contains("allaqachon") == true)
    }

    test("1-bet: qidiruv maydoni yo'q (oddiy ro'yxat)") {
        val app = page1(listOf("Navbahor", "Ma’rifat"), listOf("I.Karimov"))
        val r0 = app.rows.filterIsInstance<RSelect>()
        val app2 = FakeApp(1, app.rows.map { if (it is RSelect) RSelect(it.key, it.value, it.items, search = false) else it })
        val r = eng(app2).apply(1)
        println(r.dump())
        check("mahalla", sel(app2, "mahalla").value == "Ma’rifat", { sel(app2, "mahalla").value })
        check("ko'cha", sel(app2, "kocha").value == "I.Karimov", { sel(app2, "kocha").value })
    }

    for (style in listOf("own", "stateDesc", "selectedOnly", "parent")) {
        test("2-bet: 1.5 'O'zini o'zi band qilgan shaxs' tanlangan holat turi = $style") {
            val app = page2(style15 = style)
            val r = eng(app).apply(2)
            println(r.dump())
            check("1.5 OK", r.line("1.5 Huquqiy")?.startsWith("OK") == true, { r.line("1.5 Huquqiy") ?: "yo'q" })
            check("1.5 ga bosilmadi", app.clicks.none { it.startsWith("opt:1.5:") })
        }
    }

    test("2-bet: 1.5 holati ko'rinmasa - OK DEMAYDI, halol xabar") {
        val app = page2(style15 = "nosignal")
        val r = eng(app).apply(2)
        println(r.dump())
        val l = r.line("1.5 Huquqiy") ?: ""
        check("OK emas", !l.startsWith("OK"), { l })
        check("'!' bilan boshlanadi", l.startsWith("!"), { l })
        check("ehtimol tanlangan eslatmasi", l.contains("ehtimol"), { l })
    }

    test("2-bet: 1.5 hech narsa tanlanmagan va yoqiq bo'lsa - tanlaydi va tekshiradi") {
        val app = page2(style15 = "enabled", s15sel = -1)
        val r = eng(app).apply(2)
        println(r.dump())
        check("1.5 tanlandi", app.opt("1.5", "O'zini o'zi band qilgan shaxs").on)
        check("1.5 OK", r.line("1.5 Huquqiy")?.startsWith("OK") == true, { r.line("1.5 Huquqiy") ?: "" })
    }

    test("2-bet: 1.9 oxirgi variant (chiziqcha U+2011, matn yonida, ekrandan pastda) tanlanadi") {
        val app = page2(s19 = "sibling")
        val r = eng(app).apply(2)
        println(r.dump())
        val last = app.rows.filterIsInstance<ROpt>().first { it.group == "1.9" && it.text.startsWith("Yo'q") }
        check("1.9 tanlandi", last.on)
        check("1.9 OK", r.line("1.9 Internet")?.startsWith("OK") == true, { r.line("1.9 Internet") ?: "" })
        check("1.9 ga aynan 1 marta bosildi", app.clicks.count { it.startsWith("opt:1.9:") } == 1, { app.clicks.toString() })
        check("1.4", app.opt("1.4", app.rows.filterIsInstance<ROpt>().first { it.group == "1.4" }.text).on)
        check("1.5d naqd", app.opt("1.5d", "Naqd pul orqali").on)
        check("1.4 xatosiz yakun", r.note == null, { r.note ?: "" })
    }

    for (style in listOf("own", "stateDesc", "parent")) {
        test("2-bet: 1.9 variant matni/holati turi = $style") {
            val app = page2(s19 = style)
            val r = eng(app).apply(2)
            check("1.9 OK ($style)", r.line("1.9 Internet")?.startsWith("OK") == true, { r.line("1.9 Internet") ?: r.dump() })
        }
    }

    test("2-bet: 1.9 da boshqa variant tanlangan bo'lsa - tegilmaydi") {
        val app = page2(s19 = "own", other19On = 0)
        val r = eng(app).apply(2)
        println(r.dump())
        check("1.9 '!' va bosilmadi", r.line("1.9 Internet")?.startsWith("!") == true && app.clicks.none { it.startsWith("opt:1.9:") })
    }

    test("2-bet: bosish ishlamasa (ilova holatni o'zgartirmaydi) - bitta bosish, halol xabar, sikl yo'q") {
        val app = page2(s19 = "own")
        val rows = app.rows.map { if (it is ROpt && it.group == "1.9" && it.text.startsWith("Yo'q")) ROpt("1.9", it.text, false, multi = true, ignoresClick = true) else it }
        val app2 = FakeApp(2, rows)
        val r = eng(app2).apply(2)
        println(r.dump())
        check("1.9 '!' va OK emas", r.line("1.9 Internet")?.startsWith("!") == true)
        check("aynan 1 marta bosildi", app2.clicks.count { it.startsWith("opt:1.9:") } == 1, { app2.clicks.toString() })
    }

    test("2-bet: ACTION_CLICK false qaytarsa - koordinata bilan bir marta") {
        val app = page2(s19 = "own")
        app.forceClickFalse = true
        val r = eng(app).apply(2)
        check("tap ishlatildi, lekin bosish baribir false => '!'", r.lines.any { it.startsWith("!") })
        check("tap soni cheklangan", app.taps <= 6, { app.taps.toString() })
    }

    test("TASDIQLASH: har bir maydon bir marta, skroldan keyin takror bosilmaydi") {
        val app = page2()
        val state = EngineState()
        val r1 = eng(app, ST, state).confirm(2)
        println(r1.dump())
        check("ikkita maydon (1.1, 1.5) bosildi", app.pressCount == 2, { "pressCount=${app.pressCount}" })
        check("1.1 OK", r1.line("1.1")?.startsWith("OK") == true)
        check("1.5 OK", r1.line("1.5")?.startsWith("OK") == true)
        val r2 = eng(app, ST, state).confirm(2)
        println(r2.dump())
        check("ikkinchi ishga tushirishda qayta bosilmadi", app.pressCount == 2, { "pressCount=${app.pressCount}" })
        check("xato emas, ma'lumot", r2.lines.all { it.startsWith("-") }, { r2.dump() })
        check("matn 'maydon yo'q'", r2.lines.firstOrNull()?.contains("tasdiqlanadigan") == true)
    }

    test("TASDIQLASH: ilova qabul qilmasa - bir marta bosiladi, keyin qayta bosilmaydi") {
        val app = page2(autoWorks = false)
        val state = EngineState()
        val r1 = eng(app, ST, state).confirm(2)
        println(r1.dump())
        check("1.1 va 1.5 bittadan bosildi", app.pressCount == 2, { "pressCount=${app.pressCount}" })
        check("'!' hamon ko'rinadi", r1.lines.all { it.startsWith("!") }, { r1.dump() })
        val r2 = eng(app, ST, state).confirm(2)
        println(r2.dump())
        check("qayta bosilmadi", app.pressCount == 2, { "pressCount=${app.pressCount}" })
        check("sababi aytildi", r2.lines.all { it.contains("qayta bosilmadi") }, { r2.dump() })
    }

    test("TASDIQLASH: tugmasiz marker - bosilmaydi, ma'lumot") {
        val app = page2(auto = false, markerOnly = true)
        val r = eng(app).confirm(2)
        println(r.dump())
        check("bosilmadi", app.pressCount == 0)
        check("ma'lumot qatori", r.lines.isNotEmpty() && r.lines.all { it.startsWith("-") }, { r.dump() })
    }

    test("TASDIQLASH: 'Avtomatik to'ldirildi' bo'lmagan sahifa - xato emas") {
        val app = page4()
        val r = eng(app).confirm(4)
        println(r.dump())
        check("xato emas", r.lines.all { it.startsWith("-") } && r.note == null, { r.dump() })
    }

    test("3-bet: 2.1 holati") {
        val a = page3("", false)
        val ra = eng(a).apply(3)
        check("bo'sh", ra.line("2.1")?.contains("bo'sh") == true && ra.line("2.1")!!.startsWith("!"), { ra.dump() })
        val b = page3("2025", true)
        val rb = eng(b).apply(3)
        check("tasdiqlanmagan", rb.line("2.1")?.contains("tasdiqlanmagan") == true, { rb.dump() })
        check("2.1 ga tegilmadi", b.pressCount == 0 && edit(b, "yil").value == "2025")
        val c = page3("2025", false)
        val rc = eng(c).apply(3)
        check("to'ldirilgan va tasdiqlangan", rc.line("2.1")?.startsWith("OK") == true, { rc.dump() })
        check("2.4 tanlandi", c.opt("2.4", "Asosan aholi / uy xo'jaliklari").on)
        check("2.6 tanlandi", c.opt("2.6", "Import ham, eksport ham yo'q").on)
        check("2.3a ga tegilmadi", edit(c, "faoliyat").value == "Yengil avtotransport")
        println(rc.dump())
    }

    test("3-bet: 2.6 da boshqa belgi turgan bo'lsa tegilmaydi") {
        val app = page3("2025", false, other26On = true)
        val r = eng(app).apply(3)
        println(r.dump())
        check("2.6 '!'", r.line("2.6")?.startsWith("!") == true)
        check("2.6 bosilmadi", app.clicks.none { it.startsWith("opt:2.6") })
    }

    test("4-bet: xodimlar soni (Jami=1, Ayollar=0) yonma-yon maydonlar") {
        val app = page4()
        val r = eng(app).staff(4, 0)
        println(r.dump())
        val two = app.rows.filterIsInstance<RTwo>().first()
        check("Jami=1", two.a == "1", { two.a })
        check("Ayollar=0", two.b == "0", { two.b })
        check("ikkala OK", r.lines.all { it.startsWith("OK") } && r.lines.size == 2, { r.dump() })
        val r2 = eng(app).staff(4, 0)
        println(r2.dump())
        check("qayta bosilganda 'allaqachon to'g'ri', yozilmadi", r2.lines.all { it.startsWith("OK") && it.contains("allaqachon") }, { r2.dump() })
    }

    test("4-bet: noto'g'ri qiymat foydalanuvchi tugmasi bilan tuzatiladi; ayol 1") {
        val app = page4(a = "2", b = "5")
        val r = eng(app).staff(4, 1)
        println(r.dump())
        val two = app.rows.filterIsInstance<RTwo>().first()
        check("Jami=1", two.a == "1", { two.a })
        check("Ayollar=1", two.b == "1", { two.b })
    }

    test("4-bet: set e'tiborsiz qoldirilsa - paste zaxirasi, tekshiruv bilan") {
        val app = page4(acceptSet = false)
        val r = eng(app).staff(4, 0)
        println(r.dump())
        val two = app.rows.filterIsInstance<RTwo>().first()
        check("Jami=1 paste bilan", two.a == "1", { two.a })
        check("OK (paste)", r.lines.all { it.startsWith("OK") && it.contains("paste") }, { r.dump() })
    }

    test("4-bet: hech narsa yozilmasa - OK DEMAYDI") {
        val app = page4(acceptAny = false)
        val r = eng(app).staff(4, 0)
        println(r.dump())
        check("hammasi '!'", r.lines.size == 2 && r.lines.all { it.startsWith("!") }, { r.dump() })
    }

    test("6-bet: 5.4c, 5.5, 5.5b, izoh 'yaxshi'; 5.5a va 5.5c ga tegilmaydi") {
        val app = page6()
        val r = eng(app).apply(6)
        println(r.dump())
        check("5.4c Ha", app.opt("5.4c", "Ha").on)
        check("5.5 Yuzma-yuz", app.rows.filterIsInstance<ROpt>().first { it.group == "5.5" && it.text.startsWith("Yuzma") }.on)
        check("5.5b mos", r.line("5.5b")?.startsWith("OK") == true, { r.line("5.5b") ?: "" })
        check("izoh yozildi", edit(app, "izoh").value == "yaxshi", { edit(app, "izoh").value })
        check("izoh OK", r.line("5.5d")?.startsWith("OK") == true, { r.line("5.5d") ?: "" })
        check("5.5a tegilmadi", edit(app, "fio").value == "FAMILIYA ISM OTASI")
        check("5.5c tegilmadi", edit(app, "tel").value == "")
        check("5.5b ga bosilmadi", app.clicks.none { it.startsWith("opt:5.5b") })
        check("xatosiz yakun", r.note == null && r.lines.all { it.startsWith("OK") }, { r.dump() })
    }

    test("6-bet: izoh sarlavhasi boshqa kod bilan ('5.6 Izoh') sarlavha bo'yicha topiladi") {
        val app = page6(commentCode = "5.6")
        val r = eng(app).apply(6)
        println(r.dump())
        check("izoh yozildi", edit(app, "izoh").value == "yaxshi", { edit(app, "izoh").value })
    }

    test("6-bet: izoh maydoni to'lgan bo'lsa tegilmaydi; bo'sh sozlama - yozilmaydi") {
        val app = page6(commentValue = "oldin yozilgan")
        val r = eng(app).apply(6)
        println(r.dump())
        check("tegilmadi", edit(app, "izoh").value == "oldin yozilgan" && r.line("5.5d")?.startsWith("!") == true)
        val app2 = page6()
        val r2 = eng(app2, AutoSettings("", "Ma'rifat", "I. Karimov")).apply(6)
        check("izoh qoidasi yo'q", edit(app2, "izoh").value == "" && r2.line("5.5d") == null)
    }

    test("6-bet: izoh maydoni (EditText) yo'q - halol xabar") {
        val app = page6(withEdit = false)
        val r = eng(app).apply(6)
        println(r.dump())
        check("izoh '!'", r.line("5.5d")?.startsWith("!") == true, { r.dump() })
    }

    test("XAVFSIZLIK: tanlov qatori daraxtda bo'lmasa 'Keyingisi'/boshqa tugma bosilmaydi") {
        val rows = listOf<Row>(RLabel("0.3 Mahalla *"))
        val app = FakeApp(1, rows)
        val r = eng(app).apply(1)
        println(r.dump())
        check("hech narsa bosilmadi", app.clicks.isEmpty() && app.taps == 0, { app.clicks.toString() })
        check("Mahalla '!' (opener topilmadi yoki ko'rinmadi)", r.line("0.3 Mahalla")?.startsWith("!") == true, { r.dump() })
    }

    test("XAVFSIZLIK: Yakunlash/Keyingisi nomli variant ham bosilmaydi") {
        val rows = listOf<Row>(RLabel("0.4 Tashrifingiz vaqtida subyekt ochiq edimi? *"), ROpt("0.4", "Keyingisi", false), ROpt("0.4", "Ha, ochiq", false))
        val app = FakeApp(1, rows)
        val r = eng(app).apply(1)
        println(r.dump())
        check("faqat 'Ha, ochiq' bosildi", app.clicks == listOf("opt:0.4:Ha, ochiq"), { app.clicks.toString() })
    }

    test("STOP: ish o'rtasida to'xtaydi") {
        val app = page2()
        app.stopAtSleep = 3
        val r = eng(app).apply(2)
        println(r.dump())
        check("to'xtash sababi STOP", r.note?.contains("STOP") == true, { r.note ?: "" })
        val c = app.clicks.size
        check("STOP dan keyin amal qilinmadi (bosishlar kam)", c <= 3, { app.clicks.toString() })
    }

    test("Census oldingi planda emas - to'xtaydi") {
        val app = page2()
        app.foreground = false
        val r = eng(app).apply(2)
        check("note bor", r.note != null && app.clicks.isEmpty(), { r.dump() })
    }

    test("Sahifa mos kelmasa - to'xtaydi (noto'g'ri sahifaga yozmaydi)") {
        val app = page2()
        val r = eng(app).apply(3)
        check("sahifa o'zgardi", r.note?.contains("Sahifa o'zgardi") == true && app.clicks.isEmpty(), { r.dump() })
    }

    test("TASHXIS: hech narsa o'zgartirmaydi, variant holatini ko'rsatadi") {
        val app = page2(style15 = "stateDesc")
        val before = app.rows.filterIsInstance<ROpt>().map { it.on }
        val r = eng(app).explain(2)
        val after = app.rows.filterIsInstance<ROpt>().map { it.on }
        check("holat o'zgarmadi", before == after && app.clicks.isEmpty())
        check("1.5 savol ko'rsatildi", r.lines.any { it.startsWith("SAVOL 1.5 ") })
        check("variant holati yozildi", r.lines.any { it.contains("VARIANT yoqilgan=true(stateDesc)") }, { r.lines.take(12).joinToString("\n") })
        check("1.9 oxirgi variant ko'rindi", r.lines.any { it.contains("Yo'q, veb") })
        check("marker ko'rindi", r.lines.any { it.contains("MARKER") })
    }

    println("\n=== NATIJA: o'tdi=$passes, muvaffaqiyatsiz=$failures ===")
    if (failures > 0) System.exit(1)
}
