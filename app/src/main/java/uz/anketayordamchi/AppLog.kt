package uz.anketayordamchi

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Xatolik va hodisalar logi: faqat telefondagi ilova papkasida (log.txt). Internetga hech narsa yuborilmaydi. */
object AppLog {
    private const val MAX_BYTES = 400_000L

    private fun dir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir

    @Synchronized
    fun write(ctx: Context, msg: String) {
        try {
            val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val f = File(dir(ctx), "log.txt")
            if (f.length() > MAX_BYTES) {
                val old = File(dir(ctx), "log_old.txt")
                old.delete()
                f.renameTo(old)
            }
            File(dir(ctx), "log.txt").appendText("$t  ${mask(msg)}\n")
        } catch (_: Exception) {
        }
    }

    /** Sahifa, savol, holat va sabab bilan. */
    fun event(ctx: Context, page: Int, line: String) = write(ctx, "[$page/6] $line")

    @Synchronized
    fun read(ctx: Context, last: Int = 20000): String {
        val f = File(dir(ctx), "log.txt")
        return if (f.exists()) f.readText().takeLast(last) else "Log bo'sh."
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            File(dir(ctx), "log.txt").delete()
            File(dir(ctx), "log_old.txt").delete()
        } catch (_: Exception) {
        }
    }
}
