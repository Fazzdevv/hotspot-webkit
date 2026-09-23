package com.fazzdev.offlineedgeportal.pkg.util

import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import java.security.MessageDigest

/**
 * Generates deterministic, reproducible IDs for PKG files based on SHA-256 / Sony Package Digest.
 * Ported from PS4PkgSender v1.2.0 Desktop to ensure PS4 can resume downloads without 404 errors.
 */
object PkgIdentifier {

    fun generateDeterministicId(
        name: String,
        sizeBytes: Long,
        contentId: String? = null,
        titleId: String? = null,
        packageDigest: String? = null
    ): String {
        // 1. Sony Header Package Digest (32-byte SHA-256 from header offset 0xFE0)
        if (!packageDigest.isNullOrBlank() && isValidDigest(packageDigest)) {
            return packageDigest.lowercase().trim()
        }

        // 2. Content ID + Title ID + Size
        if (!contentId.isNullOrBlank() && sizeBytes > 0) {
            val key = "${contentId.trim()}_${titleId?.trim() ?: ""}_$sizeBytes"
            return sha256(key)
        }

        // 3. Clean filename + sizeBytes
        val cleanName = name.trim().lowercase()
        if (cleanName.isNotBlank() && sizeBytes > 0) {
            return sha256("${cleanName}_$sizeBytes")
        }

        return sha256("${name.trim()}_$sizeBytes")
    }

    fun generateDeterministicId(pkg: PkgFile): String {
        return generateDeterministicId(
            name = pkg.name,
            sizeBytes = pkg.sizeBytes,
            contentId = pkg.contentId,
            titleId = pkg.titleId,
            packageDigest = pkg.packageDigest
        )
    }

    fun isValidDigest(digest: String): Boolean {
        val trimmed = digest.trim()
        if (trimmed.length != 64) return false
        if (trimmed.all { it == '0' }) return false
        return trimmed.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
