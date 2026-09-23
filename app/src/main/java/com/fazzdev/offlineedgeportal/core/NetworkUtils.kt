package com.fazzdev.offlineedgeportal.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class InterfaceInfo(
    val name: String,
    val ipAddress: String,
    val isTethering: Boolean
)

object NetworkUtils {

    /**
     * Scans active hardware network interfaces on the Android device and resolves the real,
     * native IPv4 address without guessing or hardcoding.
     *
     * Prioritizes:
     * 1. Known Android AP subnet (192.168.43.x, 192.168.44.x, etc.)
     * 2. Known Wi-Fi AP / Tethering interfaces (e.g. ap0, wlan1, swlan0, softap)
     * 3. Active Wi-Fi (wlan0)
     * 4. Private LAN IP ranges (192.168.x.x, 172.16-31.x.x, 10.x.x.x)
     * 5. Any non-loopback IPv4 interface
     */
    fun getActiveHotspotInfo(): InterfaceInfo? {
        val detectedInterfaces = getAllAvailableInterfaces()
        if (detectedInterfaces.isEmpty()) return null

        // 1. Android default hotspot gateway subnets (universal Android tethering IP: 192.168.43.1)
        val hotspotSubnetInterface = detectedInterfaces.firstOrNull {
            it.ipAddress.startsWith("192.168.43.") || it.ipAddress.startsWith("192.168.44.")
        }
        if (hotspotSubnetInterface != null) {
            return hotspotSubnetInterface
        }

        // 2. Interfaces typical for Android SoftAP / Tethering
        val hotspotInterface = detectedInterfaces.firstOrNull { it.isTethering }
        if (hotspotInterface != null) {
            return hotspotInterface
        }

        // 3. Active wlan interface (e.g. wlan0, wlan1)
        val wlanInterface = detectedInterfaces.firstOrNull { it.name.startsWith("wlan", ignoreCase = true) }
        if (wlanInterface != null) {
            return wlanInterface
        }

        // 4. Private IPv4 addresses (192.168.x.x, 172.16-31.x.x) over cellular CGNAT/WAN
        val privateLanInterface = detectedInterfaces.firstOrNull {
            it.ipAddress.startsWith("192.168.") ||
            it.ipAddress.startsWith("172.")
        }
        if (privateLanInterface != null) {
            return privateLanInterface
        }

        // 5. Any non-loopback IPv4 interface
        return detectedInterfaces.firstOrNull()
    }

    /**
     * Lists all non-loopback IPv4 network interfaces on the device.
     */
    fun getAllAvailableInterfaces(): List<InterfaceInfo> {
        val results = mutableListOf<InterfaceInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback) continue

                // Check isUp safely - on some non-root Android 11+ devices, isUp may throw SocketException
                val isUp = try { intf.isUp } catch (_: Exception) { true }
                if (!isUp) continue

                val isTether = isLikelyTetherInterface(intf.name)
                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        results.add(
                            InterfaceInfo(
                                name = intf.name,
                                ipAddress = host,
                                isTethering = isTether
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun isLikelyTetherInterface(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("ap") ||
                lower.startsWith("softap") ||
                lower.startsWith("swlan") ||
                lower == "wlan1" ||
                lower.contains("tether") ||
                lower.contains("localonly") ||
                lower.startsWith("rndis") ||
                lower.startsWith("bridge")
    }
}
