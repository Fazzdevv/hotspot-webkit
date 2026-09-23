package com.fazzdev.offlineedgeportal.ui.util

import android.content.Context
import android.content.SharedPreferences

enum class AppLanguage {
    ID,
    EN
}

object LanguagePreferences {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANG = "selected_language"

    fun getLanguage(context: Context): AppLanguage {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANG, null)
        return if (saved == "EN") AppLanguage.EN else AppLanguage.ID
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang.name).apply()
    }
}

object AppStrings {

    fun get(lang: AppLanguage = AppLanguage.ID): Strings = when (lang) {
        AppLanguage.ID -> IndonesianStrings
        AppLanguage.EN -> EnglishStrings
    }

    interface Strings {
        // App & Header
        val appTitle: String
        val appSubtitle: String
        val appVersion: String

        // Main Tabs
        val tabWebKit: String
        val tabPkg: String

        // Server Control (General & Mode Specific)
        val serverRunningTitle: String
        val serverRunningDesc: String
        val serverStoppedTitle: String
        val serverStoppedDesc: String
        val webKitRunningTitle: String
        val webKitRunningDesc: String
        val pkgRunningTitle: String
        val pkgRunningDesc: String
        val serverActiveOtherMode: String
        val totalRequests: String
        val proxyRedirects: String
        val btnStartServer: String
        val btnStopServer: String
        val btnStartWebKit: String
        val btnStopWebKit: String
        val btnStartPkg: String
        val btnStopPkg: String

        // Hardware Network
        val hardwareNetworkTitle: String
        val nativeIpLabel: String
        val interfaceLabel: String
        val portLabel: String
        val btnCopyIp: String
        val btnCopyUrl: String
        val toastCopied: String

        // Quick QR
        val qrTitle: String
        val qrDesc: String
        val btnOpenBrowser: String

        // Source Card Tabs
        val tabZip: String
        val tabWebRipper: String

        // ZIP Package Manager
        val zipTitle: String
        val noZipImported: String
        val totalFiles: String
        val fileSize: String
        val indexDetected: String
        val indexNotFound: String
        val btnImportZip: String
        val btnImportingZip: String
        val btnPreview: String

        // Web Ripper / Auto-Cache
        val webRipperTitle: String
        val webRipperDesc: String
        val urlInputPlaceholder: String
        val btnPaste: String
        val btnClear: String
        val btnCloneWeb: String
        val btnCloningWeb: String
        val quickPresetsLabel: String

        // PS4 PKG Installer
        val pkgCardTitle: String
        val pkgCardDesc: String
        val btnPickPkg: String
        val noPkgSelected: String
        val targetPs4IpLabel: String
        val targetPortLabel: String
        val btnSendToPs4: String
        val btnHostOnly: String
        val statusInjectingPayload: String
        val statusWaitingPs4: String
        val statusSendingMetadata: String
        val statusSentSuccess: String
        val statusHostingReady: String
        val errPkgAlreadyExists: String
        val titleIdLabel: String
        val contentIdLabel: String
        val transferredLabel: String
        val speedLabel: String

        // Client Guides
        val clientGuideTitle: String
        val clientGuideSubtitle: String
        val guideProxyHost: String
        val guideProxyPort: String
        val guideProxyBypass: String
        val guideProxyBypassVal: String
        val ps4GuideTitle: String
        val ps4GuideSteps: String
        val ps5GuideTitle: String
        val ps5GuideSteps: String

        // Traffic Radar
        val radarTitle: String
        val radarEmpty: String
        val btnClearRadar: String
    }

    private object IndonesianStrings : Strings {
        override val appTitle = "Hotspot WebKit PS4-PS5"
        override val appSubtitle = "Offline Exploit Host & Edge Server (Non-Root)"
        override val appVersion = "v1.1"

        override val tabWebKit = "WebKit Exploit"
        override val tabPkg = "PS4 PKG Sender"

