package com.bmsjk.app.ui.i18n

import com.bmsjk.app.data.AppLanguage
import com.bmsjk.core.protocol.JkErrorFlag
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.ChartWindow

data class UiStrings(
    val isVi: Boolean,
    val monitor: String,
    val chart: String,
    val settings: String,
    val connect: String,
    val readOnly: String,
    val socRing: String,
    val powerGauge: String,
    val tempsMos: String,
    val miniChart: String,
    val charging: String,
    val discharging: String,
    val standby: String,
    val delta: String,
    val chgMos: String,
    val dsgMos: String,
    val balMos: String,
    val current: String,
    val power: String,
    val remCap: String,
    val avg: String,
    val cellWord: String,
    val pause: String,
    val resume: String,
    val clear: String,
    val noChartData: String,
    val roTitle: String,
    val deviceInfoGroup: String,
    val packBalanceGroup: String,
    val voltageCurrentGroup: String,
    val tempProtectGroup: String,
    val protocolGroup: String,
    val languageLabel: String,
    val scan: String,
    val scanning: String,
    val qrButton: String,
    val searchHint: String,
    val scanOnceHint: String,
    val scanIdleHint: String,
    val noBmsFoundHint: String,
    val connectedBadge: String,
    val connectingBadge: String,
    val errorBadge: String,
    val disconnect: String,
    val connectBtn: String,
    val passcodeTitle: String,
    val passcodeDesc: String,
    val passcodeWrong: String,
    val passcodeHint: String,
    val confirmPasscode: String,
    val forgetPasscode: String,
    val qrDialogTitle: String,
    val qrDialogHint: String,
    val cancel: String,
    val permissionTitle: String,
    val permissionDesc: String,
    val grantPermission: String,
    val notConnectedTitle: String,
    val notConnectedDesc: String,
    val goToConnect: String,
    val activeAlarms: String,
    val connectionLostTitle: String,
    val connectionLostDescPrefix: String,
    val connectionLostDescSuffix: String,
    val keepLastValuesBtn: String,
    val disconnectAndGoConnectBtn: String,
    val savedOfflineBadge: String,
    val lostBadge: String
) {
    fun connectionLostDesc(deviceName: String): String =
        "$connectionLostDescPrefix $deviceName $connectionLostDescSuffix"
    fun cellsTabLabel(cellCount: Int?): String {
        val n = cellCount?.takeIf { it > 0 }?.toString() ?: "N"
        return if (isVi) "$n Cell" else if (n == "1") "1 Cell" else "$n Cells"
    }

    fun cellVoltagesTitle(cellCount: Int?): String {
        val n = cellCount?.takeIf { it > 0 }?.toString() ?: "N"
        return if (isVi) "Điện áp $n Cell" else "$n-Cell Voltages"
    }
}

object AppStrings {
    private val VI = UiStrings(
        isVi = true,
        monitor = "Giám sát",
        chart = "Biểu đồ",
        settings = "Cài đặt",
        connect = "Kết nối",
        readOnly = "Read Only",
        socRing = "Dung lượng SOC",
        powerGauge = "Công suất & Dòng",
        tempsMos = "Nhiệt độ & MOSFET",
        miniChart = "Xu hướng Live",
        charging = "ĐANG SẠC",
        discharging = "ĐANG XẢ",
        standby = "ĐỨNG YÊN",
        delta = "Chênh lệch",
        chgMos = "Sạc",
        dsgMos = "Xả",
        balMos = "Cân bằng",
        current = "Dòng điện",
        power = "Công suất",
        remCap = "Dung lượng còn",
        avg = "TB",
        cellWord = "Cell",
        pause = "Tạm dừng",
        resume = "Tiếp tục",
        clear = "Xóa",
        noChartData = "Chờ dữ liệu telemetry để vẽ biểu đồ...",
        roTitle = "Cài đặt mạch JK (CHỈ ĐỌC)",
        deviceInfoGroup = "Thiết bị (Frame 0x03)",
        packBalanceGroup = "1. Pack & Cân bằng",
        voltageCurrentGroup = "2. Bảo vệ Điện áp & Dòng",
        tempProtectGroup = "3. Bảo vệ Nhiệt độ",
        protocolGroup = "Giao thức & Ngôn ngữ",
        languageLabel = "Ngôn ngữ hiển thị",
        scan = "Quét BMS",
        scanning = "Đang quét...",
        qrButton = "QR/MAC",
        searchHint = "Tìm tên hoặc MAC JK BMS...",
        scanOnceHint = "Chỉ hiện mạch JK BMS (tự quét 1 lần 8s, giữ nguyên kết nối đang chạy).",
        scanIdleHint = "Đã quét xong 1 lượt (chỉ hiện JK BMS). Bấm Quét BMS để tìm lại.",
        noBmsFoundHint = "Chưa thấy mạch JK BMS nào gần đây. Hãy đảm bảo BMS đang bật và không kết nối app khác.",
        connectedBadge = "Đang kết nối",
        connectingBadge = "Đang nối...",
        errorBadge = "Lỗi kết nối",
        disconnect = "Ngắt",
        connectBtn = "Kết nối",
        passcodeTitle = "Xác thực Passcode (Chỉ lần đầu)",
        passcodeDesc = "Lần đầu kết nối MAC mới sẽ hỏi mật khẩu, các lần sau tự mở khóa.",
        passcodeWrong = "Passcode không khớp với BMS. Vui lòng thử lại.",
        passcodeHint = "Nhập passcode (vd: 1234)",
        confirmPasscode = "Xác nhận & Xem thông số",
        forgetPasscode = "Quên passcode đã lưu",
        qrDialogTitle = "Nhập MAC hoặc dán mã QR",
        qrDialogHint = "Ví dụ: C8:47:8C:11:22:33 hoặc chuỗi QR có MAC",
        cancel = "Hủy",
        permissionTitle = "Cần quyền Bluetooth LE",
        permissionDesc = "Cấp quyền Bluetooth để quét và đọc dữ liệu JK BMS (chỉ đọc).",
        grantPermission = "Cấp quyền",
        notConnectedTitle = "Chưa kết nối JK BMS",
        notConnectedDesc = "Vào tab Kết nối để quét và kết nối mạch JK BMS của bạn.",
        goToConnect = "Mở Kết nối",
        activeAlarms = "Cảnh báo BMS",
        connectionLostTitle = "Mất kết nối",
        connectionLostDescPrefix = "Kết nối tới",
        connectionLostDescSuffix = "đã bị ngắt (có thể do đi quá xa). Giá trị cuối cùng vẫn được giữ lại để xem.",
        keepLastValuesBtn = "Đóng",
        disconnectAndGoConnectBtn = "Kết nối lại",
        savedOfflineBadge = "ĐÃ LƯU",
        lostBadge = "Mất kết nối"
    )

