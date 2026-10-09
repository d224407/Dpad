package app.dpadmouse

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.dpadmouse.ui.M3Switch
import app.dpadmouse.ui.Ui
import kotlin.reflect.KMutableProperty0

class MainActivity : AppCompatActivity() {

    private lateinit var ui: Ui
    private val prefs get() = MouseHub.prefs

    // --- main page
    private lateinit var subtitle: TextView
    private lateinit var masterRow: Ui.Row
    private lateinit var masterSwitch: M3Switch
    private lateinit var statusRow: Ui.Row
    private lateinit var a11yRow: Ui.Row
    private lateinit var hostField: EditText
    private lateinit var portField: EditText
    private lateinit var sensRow: Ui.Row
    private lateinit var keysRow: Ui.Row
    private var storageRow: Ui.Row? = null

    // --- "Map keys" sub-page
    private lateinit var keysPage: FrameLayout
    private lateinit var keysBack: View
    private val keySlots = mutableListOf<Triple<Slot, TextView, Ui.Row>>()

    // --- log page (debug only)
    private var mainPanel: View? = null
    private var logPanel: View? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var logFilter: Char? = null
    private val filterChips = LinkedHashMap<Char?, TextView>()
    private val navTabs = mutableListOf<Triple<LinearLayout, ImageView, TextView>>()

    private class Slot(val title: String, val desc: String, val prop: KMutableProperty0<Int>)