        override val serverRunningTitle = "Server Sedang Berjalan"
        override val serverRunningDesc = "Melayani web lokal, proxy wildcard & streaming PKG"
        override val serverStoppedTitle = "Server Berhenti"
        override val serverStoppedDesc = "Tekan tombol untuk mengaktifkan edge server"
        override val webKitRunningTitle = "Server WebKit Aktif"
        override val webKitRunningDesc = "Melayani exploit lokal & proxy wildcard di port 8080"
        override val pkgRunningTitle = "Server PKG Aktif"
        override val pkgRunningDesc = "Melayani PlayGo manifest & streaming PKG di port 8080"
        override val serverActiveOtherMode = "Server sedang aktif dalam mode lain"
        override val totalRequests = "Total Permintaan"
        override val proxyRedirects = "Proxy Intercept"
        override val btnStartServer = "Nyalakan Server & Proxy (Port 8080)"
        override val btnStopServer = "Matikan Server & Proxy"
        override val btnStartWebKit = "Nyalakan Server WebKit"
        override val btnStopWebKit = "Matikan Server WebKit"
        override val btnStartPkg = "Nyalakan Server PKG"
        override val btnStopPkg = "Matikan Server PKG"

        override val hardwareNetworkTitle = "Hardware Network Interface"
        override val nativeIpLabel = "NATIVE IP ADDRESS"
        override val interfaceLabel = "Interface"
        override val portLabel = "Port"
        override val btnCopyIp = "Salin IP"
        override val btnCopyUrl = "Salin URL Portal"
        override val toastCopied = "disalin ke clipboard"

        override val qrTitle = "Scan QR Akses Cepat"
        override val qrDesc = "Klien terhubung bisa scan untuk langsung membuka portal lokal"
        override val btnOpenBrowser = "Buka Portal di HP Ini"

        override val tabZip = "File .ZIP"
        override val tabWebRipper = "Web Ripper (URL)"

        override val zipTitle = "Website Source (.ZIP Package)"
        override val noZipImported = "Belum ada file ZIP diimpor"
        override val totalFiles = "Total File"
        override val fileSize = "Ukuran"
        override val indexDetected = "✓ index.html terdeteksi (Siap di-host)"
        override val indexNotFound = "⚠ index.html tidak ditemukan di root"
        override val btnImportZip = "Import .ZIP Baru"
        override val btnImportingZip = "Mengimpor..."
        override val btnPreview = "Preview Web"

        override val webRipperTitle = "Dynamic Web Ripper / Auto-Cache"
        override val webRipperDesc = "Kloning website exploit online langsung ke HP tanpa perlu file .ZIP"
        override val urlInputPlaceholder = "Masukkan URL web (misal: https://ps4.karo218.ir/)"
        override val btnPaste = "Tempel"
        override val btnClear = "Hapus"
        override val btnCloneWeb = "Clone & Host Web"
        override val btnCloningWeb = "Mengkloning..."
        override val quickPresetsLabel = "Preset Cepat:"

        override val pkgCardTitle = "PS4 PKG Installer & Sender"
        override val pkgCardDesc = "Kirim berkas .pkg ke PS4 via GoldHEN (Port 9090) atau sajikan di server"
        override val btnPickPkg = "Pilih Berkas .PKG"
        override val noPkgSelected = "Belum ada berkas PKG dipilih dari penyimpanan"
        override val targetPs4IpLabel = "Alamat IP Konsol PS4"
        override val targetPortLabel = "Port GoldHEN"
        override val btnSendToPs4 = "Kirim ke PS4"
        override val btnHostOnly = "Taruh di Server"
        override val statusInjectingPayload = "Menginjeksi payload 16KB ke GoldHEN Port 9090..."
        override val statusWaitingPs4 = "Menunggu koneksi balik dari PS4..."
        override val statusSendingMetadata = "Mengirim metadata paket PlayGo..."
        override val statusSentSuccess = "✓ Berhasil dikirim ke PS4! Unduhan aktif di menu Notifikasi."
        override val statusHostingReady = "✓ Berkas PKG siap di-host di server port 8080."
        override val errPkgAlreadyExists = "Catatan: Jika error 0x80990086 muncul di PS4, artinya paket sudah ada di antrean. Tekan 'Taruh di Server' lalu lanjutkan unduhan di Notifikasi PS4."
        override val titleIdLabel = "Title ID"
        override val contentIdLabel = "Content ID"
        override val transferredLabel = "Terkirim"
        override val speedLabel = "Kecepatan"

