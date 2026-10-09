package app.dpadmouse

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import app.dpadmouse.hid.HidMouse
import app.dpadmouse.service.CursorEngine
import app.dpadmouse.service.MouseAccessibilityService
import app.dpadmouse.shell.AdbBackend
import app.dpadmouse.shell.ShellBackend
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Shared state hub used by the Activity and the AccessibilityService. */
object MouseHub {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    lateinit var appContext: Context
        private set
    lateinit var prefs: Prefs
        private set

    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dpadmouse-io").apply { isDaemon = true }
    }
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile var state = State.DISCONNECTED
        private set
    @Volatile var mouseModeOn = false
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var engine: CursorEngine? = null
        private set
    @Volatile var serviceConnected = false
        private set

    /** In the key-mapping dialog: the accessibility service must release every key so the dialog can receive it. */
    @Volatile var learningKeys = false

    @Volatile private var backend: ShellBackend? = null
    @Volatile private var enableAfterConnect = false

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = Prefs(appContext)
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { main.post { listeners.forEach { it() } } }

    fun toast(msg: String) { main.post { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() } }

    // ------------------------------------------------------------------ connection

    fun connect() {
        if (state == State.CONNECTING) return
        state = State.CONNECTING
        lastError = null
        notifyChanged()
        io.execute {
            try {
                teardown()
                val b = createBackend()
                b.onDisconnected = { onBackendLost("Lost connection to ${b.label}") }
                DLog.i("Connecting via ${b.label}")
                b.connect()
                b.startHid()
                b.writeHid(HidMouse.registerJson())
                backend = b
                engine = CursorEngine(b, prefs) { t -> onBackendLost("Write error: ${t.message}") }
                state = State.CONNECTED
                DLog.i("Virtual HID mouse created. Toggle mouse mode with the mapped key.")
                if (DebugTools.ENABLED) DebugTools.onAdbConnected(appContext)
                if (enableAfterConnect) setMouseMode(true)
            } catch (t: Throwable) {
                DLog.e("Connection failed: ${t.message}", t)
                lastError = t.message ?: t.javaClass.simpleName
                teardown()
                state = State.DISCONNECTED
                toast("Error: $lastError")
            } finally {
                enableAfterConnect = false
            }
            notifyChanged()
        }
    }

    fun disconnect() {
        io.execute {
            teardown()
            state = State.DISCONNECTED
            DLog.i("Disconnected")
            notifyChanged()
        }
    }

    private fun createBackend(): ShellBackend = AdbBackend(appContext, prefs.host, prefs.port)

    private fun teardown() {
        mouseModeOn = false
        engine?.shutdown()
        engine = null
        runCatching { backend?.close() }
        backend = null
    }

    private fun onBackendLost(reason: String) {
        if (state == State.DISCONNECTED) return
        DLog.w(reason)
        mouseModeOn = false
        io.execute {
            teardown()
            state = State.DISCONNECTED
            lastError = reason
            notifyChanged()
            toast(reason)
        }
    }

    // ------------------------------------------------------------------ mouse mode

    fun toggleFromService() {
        when (state) {
            State.CONNECTED -> setMouseMode(!mouseModeOn)
            State.CONNECTING -> toast("Connecting…")
            State.DISCONNECTED -> {
                toast("Connecting to shell…")
                enableAfterConnect = true
                connect()
            }
        }
    }

    fun setMouseMode(on: Boolean) {
        if (on && state != State.CONNECTED) return
        if (mouseModeOn == on) return
        mouseModeOn = on
        if (!on) engine?.releaseAll()
        DLog.i("Mouse mode: ${if (on) "ON" else "OFF"}")
        toast(if (on) "Mouse: ON" else "Mouse: OFF")
        notifyChanged()
    }

    fun attachService() {
        serviceConnected = true
        notifyChanged()
    }

    fun detachService() {
        serviceConnected = false
        setMouseMode(false)
        notifyChanged()
    }

    // ------------------------------------------------------------------ one-off shell commands

    /** Runs a command (blocks the calling thread). Uses the open session if any, otherwise opens a temporary one. */
    fun execSync(command: String): String {
        val existing = backend
        if (existing != null) return existing.exec(command)
        val b = createBackend()
        try {
            b.connect()
            return b.exec(command)
        } finally {
            runCatching { b.close() }
        }
    }

    /** Like [execSync] but non-blocking; the callback runs on the main thread. */
    fun execOnce(command: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val result = runCatching { execSync(command) }
            main.post { callback(result) }
        }
    }

    /** Adds the app's accessibility service to the enabled list via the `settings` command (needs shell permission). */
    fun enableAccessibilityService(callback: (Result<String>) -> Unit) {
        val me = ComponentName(appContext, MouseAccessibilityService::class.java).flattenToString()
        val current = Settings.Secure.getString(
            appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val list = current.split(':').filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(me)) list.add(me)
        val cmd = "settings put secure enabled_accessibility_services '${list.joinToString(":")}' && " +
            "settings put secure accessibility_enabled 1 && echo OK"
        DLog.i("Enabling accessibility: $cmd")
        execOnce(cmd, callback)
    }
}
