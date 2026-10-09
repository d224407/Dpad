package app.dpadmouse

import android.app.Activity
import android.content.Context

/** Release build: completely empty, no permission requests, no logging. */
object DebugTools {
    const val ENABLED = false
    const val DIR = ""

    fun hasStorageAccess(context: Context): Boolean = false
    fun requestStorageAccess(activity: Activity) = Unit
    fun onAdbConnected(context: Context) = Unit
    fun saveLog(context: Context, text: String, done: (String) -> Unit) = Unit
}
