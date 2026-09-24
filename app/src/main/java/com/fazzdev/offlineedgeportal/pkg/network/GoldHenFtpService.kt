package com.fazzdev.offlineedgeportal.pkg.network

import android.content.Context
import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import com.fazzdev.offlineedgeportal.pkg.model.PkgLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

/**
 * Service to upload PKG files directly to PS4 internal HDD (/data/pkg/) via GoldHEN FTP (Port 2121 / 1337).
 *
 * Why this is essential:
 * Sony's BGFT (Background File Transfer) service rejects packages smaller than 1MB (e.g. small DLC unlockers)
 * with SCE_BGFT_ERROR_NOT_SUPPORTED (0x80990006).
 *
 * By uploading directly to /data/pkg/ via GoldHEN's built-in FTP server:
 * 1. Files of ANY size (including < 1MB DLC unlockers) transfer in under 1 second.
 * 2. They appear directly in PS4 Settings -> GoldHEN -> Package Installer (identical to USB flashdisk).
 * 3. Installs via native libSceAppInstUtil without BGFT restrictions.
 */
object GoldHenFtpService {

    var onLogMessage: ((String) -> Unit)? = null
    var onLogEntry: ((String, PkgLogLevel, String?) -> Unit)? = null

    private fun log(message: String, level: PkgLogLevel = PkgLogLevel.INFO, details: String? = null) {
        onLogMessage?.invoke(message)
        onLogEntry?.invoke(message, level, details)
    }

    /**
     * Probes if GoldHEN FTP server is active (checks ports 2121, 1337, and 21).
     */
    fun detectFtpPort(ps4Ip: String, timeoutMs: Int = 800): Int? {
        val ports = listOf(2121, 1337, 21)
        for (port in ports) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ps4Ip, port), timeoutMs)
                    return port
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Uploads a PKG file to /data/pkg/ on PS4 HDD using standard FTP.
     */
    suspend fun uploadPkgToDataFolder(
        context: Context,
        ps4Ip: String,
        ftpPort: Int,
        file: PkgFile,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        var controlSocket: Socket? = null
        try {
            log("Menghubungkan ke GoldHEN FTP di $ps4Ip:$ftpPort...", PkgLogLevel.INFO)
            controlSocket = Socket()
            controlSocket.connect(InetSocketAddress(ps4Ip, ftpPort), 4000)
            controlSocket.soTimeout = 15000

            val reader = BufferedReader(InputStreamReader(controlSocket.getInputStream()))
            val writer = controlSocket.getOutputStream()

            fun readResponse(): Pair<Int, String> {
                var line = reader.readLine() ?: throw Exception("Koneksi FTP ditutup server")
                while (line.length >= 4 && line[3] == '-') {
                    line = reader.readLine() ?: throw Exception("Koneksi FTP ditutup server")
                }
                val code = line.take(3).toIntOrNull() ?: 0
                return Pair(code, line)
            }

            fun sendCommand(cmd: String): Pair<Int, String> {
                writer.write("$cmd\r\n".toByteArray(Charsets.UTF_8))
                writer.flush()
                return readResponse()
            }

            // 1. Read Greeting (220)
            val greeting = readResponse()
            log("FTP Server terhubung: ${greeting.second.trim()}", PkgLogLevel.INFO)

            // 2. Login (anonymous)
            val userResp = sendCommand("USER anonymous")
            if (userResp.first == 331) {
                sendCommand("PASS anonymous")
            }

            // 3. Binary Mode
            sendCommand("TYPE I")

            // 4. Ensure /data/pkg directory exists
            sendCommand("MKD /data")
            sendCommand("MKD /data/pkg")
            sendCommand("CWD /data/pkg")

            // 5. Enter Passive Mode
            val pasvResp = sendCommand("PASV")
            val pasvPattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")
            val matcher = pasvPattern.matcher(pasvResp.second)
            if (!matcher.find()) {
                throw Exception("Gagal mendapatkan port PASV dari PS4: ${pasvResp.second}")
            }
            val g5 = matcher.group(5) ?: throw Exception("Format PASV tidak valid")
            val g6 = matcher.group(6) ?: throw Exception("Format PASV tidak valid")
            val p1 = g5.toInt()
            val p2 = g6.toInt()
            val dataPort = (p1 shl 8) or p2

            log("Membuka koneksi data FTP port $dataPort untuk mengunggah ${file.name}...", PkgLogLevel.INFO)

            // 6. Connect Data Socket
            val dataSocket = Socket()
            dataSocket.connect(InetSocketAddress(ps4Ip, dataPort), 8000)
            dataSocket.tcpNoDelay = true
            dataSocket.sendBufferSize = 512 * 1024

            val safeName = if (file.name.endsWith(".pkg", ignoreCase = true)) file.name else "${file.name}.pkg"
            val storResp = sendCommand("STOR $safeName")
            if (storResp.first !in listOf(125, 150)) {
                dataSocket.close()
                throw Exception("Server FTP menolak penyimpanan berkas: ${storResp.second}")
            }

            // 7. Stream file
            log("Sedang mengunggah berkas ${file.name} (${file.formattedSize}) ke /data/pkg/ PS4...", PkgLogLevel.INFO)
            val inStream = context.contentResolver.openInputStream(file.uri)
                ?: throw Exception("Tidak dapat membaca berkas dari penyimpanan HP: ${file.name}")

            val dataOut: OutputStream = dataSocket.getOutputStream()
            val buffer = ByteArray(256 * 1024)
            var totalSent = 0L
            val totalSize = file.sizeBytes

            inStream.use { input ->
                dataOut.use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalSent += read
                        onProgress?.invoke(totalSent, totalSize)
                    }
                    output.flush()
                }
            }

            try { dataSocket.close() } catch (_: Exception) {}

            // 8. Read transfer complete
            val doneResp = readResponse()
            val successMsg = "✓ Berkas ${file.name} berhasil diunggah ke /data/pkg/ PS4 (${doneResp.second.trim()})! Buka menu Settings -> GoldHEN -> Package Installer di PS4 untuk menginstal."
            log(successMsg, PkgLogLevel.SUCCESS)

            try {
                sendCommand("QUIT")
                controlSocket.close()
            } catch (_: Exception) {}

            Result.success(successMsg)
        } catch (e: Exception) {
            val errMsg = "Gagal unggah via FTP ke PS4: ${e.message}"
            log(errMsg, PkgLogLevel.ERROR, e.stackTraceToString())
            Result.failure(Exception(errMsg))
        } finally {
            try { controlSocket?.close() } catch (_: Exception) {}
        }
    }
}
