package uz.anketayordamchi

/** Foydalanuvchi sozlamalari (telefonda lokal saqlanadi; kodga qattiq yozilmagan). */
class AutoSettings(
    val comment: String,
    val mahalla: String,
    val kocha: String,
    /** Ro'yxat qidiruviga yoziladigan qisqa so'z (masalan "fat"). Bo'sh bo'lsa, nomning o'zi va variantlari yoziladi. */
    val mahallaKey: String = "",
    val kochaKey: String = "",
    /** Keyingi uy raqami; -1 = o'chiq. */
    val houseNext: Int = -1,
    /** Uy raqami yozilgach chaqiriladi (hisoblagichni +1 qilish uchun). */
    val onHouseUsed: () -> Unit = {}
)

sealed class Rule(val label: String)

/** Savol kodi (q) bo'yicha variantni tanlaydi. Boshqa variant allaqachon tanlangan bo'lsa, tegmaydi. */
class Choice(
    val q: String, val match: String, val exact: Boolean, val exclusive: Boolean, label: String,
    val titleHas: String? = null
) : Rule(label)

/** Tanlangan variant kutilganga mosmi. setIfEmpty=true bo'lsa va hech narsa tanlanmagan, variant yoqilgan bo'lsa, tanlaydi. */
class Verify(
    val q: String, val expect: String, label: String,
    val setIfEmpty: Boolean = false, val titleHas: String? = null
) : Rule(label)

/** Ro'yxatdan (qidiruvli) nom tanlash: mahalla/ko'cha. */
class Select(
    val q: String, val titleStart: String, val target: String, label: String,
    val searchKey: String = ""
) : Rule(label)

/** Bo'sh matn maydoniga qiymat yozadi. To'lgan maydonga tegmaydi. */
class SetText(
    val q: String, val value: String, label: String, val titleHas: String? = null,
    val titleStart: String? = null, val onDone: (() -> Unit)? = null
) : Rule(label)

/** Yonma-yon maydonlardan biri (masalan 3.1: "Jami", "shundan, ayollar"). Foydalanuvchi tugmasi bosilgandagina ishlaydi. */
class SubField(val q: String, val sub: String, val value: String, label: String) : Rule(label)

/** Faqat holatni ko'rsatadi (2.1: to'ldirilganmi, tasdiqlanganmi). Hech narsa o'zgartirmaydi. */
class Status(val q: String, label: String) : Rule(label)

object Rules {
    /** TASDIQLASH tugmasi chiqadigan sahifalar. */
    val confirmPages = setOf(2, 3, 6)

    fun forPage(n: Int, st: AutoSettings): List<Rule> = when (n) {
        1 -> {
            val l = mutableListOf<Rule>(
                Choice("0.0", "boshqa joy", true, false, "0.0 Joylashuv turi -> Boshqa joy"),
                Choice("0.1", "g'ishtdan qurilgan", false, false, "0.1 -> Bino: g'ishtdan qurilgan"),
                Choice("0.2", "umumiy kirish joyi", false, false, "0.2 -> Umumiy kirish joyi"),
                Choice("0.4", "ha, ochiq", true, false, "0.4 -> Ha, ochiq")
            )
            if (st.houseNext >= 0) {
                l.add(
                    SetText(
                        "0.3", st.houseNext.toString(), "0.3 Uy raqami -> ${st.houseNext}",
                        titleStart = "uyraqami", onDone = st.onHouseUsed
                    )
                )
            }
            if (st.mahalla.isNotBlank() || st.mahallaKey.isNotBlank()) {
                l.add(Select("0.3", "mahalla", st.mahalla, "0.3 Mahalla -> ${st.mahalla.ifBlank { st.mahallaKey }}", st.mahallaKey))
            }
            if (st.kocha.isNotBlank() || st.kochaKey.isNotBlank()) {
                l.add(Select("0.3", "kocha", st.kocha, "0.3 Ko'cha -> ${st.kocha.ifBlank { st.kochaKey }}", st.kochaKey))
            }
            l
        }
        2 -> listOf(
            Choice("1.4", "alohida mustaqil korxona", false, false, "1.4 -> Alohida mustaqil korxona/shaxs"),
            Verify("1.5", "o'zini o'zi band qilgan", "1.5 Huquqiy maqom -> O'zini o'zi band qilgan shaxs", setIfEmpty = true),
            Choice("1.5d", "naqd pul orqali", true, false, "1.5d -> Naqd pul orqali"),
            // "|" bilan ajratilgan variantlar ketma-ket sinab ko'riladi (birinchi mos kelgani tanlanadi).
            Choice(
                "1.9",
                "yo'q, foydalanmadi|yo'q, foydalanmaydi|foydalanmadi|foydalanmaydi|yo'q, veb-sayt va ijtimoiy tarmoqlar mavjud emas|yo'q",
                false, true,
                "1.9 Internet -> Yo'q, foydalanmadi", titleHas = "internetdanfoydalan"
            )
        )
        3 -> listOf(
            Status("2.1", "2.1 Faoliyat boshlangan yil (faqat holati ko'rsatiladi)"),
            Choice("2.4", "asosan aholi / uy xo'jaliklari", true, false, "2.4 -> Asosan aholi / uy xo'jaliklari"),
            Choice("2.6", "import ham, eksport ham yo'q", true, true, "2.6 -> Import ham, eksport ham yo'q")
        )
        6 -> {
            val l = mutableListOf<Rule>(
                Choice("5.4c", "ha", true, false, "5.4c -> Ha"),
                Choice("5.5", "yuzma-yuz intervyu", true, false, "5.5 So'rovnoma usuli -> Yuzma-yuz intervyu"),
                Verify("5.5b", "egasi / direktor", "5.5b Lavozim: egasi/direktor (faqat tekshiriladi)")
            )
            if (st.comment.isNotBlank()) {
                l.add(SetText("5.5d", st.comment, "5.5d Izoh -> \"${st.comment}\"", titleHas = "izoh"))
            }
            l
        }
        else -> emptyList()
    }

    /** 4/6 sahifadagi tugma bosilganda: Jami=1, Ayollar=women. */
    fun staff(women: Int): List<Rule> = listOf(
        SubField("3.1", "Jami", "1", "3.1 Jami -> 1"),
        SubField("3.1", "shundan, ayollar", women.toString(), "3.1 Ayollar -> $women")
    )

    /** Foydalanuvchi o'zi bajaradigan ishlar (ilova ularga tegmaydi). */
    fun manual(n: Int): String = when (n) {
        1 -> "GPS tugmasi, rasmga olish (uy raqami: sozlamada yoqilgan bo'lsa avtomatik, aks holda qo'lda)"
        2 -> "STIR/JShShIR qidirish, 1.10 telefon (chiqqan tasdiq oynalari avtomatik tasdiqlanadi)"
        3 -> "2.3a faoliyat turi, 2.3d tavsif, 2.3c (avto-to'ldirilganlar avtomatik tasdiqlanadi)"
        4 -> "3.1: respondentga mos tugmani bos (1 xodim, ayol 0 yoki 1) yoki qo'lda kirit"
        5 -> "4.1 xarajat va 4.2 aylanma (haqiqiy summalar)"
        6 -> "5.5a F.I.Sh., 5.5c telefon. Yakunlash'ni o'zing bos"
        else -> "-"
    }
}
