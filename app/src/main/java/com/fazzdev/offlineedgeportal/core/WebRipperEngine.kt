package com.fazzdev.offlineedgeportal.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class RipProgress(
    val isRunning: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val statusMessage: String = "",
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

object WebRipperEngine {

    private const val TAG = "WebRipperEngine"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun ripWebsite(
        context: Context,
        rawUrl: String,
        onProgress: (RipProgress) -> Unit
    ): Result<SitePackageInfo> = withContext(Dispatchers.IO) {
        try {
            var targetUrl = rawUrl.trim()
            if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                targetUrl = "https://$targetUrl"
            }

            onProgress(
                RipProgress(
                    isRunning = true,
                    statusMessage = "Menghubungkan ke $targetUrl..."
                )
            )

            // 1. Fetch main HTML document
            val htmlRequest = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val htmlResponse = httpClient.newCall(htmlRequest).execute()
            if (!htmlResponse.isSuccessful || htmlResponse.body == null) {
                val err = "Gagal memuat halaman: HTTP ${htmlResponse.code}"
                onProgress(RipProgress(errorMessage = err))
                return@withContext Result.failure(Exception(err))
            }

            val htmlContent = htmlResponse.body!!.string()
            val finalUrl = htmlResponse.request.url.toString()
            val doc = Jsoup.parse(htmlContent, finalUrl)

            val siteDir = ZipPackageManager.getSiteDirectory(context)
            siteDir.deleteRecursively()
            siteDir.mkdirs()

            // 2. Discover static assets (scripts, styles, images, icons)
            val assetUrls = mutableSetOf<String>()

            // Scripts
            doc.select("script[src]").forEach { el ->
                val abs = el.attr("abs:src")
                if (abs.isNotBlank() && isHttpUrl(abs)) assetUrls.add(abs)
            }

            // Stylesheets & Preloads
            doc.select("link[href]").forEach { el ->
                val rel = el.attr("rel").lowercase()
                if (rel.contains("stylesheet") || rel.contains("icon") || rel.contains("preload")) {
                    val abs = el.attr("abs:href")
                    if (abs.isNotBlank() && isHttpUrl(abs)) assetUrls.add(abs)
                }
            }

            // Images & Media
            doc.select("img[src], source[src]").forEach { el ->
                val abs = el.attr("abs:src")
                if (abs.isNotBlank() && isHttpUrl(abs)) assetUrls.add(abs)
            }

            val totalAssets = assetUrls.size
            onProgress(
                RipProgress(
                    isRunning = true,
                    totalCount = totalAssets,
                    statusMessage = "Ditemukan $totalAssets aset statis. Mengunduh..."
                )
            )

            // 3. Download assets and store locally
            val urlToLocalPathMap = mutableMapOf<String, String>()
            var downloaded = 0

            val baseUri = URI(finalUrl)
            for (assetUrl in assetUrls) {
                downloaded++
                val filename = sanitizeFilename(assetUrl)
                val subPath = calculateSubPath(assetUrl, filename)

                val destFile = File(siteDir, subPath)
                destFile.parentFile?.mkdirs()

                onProgress(
                    RipProgress(
                        isRunning = true,
                        currentCount = downloaded,
                        totalCount = totalAssets,
                        statusMessage = "Mengunduh: $filename ($downloaded/$totalAssets)"
                    )
                )

                try {
                    val assetReq = Request.Builder()
                        .url(assetUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", finalUrl)
                        .build()

                    httpClient.newCall(assetReq).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            FileOutputStream(destFile).use { fos ->
                                resp.body!!.byteStream().copyTo(fos)
                            }
                            urlToLocalPathMap[assetUrl] = subPath

                            // If CSS, check for internal url(...) like webfonts
                            if (subPath.endsWith(".css", ignoreCase = true)) {
                                downloadCssEmbeddedUrls(destFile, assetUrl, siteDir)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Optional asset failed: $assetUrl (${e.message})")
                }
            }

            // 4. Rewrite URLs in HTML document
            doc.select("script[src]").forEach { el ->
                val abs = el.attr("abs:src")
                urlToLocalPathMap[abs]?.let { el.attr("src", it) }
            }
            doc.select("link[href]").forEach { el ->
                val abs = el.attr("abs:href")
                urlToLocalPathMap[abs]?.let { el.attr("href", it) }
            }
            doc.select("img[src]").forEach { el ->
                val abs = el.attr("abs:src")
                urlToLocalPathMap[abs]?.let { el.attr("src", it) }
            }

            // Save index.html
            val indexFile = File(siteDir, "index.html")
            indexFile.writeText(doc.outerHtml(), Charsets.UTF_8)

            val pageTitle = doc.title().ifBlank { baseUri.host ?: "Cloned Website" }
            val packageInfo = ZipPackageManager.inspectPackage(siteDir, "Web: $pageTitle")

            onProgress(
                RipProgress(
                    isRunning = false,
                    isSuccess = true,
                    totalCount = totalAssets,
                    currentCount = downloaded,
                    statusMessage = "Berhasil dikloning! ${packageInfo.totalFiles} berkas siap di-host."
                )
            )

            Result.success(packageInfo)
        } catch (e: Exception) {
            val err = "Gagal mengkloning website: ${e.message}"
            onProgress(RipProgress(errorMessage = err))
            Result.failure(e)
        }
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    private fun sanitizeFilename(urlStr: String): String {
        val path = try {
            URL(urlStr).path.substringAfterLast("/")
        } catch (_: Exception) {
            ""
        }
        val clean = path.substringBefore("?").substringBefore("#")
        return if (clean.isNotBlank()) clean else "asset_${Math.abs(urlStr.hashCode())}"
    }

    private fun calculateSubPath(assetUrl: String, filename: String): String {
        return try {
            val assetUri = URI(assetUrl)
            val path = assetUri.path.trimStart('/')
            if (path.isNotBlank()) {
                path.substringBefore("?").substringBefore("#")
            } else {
                "assets/$filename"
            }
        } catch (_: Exception) {
            "assets/$filename"
        }
    }

    private fun downloadCssEmbeddedUrls(cssFile: File, cssUrl: String, siteDir: File) {
        try {
            val content = cssFile.readText(Charsets.UTF_8)
            val pattern = Pattern.compile("""url\(\s*['"]?([^'")]+)['"]?\s*\)""")
            val matcher = pattern.matcher(content)
            val cssUri = URI(cssUrl)

            while (matcher.find()) {
                val relUrl = matcher.group(1) ?: continue
                if (relUrl.startsWith("data:", ignoreCase = true)) continue
                val resolvedUrl = try {
                    cssUri.resolve(relUrl).toString()
                } catch (_: Exception) {
                    null
                } ?: continue

                try {
                    val subPath = calculateSubPath(resolvedUrl, sanitizeFilename(resolvedUrl))
                    val dest = File(siteDir, subPath)
                    dest.parentFile?.mkdirs()
                    val req = Request.Builder().url(resolvedUrl).header("User-Agent", USER_AGENT).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            FileOutputStream(dest).use { fos ->
                                resp.body!!.byteStream().copyTo(fos)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
