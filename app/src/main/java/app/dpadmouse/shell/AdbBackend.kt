package app.dpadmouse.shell

import android.content.Context
import app.dpadmouse.DLog
import java.io.File
import java.io.IOException

/** Runs commands through the device's own adbd (127.0.0.1:5555) using a hand-written ADB client. */
class AdbBackend(
    private val context: Context,
    private val host: String,
    private val port: Int
) : ShellBackend {

    override val label = "ADB TCP $host:$port"
    override var onHidOutput: ((String) -> Unit)? = null
    override var onDisconnected: (() -> Unit)? = null

    private var client: AdbClient? = null
    private var hid: AdbClient.Stream? = null

    override val isConnected: Boolean
        get() = client?.isAlive == true && hid?.closed != true

    override fun connect() {
        val c = AdbClient(host, port, AdbKeys(File(context.filesDir, "adb")))
        c.onClosed = { onDisconnected?.invoke() }
        c.connect()
        client = c
    }

    override fun exec(command: String, timeoutMs: Long): String = requireClient().exec(command, timeoutMs)

    override fun startHid() {
        val c = requireClient()
        val st = c.openStream("shell,v2,raw:hid -")
        st.onOutput = { text ->
            text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                DLog.w("hid: $line")
                onHidOutput?.invoke(line)
            }
        }
        hid = st
        Thread.sleep(300) // if `hid` fails (missing binary, bad JSON...) it exits almost immediately
        if (st.closed) {
            throw IOException("`hid` exited immediately (code=${st.exitCode}): ${st.output.toString().trim()}")
        }
    }

    override fun writeHid(data: String) {
        val st = hid ?: throw IOException("hid is not running")
        requireClient().writeStdin(st, (data + "\n").toByteArray())
    }

    override fun stopHid() {
        hid?.let { client?.closeStream(it) }
        hid = null
    }

    override fun close() {
        stopHid()
        client?.close()
        client = null
    }

    private fun requireClient(): AdbClient = client ?: throw IOException("ADB not connected")
}
