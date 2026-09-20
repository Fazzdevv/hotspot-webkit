package com.fazzdev.offlineedgeportal.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fazzdev.offlineedgeportal.core.InterfaceInfo
import com.fazzdev.offlineedgeportal.core.NetworkUtils
import com.fazzdev.offlineedgeportal.core.RequestLog
import com.fazzdev.offlineedgeportal.core.SitePackageInfo
import com.fazzdev.offlineedgeportal.core.ZipPackageManager
import com.fazzdev.offlineedgeportal.service.EdgeServerService
import com.fazzdev.offlineedgeportal.service.ServerRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val serverState = EdgeServerService.serverState

    private val _availableInterfaces = MutableStateFlow<List<InterfaceInfo>>(emptyList())
    val availableInterfaces = _availableInterfaces.asStateFlow()

    private val _currentSiteInfo = MutableStateFlow<SitePackageInfo?>(null)
    val currentSiteInfo = _currentSiteInfo.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage = _importStatusMessage.asStateFlow()

    private val _trafficLogs = MutableStateFlow<List<RequestLog>>(emptyList())
    val trafficLogs = _trafficLogs.asStateFlow()

    init {
        refreshNetworkInterfaces()
        observeLogs()
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

    fun toggleServer(context: Context) {
        val currentState = serverState.value
        if (currentState.isRunning) {
            EdgeServerService.stopService(context)
        } else {
            EdgeServerService.startService(context)
        }
    }

    fun importZip(context: Context, uri: Uri, fileName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importStatusMessage.value = "Mengekstrak file ZIP..."

            val result = ZipPackageManager.importZipFromUri(context, uri, fileName)
            if (result.isSuccess) {
                val info = result.getOrNull()
                _currentSiteInfo.value = info
                _importStatusMessage.value = "Berhasil: ${info?.totalFiles} file dimuat ke server."
            } else {
                _importStatusMessage.value = "Gagal: ${result.exceptionOrNull()?.message}"
            }
            _isImporting.value = false
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
}
