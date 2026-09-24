package com.fazzdev.offlineedgeportal.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fazzdev.offlineedgeportal.core.InterfaceInfo
import com.fazzdev.offlineedgeportal.core.NetworkUtils
import com.fazzdev.offlineedgeportal.core.RequestLog
import com.fazzdev.offlineedgeportal.core.RipProgress
import com.fazzdev.offlineedgeportal.core.SitePackageInfo
import com.fazzdev.offlineedgeportal.core.WebRipperEngine
import com.fazzdev.offlineedgeportal.core.ZipPackageManager
import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import com.fazzdev.offlineedgeportal.pkg.model.PkgLogEntry
import com.fazzdev.offlineedgeportal.pkg.model.PkgLogLevel
import com.fazzdev.offlineedgeportal.pkg.network.GoldHenFtpService
import com.fazzdev.offlineedgeportal.pkg.network.GoldHenPayloadService
import com.fazzdev.offlineedgeportal.pkg.server.PkgRegistry
import com.fazzdev.offlineedgeportal.pkg.util.PkgHeaderParser
import com.fazzdev.offlineedgeportal.pkg.util.PkgIdentifier
import com.fazzdev.offlineedgeportal.pkg.util.PkgPreferences
import com.fazzdev.offlineedgeportal.service.EdgeServerService
import com.fazzdev.offlineedgeportal.ui.util.AppLanguage
import com.fazzdev.offlineedgeportal.ui.util.AppStrings
import com.fazzdev.offlineedgeportal.ui.util.LanguagePreferences
import com.fazzdev.offlineedgeportal.core.ServerActiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class MainDashboardTab {
    WEBKIT,
    PKG_SENDER
}

class MainViewModel : ViewModel() {

    val serverState = EdgeServerService.serverState

    // Tab Navigation State
    private val _currentTab = MutableStateFlow(MainDashboardTab.WEBKIT)
    val currentTab = _currentTab.asStateFlow()

    fun selectTab(tab: MainDashboardTab) {
        _currentTab.value = tab
    }

    // Language State
    private val _currentLanguage = MutableStateFlow(AppLanguage.ID)
    val currentLanguage = _currentLanguage.asStateFlow()

    private val _availableInterfaces = MutableStateFlow<List<InterfaceInfo>>(emptyList())
    val availableInterfaces = _availableInterfaces.asStateFlow()

    private val _currentSiteInfo = MutableStateFlow<SitePackageInfo?>(null)
    val currentSiteInfo = _currentSiteInfo.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage = _importStatusMessage.asStateFlow()

    // Web Ripper State
    private val _ripProgress = MutableStateFlow(RipProgress())
    val ripProgress = _ripProgress.asStateFlow()

    // PS4 PKG Installer State
    private val _selectedPkgFiles = MutableStateFlow<List<PkgFile>>(emptyList())
    val selectedPkgFiles = _selectedPkgFiles.asStateFlow()

    private val _ps4TargetIp = MutableStateFlow("192.168.43.100")
    val ps4TargetIp = _ps4TargetIp.asStateFlow()

    private val _isSendingPkg = MutableStateFlow(false)
    val isSendingPkg = _isSendingPkg.asStateFlow()

    private val _pkgTransferStatus = MutableStateFlow<String?>(null)
    val pkgTransferStatus = _pkgTransferStatus.asStateFlow()

    private val _transferredBytes = MutableStateFlow(0L)
    val transferredBytes = _transferredBytes.asStateFlow()

    private val _transferSpeedBytes = MutableStateFlow(0L)
    val transferSpeedBytes = _transferSpeedBytes.asStateFlow()

    private val _trafficLogs = MutableStateFlow<List<RequestLog>>(emptyList())
    val trafficLogs = _trafficLogs.asStateFlow()

    // PKG Sender Logs State
    private val _pkgLogs = MutableStateFlow<List<PkgLogEntry>>(emptyList())
    val pkgLogs = _pkgLogs.asStateFlow()

    fun addPkgLog(message: String, level: PkgLogLevel = PkgLogLevel.INFO, details: String? = null) {
        val entry = PkgLogEntry(
            message = message,
            level = level,
            details = details
        )
        val current = _pkgLogs.value.toMutableList()
        current.add(entry)
        if (current.size > 200) {
            current.removeAt(0)
        }
        _pkgLogs.value = current
    }

    fun clearPkgLogs() {
        _pkgLogs.value = emptyList()
    }