    private val stateListener: () -> Unit = { render() }
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeKeys()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Strings.lang = prefs.language
        ui = Ui(this)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ui.c(R.color.md_surface))
            fitsSystemWindows = true
        }
        val main = buildMainPanel()
        mainPanel = main
        root.addView(main, matchParent())

        if (DebugTools.ENABLED) {
            val log = buildLogPanel()
            log.visibility = View.GONE
            logPanel = log
            root.addView(log, matchParent())
            root.addView(
                buildNav(),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = ui.dp(16) }
            )
            showPage(0)
        }

        keysPage = buildKeysPage()
        keysPage.visibility = View.GONE
        root.addView(keysPage, matchParent())

        setContentView(root)
        onBackPressedDispatcher.addCallback(this, backCallback)

        if (DebugTools.ENABLED) DebugTools.requestStorageAccess(this)
    }

    override fun onStart() {
        super.onStart()
        MouseHub.addListener(stateListener)
        if (DebugTools.ENABLED) DLog.listener = { runOnUiThread { renderLog() } }
        render()
        if (DebugTools.ENABLED) renderLog()
    }

    override fun onStop() {
        MouseHub.removeListener(stateListener)
        if (DebugTools.ENABLED) DLog.listener = null
        saveNet()
        super.onStop()
    }

    // ================================================================== main page

    private fun buildMainPanel(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(if (DebugTools.ENABLED) 104 else 32))
        }

        // --- title row with the language toggle in the top-right corner
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(ui.label(Strings.appTitle, 28f, R.color.md_on_surface, bold = true).apply {
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
        })
        subtitle = ui.label("", 13f, R.color.md_on_surface_variant).apply {
            setPadding(ui.dp(4), ui.dp(2), ui.dp(4), 0)
        }
        titleCol.addView(subtitle)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        titleRow.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(languageButton(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = ui.dp(10) })
        col.addView(titleRow)

        // --- VIRTUAL MOUSE
        col.addView(ui.listTitle(Strings.sectionVirtualMouse))
        masterSwitch = M3Switch(ui)
        masterRow = ui.Row(Strings.mouseModeRow, "", masterSwitch) { onMasterClick() }
        col.addView(ui.group(masterRow))

        // --- LOCAL ADB
        col.addView(ui.listTitle(Strings.sectionLocalAdb))
        statusRow = ui.Row(Strings.statusRowName, "")
        hostField = field(prefs.host, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, 170)
        portField = field(prefs.port.toString(), InputType.TYPE_CLASS_NUMBER, 100)
        col.addView(
            ui.group(
                statusRow,
                ui.Row(Strings.addressRowName, Strings.addressRowDesc, hostField),
                ui.Row(Strings.portRowName, Strings.portRowDesc, portField)
            )
        )
        col.addView(
            ui.actionRow(
                ui.pill(Strings.connectBtn, primary = true) { saveNet(); MouseHub.connect() },
                ui.pill(Strings.disconnectBtn) { MouseHub.disconnect() }
            )
        )

        // --- CONTROL
        col.addView(ui.listTitle(Strings.sectionControl))
        val seek = SeekBar(this).apply {
            max = 99
            progress = prefs.sensitivity - 1
            progressTintList = ColorStateList.valueOf(ui.c(R.color.md_primary))
            thumbTintList = ColorStateList.valueOf(ui.c(R.color.md_primary))
        }
        val seekBox = LinearLayout(this).apply {
            addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // trailingExpands lets the slider stretch across the remaining row width instead
        // of being squeezed down to a short, wrap-content box.
        sensRow = ui.Row(Strings.sensitivityRowName, "${prefs.sensitivity} / 100", seekBox, trailingExpands = true)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                prefs.sensitivity = progress + 1
                sensRow.setDesc("${prefs.sensitivity} / 100")
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            imageTintList = ColorStateList.valueOf(ui.c(R.color.md_on_surface_variant))
        }
        keysRow = ui.Row(Strings.mapKeysRowName, "", chevron) { openKeys() }
        col.addView(ui.group(sensRow, keysRow))

        // --- ACCESSIBILITY
        col.addView(ui.listTitle(Strings.sectionAccessibility))
        a11yRow = ui.Row(Strings.a11yServiceRowName, "")
        col.addView(ui.group(a11yRow))
        col.addView(
            ui.actionRow(
                ui.pill(Strings.enableViaAdbBtn, primary = true) { enableA11y() },
                ui.pill(Strings.openSettingsBtn) { openA11ySettings() }
            )
        )

        // --- DEBUG (debug build only)
        if (DebugTools.ENABLED) {
            col.addView(ui.listTitle(Strings.sectionDebug))
            val storage = ui.Row(Strings.storageRowName(DebugTools.DIR), "") { DebugTools.requestStorageAccess(this) }
            storageRow = storage
            col.addView(
                ui.group(
                    ui.Row(Strings.nudgeRightRowName, Strings.nudgeRightRowDesc) {
                        val e = MouseHub.engine
                        if (e == null) toast(Strings.notConnectedToast) else e.nudge(120, 0)
                    },
                    ui.Row(Strings.nudgeDownRowName, Strings.nudgeDownRowDesc) {
                        val e = MouseHub.engine
                        if (e == null) toast(Strings.notConnectedToast) else e.nudge(0, 120)
                    },
                    ui.Row(Strings.testClickRowName, Strings.testClickRowDesc) {
                        val e = MouseHub.engine
                        if (e == null) toast(Strings.notConnectedToast) else e.click()
                    },
                    ui.Row(Strings.diagnoseRowName, Strings.diagnoseRowDesc) { diagnose() },
                    storage
                )
            )
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(col)
        }
    }

    /** Small pill button in the corner that toggles the UI language (EN <-> VI). */
    private fun languageButton(): View =
        ui.pill(Strings.languageToggleLabel) { toggleLanguage() }

    private fun toggleLanguage() {
        saveNet()
        prefs.language = if (prefs.language == "en") "vi" else "en"
        recreate()
    }

    private fun field(initial: String, type: Int, widthDp: Int): EditText = EditText(this).apply {
        setText(initial)
        inputType = type
        setSingleLine()
        setTextColor(ui.c(R.color.md_on_surface))
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8))
        background = ui.pillBg(R.color.md_surface_container)
        minWidth = ui.dp(widthDp)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    private fun onMasterClick() {
        saveNet()
        when {
            MouseHub.state == MouseHub.State.CONNECTING -> Unit
            MouseHub.mouseModeOn -> MouseHub.setMouseMode(false)
            MouseHub.state == MouseHub.State.CONNECTED -> MouseHub.setMouseMode(true)
            else -> MouseHub.toggleFromService() // connect then turn on
        }
    }

    private fun saveNet() {
        if (!::hostField.isInitialized) return
        prefs.host = hostField.text.toString().trim().ifEmpty { "127.0.0.1" }
        prefs.port = portField.text.toString().toIntOrNull() ?: 5555
    }

    private fun enableA11y() {
        saveNet()
        MouseHub.enableAccessibilityService { r ->
            r.onSuccess { toast(Strings.a11yEnabledToast) }
                .onFailure { toast(Strings.errorToast(it.message ?: "")) }
            render()
        }
    }

    private fun openA11ySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast(Strings.noA11ySettingsToast)
        }
    }

    private fun diagnose() {
        saveNet()
        val cmd = "echo uid=$(id -u); getprop ro.build.version.release; getprop ro.build.version.sdk; " +
            "getprop ro.product.model; ls -l /system/bin/hid /dev/uhid 2>&1; " +
            "settings get secure enabled_accessibility_services"
        MouseHub.execOnce(cmd) { r ->
            DLog.i(Strings.diagnosePrefix + r.getOrElse { "${Strings.diagnoseErrorPrefix}${it.message}" })
        }
    }

    private fun render() {
        val st = MouseHub.state
        val on = MouseHub.mouseModeOn
        masterSwitch.checked = on
        masterRow.setActive(on)
        masterRow.setDesc(
            when {
                on -> Strings.masterDescOn
                st == MouseHub.State.CONNECTED -> Strings.masterDescConnectedOff
                st == MouseHub.State.CONNECTING -> Strings.masterDescConnecting
                else -> Strings.masterDescDefault
            }
        )
        val shell = when (st) {
            MouseHub.State.DISCONNECTED -> Strings.statusDisconnected
            MouseHub.State.CONNECTING -> Strings.statusConnecting
            MouseHub.State.CONNECTED -> Strings.statusConnected
        }
        statusRow.setDesc(shell + (MouseHub.lastError?.let { Strings.errorSuffix(it) } ?: ""))
        a11yRow.setDesc(if (MouseHub.serviceConnected) Strings.a11yRunning else Strings.a11yNotEnabled)
        subtitle.text = if (on) Strings.subtitleOn else Strings.subtitleOff
        keysRow.setDesc(Strings.toggleDesc(keyName(prefs.toggleKey)))
        storageRow?.setDesc(if (DebugTools.hasStorageAccess(this)) Strings.storageGranted else Strings.storageNotGranted)
    }

    // ================================================================== "Map keys" page

    private fun buildKeysPage(): FrameLayout {
        val page = FrameLayout(this).apply {
            setBackgroundColor(ui.c(R.color.md_surface))
            isClickable = true
            fitsSystemWindows = true
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
            minimumHeight = ui.dp(64)
        }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(ui.c(R.color.md_on_surface))
            scaleType = ImageView.ScaleType.CENTER
            background = ui.pillBg(R.color.md_surface_container_high)
            isFocusable = true
            isClickable = true
            setOnClickListener { closeKeys() }
        }
        keysBack = back
        header.addView(back, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        header.addView(
            ui.label(Strings.mapKeysTitle, 20f, R.color.md_on_surface, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(12) }
        )
        col.addView(header)

        val slots = listOf(
            Slot(Strings.slotToggleTitle, Strings.slotToggleDesc, prefs::toggleKey),
            Slot(Strings.slotLeftClickTitle, Strings.slotLeftClickDesc, prefs::clickKey),
            Slot(Strings.slotRightClickTitle, Strings.slotRightClickDesc, prefs::rightClickKey),
            Slot(Strings.slotScrollUpTitle, Strings.slotScrollUpDesc, prefs::scrollUpKey),
            Slot(Strings.slotScrollDownTitle, Strings.slotScrollDownDesc, prefs::scrollDownKey)
        )
        keySlots.clear()
        val rows = slots.map { slot ->
            val chip = ui.chip(keyName(slot.prop.get()))
            val row = ui.Row(slot.title, slot.desc, chip) {
                learn(slot) {
                    chip.text = keyName(slot.prop.get())
                    render()
                }
            }
            keySlots.add(Triple(slot, chip, row))
            row
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24))
            addView(ui.group(*rows.toTypedArray()))
            addView(
                ui.label(
                    Strings.keysPageNote,
                    13f, R.color.md_on_surface_variant
                ).apply { setPadding(ui.dp(4), ui.dp(12), ui.dp(4), 0) }
            )
            addView(ui.actionRow(ui.pill(Strings.resetDefaultsBtn) {
                prefs.resetKeys()
                keySlots.forEach { (slot, chip, _) -> chip.text = keyName(slot.prop.get()) }
                render()
            }))
        }
        col.addView(ScrollView(this).apply { addView(content) })
        page.addView(col, matchParent())
        return page
    }

    private fun openKeys() {
        keysPage.visibility = View.VISIBLE
        backCallback.isEnabled = true
        keysBack.requestFocus()
    }

    private fun closeKeys() {
        keysPage.visibility = View.GONE
        backCallback.isEnabled = false
        keysRow.requestFocus()
    }

    /** Step 1: listen for a key on the controller. */
    private fun learn(slot: Slot, after: () -> Unit) {
        MouseHub.learningKeys = true // the accessibility service releases every key so the dialog can receive it
        val dlg = AlertDialog.Builder(this)
            .setTitle(slot.title)
            .setMessage(Strings.learnMessage)
            .setNegativeButton(Strings.dontUseKeyBtn) { _, _ ->
                slot.prop.set(KeyEvent.KEYCODE_UNKNOWN)
                after()
            }
            .create()
        dlg.setOnDismissListener { MouseHub.learningKeys = false }
        dlg.setOnKeyListener { d, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                false
            } else {
                if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                    d.dismiss()
                    confirmKey(slot, keyCode, after)
                }
                true
            }
        }
        dlg.show()
    }

    /** Step 2: ask the user to press X again on the controller to confirm the captured key. */
    private fun confirmKey(slot: Slot, keyCode: Int, after: () -> Unit) {
        MouseHub.learningKeys = true
        val dlg = AlertDialog.Builder(this)
            .setTitle(slot.title)
            .setMessage(Strings.confirmKeyMessage(keyName(keyCode)))
            .setNegativeButton(Strings.cancelBtn) { _, _ -> /* keep the previous mapping, change nothing */ }
            .create()
        dlg.setOnDismissListener { MouseHub.learningKeys = false }
        dlg.setOnKeyListener { d, code, ev ->
            when {
                code == KeyEvent.KEYCODE_BACK -> false
                code == KeyEvent.KEYCODE_BUTTON_X -> {
                    if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                        slot.prop.set(keyCode)
                        after()
                        d.dismiss()
                    }
                    true
                }
                else -> true // swallow every other key while waiting for X
            }
        }
        dlg.show()
    }

    private fun keyName(code: Int): String =
        if (code == KeyEvent.KEYCODE_UNKNOWN) Strings.notUsedKeyName
        else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    // ================================================================== log page (debug)

    private fun buildNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8))
            background = ui.pillBg(R.color.md_surface_container_high, 40)
            elevation = ui.dp(8).toFloat()
        }
        val defs = listOf(Strings.navSettings to R.drawable.ic_tune, Strings.navLog to R.drawable.ic_article)
        defs.forEachIndexed { index, (label, icon) ->
            val iv = ImageView(this).apply { setImageResource(icon) }
            val tv = ui.label(label, 15f, R.color.md_on_secondary_container, bold = true)
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setOnClickListener { showPage(index) }
            }
            tab.addView(iv, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            tab.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ui.dp(10) })
            bar.addView(tab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) marginStart = ui.dp(6) })
            navTabs.add(Triple(tab, iv, tv))
        }
        return bar
    }

    private fun showPage(index: Int) {
        mainPanel?.visibility = if (index == 0) View.VISIBLE else View.GONE
        logPanel?.visibility = if (index == 1) View.VISIBLE else View.GONE
        navTabs.forEachIndexed { i, (tab, iv, tv) ->
            val active = i == index
            tv.visibility = if (active) View.VISIBLE else View.GONE
            tab.background = if (active) ui.pillBg(R.color.md_secondary_container, 40)
            else ui.pillBg(R.color.md_surface_container_high, 40)
            tab.setPadding(ui.dp(18), ui.dp(12), ui.dp(if (active) 24 else 18), ui.dp(12))
            iv.imageTintList = ColorStateList.valueOf(
                ui.c(if (active) R.color.md_on_secondary_container else R.color.md_on_surface_variant)
            )
        }
        if (index == 1) renderLog()
    }

    private fun buildLogPanel(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), 0)
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            ui.label(Strings.logTitle, 21f, R.color.md_on_surface, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(4) }
        )
        header.addView(ui.iconButton(R.drawable.ic_save) {
            DebugTools.saveLog(this, DLog.dumpAll()) { msg ->
                toast(if (msg.startsWith("Error")) msg else Strings.logSavedPrefix + msg)
            }
        })
        header.addView(ui.iconButton(R.drawable.ic_copy) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DpadMouse log", DLog.dumpAll()))
            toast(Strings.logCopiedToast)
        })
        header.addView(ui.iconButton(R.drawable.ic_delete) { DLog.clear() })
        col.addView(header)

        val chips = LinearLayout(this).apply { setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(12)) }
        listOf<Pair<Char?, String>>(
            null to "ALL", 'D' to "DEBUG", 'I' to "INFO", 'W' to "WARN", 'E' to "ERROR"
        ).forEachIndexed { i, (level, text) ->
            val chip = ui.filterChip(text) { setLogFilter(level) }
            filterChips[level] = chip
            chips.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) marginStart = ui.dp(8) })
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
        setLogFilter(null)

        val text = ui.label("", 12f, R.color.md_on_surface_variant, mono = true).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
        }
        logText = text
        val scroll = ScrollView(this).apply {
            background = ui.pillBg(R.color.md_surface_container_high, 20)
            isFocusable = true // scroll the log with the controller's arrow keys
            addView(text)
        }
        logScroll = scroll
        col.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { bottomMargin = ui.dp(88) })
        return col
    }

    private fun setLogFilter(level: Char?) {
        logFilter = level
        filterChips.forEach { (lv, chip) ->
            with(ui) { chip.setSelectedChip(lv == level, lv == 'E') }
        }
        renderLog()
    }

    private fun renderLog() {
        val tv = logText ?: return
        val sb = SpannableStringBuilder()
        for (e in DLog.snapshot(logFilter)) {
            val start = sb.length
            sb.append(e.text).append('\n')
            val color = when (e.level) {
                'E' -> ui.c(R.color.log_error)
                'W' -> ui.c(R.color.log_warn)
                else -> Color.TRANSPARENT
            }
            if (color != Color.TRANSPARENT) {
                sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = sb
        // Not using fullScroll(): it steals the controller's focus back to the log pane on every new line.
        tv.post { logScroll?.scrollTo(0, tv.bottom) }
    }

    // ================================================================== utilities

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
    )

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
