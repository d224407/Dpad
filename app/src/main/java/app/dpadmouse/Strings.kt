package app.dpadmouse

/**
 * All user-facing text for the main UI, in English and Vietnamese.
 * Set [lang] once (from [Prefs.language]) before building the UI; every
 * property below reflects the current value of [lang].
 */
object Strings {
    /** "en" or "vi" */
    var lang: String = "en"

    private fun t(en: String, vi: String): String = if (lang == "en") en else vi

    val appTitle get() = "Dpad Mouse"

    // --- Virtual mouse section
    val sectionVirtualMouse get() = t("Virtual mouse", "Chuột ảo")
    val mouseModeRow get() = t("Mouse mode", "Chế độ chuột")
    val subtitleOn get() = t("Mouse mode is ON", "Chế độ chuột đang BẬT")
    val subtitleOff get() = t("Cursor moves with controller keys", "Con trỏ chuột bằng phím điều khiển")

    // --- Local ADB section
    val sectionLocalAdb get() = t("Local ADB", "ADB cục bộ")
    val statusRowName get() = t("Status", "Trạng thái")
    val addressRowName get() = t("Address", "Địa chỉ")
    val addressRowDesc get() = t("Default 127.0.0.1", "Mặc định 127.0.0.1")
    val portRowName get() = t("Port", "Cổng")
    val portRowDesc get() = t("Enable with: adb tcpip 5555", "Bật bằng: adb tcpip 5555")
    val connectBtn get() = t("Connect", "Kết nối")
    val disconnectBtn get() = t("Disconnect", "Ngắt kết nối")

    // --- Control section
    val sectionControl get() = t("Control", "Điều khiển")
    val sensitivityRowName get() = t("Sensitivity", "Độ nhạy")
    val mapKeysRowName get() = t("Map keys", "Gán phím")

    // --- Accessibility section
    val sectionAccessibility get() = t("Accessibility", "Trợ năng")
    val a11yServiceRowName get() = t("Accessibility service", "Dịch vụ trợ năng")
    val enableViaAdbBtn get() = t("Enable via ADB", "Bật qua ADB")
    val openSettingsBtn get() = t("Open settings", "Mở cài đặt")

    // --- Debug section
    val sectionDebug get() = t("Debug", "Gỡ lỗi")
    fun storageRowName(dir: String) = t("Write access $dir", "Quyền ghi $dir")
    val nudgeRightRowName get() = t("Nudge cursor right", "Nhích chuột sang phải")
    val nudgeRightRowDesc get() = t("Try sending an HID report, no accessibility needed", "Thử gửi report HID, không cần trợ năng")
    val nudgeDownRowName get() = t("Nudge cursor down", "Nhích chuột xuống")
    val nudgeDownRowDesc get() = t("Test the vertical axis", "Thử trục dọc")
    val testClickRowName get() = t("Test click", "Click thử")
    val testClickRowDesc get() = t("Left button press and release", "Nút trái nhấn rồi nhả")
    val diagnoseRowName get() = t("Diagnose environment", "Chẩn đoán môi trường")
    val diagnoseRowDesc get() = t("uid, Android version, hid command", "uid, phiên bản Android, lệnh hid")

