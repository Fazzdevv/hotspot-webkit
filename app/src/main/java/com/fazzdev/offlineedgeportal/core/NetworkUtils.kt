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
     * Prioritizes known Wi-Fi AP / Tethering interfaces (e.g. ap0, wlan1, swlan0, softap).
     * Falls back to active Wi-Fi (wlan0) or other valid LAN interfaces.
     */
    fun getActiveHotspotInfo(): InterfaceInfo? {
        val detectedInterfaces = getAllAvailableInterfaces()

        // 1. First priority: interfaces typical for Android SoftAP / Tethering
        val hotspotInterface = detectedInterfaces.firstOrNull { it.isTethering }
        if (hotspotInterface != null) {
            return hotspotInterface
        }

        // 2. Second priority: active wlan interface (e.g. wlan0)
        val wlanInterface = detectedInterfaces.firstOrNull { it.name.startsWith("wlan", ignoreCase = true) }
        if (wlanInterface != null) {
            return wlanInterface
        }

        // 3. Third priority: any non-loopback IPv4 interface
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
                if (!intf.isUp || intf.isLoopback) continue

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
                lower.startsWith("rndis") ||
                lower.startsWith("bridge")
    }
}
