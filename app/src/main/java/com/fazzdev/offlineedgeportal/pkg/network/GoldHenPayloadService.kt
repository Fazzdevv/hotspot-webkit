package com.fazzdev.offlineedgeportal.pkg.network

import android.content.Context
import android.util.Log
import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Service to communicate with the PS4 GoldHEN Payload Server / BinLoader on Port 9090.
 * Injects a 16KB ARM/x86 payload that calls Sony's native background download manager (BGFT/PlayGo),
 * allowing PKG files to be installed directly from the PS4 Home Screen in the background.
 *
 * Ported from PS4PkgSender v1.2.0 Desktop.
 */
object GoldHenPayloadService {

    private const val TAG = "GoldHenPayloadService"
    const val GOLDHEN_PORT = 9090
    private const val PAYLOAD_ASSET_NAME = "goldhen_payload.bin"

    // 6-byte placeholder in payload binary at offset 16384 (0x4000)
    private val PLACEHOLDER_BYTES = byteArrayOf(
        0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte(),
        0xB4.toByte(), 0xB4.toByte(), 0xB4.toByte()
    )

    var onLogMessage: ((String) -> Unit)? = null

    private fun log(message: String) {
        Log.i(TAG, message)
        onLogMessage?.invoke(message)
    }

    /**
     * Patches the 16KB payload binary with the local IP and callback ServerSocket port.
     */
    fun patchPayload(originalPayload: ByteArray, localIp: String, localPort: Int): ByteArray {
        val payload = originalPayload.copyOf()
        val offset = findPlaceholderOffset(payload)
        require(offset != -1) { "Payload placeholder 0xB4x6 not found in binary" }

        val ipBytes = InetAddress.getByName(localIp).address // 4 bytes
        // Port in Network Byte Order (Big Endian)
        val portHigh = ((localPort shr 8) and 0xFF).toByte()
        val portLow = (localPort and 0xFF).toByte()

        ipBytes.copyInto(payload, destinationOffset = offset, startIndex = 0, endIndex = 4)
        payload[offset + 4] = portHigh
        payload[offset + 5] = portLow

        return payload
    }

    private fun findPlaceholderOffset(data: ByteArray): Int {
        for (i in 0..(data.size - 6)) {
            if (data[i] == PLACEHOLDER_BYTES[0] &&
                data[i + 1] == PLACEHOLDER_BYTES[1] &&
                data[i + 2] == PLACEHOLDER_BYTES[2] &&
                data[i + 3] == PLACEHOLDER_BYTES[3] &&
                data[i + 4] == PLACEHOLDER_BYTES[4] &&
                data[i + 5] == PLACEHOLDER_BYTES[5]
            ) {
                return i
            }
        }
        return -1
    }

    /**
     * Builds the binary Little-Endian packet buffer for a single package.
     * Protocol:
     * - uint32 LE: 1 (Command: New Package)
     * - uint32 LE: URL length, then URL UTF-8 bytes
     * - uint32 LE: Name length, then Name UTF-8 bytes
     * - uint32 LE: ContentID length, then ContentID UTF-8 bytes
     * - uint32 LE: ContentType length, then ContentType UTF-8 bytes ("PS4GD")
     * - uint64 LE: File size in bytes
     * - uint32 LE: Icon length (0 if no icon)
     */
    fun buildPackageInfoBuffer(
        url: String,
        name: String,
        contentId: String,
        sizeBytes: Long,
        contentType: String = "PS4GD"
    ): ByteArray {
        val urlBytes = url.toByteArray(Charsets.UTF_8)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val idBytes = contentId.toByteArray(Charsets.UTF_8)
        val typeBytes = contentType.toByteArray(Charsets.UTF_8)

        val totalSize = 4 + // command (1)
                4 + urlBytes.size +
                4 + nameBytes.size +
                4 + idBytes.size +
                4 + typeBytes.size +
                8 + // size uint64
                4   // icon len (0)

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1) // Command: 1 = New Package

