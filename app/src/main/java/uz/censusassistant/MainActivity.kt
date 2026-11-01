package uz.censusassistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var info: TextView
    private lateinit var pkgBox: LinearLayout
    private lateinit var dumpBox: LinearLayout
    private lateinit var output: TextView
    private lateinit var autoBtn: Button
    private lateinit var commentBox: EditText
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ca", Context.MODE_PRIVATE)
        pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        root.addView(label("Census Assistant v0.4 (DIAG + AVTO)", 20f, true))
        info = label("", 14f, false)
        root.addView(info)

        root.addView(button("1. Maxsus imkoniyatlarni ochish") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Cheklangan sozlama (Android 13): ilova ma'lumotlari") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        root.addView(label("2. Census ilovasi tanlangan bo'lishi kerak (uz.stat.census). Ro'yxat bo'sh bo'lsa, Census'ni bir marta oching va shu yerga qayting:", 14f, false))
        pkgBox = LinearLayout(this)
        pkgBox.orientation = LinearLayout.VERTICAL
        root.addView(pkgBox)

        root.addView(label("3. AVTO rejimi. Yoqilganda Census ilovasida har bir sahifada tepada panel chiqadi: KIRITISH bosilgandagina javoblar belgilanadi. STOP — favqulodda to'xtatish.", 14f, false))
        autoBtn = button("") { toggleAuto() }
        root.addView(autoBtn)

        root.addView(label("5.5d Izoh matni (ixtiyoriy). Bo'sh qolsa, izoh maydoniga hech narsa yozilmaydi:", 12f, false))
        commentBox = EditText(this)
        commentBox.setText(prefs.getString("comment", "") ?: "")
        root.addView(commentBox)
        root.addView(button("Izohni saqlash") {
            prefs.edit().putString("comment", commentBox.text.toString()).apply()
            Toast.makeText(this, "Saqlandi", Toast.LENGTH_SHORT).show()
        })

        root.addView(label("4. DIAG: Census ekranida DIAG tugmasini bosib dump oling.", 14f, false))
        root.addView(button("Oxirgi dump'ni ko'rsatish") { output.text = latestDump() })
        dumpBox = LinearLayout(this)
        dumpBox.orientation = LinearLayout.VERTICAL
        root.addView(button("Barcha dump'lar ro'yxati") { listDumps() })
        root.addView(dumpBox)
        root.addView(button("HAMMASINI birlashtirib nusxalash") {
            val files = allDumps()
            val sb = StringBuilder()
            for (f in files) {
                sb.append("===== ").append(f.name).append(" =====\n").append(f.readText()).append("\n\n")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dumps", sb.toString()))
            Toast.makeText(this, "${files.size} ta dump nusxalandi (${sb.length} belgi)", Toast.LENGTH_LONG).show()
        })
        root.addView(button("Ko'rsatilgan matnni nusxalash") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dump", output.text))
            Toast.makeText(this, "Nusxalandi", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Xatolik/AVTO logini ko'rsatish") { output.text = readLog() })

        output = TextView(this)
        output.typeface = Typeface.MONOSPACE
        output.textSize = 10f
        output.setTextIsSelectable(true)
        root.addView(output)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun toggleAuto() {
        val now = prefs.getBoolean("auto_on", false)
        if (!now && (prefs.getString("target", "") ?: "").isEmpty()) {
            Toast.makeText(this, "Avval Census ilovasini ro'yxatdan tanlang", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putBoolean("auto_on", !now).apply()
        refresh()
    }

    private fun refresh() {
        val target = prefs.getString("target", "") ?: ""
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = enabled.contains("$packageName/")
        val auto = prefs.getBoolean("auto_on", false)
        info.text = "Xizmat: " + (if (on) "YOQILGAN" else "o'chiq") +
            "\nTanlangan ilova: " + (if (target.isEmpty()) "tanlanmagan" else target) +
            "\nAVTO: " + (if (auto) "YOQILGAN" else "o'chiq")
        autoBtn.text = if (auto) "AVTO: YOQILGAN (o'chirish uchun bos)" else "AVTO: o'chiq (yoqish uchun bos)"
        pkgBox.removeAllViews()
        val recent = (prefs.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }
        if (recent.isEmpty()) {
            pkgBox.addView(label("(hali ilova aniqlanmadi - xizmatni yoqib, Census ilovasini oching)", 12f, false))
        }
        for (p in recent) {
            pkgBox.addView(button(p) {
                prefs.edit().putString("target", p).apply()
                refresh()
            })
        }
    }

    private fun allDumps(): List<File> =
        (getExternalFilesDir(null)?.listFiles() ?: emptyArray<File>())
            .filter { it.name.startsWith("dump_") }.sortedBy { it.name }

    private fun listDumps() {
        dumpBox.removeAllViews()
        val files = allDumps()
        if (files.isEmpty()) dumpBox.addView(label("Dump yo'q", 12f, false))
        for (f in files) {
            dumpBox.addView(button(f.name + " (" + (f.length() / 1024) + " KB)") {
                output.text = f.name + "\n\n" + f.readText().take(60000)
            })
        }
    }

    private fun latestDump(): String {
        val f = allDumps().lastOrNull() ?: return "Hali dump yo'q."
        return f.name + "\n\n" + f.readText().take(60000)
    }

    private fun readLog(): String {
        val f = File(getExternalFilesDir(null), "log.txt")
        return if (f.exists()) f.readText().takeLast(20000) else "Log bo'sh."
    }

    private fun label(t: String, size: Float, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setPadding(0, pad / 2, 0, pad / 2)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        return v
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }
}
