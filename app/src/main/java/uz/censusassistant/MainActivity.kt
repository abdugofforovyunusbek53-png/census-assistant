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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var info: TextView
    private lateinit var pkgBox: LinearLayout
    private lateinit var output: TextView
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ca", Context.MODE_PRIVATE)
        pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        root.addView(label("Census Assistant — 1-bosqich: diagnostika", 20f, true))
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

        root.addView(label("2. Census ilovasini tanlang. Avval Census ilovasini bir marta oching, keyin shu yerga qaytib, ro'yxatdan uning paketini bosing:", 14f, false))
        pkgBox = LinearLayout(this)
        pkgBox.orientation = LinearLayout.VERTICAL
        root.addView(pkgBox)

        root.addView(label("3. Census ilovasida har bir ekranda \"DIAG\" tugmasini bosing. Keyin quyidagilar:", 14f, false))
        root.addView(button("Oxirgi dump'ni ko'rsatish") { output.text = latestDump() })
        root.addView(button("Ko'rsatilgan matnni nusxalash") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dump", output.text))
            Toast.makeText(this, "Nusxalandi", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Xatolik logini ko'rsatish") { output.text = readLog() })

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

    private fun refresh() {
        val target = prefs.getString("target", "") ?: ""
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = enabled.contains("$packageName/")
        info.text = "Xizmat: " + (if (on) "YOQILGAN" else "o'chiq") +
            "\nTanlangan ilova: " + (if (target.isEmpty()) "tanlanmagan" else target)
        pkgBox.removeAllViews()
        val recent = (prefs.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }
        if (recent.isEmpty()) {
            pkgBox.addView(label("(hali ilova aniqlanmadi — xizmatni yoqib, Census ilovasini oching)", 12f, false))
        }
        for (p in recent) {
            pkgBox.addView(button(p) {
                prefs.edit().putString("target", p).apply()
                refresh()
            })
        }
    }

    private fun latestDump(): String {
        val files = getExternalFilesDir(null)?.listFiles() ?: return "Hali dump yo'q."
        val f = files.filter { it.name.startsWith("dump_") }.maxByOrNull { it.name }
            ?: return "Hali dump yo'q."
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
