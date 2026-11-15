package com.example.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility to reliably export, share, or save the actual .apk file directly on device.
 * Ensures proper ClipData and explicit URI permissions are passed so Gmail, WhatsApp,
 * and Drive receive the real binary .apk attachment instead of text-only fallback.
 */
object ApkExportHelper {

    const val APK_FILE_NAME = "KRS_Gold_Silver_Live_MCX_v1.0.apk"

    /**
     * Obtains the actual installed APK file on this device.
     */
    fun getInstalledApkFile(context: Context): File? {
        val apkPath = context.applicationInfo.sourceDir ?: return null
        val file = File(apkPath)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Prepares a clean copy of the APK in internal cache/files for sharing via FileProvider.
     */
    fun prepareShareableApk(context: Context): Pair<File, Uri>? {
        val sourceApk = getInstalledApkFile(context) ?: return null
        val exportDir = File(context.cacheDir, "exported_apk").apply { mkdirs() }
        val targetApk = File(exportDir, APK_FILE_NAME)
        sourceApk.copyTo(targetApk, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", targetApk)
        return Pair(targetApk, uri)
    }

    /**
     * Saves the APK directly to the device's public Downloads directory.
     * Accessible immediately from the Android Files / Downloads app.
     */
    fun saveApkToDownloads(context: Context): Boolean {
        try {
            val sourceApk = getInstalledApkFile(context)
            if (sourceApk == null) {
                Toast.makeText(context, "Could not access APK package", Toast.LENGTH_SHORT).show()
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, APK_FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceApk.inputStream().use { input -> input.copyTo(out) }
                    }
                    Toast.makeText(
                        context,
                        "✅ Saved '$APK_FILE_NAME' to Downloads folder! Check Files app.",
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
            }

            // Fallback for pre-Android 10
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                downloadsDir.mkdirs()
                val dest = File(downloadsDir, APK_FILE_NAME)
                sourceApk.copyTo(dest, overwrite = true)
                Toast.makeText(
                    context,
                    "✅ Saved to Downloads: ${dest.name}",
                    Toast.LENGTH_LONG
                ).show()
                return true
            }

            Toast.makeText(context, "Downloads directory unavailable", Toast.LENGTH_SHORT).show()
            return false
        } catch (e: Exception) {
            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    /**
     * Shares the APK file with proper ClipData and explicit package URI permissions
     * so that receiving apps (Gmail, WhatsApp, Drive, Telegram) attach the actual binary .apk file.
     */
    fun shareApkFile(context: Context) {
        try {
            val pair = prepareShareableApk(context)
            if (pair == null) {
                Toast.makeText(context, "APK file not found on device", Toast.LENGTH_SHORT).show()
                return
            }
            val (_, uri) = pair

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KRS Gold & Silver Live MCX APK v1.0")
                clipData = ClipData.newRawUri(APK_FILE_NAME, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Share KRS Gold & Silver APK").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Grant URI permission to all potential matching activities
            val resInfoList = context.packageManager.queryIntentActivities(
                chooser,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
