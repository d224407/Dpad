package app.dpadmouse

import android.app.Application
import android.os.StrictMode

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MouseHub.init(this)
        if (BuildConfig.DEBUG) {
            // Bắt sớm: I/O trên main thread, leak Closeable, cleartext... (chỉ ghi log, không crash)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        DLog.i("App khởi động v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
    }
}