    fun copyPkgLogsToClipboard(context: Context, strings: AppStrings.Strings) {
        val text = _pkgLogs.value.joinToString("\n") { entry ->
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
            "[$time] [${entry.level.name}] ${entry.message}" + if (!entry.details.isNullOrBlank()) "\nDetails: ${entry.details}" else ""
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("PKG Sender Logs", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, strings.toastCopiedPkgLogs, android.widget.Toast.LENGTH_SHORT).show()
    }

    init {
        refreshNetworkInterfaces()
        observeLogs()
        startTransferMonitor()
        GoldHenPayloadService.onLogEntry = { entry ->
            addPkgLog(entry.message, entry.level, entry.details)
        }
    }

    private var appContext: Context? = null

    fun loadInitialSettings(context: Context) {
        appContext = context.applicationContext
        _currentLanguage.value = LanguagePreferences.getLanguage(context)
        _ps4TargetIp.value = PkgPreferences.getTargetIp(context)
        loadInitialSiteInfo(context)
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        _currentLanguage.value = lang
        LanguagePreferences.setLanguage(context, lang)
    }

    fun setPs4TargetIp(ip: String) {
        _ps4TargetIp.value = ip
        appContext?.let { ctx ->
            PkgPreferences.setTargetIp(ctx, ip)
        }
    }

    fun loadInitialSiteInfo(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val siteDir = ZipPackageManager.getSiteDirectory(context)
            val info = ZipPackageManager.inspectPackage(siteDir)
            _currentSiteInfo.value = info
        }
    }

