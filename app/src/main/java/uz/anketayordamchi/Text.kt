package uz.anketayordamchi

import java.util.Locale

/** Apostrof/tutuq belgisining barcha ko'rinishlari. */
private val APOS_CHARS = setOf(
    '\'', '’', '‘', 'ʻ', 'ʼ', '`', '´', '′', 'ʹ', '́'
)

/** Matnni solishtirish uchun: turli apostroflarni bitta ko'rinishga keltiradi, bo'shliqlarni yig'adi. */
fun norm(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when (c) {
                '’', '‘', 'ʻ', 'ʼ', '`', '´', '′', 'ʹ' -> '\''
                ' ', ' ', ' ' -> ' '
                else -> c
            }
        )
    }
    return sb.toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * "Yumshoq" kalit: faqat harf va raqamlar, kichik harfda.
 * Apostrof (har qanday), nuqta, vergul, tire (har xil turi), bo'shliq, yulduzcha va h.k. tashlanadi.
 * "Ma'rifat" == "Ma’rifat" == "Marifat"; "I. Karimov" == "I.Karimov" == "i karimov".
 */
fun fz(s: String?): String {
    if (s == null) return ""
    val sb = StringBuilder(s.length)
    for (c in s) if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
    return sb.toString()
}

/** So'zlarga ajratish: apostroflar so'z ichida yo'qoladi (Ma'rifat -> marifat), qolgan belgilar ajratgich. */
fun words(s: String): List<String> =
    s.lowercase(Locale.ROOT).filter { it !in APOS_CHARS }
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

class Parsed(val code: String, val title: String)

private val LABEL_RE = Regex("^(\\d+\\.\\d+[a-z]?)(?:\\.\\d+)?\\s+(\\S.*)$", RegexOption.DOT_MATCHES_ALL)

/** "5.5d Izoh" -> Parsed("5.5d", "Izoh"). Savol sarlavhasi bo'lmasa null. */
fun parseLabel(s: String): Parsed? {
    val m = LABEL_RE.find(norm(s)) ?: return null
    return Parsed(m.groupValues[1].lowercase(Locale.ROOT), m.groupValues[2])
}

/** Maskalash: 9 va undan uzun raqamlar (JShShIR, telefon) va GPS koordinatalari. */
fun mask(s: String): String =
    s.replace(Regex("\\d{9,}")) { "<raqam:${it.value.length}>" }
        .replace(Regex("\\d+\\.\\d{5,}"), "<koordinata>")

// ---------------------------------------------------------------- nom moslash (mahalla / ko'cha)

private val DECOR = setOf(
    "mfy", "m", "f", "y", "mahallasi", "mahalla", "kocha", "kochasi", "kucha", "kuchasi",
    "fuqarolar", "yigini"
)

private fun core(ws: List<String>): List<String> = ws.filter { it !in DECOR }

private fun containsSeq(hay: List<String>, needle: List<String>): Boolean {
    if (needle.isEmpty() || needle.size > hay.size) return false
    for (i in 0..hay.size - needle.size) {
        var ok = true
        for (j in needle.indices) if (hay[i + j] != needle[j]) { ok = false; break }
        if (ok) return true
    }
    return false
}

/**
 * 2 = aniq (bezak so'zlar "m.f.y.", "ko'chasi" va h.k. tashlangach, nom bir xil; "I.Karimov" == "Karimov I." ham)
 * 1 = faqat o'xshash (nom ichida bor, lekin qo'shimcha so'z bor) - AVTOMATIK TANLANMAYDI
 * 0 = mos emas.
 * Qavs, vergul, slesh, "|" bilan ajratilgan bo'laklar alohida tekshiriladi: "Ma'rifat (Andijon sh.)" aniq mos.
 */
fun nameMatch(text: String, target: String): Int {
    val t = core(words(target))
    if (t.isEmpty()) return 0
    var best = 0
    val segments = text.split(Regex("[(),/|]")) + listOf(text)
    for (seg in segments) {
        val c = core(words(seg))
        if (c.isEmpty()) continue
        if (c == t || c == t.reversed()) return 2
        if (containsSeq(c, t) || containsSeq(c, t.reversed())) best = 1
    }
    return best
}

/** Qidiruv so'rovlari: foydalanuvchi yozgani, apostrof/bo'shliq variantlari, qisqa shakl ("rifat", "Karimov"). */
fun searchQueries(target: String): List<String> {
    val t = norm(target)
    val out = LinkedHashSet<String>()
    out.add(t)
    if (t.contains('\'')) {
        out.add(t.replace('\'', '’'))
        out.add(t.replace('\'', 'ʻ'))
        out.add(t.replace("'", ""))
    }
    if (Regex("\\.\\s").containsMatchIn(t)) out.add(t.replace(Regex("\\.\\s+"), "."))
    if (Regex("\\.\\S").containsMatchIn(t)) out.add(t.replace(Regex("\\.(?=\\S)"), ". "))
    val parts = t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val longest = parts.maxByOrNull { it.length }
    if (longest != null && parts.size > 1 && longest.length >= 4) out.add(longest)
    // "Ma'rifat" -> "rifat" (apostrofdan keyingi qism)
    val aposParts = t.split(Regex("['’‘ʻʼ`´]")).filter { it.length >= 4 }
    if (aposParts.isNotEmpty()) out.add(aposParts.maxByOrNull { it.length }!!)
    return out.filter { it.isNotBlank() }
}
