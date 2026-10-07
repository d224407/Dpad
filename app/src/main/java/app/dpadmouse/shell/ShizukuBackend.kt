package app.dpadmouse.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import app.dpadmouse.BuildConfig
import app.dpadmouse.DLog
import rikka.shizuku.Shizuku
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Chạy lệnh bằng Shizuku UserService (process quyền shell). Cần Shizuku đang chạy + đã cấp quyền. */
class ShizukuBackend(private val context: Context) : ShellBackend {

    override val label = "Shizuku"
    override var onHidOutput: ((String) -> Unit)? = null
    override var onDisconnected: (() -> Unit)? = null

    @Volatile private var service: IHidService? = null
    @Volatile private var closing = false
    private var latch = CountDownLatch(1)

    override val isConnected: Boolean
        get() = service?.asBinder()?.isBinderAlive == true

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, HidUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hid")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            DLog.i("Shizuku UserService đã kết nối")
            service = IHidService.Stub.asInterface(binder)
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            DLog.w("Shizuku UserService bị ngắt")
            service = null
            if (!closing) onDisconnected?.invoke()
        }
    }

    override fun connect() {
        if (!Shizuku.pingBinder()) throw IOException("Shizuku chưa chạy. Hãy mở Shizuku và khởi động nó.")
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw IOException("Chưa cấp quyền Shizuku cho app này.")
        }
        closing = false
        latch = CountDownLatch(1)
        Shizuku.bindUserService(args, connection)
        if (!latch.await(10, TimeUnit.SECONDS)) throw IOException("Hết thời gian chờ Shizuku UserService.")
    }

    override fun exec(command: String, timeoutMs: Long): String = call { svc().exec(command) }

    override fun startHid() {
        call { svc().startHid() }
        Thread.sleep(300)
        val out = call { svc().drainLog() }.trim()
        if (out.isNotEmpty()) out.lines().forEach {
            DLog.w("hid: $it")
            onHidOutput?.invoke(it)
        }
        if (!call { svc().isHidAlive }) throw IOException("`hid` thoát ngay: $out")
    }

    override fun writeHid(data: String) = call { svc().write(data + "\n") }

    override fun stopHid() {
        runCatching { service?.stopHid() }
    }

    override fun close() {
        closing = true
        runCatching { service?.stopHid() }
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
    }

    private fun svc(): IHidService = service ?: throw IOException("Shizuku UserService chưa sẵn sàng")

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: RemoteException) {
        throw IOException("Shizuku binder lỗi: ${e.message}", e)
    } catch (e: IllegalStateException) {
        throw IOException(e.message, e)
    }
}
