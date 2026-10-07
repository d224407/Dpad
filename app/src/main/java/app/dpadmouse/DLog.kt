package app.dpadmouse

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Log 2 đường: logcat (tag "DpadMouse") + bộ đệm vòng hiển thị ngay trong app.
 *  - v/d: chỉ có ở bản debug (release bị R8 xoá hẳn).
 *  - i/w/e: luôn có, để chẩn đoán cả trên bản release.
 */
object DLog {
    const val TAG = "DpadMouse"
    private const val MAX_LINES = 500

    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    var listener: ((String) -> Unit)? = null

    /** Rất chi tiết (mỗi report HID). Chỉ logcat, không vào bộ đệm. */
    fun v(msg: String) {
        if (BuildConfig.DEBUG) Log.v(TAG, msg)
    }

    fun d(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
            push("D", msg)
        }
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        push("I", msg)
    }

    fun w(msg: String, tr: Throwable? = null) {
        Log.w(TAG, msg, tr)
        push("W", msg + (tr?.let { " [$it]" } ?: ""))
    }

    fun e(msg: String, tr: Throwable? = null) {
        Log.e(TAG, msg, tr)
        push("E", msg + (tr?.let { " [$it]" } ?: ""))
    }

    @Synchronized
    private fun push(level: String, msg: String) {
        val line = "${fmt.format(Date())} $level $msg"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        listener?.invoke(line)
    }

    /** Mới nhất ở trên cùng. */
    @Synchronized
    fun dump(maxLines: Int = 80): String = lines.descendingIterator().asSequence().take(maxLines).joinToString("\n")

    @Synchronized
    fun dumpAll(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        listener?.invoke("")
    }
}
