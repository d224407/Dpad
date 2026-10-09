package app.dpadmouse

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Logging only exists in the DEBUG build: logcat (tag "DpadMouse") + an in-app ring buffer.
 * RELEASE build: every function returns immediately (BuildConfig.DEBUG = false) and R8 strips the calls
 * (see proguard-rules.pro) -> no logging at all.
 */
object DLog {
    const val TAG = "DpadMouse"
    private const val MAX_LINES = 1000
    private val ENABLED = BuildConfig.DEBUG

    class Entry(val level: Char, val text: String)

    private val entries = ArrayDeque<Entry>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    var listener: (() -> Unit)? = null

    /** Very verbose (every HID report). Logcat only, not stored in the buffer. */
    fun v(msg: String) {
        if (!ENABLED) return
        Log.v(TAG, msg)
    }

    fun d(msg: String) = log('D', msg, null)
    fun i(msg: String) = log('I', msg, null)
    fun w(msg: String, tr: Throwable? = null) = log('W', msg, tr)
    fun e(msg: String, tr: Throwable? = null) = log('E', msg, tr)

    private fun log(level: Char, msg: String, tr: Throwable?) {
        if (!ENABLED) return
        when (level) {
            'D' -> Log.d(TAG, msg)
            'I' -> Log.i(TAG, msg)
            'W' -> Log.w(TAG, msg, tr)
            else -> Log.e(TAG, msg, tr)
        }
        val text = "${fmt.format(Date())} $level $msg" + (tr?.let { " [$it]" } ?: "")
        synchronized(this) {
            entries.addLast(Entry(level, text))
            while (entries.size > MAX_LINES) entries.removeFirst()
        }
        listener?.invoke()
    }

    /** Oldest -> newest. level = null: everything. */
    @Synchronized
    fun snapshot(level: Char? = null): List<Entry> =
        entries.filter { level == null || it.level == level }

    @Synchronized
    fun dumpAll(): String = entries.joinToString("\n") { it.text }

    @Synchronized
    fun clear() {
        entries.clear()
        listener?.invoke()
    }
}
