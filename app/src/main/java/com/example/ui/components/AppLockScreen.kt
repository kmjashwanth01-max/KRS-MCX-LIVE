package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.util.UserAccountManager
import com.example.util.UserProfile

@Composable
fun AppLockScreen(
    user: UserProfile?,
    onUnlockSuccess: () -> Unit,
    onSwitchAccountClicked: () -> Unit,
    onBiometricClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-prompt biometric authentication once if enabled and hardware is available
    LaunchedEffect(user?.id) {
        if (user?.isBiometricEnabled == true &&
            onBiometricClicked != null &&
            com.example.util.BiometricAuthManager.isBiometricAvailable(context)
        ) {
            onBiometricClicked()
        }
    }

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                // Auto verify on 4th digit
                if (UserAccountManager.verifyPin(newPin)) {
                    UserAccountManager.unlockApp()
                    onUnlockSuccess()
                } else {
                    errorMessage = "Incorrect 4-digit PIN. Try again."
                    enteredPin = ""
                }
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    fun onClear() {
        enteredPin = ""
        errorMessage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TitaniumBackground)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // K.R.S. Gold & Silver Brand Logo & JASHX AI Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_krs_app_logo),
                    contentDescription = "K.R.S. Gold & Silver Logo",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                    contentDescription = "JASHX AI Logo",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "K.R.S. BULLION VAULT",
                color = GoldPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "Powered by JASHX AI • 4-Digit PIN or Biometrics",
                color = Color.LightGray,
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // User Profile Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TitaniumCardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user?.initials ?: "KR",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = user?.name ?: "K.M. Jashwanth",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${user?.role ?: "Bullion Trader"})",
                        color = GoldLight,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4-PIN Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) GoldPrimary else TitaniumCardBg)
                            .border(
                                1.5.dp,
                                if (isFilled) GoldPrimary else TitaniumBorder,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Biometric Quick Unlock Option (if supported or enabled)
            if (onBiometricClicked != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GoldPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { onBiometricClicked() }
                        .testTag("btn_biometric_unlock")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap to Scan Fingerprint / Face",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Error Message Pill
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3E1212),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // NUMERIC KEYPAD 3x4
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "DEL")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keypad.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { key ->
                            KeypadButton(
                                label = key,
                                onClick = {
                                    when (key) {
                                        "C" -> onClear()
                                        "DEL" -> onBackspace()
                                        else -> onDigitPress(key)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Device Isolation Notice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TitaniumCardSurface,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Device Locked",
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Device-Isolated Vault • Single Account Protected",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = TitaniumCardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
        modifier = Modifier
            .size(68.dp)
            .clickable { onClick() }
            .testTag("keypad_$label")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (label) {
                "DEL" -> {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Delete",
                        tint = GoldLight,
                        modifier = Modifier.size(22.dp)
                    )
                }
                "C" -> {
                    Text(
                        text = "CLR",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
