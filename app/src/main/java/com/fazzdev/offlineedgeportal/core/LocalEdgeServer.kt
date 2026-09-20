package com.fazzdev.offlineedgeportal.core

import android.webkit.MimeTypeMap
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
    private val port: Int = 8080,
    private val siteRootDir: () -> File,
    private val currentNativeIp: () -> String,
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

            // Determine if request is targeting external site (Proxy Interceptor)
            val isProxyIntercept = isExternalProxyRequest(method, fullUrlOrPath, hostHeader, nativeIp)

            if (isProxyIntercept) {
                // REDIRECT EXTERNAL REQUEST TO LOCAL PORTAL
                val redirectLocation = "http://$nativeIp:$port/"
                val response = (
                    "HTTP/1.1 302 Found\r\n" +
                    "Location: $redirectLocation\r\n" +
                    "Connection: close\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: 0\r\n\r\n"
                ).toByteArray(Charsets.UTF_8)

                output.write(response)
                output.flush()

                onLog(
                    RequestLog(
                        timestamp = System.currentTimeMillis(),
                        clientIp = clientIp,
                        method = method,
                        host = hostHeader.ifEmpty { fullUrlOrPath },
                        uri = fullUrlOrPath,
                        isRedirected = true,
                        statusCode = 302
                    )
                )
            } else {
                // SERVE LOCAL CONTENT OR PAC SCRIPT
                handleLocalRequest(method, fullUrlOrPath, rangeHeader, output, clientIp, nativeIp)
            }
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

    private fun isExternalProxyRequest(
        method: String,
        urlOrPath: String,
        hostHeader: String,
        nativeIp: String
    ): Boolean {
        // HTTPS CONNECT request for non-local domain
        if (method == "CONNECT") {
            val targetHost = urlOrPath.substringBefore(":")
            return !isLocalTarget(targetHost, nativeIp)
        }

        // Full URL starting with http://
        if (urlOrPath.startsWith("http://", ignoreCase = true) || urlOrPath.startsWith("https://", ignoreCase = true)) {
            val domain = urlOrPath.substringAfter("://").substringBefore("/").substringBefore(":")
            return !isLocalTarget(domain, nativeIp)
        }

        // Host header check
        if (hostHeader.isNotEmpty()) {
            val domain = hostHeader.substringBefore(":")
            return !isLocalTarget(domain, nativeIp)
        }

        return false
    }

    private fun isLocalTarget(target: String, nativeIp: String): Boolean {
        if (target.equals("localhost", ignoreCase = true)) return true
        if (target.equals("127.0.0.1", ignoreCase = true)) return true
        if (target.equals(nativeIp, ignoreCase = true)) return true
        if (target.equals("portal.local", ignoreCase = true)) return true
        if (target.equals("edge.local", ignoreCase = true)) return true
        return false
    }

    private fun handleLocalRequest(
        method: String,
        rawPath: String,
        rangeHeader: String?,
        output: BufferedOutputStream,
        clientIp: String,
        nativeIp: String
    ) {
        // Clean path and extract query params
        var cleanPath = rawPath
        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            cleanPath = "/" + cleanPath.substringAfter("://").substringAfter("/", "")
        }
        cleanPath = cleanPath.substringBefore("?")

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

        val docRoot = siteRootDir()
        var targetFile = if (cleanPath == "/" || cleanPath.isEmpty()) {
            File(docRoot, "index.html")
        } else {
            File(docRoot, cleanPath.removePrefix("/"))
        }

        // Single Page App (SPA) fallback: If file doesn't exist and has no extension, fallback to index.html
        if (!targetFile.exists() || targetFile.isDirectory) {
            val fallbackIndex = File(docRoot, "index.html")
            if (fallbackIndex.exists()) {
                targetFile = fallbackIndex
            }
        }

        if (targetFile.exists() && targetFile.isFile) {
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
}
