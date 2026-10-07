package app.dpadmouse

import android.content.Context
import android.view.KeyEvent

enum class Backend { SHIZUKU, ADB }

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("dpadmouse", Context.MODE_PRIVATE)

    var backend: Backend
        get() = if (sp.getString("backend", "shizuku") == "adb") Backend.ADB else Backend.SHIZUKU
        set(v) = sp.edit().putString("backend", if (v == Backend.ADB) "adb" else "shizuku").apply()

    var host: String
        get() = sp.getString("host", "127.0.0.1") ?: "127.0.0.1"
        set(v) = sp.edit().putString("host", v).apply()

    var port: Int
        get() = sp.getInt("port", 5555)
        set(v) = sp.edit().putInt("port", v).apply()

    /** 1..100 */
    var sensitivity: Int
        get() = sp.getInt("sensitivity", 50)
        set(v) = sp.edit().putInt("sensitivity", v.coerceIn(1, 100)).apply()

    // Phím gán được. 0 (KEYCODE_UNKNOWN) = không dùng.
    var toggleKey: Int
        get() = sp.getInt("key_toggle", KeyEvent.KEYCODE_MENU)
        set(v) = sp.edit().putInt("key_toggle", v).apply()

    var clickKey: Int
        get() = sp.getInt("key_click", KeyEvent.KEYCODE_DPAD_CENTER)
        set(v) = sp.edit().putInt("key_click", v).apply()

    var rightClickKey: Int
        get() = sp.getInt("key_right", KeyEvent.KEYCODE_UNKNOWN)
        set(v) = sp.edit().putInt("key_right", v).apply()

    var scrollUpKey: Int
        get() = sp.getInt("key_scroll_up", KeyEvent.KEYCODE_CHANNEL_UP)
        set(v) = sp.edit().putInt("key_scroll_up", v).apply()

    var scrollDownKey: Int
        get() = sp.getInt("key_scroll_down", KeyEvent.KEYCODE_CHANNEL_DOWN)
        set(v) = sp.edit().putInt("key_scroll_down", v).apply()
}
