package app.dpadmouse.shell

import java.io.Closeable
import java.io.IOException

/** A channel for running commands with shell permission: local ADB (see AdbBackend). */
interface ShellBackend : Closeable {
    val label: String
    val isConnected: Boolean

    /** stdout/stderr of the `hid` process (per line). */
    var onHidOutput: ((String) -> Unit)?

    /** Called when the connection is lost unexpectedly. */
    var onDisconnected: (() -> Unit)?

    @Throws(IOException::class)
    fun connect()

    /** Runs a command, returning stdout+stderr. */
    @Throws(IOException::class)
    fun exec(command: String, timeoutMs: Long = 10_000): String

    /** Runs `hid -` (long-lived); throws if the process exits immediately. */
    @Throws(IOException::class)
    fun startHid()

    /** Writes a JSON line to hid's stdin (appends '\n' automatically). */
    @Throws(IOException::class)
    fun writeHid(data: String)

    fun stopHid()
}
