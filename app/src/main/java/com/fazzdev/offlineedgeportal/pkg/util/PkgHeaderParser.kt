package com.fazzdev.offlineedgeportal.pkg.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.util.regex.Pattern

data class PkgHeaderInfo(
    val contentId: String,
    val titleId: String?,
    val digest: String,
    val contentType: String = "PS4GD"
)

/**
 * PS4 PKG Header structure:
 * - Offset 0x000 (0): 4 bytes magic (\x7FCNT = 0x7F, 0x43, 0x4E, 0x54)
 * - Offset 0x040 (64): 36 bytes content_id string (e.g. UP0001-CUSA05855_00-FINALFANTASYXV00)
 * - Offset 0xFE0 (4064): 32 bytes pkg_digest (SHA-256 binary)
 */
object PkgHeaderParser {
    private const val TAG = "PkgHeaderParser"
    private val TITLE_ID_PATTERN = Pattern.compile("([A-Z]{4}\\d{5})", Pattern.CASE_INSENSITIVE)

    fun extractFromUri(context: Context, uri: Uri, fallbackName: String): PkgHeaderInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(16384)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val read = inputStream.read(buffer, totalRead, buffer.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                if (totalRead >= 0x64) {
                    parseHeader(buffer.copyOf(totalRead), fallbackName)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading PKG header from URI: ${e.message}")
            null
        }
    }

    fun parseHeader(bytes: ByteArray, fallbackSourceName: String = ""): PkgHeaderInfo? {
        if (bytes.size < 0x64) return null

        // 1. Verify Magic \x7FCNT
        val hasMagic = bytes[0] == 0x7F.toByte() &&
                bytes[1] == 'C'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'T'.code.toByte()

        if (!hasMagic) {
            Log.d(TAG, "Not a valid PKG file header (magic mismatch)")
            return null
        }

        // 2. Extract Content ID (36 ASCII characters at offset 0x40 / 64)
        val rawContentId = String(bytes, 0x40, 36, Charsets.US_ASCII)
            .trim { it <= ' ' || it == '\u0000' }

        if (rawContentId.length < 9) return null

        // 3. Extract Title ID (e.g. CUSA05855 from UP0001-CUSA05855_00-...)
        val matcher = TITLE_ID_PATTERN.matcher(rawContentId)
        val titleId: String? = if (matcher.find()) {
            matcher.group(1)?.uppercase()
        } else {
            extractTitleIdFromFilename(fallbackSourceName)
        }

        // 4. Extract Package Digest (32 bytes at offset 0xFE0 / 4064)
        val digest = if (bytes.size >= 0x1000) {
            bytes.sliceArray(0xFE0 until 0x1000).joinToString("") { "%02X".format(it) }
        } else {
            "0000000000000000000000000000000000000000000000000000000000000000"
        }

        // 5. Category detection
        var detectedCategory: String? = null
        try {
            if (bytes.size >= 0x20) {
                val entryCount = ((bytes[0x10].toInt() and 0xFF) shl 24) or
                        ((bytes[0x11].toInt() and 0xFF) shl 16) or
                        ((bytes[0x12].toInt() and 0xFF) shl 8) or
                        (bytes[0x13].toInt() and 0xFF)
                val entryTableOffset = ((bytes[0x18].toInt() and 0xFF) shl 24) or
                        ((bytes[0x19].toInt() and 0xFF) shl 16) or
                        ((bytes[0x1A].toInt() and 0xFF) shl 8) or
                        (bytes[0x1B].toInt() and 0xFF)

                if (entryTableOffset in 0 until bytes.size && entryCount in 1..100) {
                    for (i in 0 until entryCount) {
                        val entryPos = entryTableOffset + (i * 32)
                        if (entryPos + 32 > bytes.size) break
                        val entryId = ((bytes[entryPos].toInt() and 0xFF) shl 24) or
                                ((bytes[entryPos + 1].toInt() and 0xFF) shl 16) or
                                ((bytes[entryPos + 2].toInt() and 0xFF) shl 8) or
                                (bytes[entryPos + 3].toInt() and 0xFF)
                        if (entryId == 0x00001000) { // PARAM_SFO
                            val dataOffset = ((bytes[entryPos + 16].toInt() and 0xFF) shl 24) or
                                    ((bytes[entryPos + 17].toInt() and 0xFF) shl 16) or
                                    ((bytes[entryPos + 18].toInt() and 0xFF) shl 8) or
                                    (bytes[entryPos + 19].toInt() and 0xFF)
                            val dataSize = ((bytes[entryPos + 20].toInt() and 0xFF) shl 24) or
                                    ((bytes[entryPos + 21].toInt() and 0xFF) shl 16) or
                                    ((bytes[entryPos + 22].toInt() and 0xFF) shl 8) or
                                    (bytes[entryPos + 23].toInt() and 0xFF)

                            if (dataOffset >= 0 && dataOffset + dataSize <= bytes.size && dataSize > 20) {
                                val sfoBytes = bytes.sliceArray(dataOffset until (dataOffset + dataSize))
                                detectedCategory = parseCategoryFromSfo(sfoBytes)
                            }
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val finalCategory = detectedCategory ?: if (fallbackSourceName.contains(Regex("""(?i)_00-A|-A\d{4}|patch|update""")) ||
            rawContentId.contains(Regex("""(?i)_00-A|-A\d{4}"""))
        ) "PS4GP"
        else if (fallbackSourceName.contains(Regex("""(?i)_00-C|dlc|additional""")) ||
            rawContentId.contains(Regex("""(?i)_00-C"""))
        ) "PS4AC"
        else "PS4GD"

        return PkgHeaderInfo(
            contentId = rawContentId,
            titleId = titleId,
            digest = digest,
            contentType = finalCategory
        )
    }

    private fun parseCategoryFromSfo(sfoBytes: ByteArray): String? {
        val sfoStr = String(sfoBytes, Charsets.ISO_8859_1)
        val idx = sfoStr.indexOf("CATEGORY")
        if (idx != -1 && idx + 12 < sfoBytes.size) {
            for (i in (idx + 8)..(idx + 32).coerceAtMost(sfoBytes.size - 3)) {
                val candidate = String(sfoBytes, i, 2, Charsets.US_ASCII)
                if (candidate.equals("gd", ignoreCase = true)) return "PS4GD"
                if (candidate.equals("gp", ignoreCase = true)) return "PS4GP"
                if (candidate.equals("ac", ignoreCase = true)) return "PS4AC"
            }
        }
        return null
    }

    fun extractTitleIdFromFilename(filename: String): String? {
        val matcher = TITLE_ID_PATTERN.matcher(filename)
        return if (matcher.find()) matcher.group(1)?.uppercase() else null
    }
}
