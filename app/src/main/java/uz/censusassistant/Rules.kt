package uz.censusassistant

/** Matnlarni solishtirish uchun: turli apostroflarni bitta ko'rinishga keltiradi. */
fun norm(s: String): String =
    s.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u02BB', '\'')
        .replace('\u02BC', '\'').replace('`', '\'')
        .replace(Regex("\\s+"), " ").trim()

sealed class Rule(val label: String)

/** Savol kodi (q) bo'yicha variantni tanlaydi. Boshqa variant allaqachon tanlangan bo'lsa, tegmaydi. */
class Choice(
    val q: String, val match: String, val exact: Boolean, val exclusive: Boolean, label: String
) : Rule(label)

/** Faqat tekshiradi: tanlangan variant kutilganga mosmi. Hech narsani o'zgartirmaydi. */
class Verify(val q: String, val expect: String, label: String) : Rule(label)

/** Ekranda shunday matn borligini tekshiradi (masalan, avtomatik to'ldirilgan mahalla). */
class Present(val token: String, label: String) : Rule(label)

/** Bo'sh matn maydoniga foydalanuvchi sozlagan matnni yozadi. To'lgan maydonga tegmaydi. */
class SetText(val q: String, val value: String, label: String) : Rule(label)

object Rules {
    fun forPage(n: Int, comment: String): List<Rule> = when (n) {
        1 -> listOf(
            Choice("0.0", "boshqa joy", true, false, "0.0 Joylashuv turi -> Boshqa joy"),
            Choice("0.1", "g'ishtdan qurilgan", false, false, "0.1 -> Bino: g'ishtdan qurilgan"),
            Choice("0.2", "umumiy kirish joyi", false, false, "0.2 -> Umumiy kirish joyi"),
            Choice("0.4", "ha, ochiq", true, false, "0.4 -> Ha, ochiq"),
            Present("ma'rifat", "Mahalla: Ma'rifat (faqat tekshiriladi)"),
            Present("i.karimov", "Ko'cha: I.Karimov (faqat tekshiriladi)")
        )
        2 -> listOf(
            Choice("1.4", "alohida mustaqil korxona", false, false, "1.4 -> Alohida mustaqil korxona/shaxs"),
            Verify("1.5", "o'zini o'zi band qilgan", "1.5 Huquqiy maqom: o'zini o'zi band qilgan (faqat tekshiriladi)"),
            Choice("1.5d", "naqd pul orqali", true, false, "1.5d -> Naqd pul orqali"),
            Choice("1.9", "yo'q, veb-sayt", false, true, "1.9 -> Yo'q, veb-sayt va ijtimoiy tarmoq yo'q")
        )
        3 -> listOf(
            Choice("2.4", "asosan aholi / uy xo'jaliklari", true, false, "2.4 -> Asosan aholi / uy xo'jaliklari"),
            Choice("2.6", "import ham, eksport ham yo'q", true, true, "2.6 -> Import ham, eksport ham yo'q")
        )
        6 -> {
            val l = mutableListOf<Rule>(
                Choice("5.4c", "ha", true, false, "5.4c -> Ha"),
                Verify("5.5b", "egasi / direktor", "5.5b Lavozim: egasi/direktor (faqat tekshiriladi)")
            )
            if (comment.isNotBlank()) l.add(SetText("5.5d", comment, "5.5d Izoh -> sening matning"))
            l
        }
        else -> emptyList()
    }

    /** Foydalanuvchi o'zi bajaradigan ishlar (ilova ularga tegmaydi). */
    fun manual(n: Int): String = when (n) {
        1 -> "GPS tugmasi, rasmga olish, 0.3 uy raqami/mo'ljal"
        2 -> "STIR/JShShIR qidirish, 1.10 telefon. Avto-to'ldirilganlarni ko'zdan kechirib TASDIQLASH ni bos"
        3 -> "2.3a faoliyat turi, 2.3d tavsif. 2.1 ni tekshirib TASDIQLASH ni bos"
        4 -> "3.1: haqiqatga mos tugmani bos (1 xodim, ayol 0 yoki 1) yoki qo'lda kirit"
        5 -> "4.1 xarajat va 4.2 aylanma (haqiqiy summalar)"
        6 -> "5.5 usul, 5.5a F.I.Sh., 5.5c telefon. Yakunlash'ni o'zing bos"
        else -> "-"
    }
}