    private val EN = UiStrings(
        isVi = false,
        monitor = "Monitor",
        chart = "Charts",
        settings = "Settings",
        connect = "Connect",
        readOnly = "Read Only",
        socRing = "SOC Capacity",
        powerGauge = "Power & Current",
        tempsMos = "Temps & MOSFETs",
        miniChart = "Streaming Trend",
        charging = "CHARGING",
        discharging = "DISCHARGING",
        standby = "STANDBY",
        delta = "Delta",
        chgMos = "Chg",
        dsgMos = "Dsg",
        balMos = "Bal",
        current = "Current",
        power = "Power",
        remCap = "Remaining",
        avg = "Avg",
        cellWord = "Cell",
        pause = "Pause",
        resume = "Resume",
        clear = "Clear",
        noChartData = "Waiting for telemetry samples...",
        roTitle = "JK BMS Settings (READ-ONLY)",
        deviceInfoGroup = "Device Info (Frame 0x03)",
        packBalanceGroup = "1. Pack & Balancing",
        voltageCurrentGroup = "2. Voltage & Current Protections",
        tempProtectGroup = "3. Temperature Protections",
        protocolGroup = "Protocol & Language",
        languageLabel = "Display Language",
        scan = "Scan BMS",
        scanning = "Scanning...",
        qrButton = "QR/MAC",
        searchHint = "Search JK BMS name or MAC...",
        scanOnceHint = "Shows JK BMS devices only (one-shot 8s scan, active link stays intact).",
        scanIdleHint = "One-shot scan complete (JK BMS only). Tap Scan BMS to refresh.",
        noBmsFoundHint = "No JK BMS found nearby. Make sure the BMS is awake and no other app is connected.",
        connectedBadge = "Connected",
        connectingBadge = "Connecting...",
        errorBadge = "Conn Error",
        disconnect = "Disconnect",
        connectBtn = "Connect",
        passcodeTitle = "Verify Passcode (First Time Only)",
        passcodeDesc = "Required once per BMS MAC address; future connections unlock automatically.",
        passcodeWrong = "Passcode mismatch. Please verify and try again.",
        passcodeHint = "Enter passcode (e.g. 1234)",
        confirmPasscode = "Verify & View Telemetry",
        forgetPasscode = "Forget saved passcode",
        qrDialogTitle = "Enter MAC or Paste QR Payload",
        qrDialogHint = "Example: C8:47:8C:11:22:33 or QR payload containing MAC",
        cancel = "Cancel",
        permissionTitle = "Bluetooth LE Permission Required",
        permissionDesc = "Grant Bluetooth permissions to scan and monitor your JK BMS (read-only).",
        grantPermission = "Grant",
        notConnectedTitle = "No JK BMS Connected",
        notConnectedDesc = "Open Connect to scan and connect to your JK BMS.",
        goToConnect = "Open Connect",
        activeAlarms = "BMS Alarms",
        connectionLostTitle = "Connection Lost",
        connectionLostDescPrefix = "Connection to",
        connectionLostDescSuffix = "was lost (possibly out of range). The last telemetry values are kept for viewing.",
        keepLastValuesBtn = "Close",
        disconnectAndGoConnectBtn = "Reconnect",
        savedOfflineBadge = "SAVED",
        lostBadge = "Lost"
    )

    fun forLang(lang: AppLanguage): UiStrings = if (lang == AppLanguage.VI) VI else EN

    fun metricLabel(metric: ChartMetric, lang: AppLanguage): String = when (metric) {
        ChartMetric.PACK_VOLTAGE -> if (lang == AppLanguage.VI) "ĐIỆN ÁP (V)" else "VOLTAGE (V)"
        ChartMetric.CURRENT -> if (lang == AppLanguage.VI) "DÒNG (A)" else "CURRENT (A)"
        ChartMetric.POWER -> if (lang == AppLanguage.VI) "CÔNG SUẤT (W)" else "POWER (W)"
        ChartMetric.DELTA_MV -> "DELTA (V)"
        ChartMetric.MAX_TEMPERATURE -> if (lang == AppLanguage.VI) "NHIỆT MAX (°C)" else "MAX TEMP (°C)"
    }

    fun windowLabel(window: ChartWindow, lang: AppLanguage): String = when (window) {
        ChartWindow.ONE_MINUTE -> "1m"
        ChartWindow.FIVE_MINUTES -> "5m"
        ChartWindow.FIFTEEN_MINUTES -> "15m"
        ChartWindow.FULL_SESSION -> if (lang == AppLanguage.VI) "Tất cả" else "All"
    }

    fun errorLabel(flag: JkErrorFlag): String =
        flag.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
