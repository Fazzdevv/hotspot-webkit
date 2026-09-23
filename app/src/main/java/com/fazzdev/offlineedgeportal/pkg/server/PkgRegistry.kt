package com.fazzdev.offlineedgeportal.pkg.server

import com.fazzdev.offlineedgeportal.pkg.model.PkgFile
import com.fazzdev.offlineedgeportal.pkg.util.PkgIdentifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * Registry of active PKG files with Smart Fallback Matching engine from PS4PkgSender v1.2.0 Desktop.
 * Guarantees that PS4 download requests & resumes will reliably find their package without HTTP 404 errors.
 */
object PkgRegistry {

    private val registeredFiles = ConcurrentHashMap<String, PkgFile>()

    val totalBytesServed = AtomicLong(0L)
    val sessionBytesServed = AtomicLong(0L)
    val fileBytesServed = ConcurrentHashMap<String, AtomicLong>()
    val fileInitialOffset = ConcurrentHashMap<String, AtomicLong>()
    val fileMaxOffsetReached = ConcurrentHashMap<String, AtomicLong>()
    val completedFiles = ConcurrentHashMap.newKeySet<String>()
    val activeConnectionsCount = AtomicLong(0L)
    val lastDataServedTimestamp = AtomicLong(System.currentTimeMillis())

    fun registerFile(file: PkgFile) {
        registeredFiles[file.id] = file
    }

    fun removeFile(id: String) {
        registeredFiles.remove(id)
        fileBytesServed.remove(id)
        fileInitialOffset.remove(id)
        fileMaxOffsetReached.remove(id)
        completedFiles.remove(id)
    }

    fun clearFiles() {
        registeredFiles.clear()
        fileBytesServed.clear()
        fileInitialOffset.clear()
        fileMaxOffsetReached.clear()
        completedFiles.clear()
    }

    fun getAllFiles(): List<PkgFile> = registeredFiles.values.toList()

    /**
     * Smart fallback matching from PS4PkgSender v1.2.0 Desktop:
     * Matches by:
     * 1. Exact ID
     * 2. Case-insensitive ID
     * 3. Sony Package Digest (32-byte header SHA-256)
     * 4. Content ID (UP0001-...)
     * 5. Deterministic SHA-256 ID
     * 6. Filename without extension
     * 7. Requested Filename from URL (exact, without .pkg, or Title ID regex)
     * 8. Single-file auto fallback
     */
    fun getFile(id: String, requestedFilename: String? = null): PkgFile? {
        val cleanId = id.trim()
        // 1. Direct ID match
        registeredFiles[cleanId]?.let { return it }

        // 2. Case-insensitive ID match
        registeredFiles.values.firstOrNull { it.id.equals(cleanId, ignoreCase = true) }?.let { return it }

        // 3. Sony Package Digest match (32-byte header digest)
        registeredFiles.values.firstOrNull {
            !it.packageDigest.isNullOrBlank() && it.packageDigest.equals(cleanId, ignoreCase = true)
        }?.let { return it }

        // 4. Content ID match
        registeredFiles.values.firstOrNull {
            !it.contentId.isNullOrBlank() && it.contentId.equals(cleanId, ignoreCase = true)
        }?.let { return it }

        // 5. SHA-256 deterministic ID match
        registeredFiles.values.firstOrNull {
            val detId = PkgIdentifier.generateDeterministicId(it)
            detId.equals(cleanId, ignoreCase = true)
        }?.let { return it }

        // 6. Filename match without extension
        val idName = cleanId.substringBeforeLast(".pkg").substringBeforeLast(".json")
        registeredFiles.values.firstOrNull {
            it.name.substringBeforeLast(".pkg").equals(idName, ignoreCase = true)
        }?.let { return it }

        // 7. Match against requestedFilename from the URL
        if (!requestedFilename.isNullOrBlank()) {
            val cleanReqName = requestedFilename.trim()
            registeredFiles.values.firstOrNull {
                it.name.equals(cleanReqName, ignoreCase = true)
            }?.let {
                registeredFiles[cleanId] = it
                return it
            }

            val reqBase = cleanReqName.substringBeforeLast(".pkg")
            registeredFiles.values.firstOrNull {
                it.name.substringBeforeLast(".pkg").equals(reqBase, ignoreCase = true)
            }?.let {
                registeredFiles[cleanId] = it
                return it
            }

            val titlePattern = Pattern.compile("(CUSA\\d{5})", Pattern.CASE_INSENSITIVE)
            val matcher = titlePattern.matcher(cleanReqName)
            if (matcher.find()) {
                val titleId = matcher.group(1)?.uppercase() ?: ""
                registeredFiles.values.firstOrNull {
                    it.titleId.equals(titleId, ignoreCase = true) || it.name.contains(titleId, ignoreCase = true)
                }?.let {
                    registeredFiles[cleanId] = it
                    return it
                }
            }
        }

        // 8. If only 1 file registered in server, automatically map and serve it
        if (registeredFiles.size == 1) {
            val single = registeredFiles.values.first()
            registeredFiles[cleanId] = single
            return single
        }

        return null
    }

    fun updateOffsetProgress(fileId: String, currentOffset: Long) {
        fileMaxOffsetReached.compute(fileId) { _, cur ->
            val c = cur?.get() ?: 0L
            if (currentOffset > c) AtomicLong(currentOffset) else cur ?: AtomicLong(currentOffset)
        }
    }

    fun isFileCompleted(fileId: String): Boolean = completedFiles.contains(fileId)

    fun markFileCompleted(fileId: String) {
        completedFiles.add(fileId)
    }

    fun getTransferredBytesForFile(fileId: String): Long {
        val maxOffset = fileMaxOffsetReached[fileId]?.get() ?: 0L
        val initial = fileInitialOffset[fileId]?.get() ?: 0L
        val streamed = fileBytesServed[fileId]?.get() ?: 0L
        return maxOf(maxOffset, initial + streamed)
    }

    fun resetStats() {
        totalBytesServed.set(0L)
        sessionBytesServed.set(0L)
        fileBytesServed.clear()
        fileInitialOffset.clear()
        fileMaxOffsetReached.clear()
        completedFiles.clear()
        lastDataServedTimestamp.set(System.currentTimeMillis())
    }

    fun resetSession() {
        sessionBytesServed.set(0L)
        fileBytesServed.clear()
        fileInitialOffset.clear()
        fileMaxOffsetReached.clear()
        completedFiles.clear()
        lastDataServedTimestamp.set(System.currentTimeMillis())
    }
}
