package com.fazzdev.offlineedgeportal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fazzdev.offlineedgeportal.core.RequestLog
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
import com.fazzdev.offlineedgeportal.ui.util.QRCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onPickZipClick: () -> Unit
) {
    val context = LocalContext.current
    val serverState by viewModel.serverState.collectAsState()
    val siteInfo by viewModel.currentSiteInfo.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importMessage by viewModel.importStatusMessage.collectAsState()
    val logs by viewModel.trafficLogs.collectAsState()

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
        // 1. Header
        item {
            HeaderSection(serverState.isRunning)
        }

        // 2. Server Controller Card
        item {
            ServerControlCard(
                isRunning = serverState.isRunning,
                totalRequests = serverState.totalRequests,
                redirectedRequests = serverState.redirectedRequests,
                onToggle = { viewModel.toggleServer(context) }
            )
        }

        // 3. Native Network IP & Port Display (Explicit Hardware Resolution)
        item {
            NativeNetworkCard(
                ipAddress = serverState.nativeIp,
                port = serverState.port,
                interfaceName = serverState.interfaceName,
                onCopyIp = { copyToClipboard(context, "IP Address", serverState.nativeIp) },
                onCopyUrl = { copyToClipboard(context, "Portal URL", portalUrl) },
                onRefresh = { viewModel.refreshNetworkInterfaces() }
            )
        }

        // 4. QR Code Quick Access Card
        if (serverState.isRunning && qrBitmap != null) {
            item {
                QrCodeCard(
                    bitmap = qrBitmap,
                    portalUrl = portalUrl,
                    onOpenBrowser = { openBrowser(context, portalUrl) }
                )
            }
        }

        // 5. ZIP Package Manager Card
        item {
            ZipPackageCard(
                siteInfo = siteInfo,
                isImporting = isImporting,
                importMessage = importMessage,
                onPickZip = onPickZipClick,
                onPreviewWeb = { openBrowser(context, portalUrl) },
                isServerRunning = serverState.isRunning
            )
        }

        // 6. Client Proxy Setup Guide
        item {
            ClientSetupGuideCard(
                ipAddress = serverState.nativeIp,
                port = serverState.port
            )
        }

        // 7. Realtime Traffic Logs
        item {
            TrafficLogsCard(
                logs = logs,
                onClear = { viewModel.clearLogs() }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HeaderSection(isRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = "Hotspot WebKit PS4-PS5 v1.0",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Offline Exploit Host & Edge Server (Non-Root)",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Status Dot Indicator (Green = Online, Red = Offline)
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
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isRunning) NeonGreen.copy(alpha = 0.15f) else CrimsonAlert.copy(alpha = 0.15f))
                .border(
                    width = 1.5.dp,
                    color = if (isRunning) NeonGreen.copy(alpha = 0.5f) else CrimsonAlert.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        ) {
            // Subtle pulse halo when running
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.25f * alpha))
                )
            }
            // Core indicator dot (Green = Online, Red = Offline)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) NeonGreen.copy(alpha = alpha) else CrimsonAlert)
            )
        }
    }
}

