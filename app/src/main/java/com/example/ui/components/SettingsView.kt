package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.BuildConfig
import com.example.R
import com.example.data.repository.AdminCloudDatabaseRepository
import com.example.data.repository.CloudAppInstallation
import com.example.data.repository.CloudAppUpdateConfig
import com.example.data.repository.CloudAppUpdateRecord
import com.example.ui.theme.*
import com.example.util.ApkExportHelper
import com.example.util.AppUpdateInstaller
import com.example.util.BiometricAuthManager
import com.example.util.UserAccountManager
import com.example.util.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SettingsView(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLockAppNow: () -> Unit = {},
    onTestBiometric: (() -> Unit)? = null,
    onSignOut: () -> Unit = {},
    appUpdateConfig: CloudAppUpdateConfig? = null,
    updatesHistory: List<CloudAppUpdateRecord> = emptyList(),
    appInstallations: List<CloudAppInstallation> = emptyList(),
    isCheckingForUpdate: Boolean = false,
    updateCheckMessage: String? = null,
    isPublishingUpdate: Boolean = false,
    updatePublishMessage: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onLoadInstallations: () -> Unit = {},
    onPublishAppUpdate: (
        latestVersionCode: Long,
        latestVersionName: String,
        apkDownloadUrl: String,
        releaseNotes: String,
        isForceUpdate: Boolean,
        isUpdateEnabled: Boolean,
        targetUsername: String,
        alsoBroadcastAnnouncement: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by UserAccountManager.currentUser.collectAsState()
    val allAccounts by UserAccountManager.allAccounts.collectAsState()

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var showGitHubPublishDialog by remember { mutableStateOf(false) }

    var googleSignedInEmail by remember {
        mutableStateOf(
            try { Firebase.auth.currentUser?.email.orEmpty() } catch (_: Throwable) { "" }
        )
    }
    val isGoogleAdmin = googleSignedInEmail.trim().lowercase() in AdminCloudDatabaseRepository.ADMIN_EMAILS
    val isLocalAdmin = (currentUser?.username?.equals("jashwanth", ignoreCase = true) == true) ||
        (currentUser?.email?.trim()?.lowercase() in AdminCloudDatabaseRepository.ADMIN_EMAILS) ||
        (currentUser?.role?.contains("Administrator", ignoreCase = true) == true)

    var versionCodeInput by remember { mutableStateOf((maxOf(BuildConfig.VERSION_CODE.toLong() + 1L, appUpdateConfig?.latestVersionCode ?: 2L)).toString()) }
    var versionNameInput by remember { mutableStateOf(appUpdateConfig?.latestVersionName?.takeIf { it != BuildConfig.VERSION_NAME } ?: "1.1") }
    var apkUrlInput by remember { mutableStateOf(appUpdateConfig?.apkDownloadUrl.orEmpty()) }
    var releaseNotesInput by remember {
        mutableStateOf(
            appUpdateConfig?.releaseNotes?.ifBlank { null }
                ?: "• Real-time market open/closed red & green sync\n• Physical Bullion closes automatically when market is closed\n• Performance & stability improvements"
        )
    }
    var targetUsernameInput by remember { mutableStateOf(appUpdateConfig?.targetUsername.orEmpty()) }
    var updateEnabledInput by remember { mutableStateOf(appUpdateConfig?.isUpdateEnabled ?: true) }
    var forceUpdateInput by remember { mutableStateOf(appUpdateConfig?.isForceUpdate ?: false) }
    var alsoBroadcastNoticeInput by remember { mutableStateOf(true) }

    LaunchedEffect(isGoogleAdmin) {
        if (isGoogleAdmin) {
            onLoadInstallations()
        }
    }

    LaunchedEffect(appUpdateConfig) {
        if (appUpdateConfig != null) {
            if (appUpdateConfig.latestVersionCode > BuildConfig.VERSION_CODE.toLong()) {
                versionCodeInput = appUpdateConfig.latestVersionCode.toString()
                versionNameInput = appUpdateConfig.latestVersionName
            }
            if (apkUrlInput.isBlank() && appUpdateConfig.apkDownloadUrl.isNotBlank()) {
                apkUrlInput = appUpdateConfig.apkDownloadUrl
            }
            if (appUpdateConfig.releaseNotes.isNotBlank() && appUpdateConfig.latestVersionCode > 1L) {
                releaseNotesInput = appUpdateConfig.releaseNotes
            }
            if (targetUsernameInput.isBlank() && appUpdateConfig.targetUsername.isNotBlank()) {
                targetUsernameInput = appUpdateConfig.targetUsername
            }
            updateEnabledInput = if (appUpdateConfig.latestVersionCode > BuildConfig.VERSION_CODE.toLong()) appUpdateConfig.isUpdateEnabled else true
            forceUpdateInput = appUpdateConfig.isForceUpdate
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ACTIVE USER PROFILE & ACCOUNT CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().testTag("active_account_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                                .border(1.5.dp, GoldLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser?.initials ?: "KR",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = currentUser?.name ?: "K.M. Jashwanth",
                                color = TextWhitePrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${currentUser?.username ?: "jashwanth"} • ${currentUser?.role ?: "Bullion Trader"} • ${currentUser?.city ?: "Coimbatore"}",
                                color = GoldLight,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Saved Theme: ${if (isDarkMode) "Dark Titanium" else "Light Platinum"}",
                                color = MarketGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Actions: Create New Account or Sign Out to Login Screen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCreateAccountDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_create_individual_account")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Create Account", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            UserAccountManager.signOut()
                            onSignOut()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_sign_out_account")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Sign Out / Login", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                com.example.util.GoogleSignInButton(
                    onAuthSuccess = { firebaseUser ->
                        googleSignedInEmail = firebaseUser.email.orEmpty()
                        UserAccountManager.onGoogleSignIn(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email,
                            displayName = firebaseUser.displayName
                        )
                        Toast.makeText(
                            context,
                            "Signed in with Google as ${firebaseUser.displayName ?: firebaseUser.email ?: "Trader"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onAuthError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }



        // 4. PERSONAL ACCOUNT DATA ISOLATION & SECURITY (WhatsApp-Style Architecture)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth().testTag("registered_accounts_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device-Isolated Account Security",
                        color = TextWhitePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(shape = RoundedCornerShape(4.dp), color = MarketGreenGlow) {
                        Text(
                            "SINGLE OWNER PRIVACY",
                            color = MarketGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Like WhatsApp, this installation is strictly bound to your device. No other user can see, access, or switch into your account, PIN, or private JASHX AI histories from their device.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { accountToDelete = currentUser },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                    modifier = Modifier.fillMaxWidth().height(38.dp).testTag("btn_reset_account_on_device")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset / Unlink My Account from this Device", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. THEME & DISPLAY SETTINGS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth().testTag("theme_settings_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Theme Mode",
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isDarkMode) "Dark Titanium Mode" else "Light Platinum Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "High-contrast bullion display • Rates untouched",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDimTertiary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                )
            }
        }

        // 5b. APK EXPORT & GITHUB PUBLISHING FOR PUBLIC ACCESS
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().testTag("apk_export_settings_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download APK",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Installable APK & Public Release",
                                color = TextWhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Direct download, share & GitHub access",
                                color = GoldLight,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MarketGreenGlow) {
                        Text(
                            "v1.0 APK",
                            color = MarketGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Share the exact installed APK file from this device via WhatsApp, Drive, or Telegram, or publish to GitHub so the general public can download without login or page not found errors.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Direct Save APK to Downloads Folder (Physical .apk file on device)
                    Button(
                        onClick = {
                            ApkExportHelper.saveApkToDownloads(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_save_apk_to_downloads")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save APK to Phone Downloads", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 2. Share APK File (Attaches actual 28MB .apk file via ClipData & FileProvider)
                        OutlinedButton(
                            onClick = {
                                ApkExportHelper.shareApkFile(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("btn_share_apk_file")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldLight, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share APK File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 3. GitHub & Public Release Guide Button
                        OutlinedButton(
                            onClick = { showGitHubPublishDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("btn_github_release_guide")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GoldLight, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub Links", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5c. IN-APP UPDATES (OTA FROM CLOUD DATABASE `/updates`)
        val hasRemoteUpdate = isRemoteUpdateAvailable(appUpdateConfig, currentUser?.username)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                if (hasRemoteUpdate) MarketGreen else TitaniumBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_update_settings_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasRemoteUpdate) MarketGreen.copy(alpha = 0.2f)
                                    else GoldPrimary.copy(alpha = 0.18f)
                                )
                                .border(
                                    1.dp,
                                    if (hasRemoteUpdate) MarketGreen else GoldPrimary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "App Updates",
                                tint = if (hasRemoteUpdate) MarketGreen else GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App Updates",
                                color = TextWhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = GoldLight,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasRemoteUpdate) MarketGreenGlow else TitaniumBackground
                    ) {
                        Text(
                            text = if (hasRemoteUpdate) "v${appUpdateConfig?.latestVersionName} NEW" else "UP TO DATE",
                            color = if (hasRemoteUpdate) MarketGreen else Color.LightGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (hasRemoteUpdate) {
                        "A newer release (v${appUpdateConfig?.latestVersionName} • Build ${appUpdateConfig?.latestVersionCode}) is stored in Cloud Database (/updates). Tap Update Now to download and install."
                    } else {
                        "Automatically synced with KRS Cloud Database (/updates) so every new release stored there reaches all users immediately."
                    },
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                if (!updateCheckMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = updateCheckMessage,
                        color = if (hasRemoteUpdate) MarketGreen else GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !isCheckingForUpdate,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_check_for_updates")
                    ) {
                        if (isCheckingForUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GoldPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check for Updates", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (hasRemoteUpdate) {
                        Button(
                            onClick = onOpenUpdateDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MarketGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_settings_update_now")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Now", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Release History from `/updates` database collection
                val visibleUpdates = updatesHistory.filter {
                    it.toConfig().appliesToUser(currentUser?.username)
                }
                if (visibleUpdates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = TitaniumBorder)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "STORED RELEASES IN DATABASE (/updates • ${visibleUpdates.size})",
                        color = GoldLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    visibleUpdates.take(5).forEach { rel ->
                        val isInstalledOrOlder = rel.versionCode <= BuildConfig.VERSION_CODE.toLong()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TitaniumBackground,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (rel.isUpdateEnabled && !isInstalledOrOlder) MarketGreen.copy(alpha = 0.7f) else TitaniumBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "v${rel.versionName} (Build ${rel.versionCode})",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (rel.targetUsername.isNotBlank()) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = GoldPrimary.copy(alpha = 0.2f)) {
                                                Text(
                                                    text = "@${rel.targetUsername}",
                                                    color = GoldLight,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when {
                                                !isInstalledOrOlder && rel.isUpdateEnabled -> MarketGreenGlow
                                                isInstalledOrOlder && rel.versionCode == BuildConfig.VERSION_CODE.toLong() -> GoldPrimary.copy(alpha = 0.2f)
                                                else -> Color(0xFF1E293B)
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    !isInstalledOrOlder && rel.isUpdateEnabled -> "AVAILABLE"
                                                    rel.versionCode == BuildConfig.VERSION_CODE.toLong() -> "INSTALLED"
                                                    !rel.isUpdateEnabled -> "DRAFT"
                                                    else -> "ARCHIVE"
                                                },
                                                color = when {
                                                    !isInstalledOrOlder && rel.isUpdateEnabled -> MarketGreen
                                                    rel.versionCode == BuildConfig.VERSION_CODE.toLong() -> GoldPrimary
                                                    else -> Color.LightGray
                                                },
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (rel.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = rel.releaseNotes,
                                        color = Color.LightGray,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (!isInstalledOrOlder && rel.isUpdateEnabled && rel.apkDownloadUrl.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            AppUpdateInstaller.startUpdateDownloadAndInstall(
                                                context = context,
                                                rawUrl = rel.apkDownloadUrl,
                                                versionName = rel.versionName
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MarketGreen,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install v${rel.versionName}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5d. ADMIN CLOUD UPDATE PUBLISHER (STORES IN `/updates` COLLECTION + NOTIFIES USERS)
        if (isGoogleAdmin || isLocalAdmin) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_app_update_publisher_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Admin: Push App Update (Database)",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Stores release in Firestore /updates & pushes to users",
                                color = Color.LightGray,
                                fontSize = 10.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isGoogleAdmin) MarketGreenGlow else Color(0xFF450A0A)
                        ) {
                            Text(
                                text = if (isGoogleAdmin) "ADMIN VERIFIED" else "GOOGLE SIGN-IN NEEDED",
                                color = if (isGoogleAdmin) MarketGreen else Color(0xFFFF8A80),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (!isGoogleAdmin) {
                        Text(
                            text = "Sign in with Admin Google account (kmjashwanth01@gmail.com or krsmcxofficial@gmail.com) to publish updates securely:",
                            color = Color(0xFFFFC5C5),
                            fontSize = 11.sp
                        )
                        com.example.util.GoogleSignInButton(
                            onAuthSuccess = { firebaseUser ->
                                googleSignedInEmail = firebaseUser.email.orEmpty()
                                UserAccountManager.onGoogleSignIn(
                                    uid = firebaseUser.uid,
                                    email = firebaseUser.email,
                                    displayName = firebaseUser.displayName
                                )
                            },
                            onAuthError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = versionCodeInput,
                            onValueChange = { versionCodeInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Version Code (e.g. 2)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = versionNameInput,
                            onValueChange = { versionNameInput = it },
                            label = { Text("Version Name (e.g. 1.1)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = apkUrlInput,
                        onValueChange = { apkUrlInput = it },
                        label = { Text("APK Download URL (GitHub / Drive / Direct https://)", fontSize = 11.sp) },
                        placeholder = { Text("https://github.com/.../releases/latest/download/KRS-Gold-Silver-Live-MCX.apk", fontSize = 10.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetUsernameInput,
                        onValueChange = { targetUsernameInput = it },
                        label = { Text("Target User (@username, blank = ALL Users)", fontSize = 11.sp) },
                        placeholder = { Text("Leave blank to push to ALL 20+ users", fontSize = 10.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = releaseNotesInput,
                        onValueChange = { releaseNotesInput = it },
                        label = { Text("Release Notes (What's New)", fontSize = 11.sp) },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable In-App Update Prompt", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Notify targeted users with older builds", color = Color.LightGray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = updateEnabledInput,
                            onCheckedChange = { updateEnabledInput = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Required Update", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Hide 'Later' button until user updates", color = Color.LightGray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = forceUpdateInput,
                            onCheckedChange = { forceUpdateInput = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF5252))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Also Notify in Notices Tab", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Auto-post release announcement to Notices", color = Color.LightGray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = alsoBroadcastNoticeInput,
                            onCheckedChange = { alsoBroadcastNoticeInput = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                        )
                    }

                    if (!updatePublishMessage.isNullOrBlank()) {
                        Text(
                            text = updatePublishMessage,
                            color = if (updatePublishMessage.startsWith("Publish failed", ignoreCase = true)) {
                                Color(0xFFFF8A80)
                            } else {
                                MarketGreen
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            val code = versionCodeInput.toLongOrNull() ?: (BuildConfig.VERSION_CODE.toLong() + 1L)
                            onPublishAppUpdate(
                                code,
                                versionNameInput.ifBlank { "1.1" },
                                apkUrlInput.trim(),
                                releaseNotesInput.trim(),
                                forceUpdateInput,
                                updateEnabledInput,
                                targetUsernameInput.trim(),
                                alsoBroadcastNoticeInput
                            )
                        },
                        enabled = !isPublishingUpdate && isGoogleAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_publish_app_update")
                    ) {
                        if (isPublishingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving to /updates...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Push Update to Database", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    if (appUpdateConfig != null) {
                        OutlinedButton(
                            onClick = onOpenUpdateDialog,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_preview_update_popup")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview Update Popup", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isGoogleAdmin) {
                        HorizontalDivider(color = TitaniumBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "User App Versions (${appInstallations.size})",
                                color = GoldLight,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = onLoadInstallations,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh User Versions", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (appInstallations.isEmpty()) {
                            Text(
                                text = "User build versions appear here automatically when traders open the app.",
                                color = Color.LightGray,
                                fontSize = 10.5.sp
                            )
                        } else {
                            val latestTargetBuild = appUpdateConfig?.latestVersionCode ?: BuildConfig.VERSION_CODE.toLong()
                            appInstallations.take(15).forEach { inst ->
                                val isCurrent = inst.versionCode >= latestTargetBuild
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${inst.username} • ${inst.deviceModel}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "v${inst.versionName} (Build ${inst.versionCode})",
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isCurrent) MarketGreenGlow else Color(0xFF451A03)
                                    ) {
                                        Text(
                                            text = if (isCurrent) "LATEST" else "OUTDATED",
                                            color = if (isCurrent) MarketGreen else GoldPrimary,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. JASHX AI ENGINE BRANDING & LOGO CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldPrimary),
            modifier = Modifier.fillMaxWidth().testTag("jashx_ai_settings_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                    contentDescription = "JASHX AI Logo",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Powered by JashX AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Think • Create • Achieve • Your AI Companion",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Autonomous Intelligence Engine v1.0 • Built by K.M. Jashwanth",
                        color = TextDimTertiary,
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }

    // CONFIRM UNLINK ACCOUNT FROM DEVICE DIALOG (KEEPS ACCOUNT SYNCED IN DATABASE)
    val deletingUser = accountToDelete
    if (deletingUser != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = {
                Text("Unlink Account from Device", fontWeight = FontWeight.Bold, color = GoldPrimary)
            },
            text = {
                Text(
                    text = "Unlink '@${deletingUser.username}' (${deletingUser.name}) from the active session on this device?\n\nYour account details and saved theme remain safely synced in the database so you can sign back in anytime on the Sign In page.",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        UserAccountManager.deleteAccount(deletingUser.id)
                        Toast.makeText(
                            context,
                            "Account '@${deletingUser.username}' unlinked from session. Sign in anytime with your Username.",
                            Toast.LENGTH_LONG
                        ).show()
                        accountToDelete = null
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Unlink & Go to Sign In", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = TitaniumDarkSurface
        )
    }

    // CREATE ACCOUNT MODAL DIALOG
    if (showCreateAccountDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateAccountDialog = false },
            onAccountCreated = { newProfile ->
                showCreateAccountDialog = false
                Toast.makeText(context, "Welcome, ${newProfile.name}! Account registered.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // GITHUB PUBLISHING & PUBLIC DOWNLOAD DIALOG
    if (showGitHubPublishDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubPublishDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Public APK Download & GitHub", fontWeight = FontWeight.Bold, color = GoldPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "To allow ANY public user to download your app without encountering 'Page not found' or permission errors:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. AI Studio Top Menu Download:",
                                color = GoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "In the AI Studio top navigation bar, click the 'Export' or 'Download' icon (next to Share) -> select 'Download APK' or 'Export as ZIP' to download the APK file directly to your computer or phone.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "2. Automated Public GitHub Release:",
                                color = GoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "In the top menu, click 'Push to GitHub'. Once pushed, open your repo on GitHub.com -> Actions -> 'set up a workflow yourself' and paste 'docs/android-apk-release.yml' to build and publish 'KRS-Gold-Silver-Live-MCX.apk' under GitHub Releases.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TitaniumBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "3. Instant Device-to-Device Share:",
                                color = GoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "Tap the 'Share APK' button above to send the APK file directly via WhatsApp, Telegram, Email, Bluetooth, or Save to Downloads.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(
                            "KRS Bullion APK Download Guide",
                            "KRS Gold & Silver Live MCX APK: Download via AI Studio Top Toolbar > Export APK, or Push to GitHub to get a direct public release APK link under GitHub Releases."
                        )
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Instructions copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showGitHubPublishDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Copy Guide & Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showGitHubPublishDialog = false }) {
                    Text("Close", color = Color.LightGray)
                }
            },
            containerColor = TitaniumDarkSurface
        )
    }
}



