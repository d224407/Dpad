package app.dpadmouse

import android.app.Application
import android.os.StrictMode

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MouseHub.init(this)
        if (BuildConfig.DEBUG) {
            // Catch early: I/O on the main thread, Closeable leaks, cleartext... (log only, no crash)
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        DLog.i("App starting v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
    }
}
