package com.fazzdev.offlineedgeportal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fazzdev.offlineedgeportal.core.RequestLog
import com.fazzdev.offlineedgeportal.core.RipProgress
import com.fazzdev.offlineedgeportal.core.ServerActiveMode
import com.fazzdev.offlineedgeportal.core.SitePackageInfo
import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import com.fazzdev.offlineedgeportal.pkg.model.PkgLogEntry
import com.fazzdev.offlineedgeportal.pkg.model.PkgLogLevel
import com.fazzdev.offlineedgeportal.ui.MainDashboardTab
import com.fazzdev.offlineedgeportal.ui.MainViewModel
import com.fazzdev.offlineedgeportal.ui.theme.AmberWarning
import com.fazzdev.offlineedgeportal.ui.theme.CrimsonAlert
import com.fazzdev.offlineedgeportal.ui.theme.CyberCyan
import com.fazzdev.offlineedgeportal.ui.theme.DarkNavyBg
import com.fazzdev.offlineedgeportal.ui.theme.DarkSlateElevated
import com.fazzdev.offlineedgeportal.ui.theme.DarkSlateSurface
import com.fazzdev.offlineedgeportal.ui.theme.ElectricBlue
import com.fazzdev.offlineedgeportal.ui.theme.NeonGreen
import com.fazzdev.offlineedgeportal.ui.theme.TextMuted
import com.fazzdev.offlineedgeportal.ui.theme.TextPrimary
import com.fazzdev.offlineedgeportal.ui.theme.TextSecondary
import com.fazzdev.offlineedgeportal.ui.util.AppLanguage
import com.fazzdev.offlineedgeportal.ui.util.AppStrings
import com.fazzdev.offlineedgeportal.ui.util.QRCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onPickZipClick: () -> Unit,
    onPickPkgClick: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by viewModel.currentLanguage.collectAsState()
    val strings = AppStrings.get(currentLang)

    val currentTab by viewModel.currentTab.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val isWebKitActive = serverState.isRunning && serverState.activeMode == ServerActiveMode.WEBKIT
    val isPkgActive = serverState.isRunning && serverState.activeMode == ServerActiveMode.PKG_SENDER

    val siteInfo by viewModel.currentSiteInfo.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importMessage by viewModel.importStatusMessage.collectAsState()
    val ripProgress by viewModel.ripProgress.collectAsState()
    val logs by viewModel.trafficLogs.collectAsState()

    // PKG Installer state
    val selectedPkgFiles by viewModel.selectedPkgFiles.collectAsState()
    val ps4TargetIp by viewModel.ps4TargetIp.collectAsState()
    val isSendingPkg by viewModel.isSendingPkg.collectAsState()
    val pkgTransferStatus by viewModel.pkgTransferStatus.collectAsState()
    val transferredBytes by viewModel.transferredBytes.collectAsState()
    val speedBytes by viewModel.transferSpeedBytes.collectAsState()
    val pkgLogs by viewModel.pkgLogs.collectAsState()

    val portalUrl = "http://${serverState.nativeIp}:${serverState.port}/"
    val qrBitmap = remember(serverState.nativeIp, serverState.port) {
        QRCodeGenerator.generateQRCode(portalUrl, sizePx = 380)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBg)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with In-App Bilingual Switcher
        item {
            HeaderSection(
                isRunning = serverState.isRunning,
                currentLanguage = currentLang,
                strings = strings,
                onLanguageToggle = { lang ->
                    viewModel.setLanguage(context, lang)
                }
            )
        }

        // 2. Primary Tab Selector: [ 🌐 WebKit Exploit | 🎮 PS4 PKG Sender ]
        item {
            PrimaryTabRow(
                selectedTab = currentTab,
                strings = strings,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }

        if (currentTab == MainDashboardTab.WEBKIT) {
            // ==================== TAB 1: WEBKIT EXPLOIT ====================
            // WebKit Server Controller Card
            item {
                ServerControlCard(
                    isRunning = isWebKitActive,
                    isOtherModeRunning = isPkgActive,
                    title = if (isWebKitActive) strings.webKitRunningTitle else strings.serverStoppedTitle,
                    description = if (isWebKitActive) strings.webKitRunningDesc else if (isPkgActive) strings.serverActiveOtherMode else strings.serverStoppedDesc,
                    btnText = if (isWebKitActive) strings.btnStopWebKit else strings.btnStartWebKit,
                    otherModeNotice = if (isPkgActive) "Mode PS4 PKG Sender sedang aktif di port 8080." else null,
                    metricContent = {
                        MetricItem(label = strings.totalRequests, value = serverState.totalRequests.toString(), color = CyberCyan)
                        MetricItem(label = strings.proxyRedirects, value = serverState.redirectedRequests.toString(), color = AmberWarning)
                    },
                    onToggle = { viewModel.toggleWebKitServer(context) }
                )
            }

            // Native Network IP & Port Display
            item {
                NativeNetworkCard(
                    ipAddress = serverState.nativeIp,
                    port = serverState.port,
                    interfaceName = serverState.interfaceName,
                    strings = strings,
                    onCopyIp = { copyToClipboard(context, "IP Address", serverState.nativeIp, strings) },
                    onCopyUrl = { copyToClipboard(context, "Portal URL", portalUrl, strings) },
                    onRefresh = { viewModel.refreshNetworkInterfaces() }
                )
            }

            // Quick Access QR Code Card (When WebKit is running)
            if (isWebKitActive && qrBitmap != null) {
                item {
                    QrCodeCard(
                        bitmap = qrBitmap,
                        portalUrl = portalUrl,
                        strings = strings,
                        onOpenBrowser = { openBrowser(context, portalUrl) }
                    )
                }
            }

            // Website Source Card (Dual Tab: ZIP vs Web Ripper)
            item {
                WebsiteSourceCard(
                    siteInfo = siteInfo,
                    isImporting = isImporting,
                    importMessage = importMessage,
                    ripProgress = ripProgress,
                    strings = strings,
                    onPickZip = onPickZipClick,
                    onCloneUrl = { url -> viewModel.ripWebsite(context, url) },
                    onPreviewWeb = { openBrowser(context, portalUrl) },
                    isServerRunning = isWebKitActive
                )
            }

            // Client Proxy Setup Guide (PS4 & PS5)
            item {
                ClientSetupGuideCard(
                    ipAddress = serverState.nativeIp,
                    port = serverState.port,
                    strings = strings
                )
            }

            // Realtime Traffic Logs
            item {
                TrafficLogsCard(
                    logs = logs,
                    strings = strings,
                    onClear = { viewModel.clearLogs() }
                )
            }
        } else {
            // ==================== TAB 2: PS4 PKG SENDER ====================
            // PKG Server Controller Card
            item {
                ServerControlCard(
                    isRunning = isPkgActive,
                    isOtherModeRunning = isWebKitActive,
                    title = if (isPkgActive) strings.pkgRunningTitle else strings.serverStoppedTitle,
                    description = if (isPkgActive) strings.pkgRunningDesc else if (isWebKitActive) strings.serverActiveOtherMode else strings.serverStoppedDesc,
                    btnText = if (isPkgActive) strings.btnStopPkg else strings.btnStartPkg,
                    otherModeNotice = if (isWebKitActive) "Mode WebKit Exploit sedang aktif di port 8080." else null,
                    metricContent = {
                        MetricItem(label = strings.speedLabel, value = "${formatBytes(speedBytes)}/s", color = NeonGreen)
                        MetricItem(label = strings.transferredLabel, value = formatBytes(transferredBytes), color = ElectricBlue)
                    },
                    onToggle = { viewModel.togglePkgServer(context) }
                )
            }

            // Native Network IP & Port Display
            item {
                NativeNetworkCard(
                    ipAddress = serverState.nativeIp,
                    port = serverState.port,
                    interfaceName = serverState.interfaceName,
                    strings = strings,
                    onCopyIp = { copyToClipboard(context, "IP Address", serverState.nativeIp, strings) },
                    onCopyUrl = { copyToClipboard(context, "Portal URL", portalUrl, strings) },
                    onRefresh = { viewModel.refreshNetworkInterfaces() }
                )
            }

            // PS4 PKG Installer & Sender Card (GoldHEN Port 9090)
            item {
                Ps4PkgInstallerCard(
                    pkgFiles = selectedPkgFiles,
                    targetIp = ps4TargetIp,
                    isSending = isSendingPkg,
                    statusMessage = pkgTransferStatus,
                    transferredBytes = transferredBytes,
                    speedBytes = speedBytes,
                    strings = strings,
                    onIpChanged = { viewModel.setPs4TargetIp(it) },
                    onPickPkg = onPickPkgClick,
                    onRemovePkg = { viewModel.removePkgFile(it) },
                    onToggleContentType = { viewModel.togglePkgContentType(it) },
                    onSendToPs4 = { viewModel.sendPkgToPs4(context) },
                    onHostOnly = { viewModel.hostPkgOnServerOnly(context) }
                )
            }

            // PKG Sender Live Console Log Card
            item {
                PkgLogConsoleCard(
                    logs = pkgLogs,
                    strings = strings,
                    onClear = { viewModel.clearPkgLogs() },
                    onCopy = { viewModel.copyPkgLogsToClipboard(context, strings) }
                )
            }

            // GoldHEN BinLoader Guide
            item {
                GoldHenGuideCard(strings = strings, nativeIp = serverState.nativeIp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HeaderSection(
    isRunning: Boolean,
    currentLanguage: AppLanguage,
    strings: AppStrings.Strings,
    onLanguageToggle: (AppLanguage) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.appTitle,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = strings.appVersion,
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = strings.appSubtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Language Switcher Toggle Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSlateSurface)
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguageBadge(
                        label = "ID",
                        flag = "🇮🇩",
                        isSelected = currentLanguage == AppLanguage.ID,
                        onClick = { onLanguageToggle(AppLanguage.ID) }
                    )
                    LanguageBadge(
                        label = "EN",
                        flag = "🇬🇧",
                        isSelected = currentLanguage == AppLanguage.EN,
                        onClick = { onLanguageToggle(AppLanguage.EN) }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Pulse Status Indicator
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) NeonGreen.copy(alpha = 0.15f) else CrimsonAlert.copy(alpha = 0.15f))
                    .border(
                        width = 1.5.dp,
                        color = if (isRunning) NeonGreen.copy(alpha = 0.5f) else CrimsonAlert.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            ) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(NeonGreen.copy(alpha = 0.25f * alpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) NeonGreen.copy(alpha = alpha) else CrimsonAlert)
                )
            }
        }
    }
}

