package app.dpadmouse.service

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import app.dpadmouse.DLog
import app.dpadmouse.Prefs
import app.dpadmouse.hid.HidMouse
import app.dpadmouse.shell.ShellBackend
import java.io.IOException

/**
 * Biến trạng thái phím (giữ/nhả) thành các report HID mượt: vòng lặp ~60Hz, tăng tốc khi giữ phím.
 * Mọi thao tác chạy trên 1 thread riêng ("dpadmouse-hid") nên không chặn UI/Accessibility.
 */
class CursorEngine(
    private val backend: ShellBackend,
    private val prefs: Prefs,
    private val onFailure: (Throwable) -> Unit
) {
    private val thread = HandlerThread("dpadmouse-hid").apply { start() }
    private val handler = Handler(thread.looper)

    // Chỉ truy cập trên handler thread
    private var held = 0
    private var buttons = 0
    private var scrollDir = 0
    private var nextScrollAt = 0L
    private var holdStart = 0L
    private var remX = 0f
    private var remY = 0f
    private var ticking = false
    private var failed = false

    fun press(dir: Int) = handler.post {
        if (held == 0) {
            holdStart = SystemClock.uptimeMillis()
            remX = 0f; remY = 0f
        }
        held = held or dir
        startTicking()
    }

    fun release(dir: Int) = handler.post { held = held and dir.inv() }

    fun buttonDown(mask: Int) = handler.post {
        buttons = buttons or mask
        report(buttons, 0, 0, 0)
    }

    fun buttonUp(mask: Int) = handler.post {
        buttons = buttons and mask.inv()
        report(buttons, 0, 0, 0)
    }

    /** dir: 1 = cuộn lên, -1 = cuộn xuống, 0 = dừng. */
    fun setScroll(dir: Int) = handler.post {
        scrollDir = dir
        if (dir != 0) {
            nextScrollAt = 0L
            startTicking()
        }
    }

    fun releaseAll() = handler.post {
        held = 0
        scrollDir = 0
        if (buttons != 0) {
            buttons = 0
            report(0, 0, 0, 0)
        }
    }

    // --- phục vụ nút test trong bản debug
    fun nudge(dx: Int, dy: Int) = handler.post { sendMove(dx, dy) }

    fun click() = handler.post {
        report(HidMouse.BTN_LEFT, 0, 0, 0)
        report(0, 0, 0, 0)
    }

    fun shutdown() {
        handler.post {
            held = 0
            scrollDir = 0
            ticking = false
            handler.removeCallbacks(tick)
        }
        thread.quitSafely()
    }

    // ------------------------------------------------------------------ nội bộ

    private fun startTicking() {
        if (!ticking && !failed) {
            ticking = true
            handler.post(tick)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (failed || (held == 0 && scrollDir == 0)) {
                ticking = false
                return
            }
            val now = SystemClock.uptimeMillis()

            val dirX = (if (held and RIGHT != 0) 1 else 0) - (if (held and LEFT != 0) 1 else 0)
            val dirY = (if (held and DOWN != 0) 1 else 0) - (if (held and UP != 0) 1 else 0)
            if (dirX != 0 || dirY != 0) {
                val ramp = ((now - holdStart).toFloat() / ACCEL_RAMP_MS).coerceIn(0f, 1f)
                var speed = (2f + prefs.sensitivity * 0.14f) * (1f + (ACCEL_MAX - 1f) * ramp)
                if (dirX != 0 && dirY != 0) speed *= 0.7071f
                val fx = dirX * speed + remX
                val fy = dirY * speed + remY
                val dx = fx.toInt()
                val dy = fy.toInt()
                remX = fx - dx
                remY = fy - dy
                sendMove(dx, dy)
            }

            if (scrollDir != 0 && now >= nextScrollAt) {
                report(buttons, 0, 0, scrollDir)
                nextScrollAt = now + SCROLL_INTERVAL_MS
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun sendMove(dxIn: Int, dyIn: Int) {
        var dx = dxIn
        var dy = dyIn
        while ((dx != 0 || dy != 0) && !failed) {
            val sx = dx.coerceIn(-127, 127)
            val sy = dy.coerceIn(-127, 127)
            report(buttons, sx, sy, 0)
            dx -= sx
            dy -= sy
        }
    }

    private fun report(b: Int, dx: Int, dy: Int, wheel: Int) {
        if (failed) return
        val json = HidMouse.reportJson(b, dx, dy, wheel)
        DLog.v("report $json")
        try {
            backend.writeHid(json)
        } catch (e: IOException) {
            failed = true
            held = 0
            scrollDir = 0
            DLog.e("Ghi report HID thất bại", e)
            onFailure(e)
        }
    }

    companion object {
        const val UP = 1
        const val DOWN = 2
        const val LEFT = 4
        const val RIGHT = 8

        private const val TICK_MS = 16L
        private const val ACCEL_MAX = 3f
        private const val ACCEL_RAMP_MS = 900f
        private const val SCROLL_INTERVAL_MS = 120L
    }
}