    fun refreshNetworkInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = NetworkUtils.getAllAvailableInterfaces()
            _availableInterfaces.value = list
        }
    }

    fun toggleWebKitServer(context: Context) {
        val currentState = serverState.value
        if (currentState.isRunning && currentState.activeMode == ServerActiveMode.WEBKIT) {
            EdgeServerService.stopService(context)
        } else {
            EdgeServerService.startWebKitService(context)
        }
    }

    fun togglePkgServer(context: Context) {
        val currentState = serverState.value
        if (currentState.isRunning && currentState.activeMode == ServerActiveMode.PKG_SENDER) {
            EdgeServerService.stopService(context)
        } else {
            EdgeServerService.startPkgService(context)
        }
    }

    fun toggleServer(context: Context) {
        toggleWebKitServer(context)
    }

    fun importZip(context: Context, uri: Uri, fileName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importStatusMessage.value = if (_currentLanguage.value == AppLanguage.ID) "Mengekstrak file ZIP..." else "Extracting ZIP package..."

            val result = ZipPackageManager.importZipFromUri(context, uri, fileName)
            if (result.isSuccess) {
                val info = result.getOrNull()
                _currentSiteInfo.value = info
                _importStatusMessage.value = if (_currentLanguage.value == AppLanguage.ID) {
                    "Berhasil: ${info?.totalFiles} file dimuat ke server."
                } else {
                    "Success: ${info?.totalFiles} files loaded into server."
                }
            } else {
                _importStatusMessage.value = "Error: ${result.exceptionOrNull()?.message}"
            }
            _isImporting.value = false
        }
    }

    fun ripWebsite(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = WebRipperEngine.ripWebsite(context, url) { progress ->
                _ripProgress.value = progress
            }
            if (result.isSuccess) {
                _currentSiteInfo.value = result.getOrNull()
            }
        }
    }

    fun addPkgFile(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val size = queryFileSize(context, uri)
            val header = PkgHeaderParser.extractFromUri(context, uri, fileName)
            val titleId = header?.titleId ?: PkgHeaderParser.extractTitleIdFromFilename(fileName)

            val deterministicId = PkgIdentifier.generateDeterministicId(
                name = fileName,
                sizeBytes = size,
                contentId = header?.contentId,
                titleId = titleId,
                packageDigest = header?.digest
            )

            val pkg = PkgFile(
                id = deterministicId,
                uri = uri,
                name = fileName,
                sizeBytes = size,
                formattedSize = formatBytes(size),
                titleId = titleId,
                contentId = header?.contentId,
                packageDigest = header?.digest,
                contentType = header?.contentType ?: "PS4GD"
            )

            PkgRegistry.registerFile(pkg)

            val current = _selectedPkgFiles.value.toMutableList()
            current.removeAll { it.id == pkg.id }
            current.add(pkg)
            _selectedPkgFiles.value = current

            _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                "Berkas PKG ditambahkan: ${pkg.name} (${pkg.formattedSize})"
            } else {
                "PKG added: ${pkg.name} (${pkg.formattedSize})"
            }
        }
    }

    fun removePkgFile(id: String) {
        PkgRegistry.removeFile(id)
        _selectedPkgFiles.value = _selectedPkgFiles.value.filterNot { it.id == id }
    }

    fun togglePkgContentType(id: String) {
        val currentList = _selectedPkgFiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val file = currentList[index]
            val nextType = when (file.contentType) {
                "PS4GD" -> "PS4GP"
                "PS4GP" -> "PS4AC"
                else -> "PS4GD"
            }
            val updated = file.copy(contentType = nextType)
            currentList[index] = updated
            PkgRegistry.registerFile(updated)
            _selectedPkgFiles.value = currentList
            addPkgLog("Tipe paket diubah: ${file.name} -> $nextType", PkgLogLevel.INFO)
        }
    }

    fun clearPkgFiles() {
        PkgRegistry.clearFiles()
        _selectedPkgFiles.value = emptyList()
        _pkgTransferStatus.value = null
    }

    fun hostPkgOnServerOnly(context: Context) {
        val files = _selectedPkgFiles.value
        if (files.isEmpty()) return

        val current = serverState.value
        if (!current.isRunning || current.activeMode != ServerActiveMode.PKG_SENDER) {
            EdgeServerService.startPkgService(context)
        }

        PkgRegistry.resetSession()
        _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
            "✓ Berkas aktif di server port ${serverState.value.port}. Lanjutkan unduhan di Notifikasi PS4."
        } else {
            "✓ Active on server port ${serverState.value.port}. Resume download from PS4 Notifications."
        }
    }

    fun sendPkgToPs4(context: Context) {
        val files = _selectedPkgFiles.value
        if (files.isEmpty() || _isSendingPkg.value) return

        val current = serverState.value
        if (!current.isRunning || current.activeMode != ServerActiveMode.PKG_SENDER) {
            EdgeServerService.startPkgService(context)
        }

        val targetIp = _ps4TargetIp.value.trim()
        val detectedHotspot = NetworkUtils.getActiveHotspotInfo()
        val rawLocalIp = when {
            detectedHotspot != null && detectedHotspot.ipAddress != "127.0.0.1" -> detectedHotspot.ipAddress
            current.nativeIp != "127.0.0.1" -> current.nativeIp
            else -> "192.168.43.1"
        }
        val cleanLocalIp = rawLocalIp.substringBefore(":")
        val port = if (current.port > 0) current.port else 8080

        val manifestUrls = files.map { file ->
            "http://$cleanLocalIp:$port/json/${file.id}.json"
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSendingPkg.value = true
            PkgRegistry.resetSession()

            addPkgLog("=====================================", PkgLogLevel.INFO)
            addPkgLog("Sesi Baru: Mengirim ${files.size} paket ke PS4 $targetIp", PkgLogLevel.INFO)
            addPkgLog("IP Server HP: $cleanLocalIp:$port", PkgLogLevel.INFO)

            // 1. Jika ada berkas kecil (< 1MB, seperti DLC unlocker), Sony BGFT port 9090 akan menolaknya (0x80990006).
            // Solusi: Kirim langsung via GoldHEN FTP port 2121 ke /data/pkg/ pada HDD internal PS4!
            val hasSmallFile = files.any { it.sizeBytes in 1 until 1_048_576L }
            if (hasSmallFile) {
                val smallPkg = files.first { it.sizeBytes in 1 until 1_048_576L }
                addPkgLog("Berkas '${smallPkg.name}' berukuran < 1MB (${smallPkg.formattedSize}). Sistem BGFT 9090 menolak unduhan < 1MB.", PkgLogLevel.INFO)
                addPkgLog("Mengecek port FTP GoldHEN (2121/1337) di PS4...", PkgLogLevel.INFO)
                val ftpPort = GoldHenFtpService.detectFtpPort(targetIp)
                if (ftpPort != null) {
                    addPkgLog("Mengunggah via GoldHEN FTP (Port $ftpPort) langsung ke /data/pkg/...", PkgLogLevel.INFO)
                    GoldHenFtpService.onLogMessage = { msg -> _pkgTransferStatus.value = msg }
                    GoldHenFtpService.onLogEntry = { msg, lvl, det -> addPkgLog(msg, lvl, det) }
                    var allOk = true
                    for (file in files) {
                        val res = GoldHenFtpService.uploadPkgToDataFolder(context, targetIp, ftpPort, file)
                        if (res.isFailure) {
                            allOk = false
                            break
                        }
                    }
                    _isSendingPkg.value = false
                    if (allOk) {
                        _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                            "✓ Berhasil diunggah ke /data/pkg/ PS4! Buka Settings -> GoldHEN -> Package Installer di PS4."
                        } else {
                            "✓ Uploaded to /data/pkg/ on PS4! Open Settings -> GoldHEN -> Package Installer on PS4."
                        }
                        return@launch
                    }
                } else {
                    addPkgLog("Port FTP GoldHEN (2121) tidak terbuka di PS4. Melanjutkan upaya via payload port 9090...", PkgLogLevel.WARN)
                }
            }

            // 3. Jalankan GoldHEN BinLoader (Port 9090)
            _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                "Menginjeksi payload 16KB ke GoldHEN 9090 di $targetIp..."
            } else {
                "Injecting 16KB payload to GoldHEN 9090 at $targetIp..."
            }

            GoldHenPayloadService.onLogMessage = { msg ->
                _pkgTransferStatus.value = msg
            }

            GoldHenPayloadService.onLogEntry = { entry ->
                addPkgLog(entry.message, entry.level, entry.details)
            }

            val result = GoldHenPayloadService.sendPkgPayload(
                context = context,
                ps4Ip = targetIp,
                localIp = cleanLocalIp,
                files = files,
                packageUrls = manifestUrls
            )

            _isSendingPkg.value = false
            if (result.isSuccess) {
                _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                    "✓ Berhasil dikirim ke PS4! Unduhan aktif di menu Notifikasi."
                } else {
                    "✓ Sent to PS4 successfully! Download active in Notifications."
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Gagal mengirim ke PS4"
                _pkgTransferStatus.value = "Error: $err"
            }
        }
    }

    fun uploadPkgViaFtp(context: Context) {
        val files = _selectedPkgFiles.value
        if (files.isEmpty() || _isSendingPkg.value) return

        val targetIp = _ps4TargetIp.value.trim()
        viewModelScope.launch(Dispatchers.IO) {
            _isSendingPkg.value = true
            addPkgLog("=====================================", PkgLogLevel.INFO)
            addPkgLog("Mengecek koneksi FTP ke PS4 di $targetIp...", PkgLogLevel.INFO)
            val ftpPort = GoldHenFtpService.detectFtpPort(targetIp)
            if (ftpPort == null) {
                _isSendingPkg.value = false
                val msg = "Port FTP GoldHEN (2121/1337) tidak terdeteksi di $targetIp. Pastikan FTP Server aktif di menu GoldHEN PS4."
                addPkgLog(msg, PkgLogLevel.ERROR)
                _pkgTransferStatus.value = "Error: $msg"
                return@launch
            }

            GoldHenFtpService.onLogMessage = { msg -> _pkgTransferStatus.value = msg }
            GoldHenFtpService.onLogEntry = { msg, lvl, det -> addPkgLog(msg, lvl, det) }

            var allOk = true
            for (file in files) {
                val res = GoldHenFtpService.uploadPkgToDataFolder(context, targetIp, ftpPort, file)
                if (res.isFailure) {
                    allOk = false
                    break
                }
            }

            _isSendingPkg.value = false
            if (allOk) {
                _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                    "✓ Berhasil diunggah ke /data/pkg/ PS4! Buka Settings -> GoldHEN -> Package Installer di PS4."
                } else {
                    "✓ Uploaded to /data/pkg/ on PS4! Open Settings -> GoldHEN -> Package Installer on PS4."
                }
            }
        }
    }

    private fun startTransferMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            var lastServed = 0L
            while (isActive) {
                delay(1000)
                val files = _selectedPkgFiles.value
                if (files.isNotEmpty()) {
                    var total = 0L
                    for (file in files) {
                        total += PkgRegistry.getTransferredBytesForFile(file.id)
                    }
                    _transferredBytes.value = total
                    val speed = if (total >= lastServed) total - lastServed else 0L
                    _transferSpeedBytes.value = speed
                    lastServed = total
                } else {
                    lastServed = 0L
                    _transferredBytes.value = 0L
                    _transferSpeedBytes.value = 0L
                }
            }
        }
    }

    fun clearLogs() {
        _trafficLogs.value = emptyList()
    }

    private fun observeLogs() {
        viewModelScope.launch {
            EdgeServerService.logEvents.collect { log ->
                val current = _trafficLogs.value.toMutableList()
                current.add(0, log)
                if (current.size > 100) {
                    current.removeAt(current.lastIndex)
                }
                _trafficLogs.value = current

                // If this is a PKG download or manifest request, also log to PKG Console
                if (log.uri.startsWith("/json/") || log.uri.startsWith("/pkg/")) {
                    val lvl = if (log.statusCode in 200..299) PkgLogLevel.SUCCESS else PkgLogLevel.WARN
                    addPkgLog("HTTP ${log.statusCode}: ${log.method} ${log.uri} (${log.clientIp})", lvl)
                }
            }
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        // Metoda 1: OpenableColumns.SIZE via ContentResolver query
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0L) return size
                }
            }
        } catch (_: Exception) {}

        // Metoda 2: statSize dari openFileDescriptor
        try {
            val statSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            if (statSize > 0L) return statSize
        } catch (_: Exception) {}

        // Metoda 3: Channel size dari FileInputStream
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (stream is java.io.FileInputStream) {
                    val chSize = stream.channel.size()
                    if (chSize > 0L) return chSize
                }
            }
        } catch (_: Exception) {}

        return 0L
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
