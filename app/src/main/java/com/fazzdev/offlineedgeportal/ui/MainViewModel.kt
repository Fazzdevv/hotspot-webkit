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
import com.fazzdev.offlineedgeportal.pkg.network.GoldHenPayloadService
import com.fazzdev.offlineedgeportal.pkg.server.PkgRegistry
import com.fazzdev.offlineedgeportal.pkg.util.PkgHeaderParser
import com.fazzdev.offlineedgeportal.pkg.util.PkgIdentifier
import com.fazzdev.offlineedgeportal.service.EdgeServerService
import com.fazzdev.offlineedgeportal.ui.util.AppLanguage
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

    init {
        refreshNetworkInterfaces()
        observeLogs()
        startTransferMonitor()
    }

    fun loadInitialSettings(context: Context) {
        _currentLanguage.value = LanguagePreferences.getLanguage(context)
        loadInitialSiteInfo(context)
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        _currentLanguage.value = lang
        LanguagePreferences.setLanguage(context, lang)
    }

    fun setPs4TargetIp(ip: String) {
        _ps4TargetIp.value = ip
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
        val localIp = serverState.value.nativeIp
        val port = serverState.value.port

        val manifestUrls = files.map { file ->
            "http://$localIp:$port/json/${file.id}.json"
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSendingPkg.value = true
            PkgRegistry.resetSession()

            _pkgTransferStatus.value = if (_currentLanguage.value == AppLanguage.ID) {
                "Menginjeksi payload 16KB ke GoldHEN 9090 di $targetIp..."
            } else {
                "Injecting 16KB payload to GoldHEN 9090 at $targetIp..."
            }

            GoldHenPayloadService.onLogMessage = { msg ->
                _pkgTransferStatus.value = msg
            }

            val result = GoldHenPayloadService.sendPkgPayload(
                context = context,
                ps4Ip = targetIp,
                localIp = localIp,
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
            }
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