        override val clientGuideTitle = "Panduan Setting Konsol PS4 & PS5"
        override val clientGuideSubtitle = "Hubungkan konsol ke Hotspot Wi-Fi HP ini, lalu konfigurasi Proxy:"
        override val guideProxyHost = "Proxy Hostname / Address"
        override val guideProxyPort = "Proxy Port"
        override val guideProxyBypass = "Bypass Proxy"
        override val guideProxyBypassVal = "(Kosongkan)"
        override val ps4GuideTitle = "🎮 Langkah di PlayStation 4 (PS4):"
        override val ps4GuideSteps = "1. Buka Pengaturan > Jaringan > Siapkan Koneksi Internet.\n" +
                "2. Pilih Gunakan Wi-Fi > pilih mode Khusus (Custom).\n" +
                "3. Pada Server Proksi: pilih Gunakan (Use), masukkan Host & Port di atas.\n" +
                "4. Opsi lainnya (IP, DNS, MTU): biarkan Otomatis.\n" +
                "5. Buka Pengaturan > Panduan Pengguna (User's Guide). Website lokal atau exploit langsung terbuka 100%!"
        override val ps5GuideTitle = "🎮 Langkah di PlayStation 5 (PS5):"
        override val ps5GuideSteps = "1. Buka Pengaturan > Jaringan > Pengaturan > Siapkan Koneksi Internet.\n" +
                "2. Tekan tombol Opsi pada Wi-Fi Hotspot ini > Pengaturan Lanjutan.\n" +
                "3. Pada Server Proksi: pilih Gunakan (Use), masukkan Host & Port di atas.\n" +
                "4. Opsi lainnya: biarkan Otomatis.\n" +
                "5. Buka Pengaturan > Panduan & Kiat... > Panduan Pengguna untuk memuat web lokal!"

        override val radarTitle = "Live Traffic Radar"
        override val radarEmpty = "Belum ada trafik dari konsol..."
        override val btnClearRadar = "Bersihkan Log"
    }

    private object EnglishStrings : Strings {
        override val appTitle = "Hotspot WebKit PS4-PS5"
        override val appSubtitle = "Offline Exploit Host & Edge Server (Non-Root)"
        override val appVersion = "v1.1"

        override val tabWebKit = "WebKit Exploit"
        override val tabPkg = "PS4 PKG Sender"

        override val serverRunningTitle = "Server is Running"
        override val serverRunningDesc = "Serving local web, wildcard proxy & PKG streaming"
        override val serverStoppedTitle = "Server is Stopped"
        override val serverStoppedDesc = "Tap button to activate edge server"
        override val webKitRunningTitle = "WebKit Server Active"
        override val webKitRunningDesc = "Serving local exploit & wildcard proxy on port 8080"
        override val pkgRunningTitle = "PKG Server Active"
        override val pkgRunningDesc = "Serving PlayGo manifests & PKG streaming on port 8080"
        override val serverActiveOtherMode = "Server is active in another mode"
        override val totalRequests = "Total Requests"
        override val proxyRedirects = "Proxy Intercepts"
        override val btnStartServer = "Start Server & Proxy (Port 8080)"
        override val btnStopServer = "Stop Server & Proxy"
        override val btnStartWebKit = "Start WebKit Server"
        override val btnStopWebKit = "Stop WebKit Server"
        override val btnStartPkg = "Start PKG Server"
        override val btnStopPkg = "Stop PKG Server"

        override val hardwareNetworkTitle = "Hardware Network Interface"
        override val nativeIpLabel = "NATIVE IP ADDRESS"
        override val interfaceLabel = "Interface"
        override val portLabel = "Port"
        override val btnCopyIp = "Copy IP"
        override val btnCopyUrl = "Copy Portal URL"
        override val toastCopied = "copied to clipboard"

        override val qrTitle = "Quick Access QR Code"
        override val qrDesc = "Connected clients can scan to open the local portal instantly"
        override val btnOpenBrowser = "Open Portal on this Device"