    // --- Toasts / status
    val notConnectedToast get() = t("Not connected", "Chưa kết nối")
    val masterDescOn get() = t("On: arrows move, OK clicks", "Đang bật: mũi tên di chuyển, OK để click")
    val masterDescConnectedOff get() = t("Virtual mouse created, turning off", "Đã tạo chuột ảo, đang tắt")
    val masterDescConnecting get() = t("Connecting…", "Đang kết nối…")
    val masterDescDefault get() = t("Turning on connects ADB automatically", "Bật sẽ tự kết nối ADB")
    val statusDisconnected get() = t("Not connected", "Chưa kết nối")
    val statusConnecting get() = t("Connecting…", "Đang kết nối…")
    val statusConnected get() = t("Connected, virtual mouse active", "Đã kết nối, chuột ảo đang hoạt động")
    fun errorSuffix(msg: String) = t("\nError: $msg", "\nLỗi: $msg")
    val a11yRunning get() = t("Running", "Đang chạy")
    val a11yNotEnabled get() = t("Not enabled (needed to capture arrow keys)", "Chưa bật (cần để bắt phím mũi tên)")
    fun toggleDesc(keyName: String) = t("On/off key: $keyName", "Bật/tắt: $keyName")
    val storageGranted get() = t("Granted", "Đã cấp")
    val storageNotGranted get() = t("Not granted, tap to request", "Chưa cấp, bấm để xin quyền")
    val a11yEnabledToast get() = t("Accessibility service enabled", "Đã bật dịch vụ trợ năng")
    fun errorToast(msg: String) = t("Error: $msg", "Lỗi: $msg")
    val noA11ySettingsToast get() = t(
        "No accessibility screen on this device; use the 'Enable via ADB' button.",
        "Máy không có màn hình trợ năng; dùng nút 'Bật qua ADB'."
    )
    val diagnosePrefix get() = t("Diagnosis:\n", "Chẩn đoán:\n")
    val diagnoseErrorPrefix get() = t("ERROR: ", "LỖI: ")

    // --- Map keys page
    val mapKeysTitle get() = t("Map keys", "Gán phím")
    val slotToggleTitle get() = t("Mouse on/off", "Bật / tắt chuột")
    val slotToggleDesc get() = t("Always captured, even while the mouse is off", "Luôn được bắt, kể cả khi chuột đang tắt")
    val slotLeftClickTitle get() = t("Left click", "Click trái")
    val slotLeftClickDesc get() = t("Hold to drag", "Giữ để kéo thả")
    val slotRightClickTitle get() = t("Right click", "Click phải")
    val slotRightClickDesc get() = t("Leave unset: the Back key still works normally", "Để trống: phím Back vẫn hoạt động bình thường")
    val slotScrollUpTitle get() = t("Scroll up", "Cuộn lên")
    val slotScrollUpDesc get() = t("Hold to scroll continuously", "Giữ để cuộn liên tục")
    val slotScrollDownTitle get() = t("Scroll down", "Cuộn xuống")
    val slotScrollDownDesc get() = t("Hold to scroll continuously", "Giữ để cuộn liên tục")
    val keysPageNote get() = t(
        "Tap an item, then press a key on the controller. Arrows always move the cursor while the mouse is on.",
        "Bấm vào một mục rồi bấm phím trên điều khiển. Mũi tên luôn dùng để di chuyển khi chuột đang bật."
    )
    val resetDefaultsBtn get() = t("Restore defaults", "Khôi phục mặc định")

    // --- Learn / confirm key dialogs
    val learnMessage get() = t("Press the key you want to use on the controller.", "Bấm phím muốn dùng trên điều khiển.")
    val dontUseKeyBtn get() = t("Don't use a key", "Không dùng phím này")
    fun confirmKeyMessage(keyName: String) = t(
        "Press X again on the controller to confirm \"$keyName\".",
        "Bấm lại phím X trên điều khiển để xác nhận \"$keyName\"."
    )
    val cancelBtn get() = t("Cancel", "Huỷ")
    val notUsedKeyName get() = t("Not used", "Không dùng")

    // --- Debug nav / log page
    val navSettings get() = t("Settings", "Cài đặt")
    val navLog get() = t("Log", "Log")
    val logTitle get() = t("Log", "Log")
    val logSavedPrefix get() = t("Log saved: ", "Đã ghi log: ")
    val logSaveErrorPrefix get() = t("Error", "Lỗi")
    val logCopiedToast get() = t("Log copied", "Đã sao chép log")

    // --- Language toggle
    val switchLanguageDesc get() = t("Switch language", "Đổi ngôn ngữ")
    /** Label shown on the toggle button: the language it will switch TO. */
    val languageToggleLabel get() = t("VI", "EN")
}
