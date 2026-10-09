package app.dpadmouse.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.dpadmouse.DLog
import app.dpadmouse.MouseHub
import app.dpadmouse.hid.HidMouse

/**
 * Intercepts controller keys (flagRequestFilterKeyEvents). While mouse mode is ON:
 *   arrows = move, OK/Enter = left click, mapped keys = right click / scroll.
 * The mouse on/off key is always captured, even while the mouse is off.
 */
class MouseAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        DLog.i("Accessibility service connected")
        MouseHub.attachService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        MouseHub.engine?.releaseAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DLog.i("Accessibility service unbound")
        MouseHub.detachService()
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Mapping a key: release all keys to the dialog (including the mouse on/off key).
        if (MouseHub.learningKeys) return false

        val prefs = MouseHub.prefs
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0

        DLog.d(
            "key ${KeyEvent.keyCodeToString(code)} ${if (down) "DOWN" else "UP"} " +
                "rep=${event.repeatCount} dev=${event.deviceId} mouse=${MouseHub.mouseModeOn}"
        )

        if (code != KeyEvent.KEYCODE_UNKNOWN && code == prefs.toggleKey) {
            if (first) MouseHub.toggleFromService()
            return true
        }
        if (!MouseHub.mouseModeOn) return false
        val engine = MouseHub.engine ?: return false

        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> CursorEngine.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> CursorEngine.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> CursorEngine.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> CursorEngine.RIGHT
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> CursorEngine.UP or CursorEngine.LEFT
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> CursorEngine.UP or CursorEngine.RIGHT
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> CursorEngine.DOWN or CursorEngine.LEFT
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> CursorEngine.DOWN or CursorEngine.RIGHT
            else -> 0
        }
        if (dir != 0) {
            if (down) { if (first) engine.press(dir) } else engine.release(dir)
            return true
        }

        if (isClickKey(code, prefs.clickKey)) {
            if (down) { if (first) engine.buttonDown(HidMouse.BTN_LEFT) } else engine.buttonUp(HidMouse.BTN_LEFT)
            return true
        }
        if (code != KeyEvent.KEYCODE_UNKNOWN && code == prefs.rightClickKey) {
            if (down) { if (first) engine.buttonDown(HidMouse.BTN_RIGHT) } else engine.buttonUp(HidMouse.BTN_RIGHT)
            return true
        }
        if (code != KeyEvent.KEYCODE_UNKNOWN && code == prefs.scrollUpKey) {
            engine.setScroll(if (down) 1 else 0)
            return true
        }
        if (code != KeyEvent.KEYCODE_UNKNOWN && code == prefs.scrollDownKey) {
            engine.setScroll(if (down) -1 else 0)
            return true
        }
        return false
    }

    private fun isClickKey(code: Int, clickKey: Int): Boolean {
        if (code == KeyEvent.KEYCODE_UNKNOWN || clickKey == KeyEvent.KEYCODE_UNKNOWN) return false
        if (code == clickKey) return true
        return clickKey == KeyEvent.KEYCODE_DPAD_CENTER &&
            (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)
    }
}
