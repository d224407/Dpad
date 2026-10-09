package app.dpadmouse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** DEBUG BUILD ONLY: requests storage permission and writes logs to /sdcard/Dpad/. (The release build has an empty version with the same name.) */
object DebugTools {
    const val ENABLED = true
    const val DIR = "/sdcard/Dpad"

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** Automatically requests permission when the app opens. On devices with no permission screen, it is granted via ADB on connect. */
    fun requestStorageAccess(activity: Activity) {
        if (hasStorageAccess(activity)) return
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            } catch (e: Exception) {
                DLog.w("Could not open the 'Manage all files' screen (${e.message}); will grant via ADB on connect")
            }
        } else {
            activity.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 77)
        }
    }

    /** Called once ADB is connected: grants permission via shell if still missing. */
    fun onAdbConnected(context: Context) {
        if (hasStorageAccess(context)) return
        val pkg = context.packageName
        val cmd = "appops set $pkg MANAGE_EXTERNAL_STORAGE allow; " +
            "pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE; echo done"
        DLog.i("Granting storage permission via ADB")
        MouseHub.execOnce(cmd) { r ->
            DLog.i("Grant result: " + r.getOrElse { "error ${it.message}" })
        }
    }

    /** Writes logs to /sdcard/Dpad/dpad-log-<time>.txt. [done] runs on the main thread with the path or an error. */
    fun saveLog(context: Context, text: String, done: (String) -> Unit) {
        val path = "$DIR/dpad-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        Thread({
            val msg = try {
                if (hasStorageAccess(context)) {
                    File(DIR).mkdirs()
                    File(path).writeText(text)
                    path
                } else {
                    writeViaShell(path, text)
                }
            } catch (e: Exception) {
                try {
                    writeViaShell(path, text)
                } catch (e2: Exception) {
                    "Error saving log: ${e2.message}"
                }
            }
            Handler(Looper.getMainLooper()).post { done(msg) }
        }, "dpad-save-log").start()
    }

    /** Fallback: writes via shell permission (ADB), chunking base64 to stay under the command-line limit. */
    private fun writeViaShell(path: String, text: String): String {
        MouseHub.execSync("mkdir -p $DIR && : > '$path'")
        val bytes = text.toByteArray()
        var off = 0
        while (off < bytes.size) {
            val n = minOf(2000, bytes.size - off)
            val b64 = Base64.encodeToString(bytes, off, n, Base64.NO_WRAP)
            MouseHub.execSync("echo '$b64' | base64 -d >> '$path'")
            off += n
        }
        return path
    }
}
