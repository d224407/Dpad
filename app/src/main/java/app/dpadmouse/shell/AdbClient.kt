package app.dpadmouse.shell

import app.dpadmouse.DLog
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ADB client over TCP (no TLS): CNXN -> AUTH(sign/public key) -> OPEN shell.
 * Supports several concurrent streams (one long-lived `hid -` stream + short exec commands).
 * Paired "Wireless debugging" (STLS) is not supported -> use `adb tcpip 5555`.
 */
class AdbClient(
    private val host: String,
    private val port: Int,
    private val keys: AdbKeys
) : Closeable {

    class Stream(val localId: Int) {
        @Volatile var remoteId = 0
        @Volatile var closed = false
        @Volatile var rejected = false
        @Volatile var exitCode = -1
        @Volatile var onOutput: ((String) -> Unit)? = null
        val opened = CountDownLatch(1)
        val done = CountDownLatch(1)
        val acks = Semaphore(0)
        val output = StringBuffer()
        internal val parser = ShellV2Parser()
    }

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private val sendLock = Any()
    private val streams = ConcurrentHashMap<Int, Stream>()
    private val nextId = AtomicInteger(1)

    @Volatile private var closed = false
    @Volatile private var maxPayload = 4096
    @Volatile var onClosed: (() -> Unit)? = null

    val isAlive: Boolean get() = !closed && socket?.isConnected == true

    @Throws(IOException::class)
    fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 5_000)
        s.soTimeout = 60_000 // wait for the user to tap "Allow USB debugging?"
        socket = s
        input = DataInputStream(s.getInputStream().buffered())
        output = s.getOutputStream()

        send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
        var signatureSent = false
        loop@ while (true) {
            val m = readMessage()
            when (m.command) {
                A_CNXN -> {
                    maxPayload = m.arg1.coerceIn(4096, MAX_PAYLOAD)
                    DLog.i("ADB connected: ${String(m.data, Charsets.UTF_8).trim('\u0000')} (maxdata=$maxPayload)")
                    break@loop
                }
                A_AUTH -> {
                    if (m.arg0 != AUTH_TOKEN) throw IOException("Invalid AUTH (type=${m.arg0})")
                    if (!signatureSent) {
                        DLog.d("ADB: sending signature")
                        send(A_AUTH, AUTH_SIGNATURE, 0, keys.sign(m.data))
                        signatureSent = true
                    } else {
                        DLog.i("ADB: sending public key - tap 'Allow' on the device")
                        send(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys.publicKeyMessage())
                    }
                }
                A_STLS -> throw IOException(
                    "The device requires TLS (paired Wireless debugging) - not supported yet. " +
                        "Enable 'adb tcpip 5555' instead."
                )
                else -> throw IOException("Unexpected packet during handshake: ${cmdName(m.command)}")
            }
        }
        s.soTimeout = 0
        Thread({ readerLoop() }, "adb-reader").apply { isDaemon = true; start() }
    }

    @Throws(IOException::class)
    fun openStream(service: String): Stream {
        val st = Stream(nextId.getAndIncrement())
        streams[st.localId] = st
        DLog.d("ADB OPEN '$service' local=${st.localId}")
        send(A_OPEN, st.localId, 0, (service + "\u0000").toByteArray())
        if (!st.opened.await(5, TimeUnit.SECONDS)) {
            streams.remove(st.localId)
            throw IOException("Timed out opening '$service'")
        }
        if (st.rejected) throw IOException("Device rejected '$service'")
        return st
    }

    /** Writes to the stdin of a shell v2 stream, framing it and waiting for OKAY per packet. */
    @Throws(IOException::class)
    fun writeStdin(st: Stream, bytes: ByteArray) {
        var off = 0
        val chunkMax = maxPayload - 5
        while (off < bytes.size) {
            val n = minOf(bytes.size - off, chunkMax)
            val bb = ByteBuffer.allocate(5 + n).order(ByteOrder.LITTLE_ENDIAN)
            bb.put(0.toByte()).putInt(n).put(bytes, off, n)
            if (st.closed) throw IOException("Stream already closed")
            send(A_WRTE, st.localId, st.remoteId, bb.array())
            if (!st.acks.tryAcquire(5, TimeUnit.SECONDS)) throw IOException("Device did not reply OKAY (write timed out)")
            if (st.closed) throw IOException("Stream already closed")
            off += n
        }
    }

    @Throws(IOException::class)
    fun exec(command: String, timeoutMs: Long): String {
        val st = openStream("shell,v2,raw:$command")
        try {
            if (!st.done.await(timeoutMs, TimeUnit.MILLISECONDS)) throw IOException("Command timed out: $command")
            return st.output.toString().trimEnd()
        } finally {
            closeStream(st)
        }
    }

    fun closeStream(st: Stream) {
        if (!st.closed && st.remoteId != 0) runCatching { send(A_CLSE, st.localId, st.remoteId) }
        streams.remove(st.localId)
        finishStream(st)
    }

    override fun close() {
        closed = true
        streams.values.forEach { runCatching { closeStream(it) } }
        runCatching { socket?.close() }
    }

    // ------------------------------------------------------------------ internal

    private fun readerLoop() {
        try {
            while (!closed) {
                val m = readMessage()
                val st = streams[m.arg1]
                when (m.command) {
                    A_OKAY -> if (st != null) {
                        if (st.remoteId == 0) {
                            st.remoteId = m.arg0
                            st.opened.countDown()
                        } else {
                            st.acks.release()
                        }
                    }
                    A_WRTE -> if (st != null) {
                        try {
                            handleStreamData(st, m.data)
                        } finally {
                            send(A_OKAY, st.localId, st.remoteId)
                        }
                    }
                    A_CLSE -> if (st != null) {
                        DLog.d("ADB CLSE local=${st.localId}")
                        finishStream(st)
                        streams.remove(st.localId)
                    }
                    else -> DLog.w("ADB: ignoring packet ${cmdName(m.command)}")
                }
            }
        } catch (e: IOException) {
            if (!closed) DLog.w("ADB reader stopped: ${e.message}")
        } finally {
            val wasClosed = closed
            closed = true
            streams.values.forEach { finishStream(it) }
            streams.clear()
            runCatching { socket?.close() }
            if (!wasClosed) onClosed?.invoke()
        }
    }

    private fun handleStreamData(st: Stream, data: ByteArray) {
        st.parser.feed(data) { id, payload ->
            when (id) {
                1, 2 -> {
                    val text = String(payload, Charsets.UTF_8)
                    st.output.append(text)
                    st.onOutput?.invoke(text)
                }
                3 -> {
                    st.exitCode = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0
                    st.done.countDown()
                }
            }
        }
    }

    private fun finishStream(st: Stream) {
        if (st.remoteId == 0) st.rejected = true
        st.closed = true
        st.opened.countDown()
        st.done.countDown()
        st.acks.release(1000)
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = EMPTY) {
        val bb = ByteBuffer.allocate(HEADER + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(command).putInt(arg0).putInt(arg1)
            .putInt(data.size).putInt(checksum(data)).putInt(command xor -1)
            .put(data)
        val out = output ?: throw IOException("Not connected")
        synchronized(sendLock) {
            out.write(bb.array())
            out.flush()
        }
    }

    private fun readMessage(): Message {
        val inp = input ?: throw IOException("Not connected")
        val header = ByteArray(HEADER)
        inp.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = bb.int
        val arg0 = bb.int
        val arg1 = bb.int
        val len = bb.int
        bb.int // checksum (newer adbd ignores this)
        val magic = bb.int
        if (magic != (command xor -1)) throw IOException("Bad ADB magic")
        if (len < 0 || len > MAX_PAYLOAD) throw IOException("Invalid ADB length: $len")
        val data = ByteArray(len)
        if (len > 0) inp.readFully(data)
        return Message(command, arg0, arg1, data)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xFF
        return sum
    }

    private fun cmdName(c: Int): String = String(
        byteArrayOf(c.toByte(), (c shr 8).toByte(), (c shr 16).toByte(), (c shr 24).toByte()),
        Charsets.ISO_8859_1
    )

    private companion object {
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_STLS = 0x534c5453

        const val A_VERSION = 0x01000001
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val HEADER = 24
        const val MAX_PAYLOAD = 256 * 1024
        val EMPTY = ByteArray(0)
    }
}
