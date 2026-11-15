package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.data.repository.CloudAppUpdateConfig
import com.example.ui.theme.*
import com.example.util.ApkDownloadState
import com.example.util.AppUpdateInstaller

fun isRemoteUpdateAvailable(
    config: CloudAppUpdateConfig?,
    currentUsername: String? = null
): Boolean {
    if (config == null) return false
    return config.isUpdateEnabled &&
        config.latestVersionCode > BuildConfig.VERSION_CODE.toLong() &&
        config.appliesToUser(currentUsername)
}

/**
 * Compact in-app update banner (WhatsApp/Instagram style) shown when an update is available
 * and the user tapped "Later", allowing 1-tap return to the update prompt.
 */
@Composable
fun AppUpdateBanner(
    config: CloudAppUpdateConfig,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF102A1D) else Color(0xFFECFDF5)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MarketGreen),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDialog() }
            .testTag("app_update_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MarketGreen.copy(alpha = 0.2f))
                        .border(1.dp, MarketGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "App Update Available",
                        tint = if (isDark) MarketGreen else MarketGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Update Available • v${config.latestVersionName}",
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to update from v${BuildConfig.VERSION_NAME} to v${config.latestVersionName}",
                        color = if (isDark) MarketGreen else MarketGreenDark,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MarketGreen
            ) {
                Text(
                    text = "UPDATE",
                    color = Color.Black,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * WhatsApp/Instagram-style App Update Modal Dialog driven by `/app_config/app_update` in Firestore.
 * Supports direct in-app APK download & Android Package Installer launch + browser fallback.
 */
@Composable
fun AppUpdateDialog(
    config: CloudAppUpdateConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val downloadState by AppUpdateInstaller.downloadState.collectAsState()
    val hasValidUrl = config.apkDownloadUrl.trim().let {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }

    Dialog(
        onDismissRequest = {
            if (!config.isForceUpdate && downloadState !is ApkDownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !config.isForceUpdate,
            dismissOnClickOutside = !config.isForceUpdate
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) TitaniumDarkSurface else Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GoldPrimary.copy(alpha = 0.25f), GoldLight.copy(alpha = 0.1f))
                            )
                        )
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_krs_app_logo),
                        contentDescription = "KRS Gold & Silver Update",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (config.isForceUpdate) Color(0xFF7F1D1D) else MarketGreenGlow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = if (config.isForceUpdate) Color(0xFFFF8A80) else MarketGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (config.isForceUpdate) "REQUIRED UPDATE" else "NEW UPDATE AVAILABLE",
                            color = if (config.isForceUpdate) Color(0xFFFFC5C5) else MarketGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Update KRS Gold & Silver",
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})  →  v${config.latestVersionName} (Build ${config.latestVersionCode})",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Release Notes Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) TitaniumBackground else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) TitaniumBorder else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "WHAT'S NEW IN v${config.latestVersionName}",
                            color = if (isDark) GoldLight else Color(0xFFB45309),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = config.releaseNotes.ifBlank {
                                "• Real-time 1-second bullion & market status sync\n• Physical Bullion open/close parity improvements\n• Performance and stability upgrades"
                            },
                            color = if (isDark) Color.LightGray else Color(0xFF334155),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // In-App Download Progress / Ready-to-Install Feedback
                when (val state = downloadState) {
                    is ApkDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            color = MarketGreen,
                            trackColor = TitaniumBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading APK... ${state.progressPercent}% (${state.downloadedMb} / ${state.totalMb})",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is ApkDownloadState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Update downloaded! Tap 'Install v${state.versionName}' if the installer did not open automatically.",
                            color = MarketGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is ApkDownloadState.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.message,
                            color = Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    else -> {}
                }

                if (!hasValidUrl) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Admin has enabled v${config.latestVersionName}, but the APK download link is not added in Database yet.",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action Button: Downloads APK in-app and launches Package Installer
                val readyState = downloadState as? ApkDownloadState.ReadyToInstall
                Button(
                    onClick = {
                        if (readyState != null) {
                            AppUpdateInstaller.launchApkInstaller(context, readyState.apkFile)
                        } else {
                            AppUpdateInstaller.startUpdateDownloadAndInstall(
                                context = context,
                                rawUrl = config.apkDownloadUrl,
                                versionName = config.latestVersionName
                            )
                        }
                    },
                    enabled = hasValidUrl && downloadState !is ApkDownloadState.Downloading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MarketGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                        disabledContentColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_update_now")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            readyState != null -> "Install v${readyState.versionName} Now"
                            downloadState is ApkDownloadState.Downloading -> "Downloading Update..."
                            else -> "Download & Install (v${config.latestVersionName})"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (hasValidUrl) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { AppUpdateInstaller.openUrlInBrowser(context, config.apkDownloadUrl.trim()) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_open_update_browser")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in Browser",
                                tint = GoldLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Browser", color = GoldLight, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("APK Update Link", config.apkDownloadUrl.trim()))
                                Toast.makeText(context, "Download link copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_copy_update_link")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Download Link",
                                tint = GoldLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Link", color = GoldLight, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (!config.isForceUpdate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_update_later")
                    ) {
                        Text(
                            text = "Later",
                            color = if (isDark) Color.LightGray else Color(0xFF475569),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
