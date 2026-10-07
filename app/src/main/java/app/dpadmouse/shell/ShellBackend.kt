package app.dpadmouse.shell

import java.io.Closeable
import java.io.IOException

/** Một đường chạy lệnh với quyền shell. Hai cài đặt: Shizuku và ADB TCP. */
interface ShellBackend : Closeable {
    val label: String
    val isConnected: Boolean

    /** stdout/stderr của tiến trình `hid` (mỗi dòng). */
    var onHidOutput: ((String) -> Unit)?

    /** Gọi khi đường truyền bị mất ngoài ý muốn. */
    var onDisconnected: (() -> Unit)?

    @Throws(IOException::class)
    fun connect()

    /** Chạy một lệnh, trả về stdout+stderr. */
    @Throws(IOException::class)
    fun exec(command: String, timeoutMs: Long = 10_000): String

    /** Chạy `hid -` (dài hạn); ném lỗi nếu tiến trình thoát ngay. */
    @Throws(IOException::class)
    fun startHid()

    /** Ghi một dòng JSON vào stdin của hid (tự thêm '\n'). */
    @Throws(IOException::class)
    fun writeHid(data: String)

    fun stopHid()
}