@Composable
fun ServerControlCard(
    isRunning: Boolean,
    totalRequests: Long,
    redirectedRequests: Long,
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
                Column {
                    Text(
                        text = if (isRunning) "Server Sedang Berjalan" else "Server Berhenti",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isRunning) "Melayani web lokal & mencegat permintaan eksternal" else "Tekan tombol untuk mengaktifkan edge server",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics row
            if (isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkNavyBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem(label = "Total Permintaan", value = totalRequests.toString(), color = CyberCyan)
                    MetricItem(label = "Redirect (Proxy)", value = redirectedRequests.toString(), color = AmberWarning)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                    text = if (isRunning) "Matikan Server & Proxy" else "Nyalakan Server & Proxy (Port 8080)",
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
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardware Network Interface",
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

            // IP & Port Banner
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
                        text = "NATIVE IP ADDRESS",
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
                        text = "Interface: $interfaceName | Port: $port",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row {
                    IconButton(onClick = onCopyIp) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin IP", tint = CyberCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onCopyUrl,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Salin URL: http://$ipAddress:$port/",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan QR Akses Cepat",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Klien terhubung bisa scan untuk langsung membuka portal lokal",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )

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
                    modifier = Modifier.size(170.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = portalUrl,
                color = CyberCyan,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenBrowser,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSlateElevated)
            ) {
                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Buka Portal di HP Ini", color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ZipPackageCard(
    siteInfo: com.fazzdev.offlineedgeportal.core.SitePackageInfo?,
    isImporting: Boolean,
    importMessage: String?,
    onPickZip: () -> Unit,
    onPreviewWeb: () -> Unit,
    isServerRunning: Boolean
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
                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Website Source (.ZIP Package)",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavyBg)
                    .padding(12.dp)
            ) {
                Text(
                    text = siteInfo?.title ?: "Belum ada file diimpor",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total File: ${siteInfo?.totalFiles ?: 0}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Ukuran: ${formatBytes(siteInfo?.totalSizeBytes ?: 0L)}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (siteInfo?.hasIndexHtml == true) "✓ index.html terdeteksi (Siap di-host)" else "⚠ index.html tidak ditemukan di root",
                    color = if (siteInfo?.hasIndexHtml == true) NeonGreen else AmberWarning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!importMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = importMessage,
                    color = ElectricBlue,
                    fontSize = 12.sp
                )
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
                        Text(text = "Mengimpor...", fontSize = 13.sp)
                    } else {
                        Text(text = "Import .ZIP Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (isServerRunning) {
                    OutlinedButton(
                        onClick = onPreviewWeb,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Preview", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ClientSetupGuideCard(
    ipAddress: String,
    port: Int
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
                    text = "Panduan Setting Konsol PS4 & PS5",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hubungkan konsol PS4/PS5 ke Wi-Fi Hotspot HP ini, lalu konfigurasi Proxy:",
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
                GuideRow(label = "Proxy Hostname / Address", value = ipAddress)
                Spacer(modifier = Modifier.height(6.dp))
                GuideRow(label = "Proxy Port", value = port.toString())
                Spacer(modifier = Modifier.height(6.dp))
                GuideRow(label = "Bypass Proxy", value = "(Kosongkan)")
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
                    text = "🎮 Langkah di PlayStation 4 (PS4):",
                    color = CyberCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Buka Pengaturan > Jaringan > Siapkan Koneksi Internet.\n" +
                           "2. Pilih Gunakan Wi-Fi > pilih mode Khusus (Custom).\n" +
                           "3. Pada Server Proksi (Proxy Server): pilih Gunakan (Use), masukkan Host & Port di atas.\n" +
                           "4. Opsi lainnya (IP, DNS, MTU): biarkan Otomatis.\n" +
                           "5. Buka Pengaturan > Panduan Pengguna (User's Guide). Website/exploit dari file .ZIP akan langsung tampil 100%!",
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
                    text = "🎮 Langkah di PlayStation 5 (PS5):",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Buka Pengaturan > Jaringan > Pengaturan > Siapkan Koneksi Internet.\n" +
                           "2. Tekan tombol Opsi pada Wi-Fi Hotspot ini > Pengaturan Lanjutan (Advanced Settings).\n" +
                           "3. Pada Server Proksi: pilih Gunakan (Use), masukkan Host & Port di atas.\n" +
                           "4. Opsi lainnya: biarkan Otomatis.\n" +
                           "5. Buka Pengaturan > Panduan & Kiat... > Panduan Pengguna (User's Guide) untuk langsung memuat exploit!",
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
                    text = "Live Traffic Radar (${logs.size})",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (logs.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Bersihkan", tint = TextMuted)
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
                        text = "Belum ada trafik dari klien...",
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label disalin: $text", Toast.LENGTH_SHORT).show()
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membuka browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