        buffer.putInt(urlBytes.size)
        buffer.put(urlBytes)

        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)

        buffer.putInt(idBytes.size)
        buffer.put(idBytes)

        buffer.putInt(typeBytes.size)
        buffer.put(typeBytes)

        buffer.putLong(sizeBytes)

        buffer.putInt(0) // Icon length 0

        return buffer.array()
    }

    /**
     * Builds the clean exit command buffer (uint32 LE: 0).
     */
    fun buildExitBuffer(): ByteArray {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0)
        return buffer.array()
    }

    /**
     * Executes the entire GoldHEN Payload installation workflow on Port 9090:
     * 1. Opens local ServerSocket on dynamic port.
     * 2. Patches payload with phone IP & callback port.
     * 3. Injects payload into PS4 Port 9090 (GoldHEN BinLoader).
     * 4. Waits for PS4 to connect back (up to 15s).
     * 5. Sends package data for all queued files.
     * 6. Sends clean exit signal (0) and closes.
     */
    suspend fun sendPkgPayload(
        context: Context,
        ps4Ip: String,
        localIp: String,
        files: List<PkgFile>,
        packageUrls: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        if (files.isEmpty() || packageUrls.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Daftar berkas PKG kosong"))
        }

        // 1. Open dynamic local server socket
        val serverSocket = try {
            ServerSocket(0, 5, InetAddress.getByName(localIp)).apply {
                soTimeout = 15000 // 15 seconds timeout waiting for PS4
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Gagal membuka socket server lokal di $localIp: ${e.message}"))
        }

        val localPort = serverSocket.localPort
        log("Local GoldHEN callback server listening on $localIp:$localPort")

        try {
            // 2. Load and patch raw payload from assets
            val rawPayload = try {
                context.assets.open(PAYLOAD_ASSET_NAME).use { it.readBytes() }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Berkas $PAYLOAD_ASSET_NAME tidak ditemukan di assets: ${e.message}"))
            }
            val patchedPayload = patchPayload(rawPayload, localIp, localPort)

            // 3. Connect to GoldHEN BinLoader on Port 9090
            val ps4PayloadSocket = Socket()
            ps4PayloadSocket.tcpNoDelay = true
            try {
                log("Menyambungkan ke PS4 $ps4Ip:$GOLDHEN_PORT...")
                ps4PayloadSocket.connect(InetSocketAddress(ps4Ip, GOLDHEN_PORT), 3500)
                val ps4Out = ps4PayloadSocket.getOutputStream()
                ps4Out.write(patchedPayload)
                ps4Out.flush()
                log("Payload 16KB berhasil diinjeksikan ke GoldHEN Port $GOLDHEN_PORT pada $ps4Ip")
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception("Gagal terhubung ke GoldHEN Port $GOLDHEN_PORT di $ps4Ip: ${e.message}. Pastikan GoldHEN aktif dan 'Enable BinLoader Server' sudah dicentang pada menu GoldHEN PS4.")
                )
            } finally {
                try { ps4PayloadSocket.close() } catch (_: Exception) {}
            }

            // 4. Send package information for each file in queue
            serverSocket.soTimeout = 15000
            for (i in files.indices) {
                val file = files[i]
                val url = packageUrls.getOrNull(i) ?: continue
                val name = if (file.name.endsWith(".pkg", ignoreCase = true)) file.name.removeSuffix(".pkg") else file.name
                val contentId = file.contentId?.ifBlank { null }
                    ?: if (!file.titleId.isNullOrBlank()) "UP0001-${file.titleId}_00-0000000000000000"
                    else "UP0001-CUSA00000_00-0000000000000000"
                val contentType = file.contentType?.ifBlank { "PS4GD" } ?: "PS4GD"

                val pkgBuffer = buildPackageInfoBuffer(
                    url = url,
                    name = name,
                    contentId = contentId,
                    sizeBytes = file.sizeBytes,
                    contentType = contentType
                )

                log("Menunggu koneksi balik dari PS4 untuk paket [${i + 1}/${files.size}]: $name...")
                val pkgSocket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        Exception(if (i == 0) "PS4 tidak merespons dalam 15 detik. Pastikan PS4 dan HP berada di satu jaringan hotspot." else "Koneksi ke PS4 terputus sebelum paket ${i + 1} terdaftar.")
                    )
                }

                try {
                    pkgSocket.tcpNoDelay = true
                    val clientOut = pkgSocket.getOutputStream()
                    clientOut.write(pkgBuffer)
                    clientOut.flush()
                    log("Terkirim ke PS4: $name ($url)")
                } finally {
                    try { pkgSocket.close() } catch (_: Exception) {}
                }
            }

            // 5. Send clean exit signal (uint32 = 0)
            try {
                serverSocket.soTimeout = 5000
                log("Mengirim sinyal exit bersih ke PS4...")
                val exitSocket = serverSocket.accept()
                try {
                    exitSocket.tcpNoDelay = true
                    val exitOut = exitSocket.getOutputStream()
                    exitOut.write(buildExitBuffer())
                    exitOut.flush()
                    log("Sesi payload GoldHEN selesai dengan sukses.")
                } finally {
                    try { exitSocket.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                log("Sesi payload ditutup oleh PS4: ${e.message}")
            }

            Result.success("Berhasil dikirim ke PS4! Unduhan aktif di menu Notifikasi konsol.")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { serverSocket.close() } catch (_: Exception) {}
        }
    }
}
