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
import app.dpadmouse.shell.ShizukuBackend
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Trung tâm trạng thái dùng chung cho Activity và AccessibilityService. */
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

    // ------------------------------------------------------------------ kết nối

    fun connect() {
        if (state == State.CONNECTING) return
        state = State.CONNECTING
        lastError = null
        notifyChanged()
        io.execute {
            try {
                teardown()
                val b = createBackend()
                b.onDisconnected = { onBackendLost("Mất kết nối ${b.label}") }
                DLog.i("Kết nối bằng ${b.label}")
                b.connect()
                b.startHid()
                b.writeHid(HidMouse.registerJson())
                backend = b
                engine = CursorEngine(b, prefs) { t -> onBackendLost("Lỗi ghi: ${t.message}") }
                state = State.CONNECTED
                DLog.i("Đã tạo chuột HID ảo. Bật/tắt chế độ chuột bằng phím đã gán.")
                if (enableAfterConnect) setMouseMode(true)
            } catch (t: Throwable) {
                DLog.e("Kết nối thất bại: ${t.message}", t)
                lastError = t.message ?: t.javaClass.simpleName
                teardown()
                state = State.DISCONNECTED
                toast("Lỗi: $lastError")
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
            DLog.i("Đã ngắt kết nối")
            notifyChanged()
        }
    }

    private fun createBackend(): ShellBackend = when (prefs.backend) {
        Backend.SHIZUKU -> ShizukuBackend(appContext)
        Backend.ADB -> AdbBackend(appContext, prefs.host, prefs.port)
    }

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

    // ------------------------------------------------------------------ chế độ chuột

    fun toggleFromService() {
        when (state) {
            State.CONNECTED -> setMouseMode(!mouseModeOn)
            State.CONNECTING -> toast("Đang kết nối…")
            State.DISCONNECTED -> {
                toast("Đang kết nối shell…")
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
        DLog.i("Chế độ chuột: ${if (on) "BẬT" else "TẮT"}")
        toast(if (on) "Chuột: BẬT" else "Chuột: TẮT")
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

    // ------------------------------------------------------------------ lệnh shell một lần

    /** Chạy 1 lệnh bằng backend đang chọn (dùng phiên đang mở nếu có). Callback chạy trên main thread. */
    fun execOnce(command: String, callback: (Result<String>) -> Unit) {
        io.execute {
            val existing = backend
            val result = runCatching {
                if (existing != null) {
                    existing.exec(command)
                } else {
                    val b = createBackend()
                    try {
                        b.connect()
                        b.exec(command)
                    } finally {
                        runCatching { b.close() }
                    }
                }
            }
            main.post { callback(result) }
        }
    }

    /** Thêm dịch vụ trợ năng của app vào danh sách đã bật bằng lệnh `settings` (cần quyền shell). */
    fun enableAccessibilityService(callback: (Result<String>) -> Unit) {
        val me = ComponentName(appContext, MouseAccessibilityService::class.java).flattenToString()
        val current = Settings.Secure.getString(
            appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val list = current.split(':').filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(me)) list.add(me)
        val cmd = "settings put secure enabled_accessibility_services '${list.joinToString(":")}' && " +
            "settings put secure accessibility_enabled 1 && echo OK"
        DLog.i("Bật trợ năng: $cmd")
        execOnce(cmd, callback)
    }
}
