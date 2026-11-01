package uz.censusassistant

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Xatolik va hodisalar logi: faqat telefondagi ilova papkasida (log.txt). */
object AppLog {
    fun write(ctx: Context, msg: String) {
        try {
            val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(ctx.getExternalFilesDir(null), "log.txt").appendText("$t  $msg\n")
        } catch (_: Exception) {
        }
    }
}
