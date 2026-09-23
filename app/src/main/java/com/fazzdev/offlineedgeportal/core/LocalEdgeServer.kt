package com.fazzdev.offlineedgeportal.core

import android.content.Context
import android.webkit.MimeTypeMap
import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import com.fazzdev.offlineedgeportal.pkg.server.ContentRangeStreamer
import com.fazzdev.offlineedgeportal.pkg.server.PkgRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class RequestLog(
    val timestamp: Long,
    val clientIp: String,
    val method: String,
    val host: String,
    val uri: String,
    val isRedirected: Boolean,
    val statusCode: Int
)

class LocalEdgeServer(
    private val context: Context,
    private val port: Int = 8080,
    private val siteRootDir: () -> File,
    private val currentNativeIp: () -> String,
    private val currentMode: () -> ServerActiveMode = { ServerActiveMode.WEBKIT },
    private val onLog: (RequestLog) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private val threadPool = Executors.newFixedThreadPool(16)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (isRunning) return
        try {
            // Bind to all local interfaces on port 8080
            serverSocket = ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))
            isRunning = true

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        threadPool.execute {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
            throw e
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        serverSocket = null
    }

    private fun handleClientSocket(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        try {
            socket.soTimeout = 8000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val rawHeader = readHttpHeader(input)
            if (rawHeader.isEmpty()) {
                socket.close()
                return
            }

            val lines = rawHeader.split("\r\n")
            if (lines.isEmpty()) {
                socket.close()
                return
            }

            val requestLine = lines[0].split(" ")
            if (requestLine.size < 2) {
                socket.close()
                return
            }

            val method = requestLine[0].uppercase(Locale.ROOT)
            val fullUrlOrPath = requestLine[1]

            // Extract Host header
            var hostHeader = ""
            var rangeHeader: String? = null
            for (i in 1 until lines.size) {
                val line = lines[i]
                val lower = line.lowercase(Locale.ROOT)
                if (lower.startsWith("host:")) {
                    hostHeader = line.substring(5).trim()
                } else if (lower.startsWith("range:")) {
                    rangeHeader = line.substring(6).trim()
                }
            }

            val nativeIp = currentNativeIp()
            val lowerHost = hostHeader.lowercase(Locale.ROOT)
            val lowerUrl = fullUrlOrPath.lowercase(Locale.ROOT)

            // 1. PlayStation Connection Test (PS4 & PS5 netcheck)
            if (isPlayStationNetcheck(lowerHost, lowerUrl)) {
                serveNetcheckSuccess(output)
                onLog(
                    RequestLog(
                        timestamp = System.currentTimeMillis(),
                        clientIp = clientIp,
                        method = method,
                        host = hostHeader.ifEmpty { "netcheck.playstation.net" },
                        uri = fullUrlOrPath,
                        isRedirected = false,
                        statusCode = 200
                    )
                )
                return
            }

            // 2. PlayStation System Software Update Blocker (PS4 & PS5)
            if (isPlayStationUpdate(lowerHost, lowerUrl)) {
                serveUpdateBlocker(output)
                onLog(
                    RequestLog(
                        timestamp = System.currentTimeMillis(),
                        clientIp = clientIp,
                        method = method,
                        host = hostHeader.ifEmpty { "update.playstation.net" },
                        uri = fullUrlOrPath,
                        isRedirected = false,
                        statusCode = 200
                    )
                )
                return
            }

            // 3. Handle HTTPS CONNECT Proxy Handshake
            if (method == "CONNECT") {
                val targetHost = fullUrlOrPath.substringBefore(":")
                if (!isLocalTarget(targetHost, nativeIp)) {
                    serveConnectNotSupported(output)
                    onLog(
                        RequestLog(
                            timestamp = System.currentTimeMillis(),
                            clientIp = clientIp,
                            method = method,
                            host = hostHeader.ifEmpty { targetHost },
                            uri = fullUrlOrPath,
                            isRedirected = false,
                            statusCode = 405
                        )
                    )
                    return
                }
            }

            // 4. All-Domain Direct Serving Engine (Wildcard Localhost)
            // Semua link / domain HTTP apa pun (google.com, manuals.playstation.net, detik.com, dll.)
            // langsung di-handle dan disajikan dengan HTTP 200 OK dari website lokal tanpa redirect 302!
            val isPs = isPlayStationUserGuide(lowerHost, lowerUrl)
            handleLocalRequest(
                method = method,
                rawPath = fullUrlOrPath,
                rangeHeader = rangeHeader,
                output = output,
                clientIp = clientIp,
                nativeIp = hostHeader.ifEmpty { nativeIp },
                isPlayStation = isPs
            )
        } catch (e: Exception) {
            // Client disconnect or timeout
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun isPlayStationNetcheck(lowerHost: String, lowerUrl: String): Boolean {
        return lowerHost.contains("netcheck.playstation.net") ||
                lowerUrl.contains("netcheck.playstation.net") ||
                (lowerHost.contains("playstation.net") && lowerUrl.contains("/netcheck"))
    }

    private fun isPlayStationUpdate(lowerHost: String, lowerUrl: String): Boolean {
        return lowerHost.contains("update.playstation.net") ||
                lowerHost.contains("upgrades.net") ||
                lowerUrl.contains("update.playstation.net") ||
                lowerUrl.contains("upgrades.net") ||
                lowerUrl.contains("/update/ps4") ||
                lowerUrl.contains("/update/ps5")
    }

    private fun isPlayStationUserGuide(lowerHost: String, lowerUrl: String): Boolean {
        return lowerHost.contains("manuals.playstation.net") ||
                lowerHost.contains("doc.dl.playstation.net") ||
                lowerUrl.contains("manuals.playstation.net") ||
                lowerUrl.contains("doc.dl.playstation.net") ||
                lowerUrl.contains("/document/en/ps4") ||
                lowerUrl.contains("/document/en/ps5") ||
                lowerUrl.contains("/document/") ||
                lowerUrl.contains("/doc/ps5")
    }
    private fun isLocalTarget(target: String, nativeIp: String): Boolean {
        if (target.equals("localhost", ignoreCase = true)) return true
        if (target.equals("127.0.0.1", ignoreCase = true)) return true
        if (target.equals(nativeIp, ignoreCase = true)) return true
        if (target.equals("portal.local", ignoreCase = true)) return true
        if (target.equals("edge.local", ignoreCase = true)) return true
        if (target.equals("manuals.playstation.net", ignoreCase = true)) return true
        if (target.endsWith(".manuals.playstation.net", ignoreCase = true)) return true
        if (target.equals("doc.dl.playstation.net", ignoreCase = true)) return true
        return false
    }

    private fun handleLocalRequest(
        method: String,
        rawPath: String,
        rangeHeader: String?,
        output: BufferedOutputStream,
        clientIp: String,
        nativeIp: String,
        isPlayStation: Boolean = false
    ) {
        // Clean path and extract query params
        var cleanPath = rawPath
        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            cleanPath = "/" + cleanPath.substringAfter("://").substringAfter("/", "")
        }
        cleanPath = cleanPath.substringBefore("?")

        val mode = currentMode()

        // PAC / WPAD Script Endpoint
        if (cleanPath == "/wpad.dat" || cleanPath == "/proxy.pac") {
            servePacFile(output, nativeIp)
            onLog(
                RequestLog(
                    timestamp = System.currentTimeMillis(),
                    clientIp = clientIp,
                    method = method,
                    host = nativeIp,
                    uri = cleanPath,
                    isRedirected = false,
                    statusCode = 200
                )
            )
            return
        }

        // PlayGo JSON Manifest Endpoint: /json/{fileId}.json or /json/{fileId}
        if (cleanPath.startsWith("/json/")) {
            if (mode != ServerActiveMode.PKG_SENDER) {
                serveCustomHtml(output, 404, "PKG Mode Inactive", "Server is currently running in WebKit Exploit mode. Please switch to PS4 PKG Sender tab to enable PKG streaming.")
                return
            }
            val rawId = cleanPath.removePrefix("/json/").substringBefore("/").substringBefore("?")
            val fileId = if (rawId.endsWith(".json", ignoreCase = true)) rawId.removeSuffix(".json") else rawId
            val file = PkgRegistry.getFile(fileId)
            if (file != null) {
                servePlayGoManifest(file, nativeIp, output)
                onLog(
                    RequestLog(
                        timestamp = System.currentTimeMillis(),
                        clientIp = clientIp,
                        method = method,
                        host = nativeIp,
                        uri = cleanPath,
                        isRedirected = false,
                        statusCode = 200
                    )
                )
                return
            }
        }

        // Direct PKG Streaming Endpoint: /pkg/{fileId}/{filename} or /pkg/{fileId}
        if (cleanPath.startsWith("/pkg/")) {
            if (mode != ServerActiveMode.PKG_SENDER) {
                serveCustomHtml(output, 404, "PKG Mode Inactive", "Server is currently running in WebKit Exploit mode. Please switch to PS4 PKG Sender tab to enable PKG streaming.")
                return
            }
            val pathWithoutPrefix = cleanPath.removePrefix("/pkg/")
            val fileId = pathWithoutPrefix.substringBefore("/").substringBefore("?")
            val requestedFilename = if (pathWithoutPrefix.contains("/")) pathWithoutPrefix.substringAfter("/").substringBefore("?") else null
            val file = PkgRegistry.getFile(fileId, requestedFilename)
            if (file != null) {
                ContentRangeStreamer.streamUri(
                    contentResolver = context.contentResolver,
                    uri = file.uri,
                    totalLength = file.sizeBytes,
                    rangeHeader = rangeHeader,
                    outputStream = output,
                    isHeadRequest = method == "HEAD",
                    onProgress = { delta, currentOffset ->
                        PkgRegistry.totalBytesServed.addAndGet(delta)
                        PkgRegistry.sessionBytesServed.addAndGet(delta)
                        PkgRegistry.fileBytesServed.compute(file.id) { _, cur ->
                            val c = cur?.get() ?: 0L
                            java.util.concurrent.atomic.AtomicLong(c + delta)
                        }
                        PkgRegistry.updateOffsetProgress(file.id, currentOffset)
                        if (currentOffset >= file.sizeBytes && file.sizeBytes > 0) {
                            PkgRegistry.markFileCompleted(file.id)
                        }
                    }
                )
                onLog(
                    RequestLog(
                        timestamp = System.currentTimeMillis(),
                        clientIp = clientIp,
                        method = method,
                        host = nativeIp,
                        uri = cleanPath,
                        isRedirected = false,
                        statusCode = if (rangeHeader != null) 206 else 200
                    )
                )
                return
            }
        }

        // If running in PKG_SENDER mode, serve dedicated PKG Server status page for browser / web requests
        if (mode == ServerActiveMode.PKG_SENDER) {
            servePkgServerStatusPage(output, nativeIp)
            onLog(
                RequestLog(
                    timestamp = System.currentTimeMillis(),
                    clientIp = clientIp,
                    method = method,
                    host = nativeIp,
                    uri = cleanPath,
                    isRedirected = false,
                    statusCode = 200
                )
            )
            return
        }

        val docRoot = siteRootDir()
        val targetFile = resolveTargetFile(docRoot, cleanPath, isPlayStation)

        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
            serveStaticFile(targetFile, rangeHeader, output)
            onLog(
                RequestLog(
                    timestamp = System.currentTimeMillis(),
                    clientIp = clientIp,
                    method = method,
                    host = nativeIp,
                    uri = cleanPath,
                    isRedirected = false,
                    statusCode = if (rangeHeader != null) 206 else 200
                )
            )
        } else {
            serve404(output)
            onLog(
                RequestLog(
                    timestamp = System.currentTimeMillis(),
                    clientIp = clientIp,
                    method = method,
                    host = nativeIp,
                    uri = cleanPath,
                    isRedirected = false,
                    statusCode = 404
                )
            )
        }
    }

    private fun resolveTargetFile(docRoot: File, rawCleanPath: String, isPlayStation: Boolean): File? {
        val cleanPath = try {
            URLDecoder.decode(rawCleanPath, "UTF-8")
        } catch (e: Exception) {
            rawCleanPath
        }

        // 1. Root path requested -> return index.html or index.htm
        if (cleanPath == "/" || cleanPath.isEmpty()) {
            return findIndexFile(docRoot)
        }

        val relativePath = cleanPath.removePrefix("/")

        // 2. Direct lookup in docRoot
        val directFile = File(docRoot, relativePath)
        if (directFile.exists()) {
            if (directFile.isDirectory) {
                val indexInDir = findIndexFile(directFile)
                if (indexInDir != null) return indexInDir
            } else if (directFile.isFile) {
                return directFile
            }
        }

        // 3. Case-insensitive lookup for relativePath in docRoot (fixes Android Linux ext4 case-sensitivity issues)
        val ciDirect = findFileCaseInsensitive(docRoot, relativePath)
        if (ciDirect != null) {
            if (ciDirect.isDirectory) {
                val indexInDir = findIndexFile(ciDirect)
                if (indexInDir != null) return indexInDir
            } else if (ciDirect.isFile) {
                return ciDirect
            }
        }

        // 4. PlayStation path normalization (/document/{lang}/ps4/..., /document/{lang}/ps5/..., /doc/ps5/...)
        if (isPlayStation) {
            val subPath = when {
                cleanPath.contains("/ps4/") -> cleanPath.substringAfter("/ps4/")
                cleanPath.contains("/ps5/") -> cleanPath.substringAfter("/ps5/")
                cleanPath.contains("/document/") -> cleanPath.substringAfter("/document/")
                cleanPath.contains("/doc/") -> cleanPath.substringAfter("/doc/")
                else -> cleanPath
            }.removePrefix("/")

            if (subPath.isEmpty()) {
                return findIndexFile(docRoot)
            }

            // Check if subPath exists relative to docRoot (e.g. "subfolder/index.html", "karo.html", "goldhen/")
            val subFile = File(docRoot, subPath)
            if (subFile.exists()) {
                if (subFile.isDirectory) {
                    val indexInDir = findIndexFile(subFile)
                    if (indexInDir != null) return indexInDir
                } else if (subFile.isFile) {
                    return subFile
                }
            }

            // Case-insensitive lookup for subPath
            val ciSub = findFileCaseInsensitive(docRoot, subPath)
            if (ciSub != null) {
                if (ciSub.isDirectory) {
                    val indexInDir = findIndexFile(ciSub)
                    if (indexInDir != null) return indexInDir
                } else if (ciSub.isFile) {
                    return ciSub
                }
            }

            // If subPath is specifically requesting the main root index and nothing else
            if (subPath.equals("index.html", ignoreCase = true) || subPath.equals("index.htm", ignoreCase = true)) {
                return findIndexFile(docRoot)
            }

            // Check leaf filename directly in docRoot (flat search, e.g. "page2.html" or "exploit.js")
            val leafName = subPath.substringAfterLast("/")
            if (leafName.isNotEmpty()) {
                val flatFile = File(docRoot, leafName)
                if (flatFile.exists() && flatFile.isFile) {
                    return flatFile
                }
                val ciFlat = findFileCaseInsensitive(docRoot, leafName)
                if (ciFlat != null && ciFlat.isFile) {
                    return ciFlat
                }
                val recFile = findFileRecursively(docRoot, leafName)
                if (recFile != null && recFile.isFile) {
                    return recFile
                }
            }
        }

        // 5. Generic recursive search by leaf filename if still not found
        val leafName = relativePath.substringAfterLast("/")
        if (leafName.isNotEmpty() && !leafName.equals("index.html", ignoreCase = true) && !leafName.equals("index.htm", ignoreCase = true)) {
            val recFile = findFileRecursively(docRoot, leafName)
            if (recFile != null && recFile.isFile) {
                return recFile
            }
        }

        // 6. SPA fallback: ONLY fallback if the path has NO file extension
        // (e.g. /dashboard, /settings - typical for single-page app routers),
        // NEVER fallback for files with extensions like .html, .js, .css, .bin!
        val hasExtension = relativePath.substringAfterLast("/", "").contains(".")
        if (!hasExtension) {
            val fallbackIndex = findIndexFile(docRoot)
            if (fallbackIndex != null) {
                return fallbackIndex
            }
        }

        return null
    }

    private fun findIndexFile(dir: File): File? {
        val candidates = arrayOf("index.html", "index.htm", "Index.html", "INDEX.HTML", "INDEX.HTM")
        for (candidate in candidates) {
            val f = File(dir, candidate)
            if (f.exists() && f.isFile) return f
        }
        return dir.listFiles()?.firstOrNull {
            it.isFile && (it.name.equals("index.html", ignoreCase = true) || it.name.equals("index.htm", ignoreCase = true))
        }
    }

    private fun findFileCaseInsensitive(baseDir: File, relativePath: String): File? {
        val segments = relativePath.replace('\\', '/').split("/").filter { it.isNotEmpty() }
        var current = baseDir
        for (segment in segments) {
            if (!current.exists() || !current.isDirectory) return null
            val match = current.listFiles()?.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            current = match
        }
        return current
    }

    private fun findFileRecursively(dir: File, fileName: String): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        return try {
            dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(fileName, ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }

    private fun serveNetcheckSuccess(output: BufferedOutputStream) {
        val body = "OK\r\n"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun serveUpdateBlocker(output: BufferedOutputStream) {
        val header = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        output.write(header)
        output.flush()
    }

    private fun serveConnectNotSupported(output: BufferedOutputStream) {
        val msg = "HTTPS proxy tunneling tidak didukung dalam mode offline intranet. Silakan buka situs HTTP atau gunakan menu Panduan Pengguna (User's Guide) pada PS4/PS5.\r\n"
        val bytes = msg.toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 405 Method Not Allowed\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun servePacFile(output: BufferedOutputStream, nativeIp: String) {
        val pacScript = """
            function FindProxyForURL(url, host) {
                if (shExpMatch(host, "$nativeIp") || shExpMatch(host, "127.0.0.1") || shExpMatch(host, "localhost")) {
                    return "DIRECT";
                }
                return "PROXY $nativeIp:$port; DIRECT";
            }
        """.trimIndent()

        val bytes = pacScript.toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)

        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun servePlayGoManifest(file: PkgFile, nativeIp: String, output: BufferedOutputStream) {
        val filename = if (file.name.endsWith(".pkg", ignoreCase = true)) file.name else "${file.name}.pkg"
        val encodedName = URLDecoder.decode(filename, "UTF-8").let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val pieceUrl = "http://$nativeIp:$port/pkg/${file.id}/$encodedName"
        val digest = file.packageDigest ?: "0000000000000000000000000000000000000000000000000000000000000000"
        val manifestJson = "{\"originalFileSize\":${file.sizeBytes},\"packageDigest\":\"$digest\",\"numberOfSplitFiles\":1,\"pieces\":[{\"fileOffset\":0,\"fileSize\":${file.sizeBytes},\"url\":\"$pieceUrl\",\"hashValue\":\"0000000000000000000000000000000000000000\"}]}"
        val jsonBytes = manifestJson.toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${jsonBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII)
        output.write(header)
        output.write(jsonBytes)
        output.flush()
    }

    private fun serveStaticFile(file: File, rangeHeader: String?, output: BufferedOutputStream) {
        val totalLength = file.length()
        val mime = getMimeType(file.extension)

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // Handle HTTP 206 Partial Content for video/audio streaming
            val parts = rangeHeader.removePrefix("bytes=").split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = parts.getOrNull(1)?.toLongOrNull() ?: (totalLength - 1)
            val contentLength = (end - start + 1).coerceAtLeast(0L)

            val header = (
                "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Type: $mime\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Content-Range: bytes $start-$end/$totalLength\r\n" +
                "Content-Length: $contentLength\r\n" +
                "Connection: keep-alive\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)

            output.write(header)

            FileInputStream(file).use { fis ->
                fis.skip(start)
                copyStream(fis, output, contentLength)
            }
            output.flush()
        } else {
            // Full HTTP 200 OK
            val header = (
                "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Content-Length: $totalLength\r\n" +
                "Connection: keep-alive\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)

            output.write(header)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(16384)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
            }
            output.flush()
        }
    }

    private fun serve404(output: BufferedOutputStream) {
        val body = "<h1>404 Not Found</h1><p>Halaman atau file tidak ditemukan di server intranet.</p>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = (
            "HTTP/1.1 404 Not Found\r\n" +
            "Content-Type: text/html; charset=UTF-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        ).toByteArray(Charsets.UTF_8)

        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun copyStream(input: InputStream, output: BufferedOutputStream, limitBytes: Long) {
        val buffer = ByteArray(16384)
        var remaining = limitBytes
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readHttpHeader(input: BufferedInputStream): String {
        val sb = StringBuilder()
        var lastChar = 0
        var byteCount = 0
        val maxHeaderBytes = 16384

        while (byteCount < maxHeaderBytes) {
            val b = input.read()
            if (b == -1) break
            byteCount++
            sb.append(b.toChar())

            if (lastChar == '\n'.code && b == '\n'.code) break
            if (sb.endsWith("\r\n\r\n")) break
            lastChar = b
        }
        return sb.toString()
    }

    private fun getMimeType(extension: String): String {
        val lower = extension.lowercase(Locale.ROOT)
        return when (lower) {
            "html", "htm" -> "text/html; charset=UTF-8"
            "css" -> "text/css; charset=UTF-8"
            "js" -> "application/javascript; charset=UTF-8"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower) ?: "application/octet-stream"
        }
    }

    private fun servePkgServerStatusPage(output: BufferedOutputStream, nativeIp: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>PS4 PKG Server</title>
                <style>
                    body { background: #0a0e17; color: #e6edf3; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #161f30; border: 1px solid #1f6feb; border-radius: 16px; padding: 32px; max-width: 480px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                    h1 { color: #00d2ff; font-size: 22px; margin-bottom: 8px; }
                    p { color: #8a9ba8; font-size: 14px; line-height: 1.5; }
                    .badge { display: inline-block; background: rgba(0,255,136,0.15); color: #00ff88; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; margin-bottom: 16px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="badge">SERVER ACTIVE ($nativeIp:8080)</div>
                    <h1>PS4 PKG Sender Server</h1>
                    <p>Server is ready to stream .pkg files to your PlayStation 4 console via GoldHEN Port 9090.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveCustomHtml(output: BufferedOutputStream, statusCode: Int, statusText: String, message: String) {
        val html = """
            <!DOCTYPE html><html><body style="background:#0a0e17;color:#fff;font-family:sans-serif;padding:30px;text-align:center;">
            <h2>$statusText</h2><p style="color:#8a9ba8;">$message</p>
            </body></html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }
}
