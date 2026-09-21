package com.fazzdev.offlineedgeportal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fazzdev.offlineedgeportal.R
import com.fazzdev.offlineedgeportal.core.LocalEdgeServer
import com.fazzdev.offlineedgeportal.core.NetworkUtils
import com.fazzdev.offlineedgeportal.core.RequestLog
import com.fazzdev.offlineedgeportal.core.SitePackageInfo
import com.fazzdev.offlineedgeportal.core.ZipPackageManager
import com.fazzdev.offlineedgeportal.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerRuntimeState(
    val isRunning: Boolean = false,
    val nativeIp: String = "127.0.0.1",
    val port: Int = 8080,
    val interfaceName: String = "-",
    val siteInfo: SitePackageInfo? = null,
    val startTimeMillis: Long = 0L,
    val totalRequests: Long = 0L,
    val redirectedRequests: Long = 0L
)

class EdgeServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var localServer: LocalEdgeServer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val ACTION_START = "com.fazzdev.offlineedgeportal.START"
        const val ACTION_STOP = "com.fazzdev.offlineedgeportal.STOP"
        private const val CHANNEL_ID = "edge_server_channel"
        private const val NOTIFICATION_ID = 1001

        private val _serverState = MutableStateFlow(ServerRuntimeState())
        val serverState = _serverState.asStateFlow()

        private val _logEvents = MutableSharedFlow<RequestLog>(replay = 50)
        val logEvents = _logEvents.asSharedFlow()

        fun startService(context: Context) {
            val intent = Intent(context, EdgeServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EdgeServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEdgeServer()
            ACTION_STOP -> stopEdgeServer()
        }
        return START_NOT_STICKY
    }

    private fun startEdgeServer() {
        if (_serverState.value.isRunning) return

        // 1. Acquire WakeLock and WifiLock
        acquireLocks()

        // 2. Detect real native hardware IP
        val activeInterface = NetworkUtils.getActiveHotspotInfo()
        val detectedIp = activeInterface?.ipAddress ?: "127.0.0.1"
        val detectedInterfaceName = activeInterface?.name ?: "Unknown"

        // 3. Inspect current site directory
        val siteDir = ZipPackageManager.getSiteDirectory(this)
        val siteInfo = ZipPackageManager.inspectPackage(siteDir)

        // 4. Start Local Edge Server on port 8080
        localServer = LocalEdgeServer(
            port = 8080,
            siteRootDir = { ZipPackageManager.inspectPackage(siteDir).documentRootDir },
            currentNativeIp = { _serverState.value.nativeIp },
            onLog = { log ->
                scope.launch {
                    val current = _serverState.value
                    _serverState.value = current.copy(
                        totalRequests = current.totalRequests + 1,
                        redirectedRequests = if (log.isRedirected) current.redirectedRequests + 1 else current.redirectedRequests
                    )
                    _logEvents.emit(log)
                }
            }
        )

        try {
            localServer?.start()

            _serverState.value = ServerRuntimeState(
                isRunning = true,
                nativeIp = detectedIp,
                port = 8080,
                interfaceName = detectedInterfaceName,
                siteInfo = siteInfo,
                startTimeMillis = System.currentTimeMillis()
            )

            // Start Foreground Notification
            val notification = buildNotification("IP: $detectedIp:8080 (Aktif)")
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
            stopEdgeServer()
        }
    }

    private fun stopEdgeServer() {
        try {
            localServer?.stop()
        } catch (e: Exception) {
            // ignore
        }
        localServer = null
        releaseLocks()

        _serverState.value = _serverState.value.copy(
            isRunning = false
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OfflineEdgePortal::ServerWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager.createWifiLock(
            wifiMode,
            "OfflineEdgePortal::ServerWifiLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, EdgeServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hotspot WebKit PS4-PS5")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopEdgeServer()
        super.onDestroy()
    }
}