        override val tabZip = ".ZIP Package"
        override val tabWebRipper = "Web Ripper (URL)"

        override val zipTitle = "Website Source (.ZIP Package)"
        override val noZipImported = "No ZIP file imported yet"
        override val totalFiles = "Total Files"
        override val fileSize = "Size"
        override val indexDetected = "✓ index.html detected (Ready to host)"
        override val indexNotFound = "⚠ index.html not found in root"
        override val btnImportZip = "Import New .ZIP"
        override val btnImportingZip = "Importing..."
        override val btnPreview = "Preview Web"

        override val webRipperTitle = "Dynamic Web Ripper / Auto-Cache"
        override val webRipperDesc = "Clone online exploit websites directly to your phone without manual .ZIP"
        override val urlInputPlaceholder = "Enter website URL (e.g., https://ps4.karo218.ir/)"
        override val btnPaste = "Paste"
        override val btnClear = "Clear"
        override val btnCloneWeb = "Clone & Host Web"
        override val btnCloningWeb = "Cloning..."
        override val quickPresetsLabel = "Quick Presets:"

        override val pkgCardTitle = "PS4 PKG Installer & Sender"
        override val pkgCardDesc = "Send .pkg files to PS4 via GoldHEN (Port 9090) or host on server"
        override val btnPickPkg = "Select .PKG Files"
        override val noPkgSelected = "No PKG files selected from storage yet"
        override val targetPs4IpLabel = "PS4 Console IP Address"
        override val targetPortLabel = "GoldHEN Port"
        override val btnSendToPs4 = "Send to PS4"
        override val btnHostOnly = "Host on Server"
        override val statusInjectingPayload = "Injecting 16KB payload into GoldHEN Port 9090..."
        override val statusWaitingPs4 = "Waiting for callback connection from PS4..."
        override val statusSendingMetadata = "Sending PlayGo package metadata..."
        override val statusSentSuccess = "✓ Sent to PS4 successfully! Download active in Notifications."
        override val statusHostingReady = "✓ PKG file ready and hosted on port 8080."
        override val errPkgAlreadyExists = "Note: If error 0x80990086 appears on PS4, the package is already queued. Tap 'Host on Server' and resume download in PS4 Notifications."
        override val titleIdLabel = "Title ID"
        override val contentIdLabel = "Content ID"
        override val transferredLabel = "Transferred"
        override val speedLabel = "Speed"

        override val clientGuideTitle = "PS4 & PS5 Setup Guide"
        override val clientGuideSubtitle = "Connect console to this phone's Wi-Fi hotspot, then configure Proxy:"
        override val guideProxyHost = "Proxy Hostname / Address"
        override val guideProxyPort = "Proxy Port"
        override val guideProxyBypass = "Bypass Proxy"
        override val guideProxyBypassVal = "(Leave Empty)"
        override val ps4GuideTitle = "🎮 Steps on PlayStation 4 (PS4):"
        override val ps4GuideSteps = "1. Go to Settings > Network > Set Up Internet Connection.\n" +
                "2. Select Use Wi-Fi > choose Custom mode.\n" +
                "3. Under Proxy Server: choose Use, enter Host & Port above.\n" +
                "4. Other options (IP, DNS, MTU): leave as Automatic.\n" +
                "5. Open Settings > User's Guide. Your local website or exploit loads instantly!"
        override val ps5GuideTitle = "🎮 Steps on PlayStation 5 (PS5):"
        override val ps5GuideSteps = "1. Go to Settings > Network > Settings > Set Up Internet Connection.\n" +
                "2. Press Options button on this Hotspot > Advanced Settings.\n" +
                "3. Under Proxy Server: choose Use, enter Host & Port above.\n" +
                "4. Other options: leave as Automatic.\n" +
                "5. Open Settings > Guide & Tips... > User's Guide to load local exploit!"

        override val radarTitle = "Live Traffic Radar"
        override val radarEmpty = "No traffic recorded from console yet..."
        override val btnClearRadar = "Clear Logs"
    }
}