@Composable
fun LanguageBadge(
    label: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) CyberCyan.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$flag $label",
            color = if (isSelected) CyberCyan else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PrimaryTabRow(
    selectedTab: MainDashboardTab,
    strings: AppStrings.Strings,
    onTabSelected: (MainDashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSlateSurface)
            .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val isWebKit = selectedTab == MainDashboardTab.WEBKIT
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isWebKit) CyberCyan.copy(alpha = 0.2f) else Color.Transparent)
                .border(
                    width = if (isWebKit) 1.dp else 0.dp,
                    color = if (isWebKit) CyberCyan.copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp)
                )
                .clickable { onTabSelected(MainDashboardTab.WEBKIT) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = if (isWebKit) CyberCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.tabWebKit,
                    color = if (isWebKit) CyberCyan else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isWebKit) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        val isPkg = selectedTab == MainDashboardTab.PKG_SENDER
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isPkg) ElectricBlue.copy(alpha = 0.25f) else Color.Transparent)
                .border(
                    width = if (isPkg) 1.dp else 0.dp,
                    color = if (isPkg) ElectricBlue.copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(9.dp)
                )
                .clickable { onTabSelected(MainDashboardTab.PKG_SENDER) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = if (isPkg) ElectricBlue else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.tabPkg,
                    color = if (isPkg) ElectricBlue else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isPkg) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ServerControlCard(
    isRunning: Boolean,
    isOtherModeRunning: Boolean = false,
    title: String,
    description: String,
    btnText: String,
    otherModeNotice: String? = null,
    metricContent: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Status Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isRunning) NeonGreen.copy(alpha = 0.15f)
                            else if (isOtherModeRunning) AmberWarning.copy(alpha = 0.15f)
                            else CrimsonAlert.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isRunning) "AKTIF" else if (isOtherModeRunning) "STANDBY" else "MATI",
                        color = if (isRunning) NeonGreen else if (isOtherModeRunning) AmberWarning else CrimsonAlert,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!otherModeNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberWarning.copy(alpha = 0.1f))
                        .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "ℹ $otherModeNotice", color = AmberWarning, fontSize = 11.sp)
                }
            }

            if (isRunning && metricContent != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    metricContent()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) CrimsonAlert else CyberCyan,
                    contentColor = if (isRunning) Color.White else DarkNavyBg
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = btnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun NativeNetworkCard(
    ipAddress: String,
    port: Int,
    interfaceName: String,
    strings: AppStrings.Strings,
    onCopyIp: () -> Unit,
    onCopyUrl: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.hardwareNetworkTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkNavyBg)
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.nativeIpLabel,
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = ipAddress,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${strings.interfaceLabel}: $interfaceName | ${strings.portLabel}: $port",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onCopyIp) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = CyberCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onCopyUrl,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${strings.btnCopyUrl}: http://$ipAddress:$port/",
                    color = ElectricBlue,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun QrCodeCard(
    bitmap: Bitmap,
    portalUrl: String,
    strings: AppStrings.Strings,
    onOpenBrowser: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = strings.qrTitle, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = strings.qrDesc, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code Portal",
                    modifier = Modifier.size(160.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = portalUrl, color = CyberCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenBrowser,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSlateElevated)
            ) {
                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = strings.btnOpenBrowser, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun WebsiteSourceCard(
    siteInfo: SitePackageInfo?,
    isImporting: Boolean,
    importMessage: String?,
    ripProgress: RipProgress,
    strings: AppStrings.Strings,
    onPickZip: () -> Unit,
    onCloneUrl: (String) -> Unit,
    onPreviewWeb: () -> Unit,
    isServerRunning: Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inputUrl by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Source Tabs (ZIP vs Web Ripper)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkNavyBg,
                contentColor = CyberCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyberCyan
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .padding(2.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.tabZip, fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.tabWebRipper, fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // ZIP Package Mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBg)
                        .padding(12.dp)
                ) {
                    Text(
                        text = siteInfo?.title ?: strings.noZipImported,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${strings.totalFiles}: ${siteInfo?.totalFiles ?: 0}", color = TextSecondary, fontSize = 12.sp)
                        Text(text = "${strings.fileSize}: ${formatBytes(siteInfo?.totalSizeBytes ?: 0L)}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (siteInfo?.hasIndexHtml == true) strings.indexDetected else strings.indexNotFound,
                        color = if (siteInfo?.hasIndexHtml == true) NeonGreen else AmberWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!importMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = importMessage, color = ElectricBlue, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickZip,
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkNavyBg)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkNavyBg, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = strings.btnImportingZip, fontSize = 13.sp)
                        } else {
                            Text(text = strings.btnImportZip, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (isServerRunning) {
                        OutlinedButton(onClick = onPreviewWeb, shape = RoundedCornerShape(10.dp)) {
                            Text(text = strings.btnPreview, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Web Ripper Mode
                Text(
                    text = strings.webRipperDesc,
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // URL Input Field
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(strings.urlInputPlaceholder, color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkNavyBg,
                        unfocusedContainerColor = DarkNavyBg,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputUrl.isNotBlank()) {
                                IconButton(onClick = { inputUrl = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                            IconButton(onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) inputUrl = clip
                            }) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyberCyan)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = strings.quickPresetsLabel, color = TextMuted, fontSize = 11.sp)
                    QuickUrlChip(label = "Karo Exploit", url = "https://ps4.karo218.ir/") { inputUrl = it }
                    QuickUrlChip(label = "KME PS4", url = "https://kmeps4.site/") { inputUrl = it }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Indicator
                if (ripProgress.isRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavyBg)
                            .padding(10.dp)
                    ) {
                        Text(text = ripProgress.statusMessage, color = CyberCyan, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val progressFraction = if (ripProgress.totalCount > 0) {
                            (ripProgress.currentCount.toFloat() / ripProgress.totalCount).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonGreen,
                            trackColor = DarkSlateElevated
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (!ripProgress.errorMessage.isNullOrBlank()) {
                    Text(text = ripProgress.errorMessage, color = CrimsonAlert, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (ripProgress.isSuccess) {
                    Text(text = ripProgress.statusMessage, color = NeonGreen, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { onCloneUrl(inputUrl) },
                    enabled = !ripProgress.isRunning && inputUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkNavyBg)
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (ripProgress.isRunning) strings.btnCloningWeb else strings.btnCloneWeb,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickUrlChip(label: String, url: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSlateElevated)
            .clickable { onSelect(url) }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = CyberCyan, fontSize = 10.sp)
    }
}

@Composable
fun Ps4PkgInstallerCard(
    pkgFiles: List<PkgFile>,
    targetIp: String,
    isSending: Boolean,
    statusMessage: String?,
    transferredBytes: Long,
    speedBytes: Long,
    strings: AppStrings.Strings,
    onIpChanged: (String) -> Unit,
    onPickPkg: () -> Unit,
    onRemovePkg: (String) -> Unit,
    onToggleContentType: (String) -> Unit = {},
    onSendToPs4: () -> Unit,
    onHostOnly: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.SportsEsports, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = strings.pkgCardTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.pkgCardDesc,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PKG File List or Empty State
            if (pkgFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBg)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noPkgSelected,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pkgFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkNavyBg)
                                .border(1.dp, ElectricBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    file.titleId?.let {
                                        BadgeChip(text = it, bgColor = ElectricBlue.copy(alpha = 0.2f), textColor = ElectricBlue)
                                    }
                                    val (typeLabel, chipBg, chipFg) = when (file.contentType) {
                                        "PS4GP" -> Triple("Patch (PS4GP) ⟳", AmberWarning.copy(alpha = 0.2f), AmberWarning)
                                        "PS4AC" -> Triple("DLC (PS4AC) ⟳", CyberCyan.copy(alpha = 0.2f), CyberCyan)
                                        else -> Triple("Game (PS4GD) ⟳", NeonGreen.copy(alpha = 0.2f), NeonGreen)
                                    }
                                    BadgeChip(
                                        text = typeLabel,
                                        bgColor = chipBg,
                                        textColor = chipFg,
                                        onClick = { onToggleContentType(file.id) }
                                    )
                                    BadgeChip(text = file.formattedSize, bgColor = ElectricBlue.copy(alpha = 0.2f), textColor = ElectricBlue)
                                }
                            }
                            IconButton(onClick = { onRemovePkg(file.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add PKG Button
            OutlinedButton(
                onClick = onPickPkg,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = strings.btnPickPkg, color = ElectricBlue, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Target PS4 IP & Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = onIpChanged,
                    label = { Text(strings.targetPs4IpLabel, color = TextMuted, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkNavyBg,
                        unfocusedContainerColor = DarkNavyBg,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = ElectricBlue.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBg)
                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = strings.targetPortLabel, color = TextMuted, fontSize = 9.sp)
                        Text(text = "9090", color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Status message
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyBg)
                        .padding(10.dp)
                ) {
                    Text(text = statusMessage, color = ElectricBlue, fontSize = 12.sp)
                }
            }

            // Transfer Monitor Banner
            if (transferredBytes > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSlateElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${strings.speedLabel}: ${formatBytes(speedBytes)}/s", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "${strings.transferredLabel}: ${formatBytes(transferredBytes)}", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Send to PS4 & Host Only (Clean, Balanced & Minimalist)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSendToPs4,
                    enabled = !isSending && pkgFiles.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = strings.btnSendToPs4, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onHostOnly,
                    enabled = pkgFiles.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = strings.btnHostOnly, color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = strings.errPkgAlreadyExists, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun GoldHenGuideCard(strings: AppStrings.Strings, nativeIp: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GoldHEN BinLoader (Port 9090)",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val isIndo = strings.tabPkg == "PS4 PKG Sender" && strings.btnSendToPs4 == "Kirim ke PS4"
            val steps = if (isIndo) {
                "1. Sambungkan PS4 ke Hotspot Wi-Fi HP ini (Server PKG di $nativeIp:8080).\n" +
                "2. Pada menu GoldHEN di PS4, aktifkan 'BinLoader Server' (Port 9090).\n" +
                "3. Masukkan IP PS4 pada kolom di atas, lalu tekan 'Kirim ke PS4'.\n" +
                "4. Notifikasi unduhan akan langsung muncul di layar PS4!"
            } else {
                "1. Connect PS4 to this phone's Wi-Fi Hotspot (PKG Server at $nativeIp:8080).\n" +
                "2. In GoldHEN settings on PS4, enable 'BinLoader Server' (Port 9090).\n" +
                "3. Enter your PS4 IP above, then tap 'Send to PS4'.\n" +
                "4. Download progress notification will pop up on your PS4!"
            }
            Text(
                text = steps,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun BadgeChip(
    text: String,
    bgColor: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ClientSetupGuideCard(
    ipAddress: String,
    port: Int,
    strings: AppStrings.Strings
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = NeonGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.clientGuideTitle,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.clientGuideSubtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavyBg)
                    .padding(12.dp)
            ) {
                GuideRow(label = strings.guideProxyHost, value = ipAddress)
                Spacer(modifier = Modifier.height(6.dp))
                GuideRow(label = strings.guideProxyPort, value = port.toString())
                Spacer(modifier = Modifier.height(6.dp))
                GuideRow(label = strings.guideProxyBypass, value = strings.guideProxyBypassVal)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PS4 Instructions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSlateElevated)
                    .padding(12.dp)
            ) {
                Text(
                    text = strings.ps4GuideTitle,
                    color = CyberCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.ps4GuideSteps,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PS5 Instructions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSlateElevated)
                    .padding(12.dp)
            ) {
                Text(
                    text = strings.ps5GuideTitle,
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.ps5GuideSteps,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun GuideRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(text = value, color = CyberCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
fun TrafficLogsCard(
    logs: List<RequestLog>,
    strings: AppStrings.Strings,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.radarTitle} (${logs.size})",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (logs.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = strings.btnClearRadar, tint = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.radarEmpty,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyBg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    logs.take(10).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (log.isRedirected) AmberWarning.copy(alpha = 0.2f) else NeonGreen.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (log.isRedirected) "302 REDIRECT" else "${log.statusCode} OK",
                                        color = if (log.isRedirected) AmberWarning else NeonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${log.clientIp} -> ${log.uri.take(24)}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = timeFormat.format(Date(log.timestamp)),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PkgLogConsoleCard(
    logs: List<PkgLogEntry>,
    strings: AppStrings.Strings,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.pkgLogTitle} (${logs.size})",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (logs.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = strings.btnCopyPkgLog,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = strings.btnClearPkgLog,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.pkgLogEmpty,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                LaunchedEffect(logs.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavyBg)
                        .padding(10.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    logs.forEach { log ->
                        val (tagText, tagBg, tagColor) = when (log.level) {
                            PkgLogLevel.SUCCESS -> Triple("OK", NeonGreen.copy(alpha = 0.15f), NeonGreen)
                            PkgLogLevel.WARN -> Triple("WARN", AmberWarning.copy(alpha = 0.15f), AmberWarning)
                            PkgLogLevel.ERROR -> Triple("ERR", CrimsonAlert.copy(alpha = 0.2f), CrimsonAlert)
                            PkgLogLevel.INFO -> Triple("INFO", CyberCyan.copy(alpha = 0.15f), CyberCyan)
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = timeFormat.format(Date(log.timestamp)),
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tagBg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = tagText,
                                        color = tagColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = log.message,
                                    color = when (log.level) {
                                        PkgLogLevel.ERROR -> CrimsonAlert
                                        PkgLogLevel.WARN -> AmberWarning
                                        PkgLogLevel.SUCCESS -> TextPrimary
                                        PkgLogLevel.INFO -> TextSecondary
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (!log.details.isNullOrBlank()) {
                                Text(
                                    text = log.details,
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(start = 58.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String, strings: AppStrings.Strings) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label ${strings.toastCopied}: $text", Toast.LENGTH_SHORT).show()
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Browser error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp.coerceAtLeast(1) - 1]
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
