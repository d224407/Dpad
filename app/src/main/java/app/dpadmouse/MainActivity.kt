package app.dpadmouse

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.dpadmouse.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import kotlin.reflect.KMutableProperty0

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val prefs get() = MouseHub.prefs

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grant ->
        if (grant == PackageManager.PERMISSION_GRANTED) {
            DLog.i("Shizuku: đã cấp quyền")
            MouseHub.connect()
        } else {
            DLog.w("Shizuku: quyền bị từ chối")
        }
    }
    private val stateListener: () -> Unit = { render() }
    private val logListener: (String) -> Unit = { runOnUiThread { renderLog() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // --- backend
        if (prefs.backend == Backend.ADB) b.rbAdb.isChecked = true else b.rbShizuku.isChecked = true
        b.rgBackend.setOnCheckedChangeListener { _, id ->
            prefs.backend = if (id == b.rbAdb.id) Backend.ADB else Backend.SHIZUKU
            updateNetFields()
        }
        b.etHost.setText(prefs.host)
        b.etPort.setText(prefs.port.toString())
        updateNetFields()

        // --- độ nhạy
        b.sbSensitivity.progress = prefs.sensitivity - 1
        b.tvSens.text = getString(R.string.sensitivity, prefs.sensitivity)
        b.sbSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.sensitivity = progress + 1
                b.tvSens.text = getString(R.string.sensitivity, prefs.sensitivity)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        // --- nút chính
        b.btnConnect.setOnClickListener { saveNet(); startConnect() }
        b.btnDisconnect.setOnClickListener { MouseHub.disconnect() }
        b.btnEnableA11y.setOnClickListener { enableA11y() }
        b.btnOpenA11y.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                toast("Máy không có màn hình cài đặt trợ năng; dùng nút 'Bật dịch vụ trợ năng (qua shell)'.")
            }
        }
        b.btnDiag.setOnClickListener { diagnose() }

        // --- gán phím
        bindKey(b.btnKeyToggle, R.string.key_toggle, prefs::toggleKey)
        bindKey(b.btnKeyClick, R.string.key_click, prefs::clickKey)
        bindKey(b.btnKeyRight, R.string.key_right, prefs::rightClickKey)
        bindKey(b.btnKeyScrollUp, R.string.key_scroll_up, prefs::scrollUpKey)
        bindKey(b.btnKeyScrollDown, R.string.key_scroll_down, prefs::scrollDownKey)

        // --- công cụ debug (chỉ hiện ở bản debug)
        b.groupDebug.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        b.btnTestMove.setOnClickListener {
            val e = MouseHub.engine
            if (e == null) toast("Chưa kết nối") else e.nudge(120, 0)
        }
        b.btnTestClick.setOnClickListener {
            val e = MouseHub.engine
            if (e == null) toast("Chưa kết nối") else e.click()
        }
        b.btnCopyLog.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DpadMouse log", DLog.dumpAll()))
            toast("Đã sao chép log")
        }
        b.btnClearLog.setOnClickListener { DLog.clear() }

        Shizuku.addRequestPermissionResultListener(permListener)
    }

    override fun onStart() {
        super.onStart()
        MouseHub.addListener(stateListener)
        DLog.listener = logListener
        render()
        renderLog()
    }

    override fun onStop() {
        MouseHub.removeListener(stateListener)
        DLog.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ hành động

    private fun saveNet() {
        prefs.host = b.etHost.text.toString().trim().ifEmpty { "127.0.0.1" }
        prefs.port = b.etPort.text.toString().toIntOrNull() ?: 5555
    }

    private fun updateNetFields() {
        val adb = prefs.backend == Backend.ADB
        b.etHost.isEnabled = adb
        b.etPort.isEnabled = adb
    }

    private fun startConnect() {
        if (prefs.backend == Backend.SHIZUKU) {
            if (!Shizuku.pingBinder()) {
                DLog.w("Shizuku chưa chạy")
                toast("Shizuku chưa chạy. Hãy mở Shizuku và khởi động nó.")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQ_SHIZUKU)
                return
            }
        }
        MouseHub.connect()
    }

    private fun enableA11y() {
        saveNet()
        if (prefs.backend == Backend.SHIZUKU && (!Shizuku.pingBinder() ||
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
        ) {
            toast("Cần Shizuku đang chạy và đã cấp quyền (bấm 'Kết nối' trước).")
            return
        }
        MouseHub.enableAccessibilityService { r ->
            r.onSuccess { DLog.i("Bật trợ năng: $it") }
                .onFailure { DLog.e("Bật trợ năng lỗi: ${it.message}", it) }
            render()
        }
    }

    private fun diagnose() {
        saveNet()
        val cmd = "echo uid=$(id -u); getprop ro.build.version.release; getprop ro.build.version.sdk; " +
            "getprop ro.product.model; ls -l /system/bin/hid /dev/uhid 2>&1; " +
            "settings get secure enabled_accessibility_services"
        MouseHub.execOnce(cmd) { r ->
            DLog.i("Chẩn đoán:\n" + r.getOrElse { "LỖI: ${it.message}" })
        }
    }

    private fun bindKey(btn: Button, labelRes: Int, prop: KMutableProperty0<Int>) {
        fun refresh() { btn.text = getString(labelRes, keyName(prop.get())) }
        refresh()
        btn.setOnClickListener {
            learnKey { code ->
                prop.set(code)
                refresh()
            }
        }
    }

    private fun learnKey(onKey: (Int) -> Unit) {
        val dlg = AlertDialog.Builder(this)
            .setMessage(R.string.learn_msg)
            .setNegativeButton(R.string.learn_none) { _, _ -> onKey(KeyEvent.KEYCODE_UNKNOWN) }
            .create()
        dlg.setOnKeyListener { d, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                false
            } else {
                if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                    onKey(keyCode)
                    d.dismiss()
                }
                true
            }
        }
        dlg.show()
    }

    private fun keyName(code: Int): String =
        if (code == KeyEvent.KEYCODE_UNKNOWN) "(không dùng)"
        else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    // ------------------------------------------------------------------ hiển thị

    private fun render() {
        val st = when (MouseHub.state) {
            MouseHub.State.DISCONNECTED -> "chưa kết nối"
            MouseHub.State.CONNECTING -> "đang kết nối…"
            MouseHub.State.CONNECTED -> "đã tạo chuột ảo"
        }
        b.tvStatus.text = buildString {
            append("Shell: ").append(st)
            append("\nChế độ chuột: ").append(if (MouseHub.mouseModeOn) "BẬT" else "tắt")
            append("\nTrợ năng: ").append(if (MouseHub.serviceConnected) "đang chạy" else "chưa bật")
            MouseHub.lastError?.let { append("\nLỗi gần nhất: ").append(it) }
        }
    }

    private fun renderLog() {
        b.tvLog.text = DLog.dump()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQ_SHIZUKU = 1001
    }
}
