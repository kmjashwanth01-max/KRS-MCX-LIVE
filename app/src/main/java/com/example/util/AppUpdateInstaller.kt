package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class ApkDownloadState {
    data object Idle : ApkDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedMb: String, val totalMb: String) : ApkDownloadState()
    data class ReadyToInstall(val apkFile: File, val versionName: String) : ApkDownloadState()
    data class OpenedBrowserFallback(val url: String) : ApkDownloadState()
    data class Error(val message: String) : ApkDownloadState()
}

/**
 * Handles in-app OTA APK download & Android Package Installer launch for Cloud Database updates.
 * Automatically normalizes Google Drive / Dropbox share links into direct download endpoints,
 * streams download progress via StateFlow, verifies APK ZIP headers, and triggers FileProvider install.
 */
object AppUpdateInstaller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _downloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val downloadState: StateFlow<ApkDownloadState> = _downloadState.asStateFlow()

    fun resetState() {
        _downloadState.value = ApkDownloadState.Idle
    }

    /**
     * Converts common file-sharing links (Google Drive, Dropbox) into direct-download URLs
     * so Admin can paste regular sharing links into Firebase Console and still deliver an APK.
     */
    fun normalizeDirectApkUrl(rawUrl: String): String {
        val clean = rawUrl.trim()
        if (clean.isBlank()) return ""

        // Convert Google Drive sharing link: https://drive.google.com/file/d/<FILE_ID>/view?usp=sharing
        val driveRegex = Regex("https?://drive\\.google\\.com/file/d/([^/?#]+)")
        val driveMatch = driveRegex.find(clean)
        if (driveMatch != null) {
            val fileId = driveMatch.groupValues[1]
            return "https://drive.google.com/uc?export=download&confirm=t&id=$fileId"
        }

        // Convert Dropbox ?dl=0 to ?dl=1
        if (clean.contains("dropbox.com", ignoreCase = true) && clean.contains("dl=0")) {
            return clean.replace("dl=0", "dl=1")
        }

        return clean
    }

    fun startUpdateDownloadAndInstall(
        context: Context,
        rawUrl: String,
        versionName: String
    ) {
        val normalizedUrl = normalizeDirectApkUrl(rawUrl)
        if (normalizedUrl.isBlank() ||
            (!normalizedUrl.startsWith("https://", ignoreCase = true) &&
                !normalizedUrl.startsWith("http://", ignoreCase = true))
        ) {
            _downloadState.value = ApkDownloadState.Error("Invalid update URL. Must start with https:// or http://")
            return
        }

        // If clearly a web release listing rather than a binary URL, open cleanly in browser
        val lower = normalizedUrl.lowercase()
        val looksLikeReleasePage = lower.contains("github.com/") && lower.contains("/releases/tag/")
        if (looksLikeReleasePage) {
            openUrlInBrowser(context, normalizedUrl)
            _downloadState.value = ApkDownloadState.OpenedBrowserFallback(normalizedUrl)
            return
        }

        if (_downloadState.value is ApkDownloadState.Downloading) return

        val appCtx = context.applicationContext
        _downloadState.value = ApkDownloadState.Downloading(0, "0.0 MB", "...")

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(normalizedUrl)
                    .header("User-Agent", "KRS-Bullion-Updater/1.0")
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _downloadState.value = ApkDownloadState.Error("Server returned HTTP ${response.code}. Opening link in browser...")
                        openUrlInBrowser(appCtx, rawUrl.trim())
                        return@launch
                    }

                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val body = response.body
                    if (body == null) {
                        _downloadState.value = ApkDownloadState.Error("Empty response from update server.")
                        return@launch
                    }

                    // If the server served an HTML page instead of binary APK (e.g., Play Store / GitHub release page)
                    if (contentType.contains("text/html")) {
                        openUrlInBrowser(appCtx, rawUrl.trim())
                        _downloadState.value = ApkDownloadState.OpenedBrowserFallback(rawUrl.trim())
                        return@launch
                    }

                    val totalBytes = body.contentLength()
                    val updatesDir = File(appCtx.cacheDir, "app_updates").apply { mkdirs() }
                    val safeVer = versionName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                    val targetFile = File(updatesDir, "KRS_Gold_Silver_v$safeVer.apk")

                    var downloaded = 0L
                    val buffer = ByteArray(16 * 1024)
                    var firstTwoBytesRead = 0
                    var magic0: Byte = 0
                    var magic1: Byte = 0

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { out ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (firstTwoBytesRead == 0 && read >= 1) {
                                    magic0 = buffer[0]
                                    if (read >= 2) {
                                        magic1 = buffer[1]
                                        firstTwoBytesRead = 2
                                    } else {
                                        firstTwoBytesRead = 1
                                    }
                                } else if (firstTwoBytesRead == 1 && read >= 1) {
                                    magic1 = buffer[0]
                                    firstTwoBytesRead = 2
                                }
                                out.write(buffer, 0, read)
                                downloaded += read

                                val percent = if (totalBytes > 0) {
                                    ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    50
                                }
                                val dlMb = String.format("%.1f MB", downloaded / (1024.0 * 1024.0))
                                val totMb = if (totalBytes > 0) {
                                    String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))
                                } else {
                                    "APK"
                                }
                                _downloadState.value = ApkDownloadState.Downloading(percent, dlMb, totMb)
                            }
                        }
                    }

                    // Verify APK ZIP magic header ('P','K') so an HTML warning page isn't sent to PackageInstaller
                    val isApkZip = (firstTwoBytesRead >= 2 && magic0 == 0x50.toByte() && magic1 == 0x4B.toByte())
                    if (!isApkZip || downloaded < 50_000L) {
                        targetFile.delete()
                        openUrlInBrowser(appCtx, rawUrl.trim())
                        _downloadState.value = ApkDownloadState.OpenedBrowserFallback(rawUrl.trim())
                        return@launch
                    }

                    _downloadState.value = ApkDownloadState.ReadyToInstall(targetFile, versionName)
                    launchApkInstaller(appCtx, targetFile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _downloadState.value = ApkDownloadState.Error(
                    t.localizedMessage ?: "Download failed. Check internet connection."
                )
            }
        }
    }

    private fun showToastMain(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                showToastMain(context, "Enable 'Allow from this source' and then tap Install Update.")
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (t: Throwable) {
            showToastMain(
                context,
                "Unable to open installer: ${t.localizedMessage ?: "Check Downloads"}"
            )
        }
    }

    fun openUrlInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {}
    }
}
