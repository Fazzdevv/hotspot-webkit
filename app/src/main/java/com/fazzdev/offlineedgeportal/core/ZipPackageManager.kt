package com.fazzdev.offlineedgeportal.core

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class SitePackageInfo(
    val title: String,
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val hasIndexHtml: Boolean,
    val documentRootDir: File,
    val lastUpdated: Long
)

object ZipPackageManager {

    private const val HOSTED_DIR_NAME = "hosted_site"

    fun getSiteDirectory(context: Context): File {
        val dir = File(context.filesDir, HOSTED_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Extracts a ZIP archive from an Android Content Uri with Zip-Slip attack prevention.
     */
    fun importZipFromUri(context: Context, zipUri: Uri, originalFileName: String?): Result<SitePackageInfo> {
        return try {
            val inputStream = context.contentResolver.openInputStream(zipUri)
                ?: return Result.failure(Exception("Gagal membuka file ZIP dari storage."))

            val siteDir = getSiteDirectory(context)
            siteDir.deleteRecursively()
            siteDir.mkdirs()

            extractZipStream(inputStream, siteDir)

            val packageInfo = inspectPackage(siteDir, originalFileName ?: "Imported Web Package")
            Result.success(packageInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decompresses ZIP input stream securely.
     */
    private fun extractZipStream(inputStream: InputStream, targetDir: File) {
        val canonicalDestDirPath = targetDir.canonicalPath
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                val canonicalNewFilePath = newFile.canonicalPath

                // Zip Slip vulnerability guard
                if (!canonicalNewFilePath.startsWith(canonicalDestDirPath + File.separator) &&
                    canonicalNewFilePath != canonicalDestDirPath
                ) {
                    throw SecurityException("File ZIP tidak aman: mendeteksi path traversal (${entry.name})")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        BufferedOutputStream(fos).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Analyzes the extracted folder. If the ZIP contained a single enclosing directory,
     * adjusts the document root to point directly inside that directory.
     */
    fun inspectPackage(siteDir: File, customTitle: String = "Website Aktif"): SitePackageInfo {
        if (!siteDir.exists() || siteDir.listFiles().isNullOrEmpty()) {
            // Provide default demo site
            createDefaultDemoSite(siteDir)
        }

        var docRoot = siteDir
        val children = siteDir.listFiles() ?: emptyArray()

        // If user zipped a single folder (e.g. site/index.html instead of index.html in root)
        if (children.size == 1 && children[0].isDirectory) {
            val nestedIndex = File(children[0], "index.html")
            if (nestedIndex.exists()) {
                docRoot = children[0]
            }
        }

        var fileCount = 0
        var totalBytes = 0L
        docRoot.walkTopDown().forEach { file ->
            if (file.isFile) {
                fileCount++
                totalBytes += file.length()
            }
        }

        val hasIndex = File(docRoot, "index.html").exists()

        return SitePackageInfo(
            title = customTitle,
            totalFiles = fileCount,
            totalSizeBytes = totalBytes,
            hasIndexHtml = hasIndex,
            documentRootDir = docRoot,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Creates an aesthetic default offline portal if no ZIP is imported yet.
     */
    fun createDefaultDemoSite(targetDir: File) {
        targetDir.mkdirs()
        val indexHtml = File(targetDir, "index.html")
        if (!indexHtml.exists()) {
            val defaultHtml = """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Offline Intranet Portal</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            background: radial-gradient(circle at top right, #1e293b, #0f172a);
                            color: #f8fafc;
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 20px;
                        }
                        .card {
                            background: rgba(30, 41, 59, 0.8);
                            backdrop-filter: blur(16px);
                            border: 1px solid rgba(255, 255, 255, 0.1);
                            border-radius: 20px;
                            padding: 40px;
                            max-width: 580px;
                            width: 100%;
                            box-shadow: 0 20px 40px rgba(0,0,0,0.5);
                            text-align: center;
                        }
                        .badge {
                            display: inline-block;
                            background: rgba(0, 229, 255, 0.15);
                            color: #00e5ff;
                            padding: 6px 16px;
                            border-radius: 999px;
                            font-size: 13px;
                            font-weight: 600;
                            letter-spacing: 1px;
                            margin-bottom: 20px;
                            border: 1px solid rgba(0, 229, 255, 0.3);
                        }
                        h1 { font-size: 28px; margin-bottom: 12px; color: #ffffff; }
                        p { font-size: 15px; color: #94a3b8; line-height: 1.6; margin-bottom: 24px; }
                        .info-box {
                            background: rgba(15, 23, 42, 0.6);
                            border: 1px dashed rgba(255,255,255,0.15);
                            border-radius: 12px;
                            padding: 16px;
                            text-align: left;
                            font-size: 14px;
                            margin-bottom: 24px;
                        }
                        .info-row { display: flex; justify-content: space-between; margin-bottom: 8px; }
                        .info-row:last-child { margin-bottom: 0; }
                        .info-label { color: #64748b; }
                        .info-val { color: #38bdf8; font-family: monospace; font-weight: bold; }
                        .status-dot {
                            width: 10px; height: 10px; background: #10b981; border-radius: 50%;
                            display: inline-block; margin-right: 6px; box-shadow: 0 0 10px #10b981;
                        }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="badge">OFFLINE EDGE PORTAL ACTIVE</div>
                        <h1>Selamat Datang di Jaringan Lokal</h1>
                        <p>Anda telah berhasil terhubung ke server intranet lokal Android. Seluruh akses internet dialihkan secara aman ke portal lokal ini.</p>
                        <div class="info-box">
                            <div class="info-row">
                                <span class="info-label">Status Server:</span>
                                <span class="info-val"><span class="status-dot"></span>Terhubung (Offline)</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">Mode Interceptor:</span>
                                <span class="info-val">Proxy Port 8080</span>
                            </div>
                            <div class="info-row">
                                <span class="info-label">File Sumber:</span>
                                <span class="info-val">Default Portal (Silakan Import .ZIP)</span>
                            </div>
                        </div>
                        <p style="font-size: 13px; color: #64748b; margin-bottom: 0;">
                            Administrator dapat mengimpor file <b>.zip</b> kustom (HTML/CSS/JS) melalui aplikasi Android untuk mengganti tampilan ini.
                        </p>
                    </div>
                </body>
                </html>
            """.trimIndent()
            indexHtml.writeText(defaultHtml)
        }
    }
}
