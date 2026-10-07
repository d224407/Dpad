package app.dpadmouse.shell

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import kotlin.system.exitProcess

/**
 * CHẠY TRONG PROCESS QUYỀN SHELL do Shizuku tạo (không phải process của app).
 * Gỡ lỗi: logcat tag "DpadMouse:svc". Bản debug bật debuggable nên gắn debugger được.
 */
class HidUserService : IHidService.Stub() {
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private val logBuf = StringBuilder()

    override fun destroy() {
        Log.i(TAG, "destroy()")
        stopHid()
        exitProcess(0)
    }

    override fun startHid() {
        synchronized(this) {
            stopHid()
            try {
                val p = ProcessBuilder("/system/bin/hid", "-").redirectErrorStream(true).start()
                process = p
                stdin = p.outputStream
                Thread({ pump(p) }, "hid-pump").start()
                Log.i(TAG, "hid started")
            } catch (e: IOException) {
                throw IllegalStateException("Không chạy được hid: ${e.message}")
            }
        }
    }

    override fun write(data: String) {
        synchronized(this) {
            val o = stdin ?: throw IllegalStateException("hid chưa chạy")
            try {
                o.write(data.toByteArray())
                o.flush()
            } catch (e: IOException) {
                throw IllegalStateException("Ghi vào hid lỗi: ${e.message}")
            }
        }
    }

    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.trimEnd()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun drainLog(): String = synchronized(logBuf) {
        val s = logBuf.toString()
        logBuf.setLength(0)
        s
    }

    override fun stopHid() {
        synchronized(this) {
            runCatching { stdin?.close() }
            runCatching { process?.destroy() }
            process = null
            stdin = null
        }
    }

    override fun isHidAlive(): Boolean {
        val p = process ?: return false
        return try {
            p.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    private fun pump(p: Process) {
        try {
            p.inputStream.bufferedReader().forEachLine { append(it) }
        } catch (e: IOException) {
            // process đã đóng
        }
        val code = try { p.waitFor() } catch (e: InterruptedException) { -1 }
        append("[hid thoát, code=$code]")
    }

    private fun append(line: String) {
        Log.w(TAG, "hid: $line")
        synchronized(logBuf) {
            if (logBuf.length < 16_000) logBuf.append(line).append('\n')
        }
    }

    private companion object {
        const val TAG = "DpadMouse:svc"
    }
}
