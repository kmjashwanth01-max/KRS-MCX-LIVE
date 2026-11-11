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
import com.example.R
import com.example.ui.theme.*
import com.example.util.ApkExportHelper
import com.example.util.BiometricAuthManager
import com.example.util.UserAccountManager
import com.example.util.UserProfile

@Composable
fun SettingsView(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLockAppNow: () -> Unit,
    onTestBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by UserAccountManager.currentUser.collectAsState()
    val allAccounts by UserAccountManager.allAccounts.collectAsState()

    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var showGitHubPublishDialog by remember { mutableStateOf(false) }

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

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentUser?.isAppLockEnabled == true) MarketGreenGlow else Color(0xFF332010)
                    ) {
                        Text(
                            text = if (currentUser?.isAppLockEnabled == true) "LOCK ON" else "LOCK OFF",
                            color = if (currentUser?.isAppLockEnabled == true) MarketGreen else GoldLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
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
                        onClick = { UserAccountManager.signOut() },
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

        // 2. APP LOCK & 4-DIGIT PIN SECURITY SECTION
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
            modifier = Modifier.fillMaxWidth().testTag("app_lock_settings_card")
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "App Lock",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App Lock (4-Digit PIN)",
                                color = TextWhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lock app using custom 4-digit numeric code",
                                color = TextMutedSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = currentUser?.isAppLockEnabled == true,
                        onCheckedChange = { enabled ->
                            UserAccountManager.setAppLockEnabled(enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "App Lock enabled with 4-digit PIN" else "App Lock disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "When active, entering your personal 4-digit PIN or scanning your biometric fingerprint/face unlocks the app.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Change PIN Button
                    OutlinedButton(
                        onClick = { showChangePinDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_change_pin")
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null, tint = GoldLight, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change 4-Digit PIN", color = GoldLight, fontSize = 11.sp)
                    }

                    // Lock App Now Button
                    Button(
                        onClick = onLockAppNow,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C14), contentColor = GoldPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("btn_lock_app_now")
                    ) {
                        Icon(Icons.Default.LockClock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock App Now", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. BIOMETRIC AUTHENTICATION (FINGERPRINT / FACE UNLOCK) CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TitaniumDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("biometric_settings_card")
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Biometric Authentication",
                                color = TextWhitePrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fingerprint / Face Unlock alternative",
                                color = GoldLight,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = currentUser?.isBiometricEnabled == true,
                        onCheckedChange = { enabled ->
                            UserAccountManager.setBiometricEnabled(enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "Biometric unlock enabled" else "Biometric unlock disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary),
                        modifier = Modifier.testTag("toggle_biometric_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Hardware Status: ${BiometricAuthManager.canAuthenticateStatus(context)}",
                    color = Color.LightGray,
                    fontSize = 10.5.sp
                )

                if (onTestBiometric != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onTestBiometric,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("btn_test_biometric")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Fingerprint / Face Prompt", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                    text = "Unlink '@${deletingUser.username}' (${deletingUser.name}) from the active session on this device?\n\nYour account, Password / PIN, and saved theme remain safely synced in the database so you can sign back in anytime on the Sign In page.",
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
                            "Account '@${deletingUser.username}' unlinked from session. Sign in anytime with your Username & PIN.",
                            Toast.LENGTH_LONG
                        ).show()
                        accountToDelete = null
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
                Toast.makeText(context, "Welcome, ${newProfile.name}! Account registered with custom 4-digit PIN.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // CHANGE PIN DIALOG
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onPinChanged = {
                showChangePinDialog = false
                Toast.makeText(context, "4-digit PIN updated successfully!", Toast.LENGTH_SHORT).show()
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
                                text = "In the top menu, click 'Push to GitHub'. The included GitHub Actions workflow (.github/workflows/android-apk-release.yml) automatically builds and publishes 'KRS-Gold-Silver-Live-MCX.apk' under GitHub Releases. Anyone worldwide can download and install it freely without needing any Google login!",
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

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onPinChanged: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TitaniumDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Change 4-Digit Security PIN",
                    color = TextWhitePrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter current PIN, then choose a new 4-digit code.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 4) oldPin = it },
                    label = { Text("Current PIN", fontSize = 11.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it },
                    label = { Text("New 4-Digit PIN", fontSize = 11.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 4) confirmNewPin = it },
                    label = { Text("Confirm New PIN", fontSize = 11.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = Color(0xFFFF5252), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (!UserAccountManager.verifyPin(oldPin)) {
                                error = "Current PIN is incorrect"
                                return@Button
                            }
                            if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
                                error = "New PIN must be exactly 4 digits"
                                return@Button
                            }
                            if (newPin != confirmNewPin) {
                                error = "New PINs do not match"
                                return@Button
                            }
                            UserAccountManager.changePin(newPin)
                            onPinChanged()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("Save PIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

