package com.fazzdev.offlineedgeportal.pkg.server

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel

/**
 * High-performance HTTP Range streamer (RFC 7233) for 40GB+ PKG files.
 * Streams content directly from Android SAF ParcelFileDescriptor using 256 KB direct byte buffers
 * and FileChannel position indexing without loading large files into RAM.
 */
object ContentRangeStreamer {

    private const val TAG = "ContentRangeStreamer"
    private const val BUFFER_SIZE = 512 * 1024 // 512 KB buffer for high-throughput PS4 transfers

    data class Range(val start: Long, val end: Long, val total: Long) {
        val length: Long get() = end - start + 1
    }

    fun parseRangeHeader(rangeHeader: String?, totalLength: Long): Range? {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
            return null
        }
        try {
            val spec = rangeHeader.removePrefix("bytes=").trim().substringBefore(",")
            val dashIndex = spec.indexOf('-')
            if (dashIndex == -1) return null

            val startStr = spec.substring(0, dashIndex).trim()
            val endStr = spec.substring(dashIndex + 1).trim()

            var start: Long
            var end: Long

            if (startStr.isEmpty()) {
                val suffix = endStr.toLongOrNull() ?: return null
                start = (totalLength - suffix).coerceAtLeast(0L)
                end = totalLength - 1
            } else if (endStr.isEmpty()) {
                start = startStr.toLongOrNull() ?: return null
                end = totalLength - 1
            } else {
                start = startStr.toLongOrNull() ?: return null
                end = endStr.toLongOrNull() ?: return null
            }

            if (start > end || start >= totalLength || start < 0) {
                return null
            }

            end = end.coerceAtMost(totalLength - 1)
            return Range(start, end, totalLength)
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing range: '$rangeHeader'", e)
            return null
        }
    }

    fun streamUri(
        contentResolver: ContentResolver,
        uri: Uri,
        totalLength: Long,
        rangeHeader: String?,
        outputStream: OutputStream,
        isHeadRequest: Boolean = false,
        onProgress: ((bytesSentDelta: Long, currentOffset: Long) -> Unit)? = null
    ) {
        val pfd = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open ParcelFileDescriptor for $uri: ${e.message}")
            writeError(outputStream, 404, "File Not Found")
            return
        }

        if (pfd == null) {
            writeError(outputStream, 404, "Cannot Open Descriptor")
            return
        }

        try {
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel = fis.channel
                val actualTotal = if (totalLength > 0) totalLength else channel.size()
                val range = parseRangeHeader(rangeHeader, actualTotal)

                if (range != null) {
                    val header = (
                        "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Content-Range: bytes ${range.start}-${range.end}/$actualTotal\r\n" +
                        "Content-Length: ${range.length}\r\n" +
                        "Connection: keep-alive\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)

                    outputStream.write(header)
                    outputStream.flush()

                    if (!isHeadRequest) {
                        channel.position(range.start)
                        val outChannel: WritableByteChannel = Channels.newChannel(outputStream)
                        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
                        var remaining = range.length
                        var currentOffset = range.start

                        while (remaining > 0) {
                            val toRead = remaining.coerceAtMost(BUFFER_SIZE.toLong()).toInt()
                            buffer.limit(toRead)
                            val read = channel.read(buffer)
                            if (read <= 0) break

                            buffer.flip()
                            while (buffer.hasRemaining()) {
                                outChannel.write(buffer)
                            }
                            buffer.clear()

                            remaining -= read
                            currentOffset += read
                            onProgress?.invoke(read.toLong(), currentOffset)
                        }
                    }
                } else {
                    val header = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Content-Length: $actualTotal\r\n" +
                        "Connection: keep-alive\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII)

                    outputStream.write(header)
                    outputStream.flush()

                    if (!isHeadRequest) {
                        channel.position(0)
                        val outChannel: WritableByteChannel = Channels.newChannel(outputStream)
                        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
                        var currentOffset = 0L

                        while (true) {
                            val read = channel.read(buffer)
                            if (read <= 0) break

                            buffer.flip()
                            while (buffer.hasRemaining()) {
                                outChannel.write(buffer)
                            }
                            buffer.clear()

                            currentOffset += read
                            onProgress?.invoke(read.toLong(), currentOffset)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Client interrupted or closed socket
            Log.d(TAG, "Stream transfer ended: ${e.message}")
        } finally {
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    private fun writeError(output: OutputStream, code: Int, msg: String) {
        try {
            val resp = "HTTP/1.1 $code $msg\r\nContent-Type: text/plain\r\nContent-Length: ${msg.length}\r\n\r\n$msg"
            output.write(resp.toByteArray(Charsets.US_ASCII))
            output.flush()
        } catch (_: Exception) {}
    }
}
