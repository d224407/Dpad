package app.dpadmouse

import android.content.Context
import android.view.KeyEvent

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("dpadmouse", Context.MODE_PRIVATE)

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

    /** UI language: "en" or "vi". */
    var language: String
        get() = sp.getString("language", "en") ?: "en"
        set(v) = sp.edit().putString("language", v).apply()

    // Mappable keys. 0 (KEYCODE_UNKNOWN) = not used.
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

    fun resetKeys() {
        sp.edit().remove("key_toggle").remove("key_click").remove("key_right")
            .remove("key_scroll_up").remove("key_scroll_down").apply()
    }
}
